package mx.sisetracker

import android.app.Application
import androidx.room.Room
import java.util.concurrent.TimeUnit
import mx.sisetracker.data.cases.CaseRepository
import mx.sisetracker.data.catalog.CatalogDatabase
import mx.sisetracker.data.catalog.CatalogRepository
import mx.sisetracker.data.db.SiseDatabase
import mx.sisetracker.data.lookup.LookupRepository
import mx.sisetracker.data.net.InMemoryCookieJar
import mx.sisetracker.data.net.OkHttpSiseClient
import mx.sisetracker.data.net.PoliteRequestQueue
import mx.sisetracker.data.net.SiseClient
import mx.sisetracker.data.net.UserAgent
import mx.sisetracker.data.settings.SettingsStore
import mx.sisetracker.data.settings.settingsDataStore
import okhttp3.OkHttpClient

/** Manual dependency wiring, one instance per process. */
class AppContainer(private val app: Application) {

    private val userAgent: String by lazy { UserAgent.create(app) }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .cookieJar(InMemoryCookieJar())
            .build()
    }

    /** Shared by every portal request in the app (hard rule 3). */
    private val requestQueue = PoliteRequestQueue()

    val siseClient: SiseClient by lazy { OkHttpSiseClient(httpClient, requestQueue) { userAgent } }

    private val catalogDatabase: CatalogDatabase by lazy {
        Room.databaseBuilder(app, CatalogDatabase::class.java, "catalog.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    /** Saved cases: user data, so schema changes need real migrations. */
    private val database: SiseDatabase by lazy {
        Room.databaseBuilder(app, SiseDatabase::class.java, SiseDatabase.NAME).build()
    }

    val catalogRepository: CatalogRepository by lazy {
        CatalogRepository(catalogDatabase.catalogDao(), siseClient)
    }

    val lookupRepository: LookupRepository by lazy { LookupRepository(siseClient) }

    val caseRepository: CaseRepository by lazy { CaseRepository(database, siseClient, lookupRepository) }

    val settings: SettingsStore by lazy { SettingsStore(app.settingsDataStore) }
}
