package mx.sisetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.sisetracker.container
import mx.sisetracker.core.Audiencia
import mx.sisetracker.core.Audiencias
import mx.sisetracker.core.Catalogo
import mx.sisetracker.core.ExpedienteFormat
import mx.sisetracker.core.ParseLocation
import mx.sisetracker.core.SearchText
import mx.sisetracker.core.TiposDeAsunto
import mx.sisetracker.data.cases.CaseRepository
import mx.sisetracker.data.cases.SaveResult
import mx.sisetracker.data.db.CaseSummary
import mx.sisetracker.data.db.PendingCaseEntity
import mx.sisetracker.data.net.PortalError
import mx.sisetracker.data.net.PortalResult
import mx.sisetracker.data.net.portalCall
import mx.sisetracker.data.settings.CaseSort
import mx.sisetracker.data.settings.SettingsStore

/** A saved case on "Mis expedientes", with its next hearing, if the case data gives one. */
data class HomeCase(val summary: CaseSummary, val nextHearing: Audiencia?)

/** An imported link not loaded yet, named from the bundled catalog. */
data class PendingItem(
    val caseUrl: String,
    val expediente: String,
    val organismoId: String,
    /** Null for an órgano the bundled catalog doesn't have ("Organismo 9999"). */
    val organoName: String?,
    val tipoAsuntoName: String?,
    val notFound: Boolean,
)

sealed interface HomeMessage {
    data object PendingNotFound : HomeMessage

    data class Failed(val error: PortalError, val location: ParseLocation? = null) : HomeMessage
}

data class HomeUiState(
    val loaded: Boolean = false,
    val filter: String = "",
    /** Saved cases matching the filters, in [sort] order. */
    val cases: List<HomeCase> = emptyList(),
    /** Imported links matching [filter] (none while a chip filter is on). */
    val pending: List<PendingItem> = emptyList(),
    val pendingCount: Int = 0,
    /** The imported link being loaded, if any. */
    val loadingPending: String? = null,
    val savedCount: Int = 0,
    /** Unseen acuerdos across every saved case. */
    val unseenTotal: Int = 0,
    val dailyCheck: Boolean = false,
    /** Ask for notifications: there are saved cases, the daily check is on and the prompt wasn't dismissed. */
    val suggestNotifications: Boolean = false,
    val sort: CaseSort = CaseSort.ACTIVITY,
    /** Only cases with unseen acuerdos. */
    val newOnly: Boolean = false,
    /** The user's labels on saved cases, A–Z: filter chips. */
    val labels: List<String> = emptyList(),
    val labelFilter: String? = null,
    val today: LocalDate = LocalDate.now(),
    val message: HomeMessage? = null,
    /** A case to navigate to (an imported one just loaded): consumed by the screen. */
    val openCase: String? = null,
) {
    /** Nothing saved or imported yet. */
    val isEmpty: Boolean get() = savedCount == 0 && pendingCount == 0
}

private data class Filters(
    val text: String = "",
    val newOnly: Boolean = false,
    val label: String? = null,
)

private data class Visit(
    val loadingPending: String? = null,
    val message: HomeMessage? = null,
    val openCase: String? = null,
)

/**
 * "Mis expedientes". Reads the database, plus one request per tap on an
 * imported link (hard rule 3). Next hearings come from the saved
 * "Captura de Información" (see Audiencias).
 */
class HomeViewModel(
    private val cases: CaseRepository,
    private val settings: SettingsStore,
    private val today: () -> LocalDate = { LocalDate.now(ZoneId.systemDefault()) },
) : ViewModel() {
    private val filters = MutableStateFlow(Filters())
    private val visit = MutableStateFlow(Visit())

    private val saved = combine(cases.observeSummaries(), cases.observeHearings(), cases.observePending(), ::Triple)
    private val prefs = combine(
        settings.dailyCheckEnabled,
        settings.notificationPromptDismissed,
        settings.caseSort,
    ) { dailyCheck, dismissed, sort -> Triple(dailyCheck, dismissed, sort) }

    val state: StateFlow<HomeUiState> =
        combine(saved, prefs, filters, visit) { (summaries, hearings, pending), (dailyCheck, dismissed, sort), filters, visit ->
            val today = today()
            val all = summaries.map { HomeCase(it, hearings[it.case.neun]?.let { entries -> Audiencias.next(entries, today) }) }
            val labels = summaries.mapNotNull { it.label?.takeIf(String::isNotEmpty) }.distinct().sortedBy(SearchText::fold)
            val label = filters.label?.takeIf { it in labels }
            HomeUiState(
                loaded = true,
                filter = filters.text,
                cases = all
                    .filter { matches(it.summary, filters.text) }
                    .filter { !filters.newOnly || it.summary.unseenCount > 0 }
                    .filter { label == null || it.summary.label == label }
                    .sortedWith(order(sort)),
                // Imported links have no acuerdos or label yet: the chip filters hide them.
                pending = if (filters.newOnly || label != null) {
                    emptyList()
                } else {
                    pending.map(::pendingItem).filter { matches(it, filters.text) }
                },
                pendingCount = pending.size,
                loadingPending = visit.loadingPending,
                savedCount = summaries.size,
                unseenTotal = summaries.sumOf { it.unseenCount },
                dailyCheck = dailyCheck,
                suggestNotifications = summaries.isNotEmpty() && dailyCheck && !dismissed,
                sort = sort,
                newOnly = filters.newOnly,
                labels = labels,
                labelFilter = label,
                today = today,
                message = visit.message,
                openCase = visit.openCase,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onFilterChange(text: String) {
        filters.update { it.copy(text = text) }
    }

    fun onNewOnlyChange(enabled: Boolean) {
        filters.update { it.copy(newOnly = enabled) }
    }

    /** Shows only the cases with [label]; null (or the same label again) shows all. */
    fun onLabelFilter(label: String?) {
        filters.update { current -> current.copy(label = label.takeIf { it != current.label }) }
    }

    fun onSortChange(sort: CaseSort) {
        viewModelScope.launch { settings.setCaseSort(sort) }
    }

    fun onDismissNotificationPrompt() {
        viewModelScope.launch { settings.dismissNotificationPrompt() }
    }

    /** Loads an imported case: one request, then opens it. */
    fun onOpenPending(item: PendingItem) {
        if (visit.value.loadingPending != null) return
        visit.update { it.copy(loadingPending = item.caseUrl) }
        viewModelScope.launch {
            var opened: String? = null
            val message = when (val result = portalCall { cases.loadPending(item.caseUrl) }) {
                is PortalResult.Ok -> when (val loaded = result.value) {
                    is SaveResult.Saved -> {
                        opened = loaded.neun
                        null
                    }
                    SaveResult.NotFound -> HomeMessage.PendingNotFound
                }
                is PortalResult.Failed -> HomeMessage.Failed(result.error, result.location)
            }
            visit.update { it.copy(loadingPending = null, message = message, openCase = opened) }
        }
    }

    fun onRemovePending(item: PendingItem) {
        viewModelScope.launch { cases.removePending(item.caseUrl) }
    }

    fun onMessageShown() {
        visit.update { it.copy(message = null) }
    }

    fun onCaseOpened() {
        visit.update { it.copy(openCase = null) }
    }

    private fun matches(summary: CaseSummary, query: String): Boolean =
        SearchText.matches(
            listOfNotNull(summary.case.expediente, summary.case.organoName, summary.label, summary.note).joinToString(" "),
            query,
        )

    private fun matches(item: PendingItem, query: String): Boolean =
        SearchText.matches(listOfNotNull(item.expediente, item.organoName, item.tipoAsuntoName).joinToString(" "), query)

    private fun pendingItem(pending: PendingCaseEntity) = PendingItem(
        caseUrl = pending.caseUrl,
        expediente = pending.expediente,
        organismoId = pending.organismoId,
        organoName = Catalogo.entries(pending.organismoId).firstOrNull()?.organo?.name,
        tipoAsuntoName = TiposDeAsunto.byId(pending.tipoAsuntoId)?.label,
        notFound = pending.notFound,
    )

    companion object {
        /** The summaries arrive by recent activity (see CaseDao): the other orders are applied here. */
        fun order(sort: CaseSort): Comparator<HomeCase> = when (sort) {
            CaseSort.ACTIVITY -> Comparator { _, _ -> 0 }
            CaseSort.HEARING -> compareBy<HomeCase, LocalDate?>(nullsLast()) { it.nextHearing?.fecha }
            CaseSort.EXPEDIENTE -> compareBy<HomeCase, String>(ExpedienteFormat.order) { it.summary.case.expediente }
            CaseSort.ORGANO -> compareBy<HomeCase> { SearchText.fold(it.summary.case.organoName) }
                .thenBy(ExpedienteFormat.order) { it.summary.case.expediente }
            CaseSort.ADDED -> compareByDescending<HomeCase> { it.summary.case.addedAt }
        }

        val Factory = viewModelFactory {
            initializer { HomeViewModel(this.container.caseRepository, this.container.settings) }
        }
    }
}
