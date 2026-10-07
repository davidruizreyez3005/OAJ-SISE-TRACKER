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

/** The catalog crawl's progress. [circuito] is the OAJ number being walked. */
@Serializable
data class CrawlStatus(
    val state: State = State.IDLE,
    val circuito: String? = null,
    /** A PortalError name, while waiting to retry or after stopping on errors. */
    val error: String? = null,
) {
    enum class State { IDLE, RUNNING, WAITING, FINISHED, STOPPED }
}

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

    /** The opt-in catalog crawl (see CatalogCrawl): on while the user wants it running. Off by default. */
    val crawlEnabled: Flow<Boolean> = dataStore.data.map { it[CRAWL] ?: false }

    suspend fun setCrawlEnabled(enabled: Boolean) {
        dataStore.edit { it[CRAWL] = enabled }
    }

    /** Where the crawl is, for the settings screen. */
    val crawlStatus: Flow<CrawlStatus> = dataStore.data.map { prefs ->
        prefs[CRAWL_STATUS]?.let {
            try {
                json.decodeFromString<CrawlStatus>(it)
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        } ?: CrawlStatus()
    }

    suspend fun setCrawlStatus(status: CrawlStatus) {
        dataStore.edit { it[CRAWL_STATUS] = json.encodeToString(status) }
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
        private val CRAWL = booleanPreferencesKey("catalog_crawl")
        private val CRAWL_STATUS = stringPreferencesKey("catalog_crawl_status")
        private val json = Json { ignoreUnknownKeys = true }
    }
}
