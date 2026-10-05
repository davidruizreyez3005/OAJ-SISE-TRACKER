package mx.sisetracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.sisetracker.container
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.data.cases.CaseRepository
import mx.sisetracker.data.cases.SaveResult
import mx.sisetracker.data.net.PortalError
import mx.sisetracker.data.net.PortalResult
import mx.sisetracker.data.net.portalCall

/** "¿Guardar este expediente?" for a link from the portal WebView, a share or a paste. */
data class CaptureUiState(
    val url: CaseUrl,
    val saving: Boolean = false,
    val notFound: Boolean = false,
    val error: PortalError? = null,
)

/** Activity-wide state: the save sheet, and the case to open after saving. */
class AppViewModel(private val cases: CaseRepository) : ViewModel() {
    private val _capture = MutableStateFlow<CaptureUiState?>(null)
    val capture: StateFlow<CaptureUiState?> = _capture.asStateFlow()

    private val _openCase = MutableStateFlow<String?>(null)

    /** A saved case to navigate to: consumed by the UI. */
    val openCase: StateFlow<String?> = _openCase.asStateFlow()

    /** Safe to call from any thread. */
    fun onCaseCaptured(url: CaseUrl) {
        _capture.update { current -> if (current?.saving == true) current else CaptureUiState(url) }
    }

    fun onCaptureDismissed() {
        _capture.update { current -> current?.takeIf { it.saving } }
    }

    /** Fetches the captured case's page once, saves it and opens it. */
    fun onSaveCaptured() {
        val current = _capture.value ?: return
        if (current.saving) return
        _capture.value = current.copy(saving = true, notFound = false, error = null)
        viewModelScope.launch {
            when (val result = portalCall { cases.saveFromUrl(current.url) }) {
                is PortalResult.Ok -> when (val saved = result.value) {
                    is SaveResult.Saved -> {
                        _capture.value = null
                        _openCase.value = saved.neun
                    }
                    SaveResult.NotFound -> _capture.update { it?.copy(saving = false, notFound = true) }
                }
                is PortalResult.Failed -> _capture.update { it?.copy(saving = false, error = result.error) }
            }
        }
    }

    fun onCaseOpened() {
        _openCase.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { AppViewModel(this.container.caseRepository) }
        }
    }
}
