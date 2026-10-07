package mx.sisetracker.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.sisetracker.container
import mx.sisetracker.core.CaseLookup
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.core.Circuito
import mx.sisetracker.core.Circuitos
import mx.sisetracker.core.FormOption
import mx.sisetracker.core.Materia
import mx.sisetracker.core.OrganoClase
import mx.sisetracker.core.OrganoKind
import mx.sisetracker.core.SearchText
import mx.sisetracker.core.TipoProcedimientoRule
import mx.sisetracker.core.TiposDeAsunto
import mx.sisetracker.data.cases.SavedCases
import mx.sisetracker.data.catalog.CatalogRepository
import mx.sisetracker.data.db.SavedOrgano
import mx.sisetracker.data.lookup.LookupRepository
import mx.sisetracker.data.net.PortalResult
import mx.sisetracker.data.net.portalCall
import mx.sisetracker.data.settings.RecentOrgano
import mx.sisetracker.data.settings.SettingsStore
import mx.sisetracker.ui.SearchRoute

/**
 * The native search screen. Every portal request here is user-triggered: a
 * circuit's órganos load when the circuit is picked or the Órgano field is
 * opened, the tipos when an órgano is picked or their dropdown is opened,
 * procedimientos when a tipo that needs them is picked, and the lookup on
 * "Buscar" (one per tap; the button is disabled while it runs). Anything
 * cached shows without a request.
 */
class SearchViewModel(
    private val catalog: CatalogRepository,
    private val lookupRepository: LookupRepository,
    private val cases: SavedCases,
    private val settings: SettingsStore,
    private val prefill: SearchRoute = SearchRoute(),
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState(expediente = prefill.expediente.orEmpty()))
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var organosJob: Job? = null
    private var tiposJob: Job? = null
    private var procedimientosJob: Job? = null

    init {
        viewModelScope.launch {
            // Only a circuit from the list: earlier versions took any typed number.
            val last = settings.lastCircuito.first()?.let(Circuitos::byNum)
            if (last != null && _state.value.circuito.isEmpty()) switchCircuito(last.num, fetch = false)
        }
        viewModelScope.launch {
            var prefilled = false
            combine(settings.recentOrganos, cases.observeSavedOrganos(), ::knownOrganos).collect { (known, saved) ->
                _state.update { it.copy(knownOrganos = known) }
                if (!prefilled) {
                    prefilled = true
                    applyPrefill(known, saved)
                }
            }
        }
    }

    fun onCircuitoSelected(circuito: Circuito) {
        if (circuito.num == _state.value.circuito) return
        switchCircuito(circuito.num, fetch = true)
        viewModelScope.launch { settings.setLastCircuito(circuito.num) }
    }

    /** The user opened the Órgano field: load the circuit's list if it isn't there yet. */
    fun onOrganosRequested() {
        when (_state.value.organos) {
            Loadable.Idle, is Loadable.Failed -> loadOrganos(fetch = true)
            else -> Unit
        }
    }

    /** A new kind resets the narrower filters, which may not apply to it. */
    fun onKindFilterChange(kind: OrganoKind?) {
        _state.update { it.copy(kindFilter = kind, claseFilter = null).keepingMateria() }
    }

    fun onClaseFilterChange(clase: OrganoClase?) {
        _state.update { it.copy(claseFilter = clase).keepingMateria() }
    }

    fun onMateriaFilterChange(materia: Materia?) {
        _state.update { it.copy(materiaFilter = materia) }
    }

    fun onShowClosedChange(show: Boolean) {
        _state.update { it.copy(showClosed = show) }
    }

    /** Drops the materia filter when no órgano left by the other filters hears it. */
    private fun SearchUiState.keepingMateria(): SearchUiState =
        if (materiaFilter == null || ofClase.any { materiaFilter in it.materias }) this else copy(materiaFilter = null)

    /** Free text: a known órgano's name, or a bare organismo number. */
    fun onOrganoTextChange(text: String) {
        val state = _state.value
        val trimmed = text.trim()
        val organo = when {
            state.organo != null && state.organo.name.isNotEmpty() && text == state.organo.name -> state.organo
            trimmed.isNotEmpty() && trimmed.all(Char::isDigit) ->
                (state.organos as? Loadable.Loaded)?.value?.firstOrNull { it.id == trimmed }
                    ?: state.knownOrganos.firstOrNull { it.id == trimmed }
                    ?: KnownOrgano(id = trimmed, name = "", circuito = state.circuito.ifBlank { null })
            else -> null
        }
        setOrgano(organo, text)
    }

    fun onOrganoSelected(organo: KnownOrgano) {
        viewModelScope.launch {
            val circuito = organo.circuito ?: circuitoOf(organo.id)
            if (circuito != null && circuito != _state.value.circuito) switchCircuito(circuito, fetch = false)
            setOrgano(organo.copy(circuito = circuito), organo.name)
            showTiposAsunto()
        }
    }

    /** The user opened the "Tipo de asunto" dropdown. */
    fun onTiposAsuntoRequested() {
        when (_state.value.tiposAsunto) {
            Loadable.Idle -> showTiposAsunto()
            is Loadable.Failed -> loadTiposAsunto()
            else -> Unit
        }
    }

    /** "Ver solo los tipos de este órgano": one request for the órgano's own list (step C). */
    fun onLoadOrganoTipos() {
        if (_state.value.tiposFromPortal || _state.value.tiposAsunto == Loadable.Loading) return
        loadTiposAsunto()
    }

    fun onTipoAsuntoSelected(option: FormOption) {
        procedimientosJob?.cancel()
        _state.update {
            it.copy(
                tipoAsunto = option,
                tiposProcedimiento = Loadable.Idle,
                tipoProcedimiento = null,
                lookup = LookupState.None,
            )
        }
        if (TipoProcedimientoRule.isShown(option.value)) loadTiposProcedimiento()
    }

    /** The user opened the "Tipo de procedimiento" dropdown. */
    fun onTiposProcedimientoRequested() {
        when (_state.value.tiposProcedimiento) {
            Loadable.Idle, is Loadable.Failed -> loadTiposProcedimiento()
            else -> Unit
        }
    }

    fun onTipoProcedimientoSelected(option: FormOption) {
        _state.update { it.copy(tipoProcedimiento = option, lookup = LookupState.None) }
    }

    fun onExpedienteChange(text: String) {
        _state.update {
            it.copy(expediente = text.take(SearchUiState.MAX_EXPEDIENTE_LENGTH), lookup = LookupState.None)
        }
    }

    /** "Buscar": builds the case URL from the choices and fetches it once. */
    fun onSearch() {
        val state = _state.value
        if (!state.canSearch) return
        val organo = state.organo ?: return
        val tipoAsunto = state.tipoAsunto ?: return
        val url = CaseUrl.forLookup(
            organismo = organo.id,
            tipoAsunto = tipoAsunto.value,
            tipoProcedimientoShown = state.showsProcedimiento,
            tipoProcedimiento = state.tipoProcedimiento?.value,
            expediente = state.expediente,
        )
        _state.update { it.copy(lookup = LookupState.Loading) }
        viewModelScope.launch {
            settings.setLastCircuito(state.circuito)
            val result = portalCall { lookupRepository.lookup(url) }
            val lookup = when (result) {
                is PortalResult.Failed -> LookupState.Failed(result.error)
                is PortalResult.Ok -> when (val found = result.value) {
                    is CaseLookup.Found -> LookupState.Found(url, found.page, cases.isSaved(found.page.neun))
                    CaseLookup.NotFound -> LookupState.NotFound
                }
            }
            _state.update { current ->
                if (lookup is LookupState.Found && current.organo?.id == organo.id && current.organo.name.isEmpty()) {
                    // A bare organismo number: show the name the portal just gave.
                    val named = current.organo.copy(name = lookup.page.organoName)
                    current.copy(lookup = lookup, organo = named, organoText = named.name)
                } else {
                    current.copy(lookup = lookup)
                }
            }
            if (lookup is LookupState.Found) {
                settings.addRecentOrgano(RecentOrgano(organo.id, lookup.page.organoName, state.circuito))
            }
        }
    }

    /** "Guardar" on the preview: stores the page already fetched, with no new request. */
    fun onSave() {
        val found = _state.value.lookup as? LookupState.Found ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            cases.save(found.url, found.page)
            _state.update { it.copy(saving = false, openCase = found.page.neun) }
        }
    }

    /** "Abrir" on the preview of a case that's already saved. */
    fun onOpenSaved() {
        val found = _state.value.lookup as? LookupState.Found ?: return
        _state.update { it.copy(openCase = found.page.neun) }
    }

    fun onCaseOpened() {
        _state.update { it.copy(openCase = null) }
    }

    /** The circuit to open on the portal, remembered as the last one used. */
    fun onOpenPortal(): String? {
        val circuito = _state.value.circuito.takeIf { it.isNotBlank() } ?: return null
        viewModelScope.launch { settings.setLastCircuito(circuito) }
        return circuito
    }

    /**
     * "Buscar este expediente" from a related case: the expediente always, and
     * the circuit, órgano and tipo de asunto when cached catalogs or known
     * órganos match their names exactly. Makes no request.
     */
    private suspend fun applyPrefill(known: List<KnownOrgano>, saved: List<SavedOrgano>) {
        val organoName = prefill.organoName?.takeIf { it.isNotBlank() } ?: return
        val fromCatalog = catalog.findCachedOrganos(organoName)
            .distinctBy { it.organo.id }
            .singleOrNull()
            ?.let { KnownOrgano(it.organo.id, it.organo.name, it.circuito) }
        val organo = fromCatalog
            ?: known.firstOrNull { SearchText.sameName(it.name, organoName) }?.let { it.copy(circuito = it.circuito ?: circuitoOf(it.id)) }
            ?: return
        if (organo.circuito != null && organo.circuito != _state.value.circuito) {
            switchCircuito(organo.circuito, fetch = false)
        }
        setOrgano(organo, organo.name)

        val tipos = tiposFor(organo)
        _state.update { it.copy(tiposAsunto = Loadable.Loaded(tipos.options), tiposFromPortal = tipos.fromPortal) }
        val tipoName = prefill.tipoAsuntoName?.takeIf { it.isNotBlank() } ?: return
        val tipo = tipos.options.firstOrNull { SearchText.sameName(it.label, tipoName) }
            ?: saved.firstOrNull { it.organismoId == organo.id && SearchText.sameName(it.tipoAsuntoName, tipoName) }
                ?.let { FormOption(it.tipoAsuntoId, it.tipoAsuntoName, position = 0, selected = false) }
            ?: return
        _state.update { it.copy(tipoAsunto = tipo) }
    }

    private class Tipos(val options: List<FormOption>, val fromPortal: Boolean)

    /**
     * The órgano's own list when it's cached, else the bundled tipos of its
     * class (IDs are global), or every known tipo for an órgano whose class
     * has no known list. Never makes a request.
     */
    private suspend fun tiposFor(organo: KnownOrgano): Tipos {
        val cached = catalog.cachedTiposDeAsunto(organo.id)
        if (cached.isNotEmpty()) return Tipos(cached, fromPortal = true)
        val clase = organo.clase.takeIf { organo.name.isNotBlank() }
        return Tipos(TiposDeAsunto.forClase(clase), fromPortal = false)
    }

    /** Shows the tipos with no request, so Buscar is one GET of the case page. */
    private fun showTiposAsunto() {
        val organo = _state.value.organo ?: return
        tiposJob?.cancel()
        tiposJob = viewModelScope.launch {
            val tipos = tiposFor(organo)
            _state.update { current ->
                if (current.organo?.id != organo.id) return@update current
                current.copy(
                    tiposAsunto = Loadable.Loaded(tipos.options),
                    tiposFromPortal = tipos.fromPortal,
                    tipoAsunto = current.tipoAsunto?.takeIf { tipo -> tipos.options.any { it.value == tipo.value } },
                )
            }
        }
    }

    /**
     * Changes the circuit, keeping the órgano only if it belongs to it, and
     * shows the circuit's órgano list: from the cache, or with [fetch] one
     * request for it.
     */
    private fun switchCircuito(circuito: String, fetch: Boolean) {
        organosJob?.cancel()
        _state.update { it.copy(circuito = circuito, organos = Loadable.Idle) }
        val organo = _state.value.organo
        if (organo != null && organo.circuito != circuito) setOrgano(null, "")
        loadOrganos(fetch)
    }

    private fun loadOrganos(fetch: Boolean) {
        val circuito = _state.value.circuito.takeIf { it.isNotBlank() } ?: return
        organosJob?.cancel()
        organosJob = viewModelScope.launch {
            val cached = catalog.cachedOrganos(circuito)
            if (cached == null && !fetch) return@launch
            val result = if (cached != null) {
                PortalResult.Ok(cached)
            } else {
                _state.update { if (it.circuito == circuito) it.copy(organos = Loadable.Loading) else it }
                portalCall { catalog.organos(circuito) }
            }
            _state.update { current ->
                if (current.circuito != circuito) return@update current
                when (result) {
                    is PortalResult.Ok -> current.copy(
                        organos = Loadable.Loaded(result.value.map { KnownOrgano(it.id, it.name, circuito) }),
                    )
                    is PortalResult.Failed -> current.copy(organos = Loadable.Failed(result.error))
                }
            }
        }
    }

    /** The circuit of an órgano with none on record: the chosen one if its list has it, else the cached lists'. */
    private suspend fun circuitoOf(organismo: String): String? {
        val state = _state.value
        if ((state.organos as? Loadable.Loaded)?.value?.any { it.id == organismo } == true) return state.circuito
        return catalog.cachedCircuitosOf(organismo).singleOrNull() ?: state.circuito.ifBlank { null }
    }

    private fun setOrgano(organo: KnownOrgano?, text: String) {
        val changed = organo?.id != _state.value.organo?.id
        if (changed) {
            tiposJob?.cancel()
            procedimientosJob?.cancel()
        }
        _state.update {
            if (changed) {
                it.copy(
                    organoText = text,
                    organo = organo,
                    tiposAsunto = Loadable.Idle,
                    tiposFromPortal = false,
                    tipoAsunto = null,
                    tiposProcedimiento = Loadable.Idle,
                    tipoProcedimiento = null,
                    lookup = LookupState.None,
                )
            } else {
                it.copy(organoText = text, organo = organo)
            }
        }
    }

    private fun loadTiposAsunto() {
        val state = _state.value
        val organo = state.organo ?: return
        if (state.circuito.isBlank()) return
        tiposJob?.cancel()
        _state.update { it.copy(tiposAsunto = Loadable.Loading) }
        tiposJob = viewModelScope.launch {
            val result = portalCall { catalog.tiposDeAsunto(state.circuito, organo.id) }
            _state.update { current ->
                if (current.organo?.id != organo.id) return@update current
                when (result) {
                    is PortalResult.Ok -> current.copy(
                        tiposAsunto = Loadable.Loaded(result.value),
                        tiposFromPortal = true,
                        // No preselection: the user picks a tipo explicitly, even when there's only one.
                        tipoAsunto = current.tipoAsunto?.takeIf { tipo -> result.value.any { it.value == tipo.value } },
                    )
                    is PortalResult.Failed -> current.copy(tiposAsunto = Loadable.Failed(result.error))
                }
            }
            // Loading the tipos may have cached the circuit's órgano list.
            if (_state.value.organos !is Loadable.Loaded) loadOrganos(fetch = false)
            val tipo = _state.value.tipoAsunto
            if (tipo != null && TipoProcedimientoRule.isShown(tipo.value) && _state.value.tiposProcedimiento == Loadable.Idle) {
                loadTiposProcedimiento()
            }
        }
    }

    private fun loadTiposProcedimiento() {
        val state = _state.value
        val organo = state.organo ?: return
        val tipoAsunto = state.tipoAsunto ?: return
        procedimientosJob?.cancel()
        _state.update { it.copy(tiposProcedimiento = Loadable.Loading) }
        procedimientosJob = viewModelScope.launch {
            val result = portalCall {
                catalog.tiposDeProcedimiento(state.circuito, organo.id, tipoAsunto.value)
            }
            _state.update { current ->
                if (current.organo?.id != organo.id || current.tipoAsunto != tipoAsunto) return@update current
                when (result) {
                    is PortalResult.Ok -> current.copy(
                        tiposProcedimiento = Loadable.Loaded(result.value),
                        tipoProcedimiento = null,
                    )
                    is PortalResult.Failed -> current.copy(tiposProcedimiento = Loadable.Failed(result.error))
                }
            }
        }
    }

    companion object {
        /** Recent órganos first (they know their circuit), then those of saved cases. */
        private fun knownOrganos(recents: List<RecentOrgano>, saved: List<SavedOrgano>): Pair<List<KnownOrgano>, List<SavedOrgano>> {
            val known = recents.map { KnownOrgano(it.id, it.name, it.circuito) } +
                saved.distinctBy { it.organismoId }.map { KnownOrgano(it.organismoId, it.organoName) }
            return known.distinctBy { it.id } to saved
        }

        val Factory = viewModelFactory {
            initializer {
                val container = this.container
                SearchViewModel(
                    catalog = container.catalogRepository,
                    lookupRepository = container.lookupRepository,
                    cases = container.caseRepository,
                    settings = container.settings,
                    prefill = createSavedStateHandle().searchRoute(),
                )
            }
        }

        private fun SavedStateHandle.searchRoute(): SearchRoute =
            runCatching { toRoute<SearchRoute>() }.getOrDefault(SearchRoute())
    }
}
