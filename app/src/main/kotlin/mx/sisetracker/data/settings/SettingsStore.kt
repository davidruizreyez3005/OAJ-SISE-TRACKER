package mx.sisetracker.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
        private val LAST_CIRCUITO = stringPreferencesKey("last_circuito")
        private val RECENT_ORGANOS = stringPreferencesKey("recent_organos")
        private val json = Json { ignoreUnknownKeys = true }
    }
}
