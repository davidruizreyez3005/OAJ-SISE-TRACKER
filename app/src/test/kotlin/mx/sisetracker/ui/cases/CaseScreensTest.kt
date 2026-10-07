package mx.sisetracker.ui.cases

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.runBlocking
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.core.SintesisPageParser
import mx.sisetracker.data.cases.CaseRepository
import mx.sisetracker.data.db.SiseDatabase
import mx.sisetracker.data.lookup.LookupRepository
import mx.sisetracker.data.settings.SettingsStore
import mx.sisetracker.testing.FakeSiseClient
import mx.sisetracker.testing.InMemoryPreferences
import mx.sisetracker.testing.Fixtures
import mx.sisetracker.testing.inMemoryDatabase
import mx.sisetracker.testing.parseCase
import mx.sisetracker.testing.saveScreenshot
import mx.sisetracker.data.capture.CaptureStore
import mx.sisetracker.data.catalog.CatalogRepository
import mx.sisetracker.data.check.DailyCheckScheduler
import mx.sisetracker.testing.FakeCatalogDao
import mx.sisetracker.ui.acuerdo.AcuerdoScreen
import mx.sisetracker.ui.searchacuerdos.AcuerdoSearchScreen
import mx.sisetracker.ui.searchacuerdos.AcuerdoSearchViewModel
import mx.sisetracker.ui.settings.SettingsScreen
import mx.sisetracker.ui.settings.SettingsViewModel
import mx.sisetracker.ui.acuerdo.AcuerdoViewModel
import mx.sisetracker.ui.home.HomeScreen
import mx.sisetracker.ui.home.HomeViewModel
import mx.sisetracker.ui.theme.SiseTrackerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** "Mis expedientes", the case detail and the síntesis view, on an in-memory database. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CaseScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val neun = "40612904"
    private val url = CaseUrl("1", "767", "1183/2025", "0")
    private val page = parseCase(Fixtures.CASE_1183)
    private val client = FakeSiseClient()
    private lateinit var db: SiseDatabase
    private lateinit var repository: CaseRepository

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repository = CaseRepository(db, client, LookupRepository(client))
    }

    @After
    fun tearDown() = db.close()

    private fun waitForText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Saved before orden 38 was published, then refreshed: orden 38 is new. */
    private fun saveWithNewAcuerdo() = runBlocking {
        repository.save(url, page.copy(acuerdos = page.acuerdos.filterNot { it.orden == 38 }))
        client.onGet = { Fixtures.load(Fixtures.CASE_1183) }
        repository.refresh(neun)
        client.requests.clear()
    }

    @Test
    fun `home shows the empty state`() {
        val viewModel = HomeViewModel(repository, SettingsStore(InMemoryPreferences()))
        compose.setContent { SiseTrackerTheme { HomeScreen(onSearchClick = {}, onOpenCase = {}, viewModel = viewModel) } }

        waitForText("Aún no guardas expedientes")
        compose.onRoot().saveScreenshot("home_empty_light")
    }

    @Test
    fun `home lists saved cases with their new acuerdos and filters them`() {
        saveWithNewAcuerdo()
        val viewModel = HomeViewModel(repository, SettingsStore(InMemoryPreferences()))
        compose.setContent { SiseTrackerTheme { HomeScreen(onSearchClick = {}, onOpenCase = {}, viewModel = viewModel) } }

        waitForText("1183/2025")
        compose.onNodeWithText("1 nuevo").assertExists()
        compose.onNodeWithText("Último acuerdo publicado: 01/09/2026").assertExists()
        compose.onRoot().saveScreenshot("home_cases_light")

        compose.onNodeWithText("Filtrar por expediente u órgano").performTextInput("civil")
        waitForText("Ningún expediente guardado coincide con el filtro.")
        compose.onNodeWithText("1183/2025").assertDoesNotExist()
    }

    @Test
    fun `case detail badges new acuerdos and marks them seen`() {
        saveWithNewAcuerdo()
        val viewModel = CaseViewModel(neun, repository)
        compose.setContent {
            SiseTrackerTheme {
                CaseScreen(onBack = {}, onOpenAcuerdo = {}, onSearchRelated = {}, onOpenCase = {}, viewModel = viewModel)
            }
        }

        waitForText("No. 28")
        compose.onNodeWithText("Nuevo").assertExists()
        compose.onNodeWithText("Acuerdos (28)").assertExists()
        compose.waitUntil(5_000) { runBlocking { repository.unseenOrdenes(neun).isEmpty() } }
        compose.onRoot().saveScreenshot("case_acuerdos_light")
        assertEquals(0, client.requests.size)
    }

    @Test
    fun `case detail shows related cases and collapsible case data`() {
        runBlocking { repository.save(url, page) }
        var searched: String? = null
        val viewModel = CaseViewModel(neun, repository)
        compose.setContent {
            SiseTrackerTheme(darkTheme = true) {
                CaseScreen(
                    onBack = {},
                    onOpenAcuerdo = {},
                    onSearchRelated = { searched = it.expediente },
                    onOpenCase = {},
                    viewModel = viewModel,
                )
            }
        }
        waitForText("Relacionados (1)")

        compose.onNodeWithText("Relacionados (1)").performClick()
        compose.onNodeWithText("Buscar este expediente").performClick()
        assertEquals("293/2026", searched)

        compose.onNodeWithText("Datos").performClick()
        compose.onNodeWithText("Audiencia").performClick()
        waitForText("23/03/2026")
        compose.onRoot().saveScreenshot("case_datos_dark")
    }

    @Test
    fun `a truncated acuerdo loads its full sintesis once`() {
        runBlocking { repository.save(url, page) }
        client.onGet = { Fixtures.load(Fixtures.SINTESIS_1183_38) }
        val viewModel = AcuerdoViewModel(neun, 38, repository)
        compose.setContent { SiseTrackerTheme { AcuerdoScreen(onBack = {}, viewModel = viewModel) } }

        waitForText("Notifíquese.", substring = true)

        assertEquals(1, client.requests.size)
        compose.onRoot().saveScreenshot("acuerdo_38_light")
    }

    @Test
    fun `a long sintesis renders with its headings`() {
        runBlocking {
            repository.save(url, page)
            // Test data only: the 21,507-character síntesis of another case, to render a long text.
            val long = SintesisPageParser.parse(Fixtures.load("veracuerdo_1068-2025_orden1.html")).text
            db.caseDao().setSintesis(neun, 1, long)
        }
        val viewModel = AcuerdoViewModel(neun, 1, repository)
        compose.setContent { SiseTrackerTheme { AcuerdoScreen(onBack = {}, viewModel = viewModel) } }

        waitForText("SUSPENSIÓN DE PLANO", substring = true)

        assertEquals(0, client.requests.size)
        compose.onRoot().saveScreenshot("acuerdo_long_light")
    }

    private fun waitForText(text: String, substring: Boolean) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `acuerdo search shows highlighted results that open the acuerdo`() {
        runBlocking { repository.save(url, page) }
        var opened: Pair<String, Int>? = null
        val viewModel = AcuerdoSearchViewModel(repository)
        compose.setContent {
            SiseTrackerTheme {
                AcuerdoSearchScreen(onBack = {}, onOpenAcuerdo = { n, o -> opened = n to o }, onOpenCase = {}, viewModel = viewModel)
            }
        }

        compose.onNodeWithText("Palabras del resumen o la síntesis").performTextInput("sin materia")
        waitForText("1183/2025 · No. 3 · 08/12/2025")
        compose.onRoot().saveScreenshot("acuerdo_search_light")
        compose.onNodeWithText("1183/2025 · No. 3 · 08/12/2025").performClick()

        assertEquals(neun to 4, opened)
        assertEquals(0, client.requests.size)
    }

    @Test
    fun `settings show the daily check and catalog refresh`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val settings = SettingsStore(InMemoryPreferences())
        val viewModel = SettingsViewModel(
            settings,
            DailyCheckScheduler { androidx.work.WorkManager.getInstance(context) },
            CatalogRepository(FakeCatalogDao(), client),
            CaptureStore(
                dir = java.io.File(context.cacheDir, "captures-test"),
                shareDir = java.io.File(context.cacheDir, "shared-test"),
                settings = settings,
                versionName = "test",
            ),
            mx.sisetracker.data.crawl.CatalogCrawlScheduler { androidx.work.WorkManager.getInstance(context) },
            mx.sisetracker.data.capture.DownloadsSaver(context),
        )
        compose.setContent { SiseTrackerTheme { SettingsScreen(onBack = {}, viewModel = viewModel) } }

        waitForText("Revisión diaria")
        compose.onNodeWithText("Revisar cada día").assertExists()
        compose.onRoot().saveScreenshot("settings_light")
        compose.onNodeWithText("Actualizar catálogos").performClick()
        waitForText("Catálogos borrados. Se cargarán de nuevo al usarlos.")

        // The crawl starts only after confirming, and only queues work (Wi-Fi, battery).
        compose.onNodeWithText("Recorrer todos los circuitos").performScrollTo().performClick()
        waitForText("¿Recorrer todos los circuitos?")
        compose.onNodeWithText("Iniciar").performClick()
        waitForText("En espera de Wi-Fi y batería suficiente…")
        val work = androidx.work.WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(mx.sisetracker.data.crawl.CatalogCrawlScheduler.WORK_NAME).get()
        assertEquals(1, work.size)
        compose.onRoot().saveScreenshot("settings_crawl_light")
    }
}
