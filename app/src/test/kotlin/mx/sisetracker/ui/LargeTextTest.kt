package mx.sisetracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.data.capture.CaptureStore
import mx.sisetracker.data.capture.DownloadsSaver
import mx.sisetracker.data.cases.CaseRepository
import mx.sisetracker.data.catalog.CatalogRepository
import mx.sisetracker.data.check.DailyCheckScheduler
import mx.sisetracker.data.crawl.CatalogCrawlScheduler
import mx.sisetracker.data.db.SiseDatabase
import mx.sisetracker.data.lookup.LookupRepository
import mx.sisetracker.data.settings.CheckReport
import mx.sisetracker.data.settings.SettingsStore
import mx.sisetracker.data.transfer.CaseListTransfer
import mx.sisetracker.testing.FakeCatalogDao
import mx.sisetracker.testing.FakeSavedCases
import mx.sisetracker.testing.FakeSiseClient
import mx.sisetracker.testing.Fixtures
import mx.sisetracker.testing.InMemoryPreferences
import mx.sisetracker.testing.inMemoryDatabase
import mx.sisetracker.testing.parseCase
import mx.sisetracker.testing.saveScreenshot
import mx.sisetracker.ui.acuerdo.AcuerdoScreen
import mx.sisetracker.ui.acuerdo.AcuerdoViewModel
import mx.sisetracker.ui.cases.CaseScreen
import mx.sisetracker.ui.cases.CaseViewModel
import mx.sisetracker.ui.home.HomeScreen
import mx.sisetracker.ui.home.HomeViewModel
import mx.sisetracker.ui.lock.LockScreen
import mx.sisetracker.ui.search.SearchScreen
import mx.sisetracker.ui.search.SearchViewModel
import mx.sisetracker.ui.settings.SettingsScreen
import mx.sisetracker.ui.settings.SettingsViewModel
import mx.sisetracker.ui.theme.SiseTrackerTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every main screen at Android's largest font size (200%), where text that
 * doesn't fit gets cut. Each test checks the screen still shows its key
 * text; the screenshots (`SISE_SCREENSHOTS_DIR`) are for looking at it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class LargeTextTest {
    @get:Rule
    val compose = createComposeRule()

    private val neun = "40612904"
    private val url = CaseUrl("1", "767", "1183/2025", "0")
    private val today = LocalDate.of(2025, 12, 1)
    private val client = FakeSiseClient()
    private val settings = SettingsStore(InMemoryPreferences())
    private lateinit var db: SiseDatabase
    private lateinit var repository: CaseRepository

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repository = CaseRepository(db, client, LookupRepository(client))
        runBlocking {
            repository.save(url, parseCase(Fixtures.CASE_1183))
            repository.setNote(neun, "Cliente A", "Llamar antes de la audiencia.")
            repository.importLinks(listOf(CaseUrl("11", "18", "293/2026", "0")))
        }
    }

    @After
    fun tearDown() = db.close()

    /** Linear 200%: a little harsher than Android 14's, which grows large text less. */
    private fun show(content: @Composable () -> Unit) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
            SiseTrackerTheme { content() }
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun home() {
        val viewModel = HomeViewModel(repository, settings, today = { today })
        show { HomeScreen(onSearchClick = {}, onOpenCase = {}, viewModel = viewModel) }

        waitForText("Mis expedientes")
        compose.onRoot().saveScreenshot("large_home_light")
        // The list composes only what's on screen, which at this size isn't much.
        val list = compose.onAllNodes(hasScrollToNodeAction()).onFirst()
        list.performScrollToNode(hasText("Por cargar (1)"))
        compose.onRoot().saveScreenshot("large_home_pending_light")
        list.performScrollToNode(hasText("Audiencia incidental: viernes 05/12 a las 11:03"))
        compose.onRoot().saveScreenshot("large_home_cases_light")
    }

    @Test
    fun case() {
        val viewModel = CaseViewModel(neun, repository, today = { today })
        show { CaseScreen(onBack = {}, onOpenAcuerdo = {}, onSearchRelated = {}, onOpenCase = {}, viewModel = viewModel) }

        waitForText("No. 28")
        compose.onRoot().saveScreenshot("large_case_light")
    }

    @Test
    fun acuerdo() {
        val viewModel = AcuerdoViewModel(neun, 4, repository)
        show { AcuerdoScreen(onBack = {}, viewModel = viewModel) }

        waitForText("Se declara sin materia")
        compose.onRoot().saveScreenshot("large_acuerdo_light")
    }

    @Test
    fun search() {
        val viewModel = SearchViewModel(CatalogRepository(FakeCatalogDao(), client), LookupRepository(client), FakeSavedCases(), settings)
        show { SearchScreen(onBack = {}, onOpenPortal = {}, onCaseLinkFound = {}, onOpenCase = {}, viewModel = viewModel) }

        waitForText("Órgano jurisdiccional")
        compose.onRoot().saveScreenshot("large_search_light")
        compose.onNodeWithText("Buscar").performScrollTo()
        compose.onRoot().saveScreenshot("large_search_bottom_light")
    }

    @Test
    fun settings() {
        val context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        runBlocking { settings.setLastCheckReport(CheckReport(1_764_590_520_000, 1_764_590_580_000, total = 2, checked = 2, notified = 1)) }
        val viewModel = SettingsViewModel(
            settings,
            DailyCheckScheduler { WorkManager.getInstance(context) },
            CatalogRepository(FakeCatalogDao(), client),
            CaptureStore(File(context.cacheDir, "captures-large"), File(context.cacheDir, "shared-large"), settings, "test"),
            CatalogCrawlScheduler { WorkManager.getInstance(context) },
            DownloadsSaver(context),
            CaseListTransfer(context, repository, File(context.cacheDir, "shared-large")),
        )
        show { SettingsScreen(onBack = {}, viewModel = viewModel) }

        waitForText("Última revisión")
        compose.onRoot().saveScreenshot("large_settings_light")
        compose.onNodeWithText("Bloquear la app").performScrollTo()
        compose.onRoot().saveScreenshot("large_settings_privacy_light")
    }

    @Test
    fun lock() {
        show { LockScreen(onUnlock = {}) }

        waitForText("Desbloquear")
        compose.onRoot().saveScreenshot("large_lock_light")
    }
}
