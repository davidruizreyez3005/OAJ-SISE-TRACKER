package mx.sisetracker.ui.acuerdo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.sisetracker.container
import mx.sisetracker.core.ParseLocation
import mx.sisetracker.core.Resumen
import mx.sisetracker.data.cases.CaseRepository
import mx.sisetracker.data.db.AcuerdoEntity
import mx.sisetracker.data.net.PortalError
import mx.sisetracker.data.net.PortalResult
import mx.sisetracker.data.net.portalCall
import mx.sisetracker.ui.AcuerdoRoute

data class AcuerdoUiState(
    val loaded: Boolean = false,
    val acuerdo: AcuerdoEntity? = null,
    val expediente: String = "",
    val organoName: String = "",
    val loadingSintesis: Boolean = false,
    val sintesisError: PortalError? = null,
    val sintesisErrorLocation: ParseLocation? = null,
) {
    /** Whether [text] is the whole síntesis rather than the grid's truncated résumé. */
    val isComplete: Boolean
        get() = acuerdo != null && (acuerdo.sintesis != null || !Resumen.isTruncated(acuerdo.resumen))

    val text: String get() = acuerdo?.let { it.sintesis ?: it.resumen }.orEmpty()
}

private data class FetchState(
    val loading: Boolean = false,
    val error: PortalError? = null,
    val location: ParseLocation? = null,
)

/**
 * One acuerdo's síntesis. A truncated résumé is replaced by the full síntesis,
 * fetched once and stored (and indexed); a complete résumé needs no request.
 */
class AcuerdoViewModel(
    private val neun: String,
    private val orden: Int,
    private val cases: CaseRepository,
) : ViewModel() {
    private val fetch = MutableStateFlow(FetchState())

    val state: StateFlow<AcuerdoUiState> =
        combine(cases.observeAcuerdo(neun, orden), cases.observeCase(neun), fetch) { acuerdo, case, fetch ->
            AcuerdoUiState(
                loaded = true,
                acuerdo = acuerdo,
                expediente = case?.expediente.orEmpty(),
                organoName = case?.organoName.orEmpty(),
                loadingSintesis = fetch.loading,
                sintesisError = fetch.error,
                sintesisErrorLocation = fetch.location,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AcuerdoUiState())

    init {
        loadSintesis()
    }

    fun loadSintesis() {
        if (fetch.value.loading) return
        viewModelScope.launch {
            val acuerdo = cases.observeAcuerdo(neun, orden).first() ?: return@launch
            if (acuerdo.sintesis != null || !Resumen.isTruncated(acuerdo.resumen)) return@launch
            fetch.value = FetchState(loading = true)
            val result = portalCall { cases.sintesis(neun, orden) }
            val failed = result as? PortalResult.Failed
            fetch.update { FetchState(error = failed?.error, location = failed?.location) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val route = createSavedStateHandle().toRoute<AcuerdoRoute>()
                AcuerdoViewModel(route.neun, route.orden, this.container.caseRepository)
            }
        }
    }
}
