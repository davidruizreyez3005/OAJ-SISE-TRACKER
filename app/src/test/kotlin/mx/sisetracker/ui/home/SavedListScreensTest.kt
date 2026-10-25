package mx.sisetracker.ui.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mx.sisetracker.BuildConfig
import mx.sisetracker.core.Audiencia
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.core.TipoAudiencia
import mx.sisetracker.data.capture.CaptureStore
import mx.sisetracker.data.capture.DownloadsSaver
import mx.sisetracker.data.cases.CaseRepository
import mx.sisetracker.data.cases.ImportPreview
import mx.sisetracker.data.catalog.CatalogRepository
import mx.sisetracker.data.check.DailyCheckScheduler
import mx.sisetracker.data.crawl.CatalogCrawlScheduler
import mx.sisetracker.data.db.CaseEntity
import mx.sisetracker.data.db.CaseSummary
import mx.sisetracker.data.db.SiseDatabase
import mx.sisetracker.data.lookup.LookupRepository
import mx.sisetracker.data.settings.CaseSort
import mx.sisetracker.data.settings.CheckReport
import mx.sisetracker.data.settings.SettingsStore
import mx.sisetracker.data.transfer.CaseListTransfer
import mx.sisetracker.testing.FakeCatalogDao
import mx.sisetracker.testing.FakeSiseClient
import mx.sisetracker.testing.Fixtures
import mx.sisetracker.testing.InMemoryPreferences
import mx.sisetracker.testing.inMemoryDatabase
import mx.sisetracker.testing.parseCase
import mx.sisetracker.testing.saveScreenshot
import mx.sisetracker.ui.ImportUiState
import mx.sisetracker.ui.capture.ImportDialog
import mx.sisetracker.ui.cases.CaseScreen
import mx.sisetracker.ui.cases.CaseViewModel
import mx.sisetracker.ui.lock.LockScreen
import mx.sisetracker.ui.settings.SettingsScreen
import mx.sisetracker.ui.settings.SettingsViewModel
import mx.sisetracker.ui.theme.SiseTrackerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Imported links, labels and notes, sorting, next hearings, the check
 * status, the lock screen and the import dialog. 1183/2025's audiencia
 * incidental is on 05/12/2025 at 11:03, so the screens run on 01/12/2025.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SavedListScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val neun = "40612904"
    private val url = CaseUrl("1", "767", "1183/2025", "0")
    private val imported = CaseUrl("11", "18", "293/2026", "0")
    private val page = parseCase(Fixtures.CASE_1183)
    private val today = LocalDate.of(2025, 12, 1)
    private val client = FakeSiseClient()
    private val settings = SettingsStore(InMemoryPreferences())
    private lateinit var db: SiseDatabase
    private lateinit var repository: CaseRepository

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repository = CaseRepository(db, client, LookupRepository(client))
    }

    @After
    fun tearDown() = db.close()

    private fun waitForText(text: String, substring: Boolean = false) {
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `home shows imported links to load, labels, the next hearing and filters by label`() {
        runBlocking {
            repository.save(url, page)
            repository.setNote(neun, "Cliente A", "Llamar antes de la audiencia.")
            repository.importLinks(listOf(imported))
        }
        val viewModel = HomeViewModel(repository, settings, today = { today })
        compose.setContent { SiseTrackerTheme { HomeScreen(onSearchClick = {}, onOpenCase = {}, viewModel = viewModel) } }

        waitForText("Por cargar (1)")
        compose.onNodeWithText("293/2026").assertExists()
        compose.onNodeWithText("Segundo Tribunal Colegiado en Materia Penal del Primer Circuito").assertExists()
        compose.onNodeWithText("Amparo en revisión").assertExists()
        compose.onNodeWithText("Sin cargar").assertExists()
        waitForText("Audiencia incidental: viernes 05/12 a las 11:03")
        // On the card, and as a filter chip.
        compose.onAllNodesWithText("Cliente A", useUnmergedTree = true).assertCountEquals(2)
        compose.onRoot().saveScreenshot("home_pending_labels_light")

        // The note is searchable too; the label chip filters.
        compose.onNodeWithText("Filtrar mis expedientes").performTextInput("llamar")
        waitForText("1183/2025")
        compose.onNodeWithText("293/2026").assertDoesNotExist()
        compose.onNodeWithText("Filtrar mis expedientes").performTextClearance()
        assertEquals(0, client.requests.size)
    }

    @Test
    fun `tapping an imported link loads it with one request and opens it`() {
        runBlocking { repository.importLinks(listOf(url)) }
        client.onGet = { Fixtures.load(Fixtures.CASE_1183) }
        var opened: String? = null
        val viewModel = HomeViewModel(repository, settings, today = { today })
        compose.setContent { SiseTrackerTheme { HomeScreen(onSearchClick = {}, onOpenCase = { opened = it }, viewModel = viewModel) } }

        waitForText("1183/2025")
        compose.onNodeWithText("1183/2025").performClick()

        // Polling the tree keeps Compose (and the main looper) running while the case loads.
        compose.waitUntil(5_000) {
            compose.onAllNodes(isRoot()).fetchSemanticsNodes()
            opened != null
        }
        assertEquals(neun, opened)
        assertEquals(listOf("GET ${url.toUrl()}"), client.requests)
    }

    @Test
    fun `the sort chip remembers the chosen order`() {
        runBlocking { repository.save(url, page) }
        val viewModel = HomeViewModel(repository, settings, today = { today })
        compose.setContent { SiseTrackerTheme { HomeScreen(onSearchClick = {}, onOpenCase = {}, viewModel = viewModel) } }

        waitForText("Orden: Actividad reciente")
        compose.onNodeWithText("Orden: Actividad reciente").performClick()
        compose.onNodeWithText("Próxima audiencia").performClick()

        waitForText("Orden: Próxima audiencia")
        assertEquals(CaseSort.HEARING, runBlocking { settings.caseSort.first() })
    }

    @Test
    fun `sorting by hearing, expediente and organo`() {
        fun case(expediente: String, organo: String, added: Long, hearing: LocalDate?) = HomeCase(
            CaseSummary(
                CaseEntity(expediente, "1", "1", "0", expediente, organo, "Amparo", "", 1, "", added, added),
                acuerdoCount = 1,
                unseenCount = 0,
                latestPublicacion = null,
            ),
            hearing?.let { Audiencia(TipoAudiencia.CONSTITUCIONAL, it) },
        )
        val cases = listOf(
            case("10/2025", "Juzgado Segundo", 1, null),
            case("2/2026", "Juzgado Ácrata", 3, LocalDate.of(2026, 3, 1)),
            case("9/2025", "Juzgado Primero", 2, LocalDate.of(2026, 1, 15)),
        )
        fun sorted(sort: CaseSort) = cases.sortedWith(HomeViewModel.order(sort)).map { it.summary.case.expediente }

        assertEquals(listOf("10/2025", "2/2026", "9/2025"), sorted(CaseSort.ACTIVITY))
        assertEquals(listOf("9/2025", "2/2026", "10/2025"), sorted(CaseSort.HEARING))
        assertEquals(listOf("9/2025", "10/2025", "2/2026"), sorted(CaseSort.EXPEDIENTE))
        assertEquals(listOf("2/2026", "9/2025", "10/2025"), sorted(CaseSort.ORGANO))
        assertEquals(listOf("2/2026", "9/2025", "10/2025"), sorted(CaseSort.ADDED))
    }

    @Test
    fun `the case header shows the next hearing, the label and the note, and edits them`() {
        runBlocking {
            repository.save(url, page)
            repository.setNote(neun, "Cliente A", "Llamar antes de la audiencia.")
        }
        val viewModel = CaseViewModel(neun, repository, today = { today })
        compose.setContent {
            SiseTrackerTheme {
                CaseScreen(onBack = {}, onOpenAcuerdo = {}, onSearchRelated = {}, onOpenCase = {}, viewModel = viewModel)
            }
        }

        waitForText("Audiencia incidental: viernes 05/12 a las 11:03")
        compose.onNodeWithText("Cliente A").assertExists()
        compose.onNodeWithText("Llamar antes de la audiencia.").assertExists()
        compose.onRoot().saveScreenshot("case_header_note_light")

        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("Etiqueta y nota").performClick()
        waitForText("Solo se guardan en este teléfono y no se exportan.")
        compose.onNodeWithText("Etiqueta").performTextClearance()
        compose.onNodeWithText("Etiqueta").performTextInput("Cliente B")
        compose.onAllNodes(isRoot()).onLast().saveScreenshot("case_note_sheet_light")
        compose.onNodeWithText("Guardar").performClick()

        waitForText("Cliente B")
        assertEquals("Cliente B", runBlocking { repository.observeNote(neun).first()?.label })
        assertEquals(0, client.requests.size)
    }

    @Test
    fun `settings show the last check, export and import, reminders, the lock and the version`() {
        val context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        runBlocking {
            settings.setLastCheckReport(
                CheckReport(startedAt = 1_764_590_520_000, finishedAt = 1_764_590_580_000, total = 3, checked = 3, notified = 1, loaded = 1),
            )
        }
        val viewModel = SettingsViewModel(
            settings,
            DailyCheckScheduler { WorkManager.getInstance(context) },
            CatalogRepository(FakeCatalogDao(), client),
            CaptureStore(File(context.cacheDir, "captures-test"), File(context.cacheDir, "shared-test"), settings, "test"),
            CatalogCrawlScheduler { WorkManager.getInstance(context) },
            DownloadsSaver(context),
            CaseListTransfer(context, repository, File(context.cacheDir, "shared-test")),
        )
        compose.setContent { SiseTrackerTheme { SettingsScreen(onBack = {}, viewModel = viewModel) } }

        waitForText("Última revisión:", substring = true)
        compose.onNodeWithText("3 de 3 expedientes revisados · 1 con acuerdos nuevos · 1 importado cargado").assertExists()
        compose.onNodeWithText("Recordar audiencias").assertExists()
        compose.onRoot().saveScreenshot("settings_status_light")
        compose.onNodeWithText("Exportar la lista").performScrollTo()
        compose.onNodeWithText("Importar una lista").assertExists()
        compose.onNodeWithText("Bloquear la app").performScrollTo()
        compose.onRoot().saveScreenshot("settings_list_privacy_light")

        // Nothing saved yet: export says so instead of writing an empty file.
        compose.onNodeWithText("Guardar en Descargas").performScrollTo().performClick()
        waitForText("Aún no hay expedientes guardados.")

        compose.onNodeWithText("SISE Tracker ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})").performScrollTo()
        compose.onRoot().saveScreenshot("settings_about_light")
        assertEquals(0, client.requests.size)
    }

    @Test
    fun `the lock screen asks to unlock once by itself`() {
        var asked = 0
        compose.setContent { SiseTrackerTheme { LockScreen(onUnlock = { asked++ }) } }

        waitForText("SISE Tracker está bloqueado")
        compose.waitUntil(5_000) { asked == 1 }
        compose.onRoot().saveScreenshot("lock_light")
        compose.onNodeWithText("Desbloquear").performClick()
        assertEquals(2, asked)
    }

    @Test
    fun `the import dialog says how many are new`() {
        var confirmed = false
        compose.setContent {
            SiseTrackerTheme {
                ImportDialog(
                    state = ImportUiState(ImportPreview(new = listOf(url, imported), alreadySaved = 1)),
                    onConfirm = { confirmed = true },
                    onDismiss = {},
                )
            }
        }

        waitForText("2 expedientes nuevos")
        compose.onNodeWithText("1 ya estaba guardado").assertExists()
        compose.onNodeWithText("Importar").performClick()
        assertEquals(true, confirmed)
    }
}
