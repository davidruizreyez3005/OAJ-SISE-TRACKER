package mx.sisetracker.ui

import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.sisetracker.container
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.core.ParseLocation
import mx.sisetracker.data.cases.CaseRepository
import mx.sisetracker.data.cases.ImportPreview
import mx.sisetracker.data.cases.SaveResult
import mx.sisetracker.data.net.PortalError
import mx.sisetracker.data.net.PortalResult
import mx.sisetracker.data.net.portalCall
import mx.sisetracker.data.settings.SettingsStore
import mx.sisetracker.data.transfer.CaseListTransfer
import mx.sisetracker.ui.lock.LockState

/** "¿Guardar este expediente?" for a link from the portal WebView, a share or a paste. */
data class CaptureUiState(
    val url: CaseUrl,
    val saving: Boolean = false,
    val notFound: Boolean = false,
    val error: PortalError? = null,
    val errorLocation: ParseLocation? = null,
)

/** "¿Importar estos expedientes?" for an exported list or several shared links. */
data class ImportUiState(val preview: ImportPreview, val importing: Boolean = false)

/** One-off app-wide results, shown briefly by the UI. */
sealed interface AppMessage {
    data class Imported(val count: Int) : AppMessage

    data object NoLinks : AppMessage
}

/** Activity-wide state: the lock, the save sheet, the import dialog, and the case to open after saving. */
class AppViewModel(
    private val cases: CaseRepository,
    private val transfer: CaseListTransfer,
    settings: SettingsStore,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {
    private val lockEnabled = settings.appLock.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _lock = MutableStateFlow(LockState.CHECKING)

    /** Ajustes → "Bloquear la app": locked on start, and after [LOCK_AFTER_MILLIS] in the background. */
    val lock: StateFlow<LockState> = _lock.asStateFlow()
    private var hiddenSince: Long? = null

    private val _capture = MutableStateFlow<CaptureUiState?>(null)
    val capture: StateFlow<CaptureUiState?> = _capture.asStateFlow()

    private val _import = MutableStateFlow<ImportUiState?>(null)
    val import: StateFlow<ImportUiState?> = _import.asStateFlow()

    private val _message = MutableStateFlow<AppMessage?>(null)

    /** Consumed by the UI with [onMessageShown]. */
    val message: StateFlow<AppMessage?> = _message.asStateFlow()

    private val _openCase = MutableStateFlow<String?>(null)

    /** A saved case to navigate to: consumed by the UI. */
    val openCase: StateFlow<String?> = _openCase.asStateFlow()

    init {
        viewModelScope.launch {
            _lock.value = if (settings.appLock.first()) LockState.LOCKED else LockState.UNLOCKED
        }
    }

    /** The app went to the background. */
    fun onAppHidden() {
        hiddenSince = elapsed()
    }

    /** The app is back: locks it if it was away long enough and the lock is on. */
    fun onAppShown() {
        val since = hiddenSince ?: return
        hiddenSince = null
        if (lockEnabled.value == true && _lock.value == LockState.UNLOCKED && elapsed() - since >= LOCK_AFTER_MILLIS) {
            _lock.value = LockState.LOCKED
        }
    }

    fun onUnlocked() {
        _lock.value = LockState.UNLOCKED
    }

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
        _capture.value = current.copy(saving = true, notFound = false, error = null, errorLocation = null)
        viewModelScope.launch {
            when (val result = portalCall { cases.saveFromUrl(current.url) }) {
                is PortalResult.Ok -> when (val saved = result.value) {
                    is SaveResult.Saved -> {
                        _capture.value = null
                        _openCase.value = saved.neun
                    }
                    SaveResult.NotFound -> _capture.update { it?.copy(saving = false, notFound = true) }
                }
                is PortalResult.Failed -> _capture.update { it?.copy(saving = false, error = result.error, errorLocation = result.location) }
            }
        }
    }

    /** Links in shared text: one opens the save sheet, several the import dialog. */
    fun onLinksShared(urls: List<CaseUrl>) {
        when (urls.size) {
            0 -> _message.value = AppMessage.NoLinks
            1 -> onCaseCaptured(urls.single())
            else -> preview(urls)
        }
    }

    /** An exported list, picked in Ajustes or shared as a file: always the import dialog. */
    fun onImportFile(uri: Uri) {
        viewModelScope.launch {
            val urls = transfer.readLinks(uri)
            if (urls.isEmpty()) _message.value = AppMessage.NoLinks else preview(urls)
        }
    }

    private fun preview(urls: List<CaseUrl>) {
        viewModelScope.launch { _import.value = ImportUiState(cases.previewImport(urls)) }
    }

    /** Adds the new links to "Mis expedientes", to load later: no request now (hard rule 3). */
    fun onConfirmImport() {
        val current = _import.value ?: return
        if (current.importing) return
        _import.value = current.copy(importing = true)
        viewModelScope.launch {
            val count = cases.importLinks(current.preview.new)
            _import.value = null
            _message.value = AppMessage.Imported(count)
        }
    }

    fun onImportDismissed() {
        _import.update { current -> current?.takeIf { it.importing } }
    }

    fun onMessageShown() {
        _message.value = null
    }

    /** From a new-acuerdos notification. */
    fun openCase(neun: String) {
        _openCase.value = neun
    }

    fun onCaseOpened() {
        _openCase.value = null
    }

    companion object {
        /** A quick trip to another app (a file picker, the share sheet) doesn't lock. */
        const val LOCK_AFTER_MILLIS = 60_000L

        val Factory = viewModelFactory {
            initializer {
                val c = this.container
                AppViewModel(c.caseRepository, c.caseListTransfer, c.settings)
            }
        }
    }
}
