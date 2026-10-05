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
import mx.sisetracker.container
import mx.sisetracker.core.SearchText
import mx.sisetracker.data.cases.CaseRepository
import mx.sisetracker.data.db.CaseSummary

data class HomeUiState(
    val loaded: Boolean = false,
    val filter: String = "",
    /** Saved cases matching [filter]. */
    val cases: List<CaseSummary> = emptyList(),
    val savedCount: Int = 0,
)

/** "Mis expedientes". Only reads the database: no portal requests. */
class HomeViewModel(cases: CaseRepository) : ViewModel() {
    private val filter = MutableStateFlow("")

    val state: StateFlow<HomeUiState> =
        combine(cases.observeSummaries(), filter) { summaries, query ->
            HomeUiState(
                loaded = true,
                filter = query,
                cases = summaries.filter { matches(it, query) },
                savedCount = summaries.size,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onFilterChange(text: String) {
        filter.value = text
    }

    private fun matches(summary: CaseSummary, query: String): Boolean =
        SearchText.matches("${summary.case.expediente} ${summary.case.organoName}", query)

    companion object {
        val Factory = viewModelFactory {
            initializer { HomeViewModel(this.container.caseRepository) }
        }
    }
}
