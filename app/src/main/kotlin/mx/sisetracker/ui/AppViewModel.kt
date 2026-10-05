package mx.sisetracker.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mx.sisetracker.core.CaseUrl

/** Activity-wide state: a case link waiting for "¿Guardar este expediente?". */
class AppViewModel : ViewModel() {
    private val _capturedCase = MutableStateFlow<CaseUrl?>(null)
    val capturedCase: StateFlow<CaseUrl?> = _capturedCase.asStateFlow()

    /** From the portal WebView, a share or a paste. Safe to call from any thread. */
    fun onCaseCaptured(url: CaseUrl) {
        _capturedCase.value = url
    }

    fun onCaptureDismissed() {
        _capturedCase.value = null
    }
}
