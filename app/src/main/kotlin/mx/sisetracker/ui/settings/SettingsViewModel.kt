package mx.sisetracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.sisetracker.container
import mx.sisetracker.data.catalog.CatalogRepository
import mx.sisetracker.data.check.DailyCheckScheduler
import mx.sisetracker.data.settings.SettingsStore

data class SettingsUiState(
    val loaded: Boolean = false,
    val dailyCheck: Boolean = true,
    val intervalDays: Int = 1,
    val catalogsCleared: Boolean = false,
)

class SettingsViewModel(
    private val settings: SettingsStore,
    private val scheduler: DailyCheckScheduler,
    private val catalog: CatalogRepository,
) : ViewModel() {
    private val catalogsCleared = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> =
        combine(settings.dailyCheckEnabled, settings.checkIntervalDays, catalogsCleared) { enabled, days, cleared ->
            SettingsUiState(loaded = true, dailyCheck = enabled, intervalDays = days, catalogsCleared = cleared)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

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
                SettingsViewModel(c.settings, c.dailyCheckScheduler, c.catalogRepository)
            }
        }
    }
}
