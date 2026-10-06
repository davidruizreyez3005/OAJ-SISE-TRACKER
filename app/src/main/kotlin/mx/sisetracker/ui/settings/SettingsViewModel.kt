package mx.sisetracker.ui.settings

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
import mx.sisetracker.data.check.DailyCheckScheduler
import mx.sisetracker.data.settings.SettingsStore

data class SettingsUiState(
    val loaded: Boolean = false,
    val dailyCheck: Boolean = true,
    val intervalDays: Int = 1,
    val catalogsCleared: Boolean = false,
    val captureEnabled: Boolean = false,
    val captureCount: Int = 0,
)

class SettingsViewModel(
    private val settings: SettingsStore,
    private val scheduler: DailyCheckScheduler,
    private val catalog: CatalogRepository,
    private val captures: CaptureStore,
) : ViewModel() {
    private val catalogsCleared = MutableStateFlow(false)
    private val captureCount = MutableStateFlow(0)

    /** A zip of the captures to hand to the share sheet: consumed by the screen. */
    private val _shareFile = MutableStateFlow<File?>(null)
    val shareFile: StateFlow<File?> = _shareFile.asStateFlow()

    val state: StateFlow<SettingsUiState> =
        combine(
            settings.dailyCheckEnabled,
            settings.checkIntervalDays,
            catalogsCleared,
            settings.captureEnabled,
            captureCount,
        ) { enabled, days, cleared, capture, count ->
            SettingsUiState(
                loaded = true,
                dailyCheck = enabled,
                intervalDays = days,
                catalogsCleared = cleared,
                captureEnabled = capture,
                captureCount = count,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        refreshCaptureCount()
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
                SettingsViewModel(c.settings, c.dailyCheckScheduler, c.catalogRepository, c.captureStore)
            }
        }
    }
}
