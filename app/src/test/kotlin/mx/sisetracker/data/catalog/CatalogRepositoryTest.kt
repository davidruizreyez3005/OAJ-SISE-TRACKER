package mx.sisetracker.data.catalog

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import mx.sisetracker.testing.FakeCatalogDao
import mx.sisetracker.testing.FakeSiseClient
import mx.sisetracker.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRepositoryTest {
    private val dao = FakeCatalogDao()
    private val client = FakeSiseClient().apply {
        // The saved ExpedienteyTipo.asp page has the same form steps C and D return.
        onPost = { Fixtures.load(Fixtures.SEARCH_FORM_1183) }
    }
    private var now = 1_000_000L
    private val dispatcher = StandardTestDispatcher()
    private val repository = CatalogRepository(dao, client, now = { now }, parsing = dispatcher)

    private val stepC = "POST https://www.dgej.cjf.gob.mx/internet/expedientes/ExpedienteyTipo.asp " +
        "Organismo=767&Buscar=Buscar&Circuito=1&CircuitoName=PRIMER+CIRCUITO"

    @Test
    fun `loads the tipos de asunto with one step C request`() = runTest(dispatcher) {
        val tipos = repository.tiposDeAsunto("1", "PRIMER CIRCUITO", "767")

        assertEquals(listOf(stepC), client.requests)
        assertEquals(10, tipos.size)
        assertEquals("1" to "Amparo Indirecto", tipos.first().value to tipos.first().label)
        assertTrue("No portal pre-selection", tipos.none { it.selected })
        assertEquals(10, dao.tipos.size)
        assertEquals(listOf("Circuito", "CircuitoName", "Organismo", "OrgName", "TipoOrganismo", "Accion"), dao.fields.map { it.name })
    }

    @Test
    fun `uses the cache for 30 days`() = runTest(dispatcher) {
        repository.tiposDeAsunto("1", "PRIMER CIRCUITO", "767")
        now += TimeUnit.DAYS.toMillis(29)

        val cached = repository.tiposDeAsunto("1", "PRIMER CIRCUITO", "767")

        assertEquals(1, client.requests.size)
        assertEquals(10, cached.size)
        assertEquals("Procesos Civiles o Administrativos", cached.last().label)
    }

    @Test
    fun `reloads after 30 days`() = runTest(dispatcher) {
        repository.tiposDeAsunto("1", "PRIMER CIRCUITO", "767")
        now += TimeUnit.DAYS.toMillis(31)

        repository.tiposDeAsunto("1", "PRIMER CIRCUITO", "767")

        assertEquals(listOf(stepC, stepC), client.requests)
        assertEquals(10, dao.tipos.size)
    }

    @Test
    fun `procedimientos echo the cached hidden fields with Accion 2`() = runTest(dispatcher) {
        repository.tiposDeAsunto("1", "PRIMER CIRCUITO", "767")

        val procedimientos = repository.tiposDeProcedimiento("1", "PRIMER CIRCUITO", "767", "9")
        repository.tiposDeProcedimiento("1", "PRIMER CIRCUITO", "767", "9")

        assertEquals(
            listOf(
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
        repository.tiposDeAsunto("1", "PRIMER CIRCUITO", "767")
        repository.clear()

        repository.tiposDeProcedimiento("1", "PRIMER CIRCUITO", "767", "125")

        assertEquals(3, client.requests.size)
        assertEquals(stepC, client.requests[1])
        assertTrue(client.requests[2].endsWith("TipoAsunto=125&Expediente=&Accion=2"))
    }

    @Test
    fun `clear forgets everything`() = runTest(dispatcher) {
        repository.tiposDeAsunto("1", "PRIMER CIRCUITO", "767")

        repository.clear()
        repository.tiposDeAsunto("1", "PRIMER CIRCUITO", "767")

        assertEquals(2, client.requests.size)
    }
}
