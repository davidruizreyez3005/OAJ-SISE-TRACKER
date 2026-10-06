package mx.sisetracker.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** An órgano the user looked up recently, for the "Órganos recientes" chips. */
@Serializable
data class RecentOrgano(
    val id: String,
    val name: String,
    val circuito: String? = null,
)

/** Small preferences. Nothing here is case text. */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    val lastCircuito: Flow<String?> = dataStore.data.map { it[LAST_CIRCUITO] }

    val recentOrganos: Flow<List<RecentOrgano>> = dataStore.data.map { prefs ->
        prefs[RECENT_ORGANOS]?.let(::decodeRecents).orEmpty()
    }

    /** The once-a-day background check for new acuerdos (on by default). */
    val dailyCheckEnabled: Flow<Boolean> = dataStore.data.map { it[DAILY_CHECK] ?: true }

    /** Days between background checks, 1 to 7. */
    val checkIntervalDays: Flow<Int> = dataStore.data.map {
        (it[CHECK_INTERVAL_DAYS] ?: 1).coerceIn(MIN_INTERVAL_DAYS, MAX_INTERVAL_DAYS)
    }

    /** When the last background check started (epoch millis). */
    val lastBackgroundCheckAt: Flow<Long?> = dataStore.data.map { it[LAST_BACKGROUND_CHECK] }

    /** Diagnostics: keep the catalog pages the app loads (see CaptureStore). Off by default. */
    val captureEnabled: Flow<Boolean> = dataStore.data.map { it[CAPTURE] ?: false }

    suspend fun setCaptureEnabled(enabled: Boolean) {
        dataStore.edit { it[CAPTURE] = enabled }
    }

    val notificationPromptDismissed: Flow<Boolean> = dataStore.data.map { it[NOTIFICATION_PROMPT_DISMISSED] ?: false }

    suspend fun setDailyCheckEnabled(enabled: Boolean) {
        dataStore.edit { it[DAILY_CHECK] = enabled }
    }

    suspend fun setCheckIntervalDays(days: Int) {
        dataStore.edit { it[CHECK_INTERVAL_DAYS] = days.coerceIn(MIN_INTERVAL_DAYS, MAX_INTERVAL_DAYS) }
    }

    suspend fun setLastBackgroundCheckAt(time: Long) {
        dataStore.edit { it[LAST_BACKGROUND_CHECK] = time }
    }

    suspend fun dismissNotificationPrompt() {
        dataStore.edit { it[NOTIFICATION_PROMPT_DISMISSED] = true }
    }

    suspend fun setLastCircuito(circuito: String) {
        dataStore.edit { it[LAST_CIRCUITO] = circuito }
    }

    /** Puts [organo] first, without duplicates, keeping the latest [MAX_RECENTS]. */
    suspend fun addRecentOrgano(organo: RecentOrgano) {
        dataStore.edit { prefs ->
            val current = prefs[RECENT_ORGANOS]?.let(::decodeRecents).orEmpty()
            val updated = (listOf(organo) + current.filterNot { it.id == organo.id }).take(MAX_RECENTS)
            prefs[RECENT_ORGANOS] = json.encodeToString(updated)
        }
    }

    private fun decodeRecents(encoded: String): List<RecentOrgano> =
        try {
            json.decodeFromString(encoded)
        } catch (e: SerializationException) {
            emptyList()
        } catch (e: IllegalArgumentException) {
            emptyList()
        }

    companion object {
        const val MAX_RECENTS = 8
        const val MIN_INTERVAL_DAYS = 1
        const val MAX_INTERVAL_DAYS = 7
        private val LAST_CIRCUITO = stringPreferencesKey("last_circuito")
        private val RECENT_ORGANOS = stringPreferencesKey("recent_organos")
        private val DAILY_CHECK = booleanPreferencesKey("daily_check")
        private val CHECK_INTERVAL_DAYS = intPreferencesKey("check_interval_days")
        private val LAST_BACKGROUND_CHECK = longPreferencesKey("last_background_check")
        private val NOTIFICATION_PROMPT_DISMISSED = booleanPreferencesKey("notification_prompt_dismissed")
        private val CAPTURE = booleanPreferencesKey("capture_catalog_pages")
        private val json = Json { ignoreUnknownKeys = true }
    }
}
