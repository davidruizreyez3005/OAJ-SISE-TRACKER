package mx.sisetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.sisetracker.container
import mx.sisetracker.core.SearchText
import mx.sisetracker.data.cases.CaseRepository
import mx.sisetracker.data.db.CaseSummary
import mx.sisetracker.data.settings.SettingsStore

data class HomeUiState(
    val loaded: Boolean = false,
    val filter: String = "",
    /** Saved cases matching [filter]. */
    val cases: List<CaseSummary> = emptyList(),
    val savedCount: Int = 0,
    /** Ask for notifications: there are saved cases, the daily check is on and the prompt wasn't dismissed. */
    val suggestNotifications: Boolean = false,
)

/** "Mis expedientes". Only reads the database: no portal requests. */
class HomeViewModel(cases: CaseRepository, private val settings: SettingsStore) : ViewModel() {
    private val filter = MutableStateFlow("")

    val state: StateFlow<HomeUiState> =
        combine(
            cases.observeSummaries(),
            filter,
            settings.dailyCheckEnabled,
            settings.notificationPromptDismissed,
        ) { summaries, query, dailyCheck, dismissed ->
            HomeUiState(
                loaded = true,
                filter = query,
                cases = summaries.filter { matches(it, query) },
                savedCount = summaries.size,
                suggestNotifications = summaries.isNotEmpty() && dailyCheck && !dismissed,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onFilterChange(text: String) {
        filter.value = text
    }

    fun onDismissNotificationPrompt() {
        viewModelScope.launch { settings.dismissNotificationPrompt() }
    }

    private fun matches(summary: CaseSummary, query: String): Boolean =
        SearchText.matches("${summary.case.expediente} ${summary.case.organoName}", query)

    companion object {
        val Factory = viewModelFactory {
            initializer { HomeViewModel(this.container.caseRepository, this.container.settings) }
        }
    }
}
