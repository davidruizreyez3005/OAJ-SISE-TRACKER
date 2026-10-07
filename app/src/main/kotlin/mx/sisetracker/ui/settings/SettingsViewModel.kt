package mx.sisetracker.ui.settings

import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.sisetracker.container
import java.io.File
import mx.sisetracker.data.capture.CaptureStore
import mx.sisetracker.data.catalog.CatalogRepository
import mx.sisetracker.data.capture.DownloadsSaver
import mx.sisetracker.data.check.DailyCheckScheduler
import mx.sisetracker.data.crawl.CatalogCrawlScheduler
import mx.sisetracker.data.settings.CrawlStatus
import mx.sisetracker.data.settings.SettingsStore

data class SettingsUiState(
    val loaded: Boolean = false,
    val dailyCheck: Boolean = true,
    val intervalDays: Int = 1,
    val catalogsCleared: Boolean = false,
    val captureEnabled: Boolean = false,
    val captureCount: Int = 0,
    val crawlEnabled: Boolean = false,
    val crawlStatus: CrawlStatus = CrawlStatus(),
)

/** One-off results for the snackbar. */
sealed interface SettingsMessage {
    data class Saved(val fileName: String) : SettingsMessage

    data object SaveFailed : SettingsMessage
}

class SettingsViewModel(
    private val settings: SettingsStore,
    private val scheduler: DailyCheckScheduler,
    private val catalog: CatalogRepository,
    private val captures: CaptureStore,
    private val crawlScheduler: CatalogCrawlScheduler,
    private val saver: DownloadsSaver,
) : ViewModel() {
    private val catalogsCleared = MutableStateFlow(false)
    private val captureCount = MutableStateFlow(0)

    /** A zip of the captures to hand to the share sheet: consumed by the screen. */
    private val _shareFile = MutableStateFlow<File?>(null)
    val shareFile: StateFlow<File?> = _shareFile.asStateFlow()

    private val _message = MutableStateFlow<SettingsMessage?>(null)
    val message: StateFlow<SettingsMessage?> = _message.asStateFlow()

    /** Before Android 10: a zip waiting for the user to pick where to save it. Consumed by the screen. */
    private val _pickSaveLocation = MutableStateFlow<File?>(null)
    val pickSaveLocation: StateFlow<File?> = _pickSaveLocation.asStateFlow()
    private var pendingSave: File? = null

    val state: StateFlow<SettingsUiState> =
        combine(
            combine(settings.dailyCheckEnabled, settings.checkIntervalDays, catalogsCleared, ::Triple),
            combine(settings.captureEnabled, captureCount, ::Pair),
            combine(settings.crawlEnabled, settings.crawlStatus, ::Pair),
        ) { (enabled, days, cleared), (capture, count), (crawl, crawlStatus) ->
            SettingsUiState(
                loaded = true,
                dailyCheck = enabled,
                intervalDays = days,
                catalogsCleared = cleared,
                captureEnabled = capture,
                captureCount = count,
                crawlEnabled = crawl,
                crawlStatus = crawlStatus,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        refreshCaptureCount()
        // The crawl reports each new circuit: keep the page count roughly current.
        viewModelScope.launch { settings.crawlStatus.collect { captureCount.value = captures.count() } }
    }

    /** Starts or stops the opt-in catalog crawl (the screen confirms before starting). */
    fun onCrawlChange(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                settings.setCrawlEnabled(true)
                settings.setCrawlStatus(CrawlStatus(CrawlStatus.State.WAITING))
                crawlScheduler.start()
            } else {
                settings.setCrawlEnabled(false)
                crawlScheduler.stop()
                settings.setCrawlStatus(CrawlStatus(CrawlStatus.State.STOPPED, settings.crawlStatus.first().circuito))
            }
        }
    }

    /** "Guardar en Descargas": straight to Downloads on Android 10+, else via a file picker. */
    fun onSaveCaptures() {
        viewModelScope.launch {
            val zip = captures.zip() ?: return@launch
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                _message.value = if (saver.saveToDownloads(zip)) SettingsMessage.Saved(zip.name) else SettingsMessage.SaveFailed
            } else {
                pendingSave = zip
                _pickSaveLocation.value = zip
            }
        }
    }

    fun onPickSaveLocationHandled() {
        _pickSaveLocation.value = null
    }

    /** The picked document (null if the user cancelled). */
    fun onSaveLocationPicked(uri: Uri?) {
        val zip = pendingSave ?: return
        pendingSave = null
        if (uri == null) return
        viewModelScope.launch {
            _message.value = if (saver.saveTo(uri, zip)) SettingsMessage.Saved(zip.name) else SettingsMessage.SaveFailed
        }
    }

    fun onMessageShown() {
        _message.value = null
    }

    /** Call when the screen is shown again: captures may have been added meanwhile. */
    fun refreshCaptureCount() {
        viewModelScope.launch { captureCount.value = captures.count() }
    }

    fun onCaptureChange(enabled: Boolean) {
        viewModelScope.launch { settings.setCaptureEnabled(enabled) }
    }

    fun onShareCaptures() {
        viewModelScope.launch { _shareFile.value = captures.zip() }
    }

    fun onShareHandled() {
        _shareFile.value = null
    }

    fun onClearCaptures() {
        viewModelScope.launch {
            captures.clear()
            captureCount.value = captures.count()
        }
    }

    fun onDailyCheckChange(enabled: Boolean) {
        viewModelScope.launch {
            settings.setDailyCheckEnabled(enabled)
            scheduler.apply(enabled, settings.checkIntervalDays.first(), replace = true)
        }
    }

    fun onIntervalChange(days: Int) {
        viewModelScope.launch {
            settings.setCheckIntervalDays(days)
            scheduler.apply(settings.dailyCheckEnabled.first(), days, replace = true)
        }
    }

    /** "Actualizar catálogos": forget the cached options; they reload when next needed (no prefetch). */
    fun onRefreshCatalogs() {
        viewModelScope.launch {
            catalog.clear()
            catalogsCleared.value = true
        }
    }

    fun onCatalogsMessageShown() {
        catalogsCleared.value = false
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = this.container
                SettingsViewModel(
                    c.settings,
                    c.dailyCheckScheduler,
                    c.catalogRepository,
                    c.captureStore,
                    c.catalogCrawlScheduler,
                    c.downloadsSaver,
                )
            }
        }
    }
}
