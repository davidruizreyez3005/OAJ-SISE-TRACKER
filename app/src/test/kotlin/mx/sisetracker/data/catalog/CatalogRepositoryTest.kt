package mx.sisetracker.data.catalog

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import mx.sisetracker.core.OrganoKind
import mx.sisetracker.testing.FakeCatalogDao
import mx.sisetracker.testing.FakeSiseClient
import mx.sisetracker.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRepositoryTest {
    private val dao = FakeCatalogDao()
    private val client = FakeSiseClient().apply {
        onGet = { url ->
            check(url == ORGANOS_URL) { "Unexpected GET $url" }
            Fixtures.load(Fixtures.ORGANOS_CIR1)
        }
        // The saved ExpedienteyTipo.asp page has the same form steps C and D return.
        onPost = { Fixtures.load(Fixtures.SEARCH_FORM_1183) }
    }
    private var now = 1_000_000L
    private val dispatcher = StandardTestDispatcher()
    private val repository = CatalogRepository(dao, client, now = { now }, parsing = dispatcher)

    private val stepB = "GET $ORGANOS_URL"
    private val stepC = "POST https://www.dgej.cjf.gob.mx/internet/expedientes/ExpedienteyTipo.asp " +
        "Organismo=767&Buscar=Buscar&Circuito=1&CircuitoName=PRIMER+CIRCUITO"

    @Test
    fun `the circuits are bundled`() {
        assertEquals(32, repository.circuitos.size)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun `loads a circuit's organos with one request and caches them for 30 days`() = runTest(dispatcher) {
        assertNull(repository.cachedOrganos("1"))

        val organos = repository.organos("1")
        now += TimeUnit.DAYS.toMillis(29)
        val again = repository.organos("1")

        assertEquals(listOf(stepB), client.requests)
        assertEquals(184, organos.size)
        assertEquals(organos, again)
        assertEquals("PRIMER CIRCUITO", dao.circuitos.getValue("1").portalName)
        assertEquals(92, dao.organos.count { it.kind == OrganoKind.TRIBUNALES.name })
    }

    @Test
    fun `reloads organos after 30 days`() = runTest(dispatcher) {
        repository.organos("1")
        now += TimeUnit.DAYS.toMillis(31)

        assertNull(repository.cachedOrganos("1"))
        repository.organos("1")

        assertEquals(listOf(stepB, stepB), client.requests)
        assertEquals(184, dao.organos.size)
    }

    @Test
    fun `finds cached organos by name without a request`() = runTest(dispatcher) {
        assertTrue(repository.findCachedOrganos("Segundo Tribunal Colegiado en Materia Penal del Primer Circuito").isEmpty())
        repository.organos("1")

        val found = repository.findCachedOrganos("Segundo Tribunal  Colegiado en Materia Penal del Primer Circuito ")

        assertEquals(listOf("1" to "18"), found.map { it.circuito to it.organo.id })
        assertEquals(listOf("1"), repository.cachedCircuitosOf("767"))
        assertTrue("Exact names only", repository.findCachedOrganos("segundo tribunal colegiado en materia penal del primer circuito").isEmpty())
        assertEquals(1, client.requests.size)
    }

    @Test
    fun `tipos de asunto send the circuit name from the organo list`() = runTest(dispatcher) {
        val tipos = repository.tiposDeAsunto("1", "767")

        assertEquals(listOf(stepB, stepC), client.requests)
        assertEquals(10, tipos.size)
        assertEquals("1" to "Amparo Indirecto", tipos.first().value to tipos.first().label)
        assertTrue("No portal pre-selection", tipos.none { it.selected })
        assertEquals(10, dao.tipos.size)
        assertEquals(listOf("Circuito", "CircuitoName", "Organismo", "OrgName", "TipoOrganismo", "Accion"), dao.fields.map { it.name })
    }

    @Test
    fun `tipos de asunto reuse a cached organo list`() = runTest(dispatcher) {
        repository.organos("1")

        repository.tiposDeAsunto("1", "767")

        assertEquals(listOf(stepB, stepC), client.requests)
    }

    @Test
    fun `uses the cache for 30 days`() = runTest(dispatcher) {
        repository.tiposDeAsunto("1", "767")
        now += TimeUnit.DAYS.toMillis(29)

        val cached = repository.tiposDeAsunto("1", "767")

        assertEquals(2, client.requests.size)
        assertEquals(10, cached.size)
        assertEquals("Procesos Civiles o Administrativos", cached.last().label)
    }

    @Test
    fun `reloads after 30 days`() = runTest(dispatcher) {
        repository.tiposDeAsunto("1", "767")
        now += TimeUnit.DAYS.toMillis(31)

        repository.tiposDeAsunto("1", "767")

        assertEquals(listOf(stepB, stepC, stepB, stepC), client.requests)
        assertEquals(10, dao.tipos.size)
    }

    @Test
    fun `procedimientos echo the cached hidden fields with Accion 2`() = runTest(dispatcher) {
        repository.tiposDeAsunto("1", "767")

        val procedimientos = repository.tiposDeProcedimiento("1", "767", "9")
        repository.tiposDeProcedimiento("1", "767", "9")

        assertEquals(
            listOf(
                stepB,
                stepC,
                "POST https://www.dgej.cjf.gob.mx/internet/expedientes/ExpedienteyTipo.asp " +
                    "Circuito=1&CircuitoName=PRIMER+CIRCUITO&Organismo=767&OrgName=&TipoOrganismo=" +
                    "&TipoAsunto=9&Expediente=&Accion=2",
            ),
            client.requests,
        )
        assertEquals(9, procedimientos.size)
        assertEquals("276" to "Apelación", procedimientos.first().value to procedimientos.first().label)
    }

    @Test
    fun `procedimientos reload the form first when the cache was cleared`() = runTest(dispatcher) {
        repository.tiposDeAsunto("1", "767")
        repository.clear()

        repository.tiposDeProcedimiento("1", "767", "125")

        assertEquals(listOf(stepB, stepC, stepB, stepC), client.requests.take(4))
        assertTrue(client.requests[4].endsWith("TipoAsunto=125&Expediente=&Accion=2"))
        assertEquals(5, client.requests.size)
    }

    @Test
    fun `clear forgets everything`() = runTest(dispatcher) {
        repository.tiposDeAsunto("1", "767")

        repository.clear()

        assertNull(repository.cachedOrganos("1"))
        assertTrue(dao.circuitos.isEmpty())
        repository.tiposDeAsunto("1", "767")
        assertEquals(4, client.requests.size)
    }

    /** Chrome captures drop hidden inputs; the live page has them, so add the ones the portal sends. */
    private fun withHiddenFields(html: String, organismo: String) = html.replaceFirst(
        "method=\"POST\">",
        "method=\"POST\"><input type=\"hidden\" name=\"Circuito\" value=\"1\">" +
            "<input type=\"hidden\" name=\"Organismo\" value=\"$organismo\">",
    )

    @Test
    fun `an organo with no tipos is cached as empty`() = runTest(dispatcher) {
        client.onPost = { withHiddenFields(Fixtures.load(Fixtures.FORM_NO_TIPOS_6315), "6315") }

        val first = repository.tiposDeAsunto("1", "6315")
        val again = repository.tiposDeAsunto("1", "6315")

        assertTrue(first.isEmpty())
        assertTrue(again.isEmpty())
        assertEquals(listOf(stepB, stepC.replace("767", "6315")), client.requests)
    }

    @Test
    fun `procedimientos come from the Accion 2 reload, never the form's defaults`() = runTest(dispatcher) {
        client.onPost = { request ->
            val accion2 = request.fields.any { it == "Accion" to "2" }
            Fixtures.load(if (accion2) Fixtures.ACCION2_4343_125 else Fixtures.SEARCH_FORM_1183)
        }
        repository.tiposDeAsunto("1", "4343")

        val procedimientos = repository.tiposDeProcedimiento("1", "4343", "125")

        assertEquals(11, procedimientos.size)
        assertEquals("22800" to "Apelación", procedimientos.first().value to procedimientos.first().label)
        assertTrue(procedimientos.none { it.value == "276" || it.selected })
    }

    @Test
    fun `catalog pages go to the capture under fixture names`() = runTest(dispatcher) {
        val captured = mutableListOf<String>()
        val capturing = CatalogRepository(dao, client, now = { now }, parsing = dispatcher) { name, html ->
            captured += name
            assertTrue(html.isNotEmpty())
        }

        capturing.tiposDeAsunto("1", "767")
        capturing.tiposDeProcedimiento("1", "767", "125")

        assertEquals(
            listOf("circuitos_cir1", "expedienteytipo_form_767", "expedienteytipo_accion2_767_tipo125"),
            captured,
        )
    }

    private companion object {
        const val ORGANOS_URL = "https://www.dgej.cjf.gob.mx/internet/expedientes/circuitos.asp?Cir=1&Exp=1"
    }
}
