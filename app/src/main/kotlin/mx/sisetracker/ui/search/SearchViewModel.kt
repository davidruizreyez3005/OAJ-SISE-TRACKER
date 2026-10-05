package mx.sisetracker.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.sisetracker.container
import mx.sisetracker.core.CaseLookup
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.core.FormOption
import mx.sisetracker.core.OrganoKind
import mx.sisetracker.core.TipoProcedimientoRule
import mx.sisetracker.data.catalog.CatalogRepository
import mx.sisetracker.data.lookup.LookupRepository
import mx.sisetracker.data.net.PortalResult
import mx.sisetracker.data.net.portalCall
import mx.sisetracker.data.settings.RecentOrgano
import mx.sisetracker.data.settings.SettingsStore

/**
 * The native search screen. Every portal request here is user-triggered: the
 * tipos load when an órgano is picked or the dropdown is opened, procedimientos
 * when a tipo that needs them is picked, and the lookup on "Buscar" (one per
 * tap; the button is disabled while it runs).
 */
class SearchViewModel(
    private val catalog: CatalogRepository,
    private val lookupRepository: LookupRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var tiposJob: Job? = null
    private var procedimientosJob: Job? = null

    init {
        viewModelScope.launch {
            val last = settings.lastCircuito.first()
            if (!last.isNullOrBlank()) _state.update { if (it.circuito.isEmpty()) it.copy(circuito = last) else it }
        }
        viewModelScope.launch {
            settings.recentOrganos.collect { recents ->
                _state.update { state -> state.copy(knownOrganos = recents.map { KnownOrgano(it.id, it.name, it.circuito) }) }
            }
        }
    }

    fun onCircuitoChange(text: String) {
        _state.update { it.copy(circuito = text.filter(Char::isDigit).take(MAX_CIRCUITO_LENGTH)) }
    }

    fun onKindFilterChange(kind: OrganoKind?) {
        _state.update { it.copy(kindFilter = kind) }
    }

    /** Free text: a known órgano's name, or a bare organismo number. */
    fun onOrganoTextChange(text: String) {
        val state = _state.value
        val trimmed = text.trim()
        val organo = when {
            state.organo != null && state.organo.name.isNotEmpty() && text == state.organo.name -> state.organo
            trimmed.isNotEmpty() && trimmed.all(Char::isDigit) ->
                state.knownOrganos.firstOrNull { it.id == trimmed } ?: KnownOrgano(id = trimmed, name = "")
            else -> null
        }
        setOrgano(organo, text)
    }

    fun onOrganoSelected(organo: KnownOrgano) {
        if (organo.circuito != null) _state.update { it.copy(circuito = organo.circuito) }
        setOrgano(organo, organo.name)
        loadTiposAsunto()
    }

    /** The user opened the "Tipo de asunto" dropdown. */
    fun onTiposAsuntoRequested() {
        when (_state.value.tiposAsunto) {
            Loadable.Idle, is Loadable.Failed -> loadTiposAsunto()
            else -> Unit
        }
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
                    is CaseLookup.Found -> LookupState.Found(url, found.page)
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

    /** The circuit to open on the portal, remembered as the last one used. */
    fun onOpenPortal(): String? {
        val circuito = _state.value.circuito.takeIf { it.isNotBlank() } ?: return null
        viewModelScope.launch { settings.setLastCircuito(circuito) }
        return circuito
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
        if (!state.canLoadTipos) return
        tiposJob?.cancel()
        _state.update { it.copy(tiposAsunto = Loadable.Loading) }
        tiposJob = viewModelScope.launch {
            // The circuit's name only comes with the circuit catalog (milestone 4).
            val result = portalCall { catalog.tiposDeAsunto(state.circuito, circuitoName = "", organismo = organo.id) }
            _state.update { current ->
                if (current.organo?.id != organo.id) return@update current
                when (result) {
                    is PortalResult.Ok -> current.copy(
                        tiposAsunto = Loadable.Loaded(result.value),
                        tipoAsunto = current.tipoAsunto ?: result.value.singleOrNull(),
                    )
                    is PortalResult.Failed -> current.copy(tiposAsunto = Loadable.Failed(result.error))
                }
            }
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
                catalog.tiposDeProcedimiento(state.circuito, circuitoName = "", organismo = organo.id, tipoAsunto = tipoAsunto.value)
            }
            _state.update { current ->
                if (current.organo?.id != organo.id || current.tipoAsunto != tipoAsunto) return@update current
                when (result) {
                    is PortalResult.Ok -> current.copy(
                        tiposProcedimiento = Loadable.Loaded(result.value),
                        tipoProcedimiento = result.value.singleOrNull(),
                    )
                    is PortalResult.Failed -> current.copy(tiposProcedimiento = Loadable.Failed(result.error))
                }
            }
        }
    }

    companion object {
        private const val MAX_CIRCUITO_LENGTH = 3

        val Factory = viewModelFactory {
            initializer {
                val container = this.container
                SearchViewModel(container.catalogRepository, container.lookupRepository, container.settings)
            }
        }
    }
}
