package mx.sisetracker.data.cases

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.data.db.SiseDatabase
import mx.sisetracker.data.lookup.LookupRepository
import mx.sisetracker.testing.FakeSiseClient
import mx.sisetracker.testing.Fixtures
import mx.sisetracker.testing.inMemoryDatabase
import mx.sisetracker.testing.parseCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Export and import of the saved list (links only), imported links, and the user's notes. */
@RunWith(RobolectricTestRunner::class)
class SavedListTest {
    private val neun = "40612904"
    private val url = CaseUrl("1", "767", "1183/2025", "0")
    private val other = CaseUrl("11", "18", "293/2026", "0")
    private val page = parseCase(Fixtures.CASE_1183)
    private val client = FakeSiseClient()
    private var now = 1_000L
    private lateinit var db: SiseDatabase
    private lateinit var repository: CaseRepository

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repository = CaseRepository(db, client, LookupRepository(client), now = { now })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `import adds only links that aren't saved yet, with no request`() = runTest {
        repository.save(url, page)

        val preview = repository.previewImport(listOf(url, other, other))
        assertEquals(listOf(other), preview.new)
        assertEquals(1, preview.alreadySaved)

        assertEquals(1, repository.importLinks(listOf(url, other)))
        assertEquals(0, repository.importLinks(listOf(other)))
        assertTrue(client.requests.isEmpty())
        val pending = repository.observePending().first().single()
        assertEquals(other.toUrl(), pending.caseUrl)
        assertEquals("293/2026", pending.expediente)
        assertEquals("18", pending.organismoId)
        assertEquals("11", pending.tipoAsuntoId)
    }

    @Test
    fun `export lists saved cases, then imported links`() = runTest {
        repository.save(url, page)
        repository.importLinks(listOf(other))

        assertEquals(listOf(url.toUrl(), other.toUrl()), repository.exportLinks())
    }

    @Test
    fun `loading an imported link saves the case with one request and removes the link`() = runTest {
        client.onGet = { Fixtures.load(Fixtures.CASE_1183) }
        repository.importLinks(listOf(url))

        val result = repository.loadPending(url.toUrl())

        assertEquals(SaveResult.Saved(neun), result)
        assertEquals(listOf("GET ${url.toUrl()}"), client.requests)
        assertTrue(repository.observePending().first().isEmpty())
        // Already published when it was imported: nothing is "new".
        assertTrue(repository.unseenOrdenes(neun).isEmpty())
    }

    @Test
    fun `an imported link the portal doesn't find stays, marked, and the daily check skips it`() = runTest {
        client.onGet = { Fixtures.load(Fixtures.CASE_NOT_FOUND) }
        repository.importLinks(listOf(url))

        assertEquals(SaveResult.NotFound, repository.loadPending(url.toUrl()))

        assertTrue(repository.observePending().first().single().notFound)
        assertTrue(repository.getPendingToLoad().isEmpty())
        repository.removePending(url.toUrl())
        assertTrue(repository.observePending().first().isEmpty())
    }

    @Test
    fun `saving a case some other way removes its imported link`() = runTest {
        repository.importLinks(listOf(url))

        repository.save(url, page)

        assertTrue(repository.observePending().first().isEmpty())
    }

    @Test
    fun `label and note show in the summary, survive a refresh and go with the case`() = runTest {
        client.onGet = { Fixtures.load(Fixtures.CASE_1183) }
        repository.save(url, page)

        repository.setNote(neun, "  Cliente A ", "Llamar antes de la audiencia.")
        repository.refresh(neun)

        val summary = repository.observeSummaries().first().single()
        assertEquals("Cliente A", summary.label)
        assertEquals("Llamar antes de la audiencia.", summary.note)
        repository.delete(neun)
        assertNull(repository.observeNote(neun).first())
    }

    @Test
    fun `blank label and note remove them`() = runTest {
        repository.save(url, page)
        repository.setNote(neun, "Cliente A", "")

        repository.setNote(neun, " ", "")

        assertNull(repository.observeNote(neun).first())
        assertNull(repository.observeSummaries().first().single().label)
    }

    @Test
    fun `hearing entries are grouped by case, in page order`() = runTest {
        repository.save(url, page)

        val entries = repository.observeHearings().first().getValue(neun)

        assertEquals("Fecha señalada para audiencia constitucional", entries.first().label)
        assertTrue(entries.all { "audiencia" in it.label.lowercase() })
        assertEquals(repository.getHearings(), repository.observeHearings().first())
    }
}
