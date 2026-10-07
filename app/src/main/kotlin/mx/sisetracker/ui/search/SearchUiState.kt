package mx.sisetracker.ui.search

import mx.sisetracker.core.CasePage
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.core.Circuito
import mx.sisetracker.core.Circuitos
import mx.sisetracker.core.ExpedienteFormat
import mx.sisetracker.core.FormOption
import java.time.LocalDate
import mx.sisetracker.core.Materia
import mx.sisetracker.core.OrganoClase
import mx.sisetracker.core.OrganoKind
import mx.sisetracker.core.OrganoPeriod
import mx.sisetracker.core.ParseLocation
import mx.sisetracker.core.SearchText
import mx.sisetracker.core.TipoProcedimientoRule
import mx.sisetracker.data.net.PortalError

/**
 * An órgano the app knows the ID of: from a circuit's list, a recent search or
 * a saved case. [name] is blank when the user typed a bare organismo number.
 */
data class KnownOrgano(
    val id: String,
    val name: String,
    val circuito: String? = null,
) {
    val kind: OrganoKind get() = clase.kind

    // Derived from the name; lazy because the filters read them on every keystroke.
    val clase: OrganoClase by lazy(LazyThreadSafetyMode.NONE) { OrganoClase.fromName(name) }

    val materias: Set<Materia> by lazy(LazyThreadSafetyMode.NONE) { Materia.fromName(name) }

    private val closedOn: LocalDate? by lazy(LazyThreadSafetyMode.NONE) { OrganoPeriod.endOf(name) }

    /** A closed órgano (its name ends with a past active period): still searchable, marked "Cerrado". */
    fun isClosed(today: LocalDate = LocalDate.now()): Boolean = closedOn?.let { it < today } == true
}

sealed interface Loadable<out T> {
    data object Idle : Loadable<Nothing>

    data object Loading : Loadable<Nothing>

    data class Loaded<T>(val value: T) : Loadable<T>

    data class Failed(val error: PortalError) : Loadable<Nothing>
}

sealed interface LookupState {
    data object None : LookupState

    data object Loading : LookupState

    data class Found(val url: CaseUrl, val page: CasePage, val alreadySaved: Boolean = false) : LookupState

    data object NotFound : LookupState

    data class Failed(val error: PortalError, val location: ParseLocation? = null) : LookupState
}

data class SearchUiState(
    /** The chosen circuit's number (`Cir`), or "" before one is chosen. */
    val circuito: String = "",
    /** The chosen circuit's órgano list (step B). */
    val organos: Loadable<List<KnownOrgano>> = Loadable.Idle,
    /** "Tipo de órgano": Juzgados, Tribunales or Otros. */
    val kindFilter: OrganoKind? = null,
    /** Within Tribunales or Otros: e.g. only the Colegiados de Circuito. */
    val claseFilter: OrganoClase? = null,
    val materiaFilter: Materia? = null,
    /** Closed órganos are hidden from the list unless the user asks for them. */
    val showClosed: Boolean = false,
    val organoText: String = "",
    val organo: KnownOrgano? = null,
    val knownOrganos: List<KnownOrgano> = emptyList(),
    val tiposAsunto: Loadable<List<FormOption>> = Loadable.Idle,
    /**
     * Whether [tiposAsunto] is the órgano's own list (from the portal, step C,
     * or the bundled snapshot of it), rather than a guess from its name's class.
     */
    val tiposFromPortal: Boolean = false,
    /** The órgano's own list is loading in the background, over the guessed one. */
    val checkingOrganoTipos: Boolean = false,
    val tipoAsunto: FormOption? = null,
    val tiposProcedimiento: Loadable<List<FormOption>> = Loadable.Idle,
    val tipoProcedimiento: FormOption? = null,
    val expediente: String = "",
    val lookup: LookupState = LookupState.None,
    val saving: Boolean = false,
    /** A case to open (just saved, or already saved): consumed by the screen. */
    val openCase: String? = null,
) {
    val circuitos: List<Circuito> get() = Circuitos.all

    val circuitoLabel: String get() = Circuitos.byNum(circuito)?.label.orEmpty()

    /** The circuit's órgano list once loaded, else the órganos of recent searches and saved cases. */
    private val organoSource: List<KnownOrgano>
        get() = (organos as? Loadable.Loaded)?.value?.takeIf { it.isNotEmpty() } ?: knownOrganos

    /** Whether the "Tipo de órgano" chips have anything to filter. */
    val showsKindFilter: Boolean get() = organoSource.isNotEmpty()

    /** The kinds the list has, for the "Tipo de órgano" chips. */
    val kindOptions: List<OrganoKind>
        get() = OrganoKind.entries.filter { kind -> kind == kindFilter || organoSource.any { it.kind == kind } }

    private val ofKind: List<KnownOrgano>
        get() = organoSource.filter { kindFilter == null || it.kind == kindFilter }

    /** The classes within the chosen kind, when there's more than one to choose from. */
    val claseOptions: List<OrganoClase>
        get() {
            if (kindFilter == null) return emptyList()
            val present = ofKind.map { it.clase }.toSet()
            // A chosen class stays visible (to unselect) even if this list lacks it.
            return OrganoClase.entries.filter { it in present || it == claseFilter }
                .takeIf { it.size > 1 || claseFilter != null }.orEmpty()
        }

    /** The órganos the kind and class filters leave. */
    internal val ofClase: List<KnownOrgano>
        get() = ofKind.filter { claseFilter == null || it.clase == claseFilter }

    /** The materias the órganos left by the other filters hear, when there's more than one. */
    val materiaOptions: List<Materia>
        get() {
            val present = ofClase.flatMap { it.materias }.toSet()
            return Materia.entries.filter { it in present || it == materiaFilter }
                .takeIf { it.size > 1 || materiaFilter != null }.orEmpty()
        }

    /** Whether the list has closed órganos to show or hide. */
    val hasClosed: Boolean get() = organoSource.any { it.isClosed() }

    /** Órganos matching the filters and what's typed, for the type-ahead. */
    val organoSuggestions: List<KnownOrgano>
        get() {
            val query = organoText.takeUnless { organo != null && it == organo.name }.orEmpty()
            return ofClase
                .filter { materiaFilter == null || materiaFilter in it.materias }
                .filter { showClosed || !it.isClosed() }
                .filter { SearchText.matches("${it.name} ${it.id}", query) }
                .take(MAX_SUGGESTIONS)
        }

    /** Whether the portal would show the "Tipo de procedimiento" row. */
    val showsProcedimiento: Boolean
        get() = tipoAsunto?.let { TipoProcedimientoRule.isShown(it.value) } == true

    val canLoadTipos: Boolean get() = organo != null

    /** "Ver solo los tipos de este órgano" needs the circuit for step C. */
    val canLoadOrganoTipos: Boolean
        get() = organo != null && circuito.isNotBlank() && !tiposFromPortal && !checkingOrganoTipos &&
            tiposAsunto is Loadable.Loaded

    val expedienteWarning: Boolean
        get() = expediente.isNotBlank() && !ExpedienteFormat.isUsual(expediente)

    val canSearch: Boolean
        get() = organo != null &&
            tipoAsunto != null &&
            (!showsProcedimiento || tipoProcedimiento != null) &&
            expediente.isNotBlank() &&
            lookup != LookupState.Loading

    val canOpenPortal: Boolean get() = circuito.isNotBlank()

    companion object {
        /** Enough for a whole circuit's list (184 órganos in the Primer Circuito). */
        const val MAX_SUGGESTIONS = 300

        /** The portal's `maxlength` for Expediente. */
        const val MAX_EXPEDIENTE_LENGTH = 15
    }
}
