package mx.sisetracker.ui.cases

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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.sisetracker.container
import mx.sisetracker.core.ParseLocation
import mx.sisetracker.data.cases.CaseRepository
import mx.sisetracker.data.cases.RefreshResult
import mx.sisetracker.data.db.AcuerdoEntity
import mx.sisetracker.data.db.AsuntoRelacionadoEntity
import mx.sisetracker.data.db.CapturaEntryEntity
import mx.sisetracker.data.db.CaseEntity
import mx.sisetracker.data.db.ResolucionEntity
import mx.sisetracker.data.net.PortalError
import mx.sisetracker.data.net.PortalResult
import mx.sisetracker.data.net.portalCall
import mx.sisetracker.ui.CaseRoute

sealed interface CaseMessage {
    data class Refreshed(val newCount: Int) : CaseMessage

    data object NotFoundOnPortal : CaseMessage

    data class Failed(val error: PortalError, val location: ParseLocation? = null) : CaseMessage
}

data class CaseUiState(
    val loaded: Boolean = false,
    val case: CaseEntity? = null,
    /** Newest first. */
    val acuerdos: List<AcuerdoEntity> = emptyList(),
    /** Acuerdos to badge as "Nuevo" during this visit. */
    val newOrdenes: Set<Int> = emptySet(),
    val resoluciones: List<ResolucionEntity> = emptyList(),
    val relacionados: List<AsuntoRelacionadoEntity> = emptyList(),
    val captura: List<CapturaEntryEntity> = emptyList(),
    val savedNeuns: Set<String> = emptySet(),
    val refreshing: Boolean = false,
    val message: CaseMessage? = null,
    val deleted: Boolean = false,
)

private data class VisitState(
    val newOrdenes: Set<Int> = emptySet(),
    val refreshing: Boolean = false,
    val message: CaseMessage? = null,
    val deleted: Boolean = false,
)

/**
 * One saved case. Opening it marks its new acuerdos as seen (they keep their
 * "Nuevo" badge for this visit). Pull to refresh is one request.
 */
class CaseViewModel(private val neun: String, private val cases: CaseRepository) : ViewModel() {
    private val visit = MutableStateFlow(VisitState())

    private val stored = combine(
        cases.observeCase(neun),
        cases.observeAcuerdos(neun),
        cases.observeResoluciones(neun),
        cases.observeRelacionados(neun),
        cases.observeCaptura(neun),
    ) { case, acuerdos, resoluciones, relacionados, captura ->
        CaseUiState(
            loaded = true,
            case = case,
            acuerdos = acuerdos,
            resoluciones = resoluciones,
            relacionados = relacionados,
            captura = captura,
        )
    }

    val state: StateFlow<CaseUiState> =
        combine(stored, cases.observeSavedNeuns(), visit) { stored, saved, visit ->
            stored.copy(
                savedNeuns = saved,
                newOrdenes = visit.newOrdenes,
                refreshing = visit.refreshing,
                message = visit.message,
                deleted = visit.deleted,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaseUiState())

    init {
        viewModelScope.launch {
            val unseen = cases.unseenOrdenes(neun)
            visit.update { it.copy(newOrdenes = it.newOrdenes + unseen) }
            cases.markSeen(neun)
        }
    }

    fun refresh() {
        if (visit.value.refreshing) return
        visit.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val message = when (val result = portalCall { cases.refresh(neun) }) {
                is PortalResult.Failed -> CaseMessage.Failed(result.error, result.location)
                is PortalResult.Ok -> when (val refreshed = result.value) {
                    is RefreshResult.Updated -> {
                        val ordenes = refreshed.newAcuerdos.map { it.orden }
                        visit.update { it.copy(newOrdenes = it.newOrdenes + ordenes) }
                        cases.markSeen(neun)
                        CaseMessage.Refreshed(ordenes.size)
                    }
                    RefreshResult.NotFound -> CaseMessage.NotFoundOnPortal
                    RefreshResult.NotSaved -> null
                }
            }
            visit.update { it.copy(refreshing = false, message = message) }
        }
    }

    fun onMessageShown() {
        visit.update { it.copy(message = null) }
    }

    fun delete() {
        viewModelScope.launch {
            cases.delete(neun)
            visit.update { it.copy(deleted = true) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                CaseViewModel(createSavedStateHandle().toRoute<CaseRoute>().neun, this.container.caseRepository)
            }
        }
    }
}
