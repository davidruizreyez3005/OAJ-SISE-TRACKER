package mx.sisetracker

import android.app.Application
import androidx.room.Room
import androidx.work.WorkManager
import java.io.File
import java.util.concurrent.TimeUnit
import mx.sisetracker.data.capture.CaptureStore
import mx.sisetracker.data.cases.CaseRepository
import mx.sisetracker.data.check.DailyCheck
import mx.sisetracker.data.check.DailyCheckScheduler
import mx.sisetracker.data.check.SystemNewAcuerdosNotifier
import mx.sisetracker.data.crawl.CatalogCrawl
import mx.sisetracker.data.crawl.CatalogCrawlScheduler
import mx.sisetracker.data.capture.DownloadsSaver
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

    val captureStore: CaptureStore by lazy {
        CaptureStore(
            dir = File(app.filesDir, "captures"),
            shareDir = File(app.cacheDir, "shared"),
            settings = settings,
            versionName = BuildConfig.VERSION_NAME,
        )
    }

    val catalogRepository: CatalogRepository by lazy {
        CatalogRepository(catalogDatabase.catalogDao(), siseClient, capture = captureStore)
    }

    val lookupRepository: LookupRepository by lazy { LookupRepository(siseClient) }

    val caseRepository: CaseRepository by lazy { CaseRepository(database, siseClient, lookupRepository) }

    val settings: SettingsStore by lazy { SettingsStore(app.settingsDataStore) }

    val dailyCheck: DailyCheck by lazy {
        DailyCheck(caseRepository, settings, SystemNewAcuerdosNotifier(app))
    }

    val dailyCheckScheduler: DailyCheckScheduler by lazy {
        DailyCheckScheduler { WorkManager.getInstance(app) }
    }

    /** Opt-in, user-started: see CatalogCrawl. */
    val catalogCrawl: CatalogCrawl by lazy { CatalogCrawl(catalogRepository, captureStore, settings) }

    val catalogCrawlScheduler: CatalogCrawlScheduler by lazy {
        CatalogCrawlScheduler { WorkManager.getInstance(app) }
    }

    val downloadsSaver: DownloadsSaver by lazy { DownloadsSaver(app) }
}
