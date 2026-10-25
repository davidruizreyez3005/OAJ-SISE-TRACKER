package mx.sisetracker.ui

import android.net.Uri
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.data.cases.CaseRepository
import mx.sisetracker.data.db.SiseDatabase
import mx.sisetracker.data.lookup.LookupRepository
import mx.sisetracker.data.settings.SettingsStore
import mx.sisetracker.data.transfer.CaseListTransfer
import mx.sisetracker.testing.FakeSiseClient
import mx.sisetracker.testing.Fixtures
import mx.sisetracker.testing.InMemoryPreferences
import mx.sisetracker.testing.MainDispatcherRule
import mx.sisetracker.testing.inMemoryDatabase
import mx.sisetracker.testing.parseCase
import mx.sisetracker.ui.lock.LockState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The app lock, and links coming in from shares and exported lists. */
@RunWith(RobolectricTestRunner::class)
class AppViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val context = RuntimeEnvironment.getApplication()
    private val url = CaseUrl("1", "767", "1183/2025", "0")
    private val other = CaseUrl("11", "18", "293/2026", "0")
    private val client = FakeSiseClient()
    private val settings = SettingsStore(InMemoryPreferences())
    private val shareDir = File(context.cacheDir, "transfer-test")
    private var elapsed = 0L
    private lateinit var db: SiseDatabase
    private lateinit var cases: CaseRepository
    private lateinit var transfer: CaseListTransfer

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        cases = CaseRepository(db, client, LookupRepository(client))
        transfer = CaseListTransfer(context, cases, shareDir, now = { 1_761_400_000_000 })
    }

    @After
    fun tearDown() {
        db.close()
        shareDir.deleteRecursively()
    }

    private fun TestScope.viewModel(): AppViewModel =
        AppViewModel(cases, transfer, settings, elapsed = { elapsed }).also { advanceUntilIdle() }

    @Test
    fun `without the lock the app opens unlocked and stays so`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        assertEquals(LockState.UNLOCKED, viewModel.lock.value)

        viewModel.onAppHidden()
        elapsed += 10 * 60_000
        viewModel.onAppShown()

        assertEquals(LockState.UNLOCKED, viewModel.lock.value)
    }

    @Test
    fun `with the lock on it opens locked, and locks again after a minute away`() = runTest(main.dispatcher) {
        settings.setAppLock(true)
        val viewModel = viewModel()
        assertEquals(LockState.LOCKED, viewModel.lock.value)
        viewModel.onUnlocked()

        // A file picker or the share sheet: back within a minute.
        viewModel.onAppHidden()
        elapsed += 30_000
        viewModel.onAppShown()
        assertEquals(LockState.UNLOCKED, viewModel.lock.value)

        viewModel.onAppHidden()
        elapsed += AppViewModel.LOCK_AFTER_MILLIS
        viewModel.onAppShown()
        assertEquals(LockState.LOCKED, viewModel.lock.value)
    }

    @Test
    fun `one shared link opens the save sheet, several the import dialog`() = runTest(main.dispatcher) {
        val viewModel = viewModel()

        viewModel.onLinksShared(listOf(url))
        assertEquals(url, viewModel.capture.value?.url)
        assertNull(viewModel.import.value)

        viewModel.onCaptureDismissed()
        viewModel.onLinksShared(listOf(url, other))
        val preview = viewModel.import.first { it != null }?.preview
        assertEquals(listOf(url, other), preview?.new)

        viewModel.onConfirmImport()
        assertEquals(AppMessage.Imported(2), viewModel.message.first { it != null })
        assertEquals(2, cases.observePending().first().size)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun `an exported list imports back on another phone`() = runTest(main.dispatcher) {
        cases.save(url, parseCase(Fixtures.CASE_1183))
        cases.importLinks(listOf(other))
        val file = checkNotNull(transfer.exportFile())

        val text = file.readText()
        assertTrue(text.startsWith("SISE Tracker: 2 expedientes guardados, "))
        assertTrue(text.lines().containsAll(listOf(url.toUrl(), other.toUrl())))
        assertTrue(file.name.matches(Regex("sise-expedientes-\\d{8}\\.txt")))

        // On the other phone nothing is saved yet.
        cases.delete("40612904")
        cases.removePending(other.toUrl())
        val viewModel = viewModel()
        viewModel.onImportFile(Uri.fromFile(file))

        assertEquals(listOf(url, other), viewModel.import.first { it != null }?.preview?.new)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun `a file without links says so`() = runTest(main.dispatcher) {
        val file = File(context.cacheDir, "sin-enlaces.txt").apply { writeText("Hola") }
        val viewModel = viewModel()

        viewModel.onImportFile(Uri.fromFile(file))

        assertEquals(AppMessage.NoLinks, viewModel.message.first { it != null })
        file.delete()
    }
}
