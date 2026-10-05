package mx.sisetracker.data.cases

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.data.db.SiseDatabase
import mx.sisetracker.data.lookup.LookupRepository
import mx.sisetracker.testing.FakeSiseClient
import mx.sisetracker.testing.Fixtures
import mx.sisetracker.testing.ftsOrdenes
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

/** Saved cases against an in-memory database, with the portal answering from fixtures. */
@RunWith(RobolectricTestRunner::class)
class CaseRepositoryTest {
    private val neun = "40612904"
    private val url = CaseUrl("1", "767", "1183/2025", "0")
    private val caseUrlRequest =
        "GET https://www.dgej.cjf.gob.mx/siseinternet/reportes/vercaptura.aspx?tipoasunto=1&organismo=767&expediente=1183/2025&tipoprocedimiento=0"
    private val sintesis38Url =
        "https://www.dgej.cjf.gob.mx/siseinternet/Actuaria/VerAcuerdo.aspx?listaAcOrd=38&listaCatOrg=767&listaNeun=40612904&listaAsuId=1&listaExped=1183/2025&listaFAuto=31/08/2026&listaFPublicacion=01/09/2026"

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
    fun `saving a previewed case stores everything without a request`() = runTest {
        repository.save(url, page)

        assertTrue(client.requests.isEmpty())
        val dao = db.caseDao()
        val case = dao.getCase(neun)!!
        assertEquals("1183/2025", case.expediente)
        assertEquals("767", case.organismoId)
        assertEquals("1", case.tipoAsuntoId)
        assertEquals("0", case.tipoProcedimiento)
        assertEquals("Juzgado Sexto de Distrito en Materia Penal en la Ciudad de México", case.organoName)
        assertEquals("Amparo Indirecto", case.tipoAsuntoName)
        assertEquals("20255739005400076/2025", case.noControlOcc)
        assertEquals(5, case.partyCount)
        assertEquals(url.toUrl(), case.caseUrl)

        val acuerdos = dao.getAcuerdos(neun).sortedBy { it.orden }
        assertEquals(28, acuerdos.size)
        assertTrue("Saved acuerdos aren't new", acuerdos.all { it.seen && it.firstSeenAt == 1_000L })
        assertEquals(sintesis38Url, acuerdos.last().verAcuerdoUrl)
        assertTrue(acuerdos.all { it.sintesis == null })

        assertEquals(1, repository.observeResoluciones(neun).first().size)
        assertEquals("42423129", repository.observeRelacionados(neun).first().single().relatedNeun)
        val captura = repository.observeCaptura(neun).first()
        assertEquals(page.captura.entries.size, captura.size)
        assertEquals(page.captura.entries.map { it.label }, captura.map { it.label })
        assertEquals(page.captura.entries.map { it.group }, captura.map { it.group })

        val summary = repository.observeSummaries().first().single()
        assertEquals(28, summary.acuerdoCount)
        assertEquals(0, summary.unseenCount)
        assertEquals(LocalDate.of(2026, 9, 1), summary.latestPublicacion)
    }

    @Test
    fun `saving from a link fetches the case page once`() = runTest {
        client.onGet = { Fixtures.load(Fixtures.CASE_1183) }

        assertEquals(SaveResult.Saved(neun), repository.saveFromUrl(url))

        assertEquals(listOf(caseUrlRequest), client.requests)
        assertTrue(repository.isSaved(neun))
    }

    @Test
    fun `an unknown case from a link isn't saved`() = runTest {
        client.onGet = { Fixtures.load(Fixtures.CASE_NOT_FOUND) }

        assertEquals(SaveResult.NotFound, repository.saveFromUrl(CaseUrl("1", "767", "99999/2025", "0")))

        assertTrue(repository.observeSummaries().first().isEmpty())
    }

    @Test
    fun `refresh stores acuerdos missing from the last read as new, by orden`() = runTest {
        // Saved before ordenes 21 (a gap in the middle) and 38 were published.
        repository.save(url, page.copy(acuerdos = page.acuerdos.filterNot { it.orden == 21 || it.orden == 38 }))
        client.onGet = { Fixtures.load(Fixtures.CASE_1183) }
        now = 2_000L

        val result = repository.refresh(neun)

        assertEquals(listOf(caseUrlRequest), client.requests)
        val added = (result as RefreshResult.Updated).newAcuerdos
        assertEquals(listOf(21, 38), added.map { it.orden }.sorted())
        assertTrue(added.all { !it.seen && it.firstSeenAt == 2_000L })
        assertEquals(setOf(21, 38), repository.unseenOrdenes(neun))
        assertEquals(2, repository.observeSummaries().first().single().unseenCount)
        val old = db.caseDao().getAcuerdo(neun, 1)!!
        assertTrue(old.seen)
        assertEquals(1_000L, old.firstSeenAt)
        assertEquals(2_000L, db.caseDao().getCase(neun)!!.lastCheckedAt)

        repository.markSeen(neun)
        assertTrue(repository.unseenOrdenes(neun).isEmpty())
    }

    @Test
    fun `refresh keeps fetched sintesis`() = runTest {
        repository.save(url, page)
        db.caseDao().setSintesis(neun, 38, "Síntesis guardada")
        client.onGet = { Fixtures.load(Fixtures.CASE_1183) }

        val result = repository.refresh(neun)

        assertTrue((result as RefreshResult.Updated).newAcuerdos.isEmpty())
        assertEquals("Síntesis guardada", db.caseDao().getAcuerdo(neun, 38)?.sintesis)
    }

    @Test
    fun `a case the portal no longer finds keeps its data`() = runTest {
        repository.save(url, page)
        client.onGet = { Fixtures.load(Fixtures.CASE_NOT_FOUND) }
        now = 5_000L

        assertEquals(RefreshResult.NotFound, repository.refresh(neun))

        assertEquals(28, db.caseDao().getAcuerdos(neun).size)
        assertEquals(5_000L, db.caseDao().getCase(neun)!!.lastCheckedAt)
    }

    @Test
    fun `a truncated resumen fetches the sintesis once, then stores and indexes it`() = runTest {
        repository.save(url, page)
        client.onGet = { requested ->
            check(requested == sintesis38Url) { "Unexpected GET $requested" }
            Fixtures.load(Fixtures.SINTESIS_1183_38)
        }
        assertTrue(db.ftsOrdenes("legitimacion").isEmpty())

        val text = repository.sintesis(neun, 38)
        val again = repository.sintesis(neun, 38)

        assertTrue(text.startsWith("Ciudad de México, treinta y uno de agosto de dos mil veintiséis.\nTribunal colegiado"))
        assertEquals(text, again)
        assertEquals(listOf("GET $sintesis38Url"), client.requests)
        assertEquals(text, db.caseDao().getAcuerdo(neun, 38)?.sintesis)
        // "legitimación" only appears past the résumé's 255 characters.
        assertEquals(listOf(38), db.ftsOrdenes("legitimacion"))
    }

    @Test
    fun `a complete resumen is the sintesis, with no request`() = runTest {
        repository.save(url, page)

        val text = repository.sintesis(neun, 4)

        assertEquals("Único. Se declara sin materia la suspensión definitiva solicitada.", text)
        assertTrue(client.requests.isEmpty())
        assertNull(db.caseDao().getAcuerdo(neun, 4)?.sintesis)
    }

    @Test
    fun `the index ignores accents and case`() = runTest {
        repository.save(url, page)

        assertTrue(1 in db.ftsOrdenes("EXTRADICION"))
        assertEquals(db.ftsOrdenes("extradición"), db.ftsOrdenes("extradicion"))
        assertEquals(listOf(4), db.ftsOrdenes("\"sin materia la suspension definitiva\""))
    }

    @Test
    fun `deleting a case removes its rows and index entries`() = runTest {
        repository.save(url, page)

        repository.delete(neun)

        assertTrue(db.caseDao().getAcuerdos(neun).isEmpty())
        assertTrue(repository.observeCaptura(neun).first().isEmpty())
        assertTrue(db.ftsOrdenes("extradicion").isEmpty())
        assertTrue(repository.observeSummaries().first().isEmpty())
    }
}
