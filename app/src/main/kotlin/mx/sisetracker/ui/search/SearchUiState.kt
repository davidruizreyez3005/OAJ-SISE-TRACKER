package mx.sisetracker.ui.search

import mx.sisetracker.core.CasePage
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.core.Circuito
import mx.sisetracker.core.Circuitos
import mx.sisetracker.core.ExpedienteFormat
import mx.sisetracker.core.FormOption
import mx.sisetracker.core.OrganoKind
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
    val kind: OrganoKind get() = OrganoKind.fromName(name)
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

    data class Failed(val error: PortalError) : LookupState
}

data class SearchUiState(
    /** The chosen circuit's number (`Cir`), or "" before one is chosen. */
    val circuito: String = "",
    /** The chosen circuit's órgano list (step B). */
    val organos: Loadable<List<KnownOrgano>> = Loadable.Idle,
    val kindFilter: OrganoKind? = null,
    val organoText: String = "",
    val organo: KnownOrgano? = null,
    val knownOrganos: List<KnownOrgano> = emptyList(),
    val tiposAsunto: Loadable<List<FormOption>> = Loadable.Idle,
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

    /** Órganos matching the "Tipo de órgano" chip and what's typed, for the type-ahead. */
    val organoSuggestions: List<KnownOrgano>
        get() {
            val query = organoText.takeUnless { organo != null && it == organo.name }.orEmpty()
            return organoSource
                .filter { kindFilter == null || it.kind == kindFilter }
                .filter { SearchText.matches("${it.name} ${it.id}", query) }
                .take(MAX_SUGGESTIONS)
        }

    /** Whether the portal would show the "Tipo de procedimiento" row. */
    val showsProcedimiento: Boolean
        get() = tipoAsunto?.let { TipoProcedimientoRule.isShown(it.value) } == true

    val canLoadTipos: Boolean get() = organo != null && circuito.isNotBlank()

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
