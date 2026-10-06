package mx.sisetracker.ui.searchacuerdos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import mx.sisetracker.container
import mx.sisetracker.core.FtsQuery
import mx.sisetracker.data.cases.CaseRepository
import mx.sisetracker.data.db.AcuerdoSearchResult

/** Search across every saved acuerdo. Local only: no portal requests. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class AcuerdoSearchViewModel(private val cases: CaseRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Null until the query has something to search for. */
    val results: StateFlow<List<AcuerdoSearchResult>?> =
        _query
            .debounce(DEBOUNCE_MILLIS)
            .mapLatest { query -> if (FtsQuery.from(query) == null) null else cases.searchAcuerdos(query) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onQueryChange(text: String) {
        _query.value = text
    }

    companion object {
        private const val DEBOUNCE_MILLIS = 250L

        val Factory = viewModelFactory {
            initializer { AcuerdoSearchViewModel(this.container.caseRepository) }
        }
    }
}
