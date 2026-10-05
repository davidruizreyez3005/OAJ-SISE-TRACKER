package mx.sisetracker.data.lookup

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import mx.sisetracker.core.CaseLookup
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.core.SiseParseException
import mx.sisetracker.testing.FakeSiseClient
import mx.sisetracker.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LookupRepositoryTest {
    private val client = FakeSiseClient()
    private val dispatcher = StandardTestDispatcher()
    private val repository = LookupRepository(client, dispatcher)

    @Test
    fun `fetches the case page once and parses it`() = runTest(dispatcher) {
        client.onGet = { Fixtures.load(Fixtures.CASE_1183) }

        val lookup = repository.lookup(CaseUrl("1", "767", "1183/2025", "0"))

        assertEquals(
            listOf("GET https://www.dgej.cjf.gob.mx/siseinternet/reportes/vercaptura.aspx?tipoasunto=1&organismo=767&expediente=1183/2025&tipoprocedimiento=0"),
            client.requests,
        )
        assertTrue(lookup is CaseLookup.Found)
        assertEquals("40612904", (lookup as CaseLookup.Found).page.neun)
    }

    @Test
    fun `an empty NEUN is not found`() = runTest(dispatcher) {
        client.onGet = { Fixtures.load(Fixtures.CASE_NOT_FOUND) }

        assertEquals(CaseLookup.NotFound, repository.lookup(CaseUrl("1", "767", "99999/2025", "0")))
    }

    @Test
    fun `a page that isn't a case page is a parse error`() {
        client.onGet = { "<html><body>Mantenimiento</body></html>" }

        assertThrows(SiseParseException::class.java) {
            runTest(dispatcher) { repository.lookup(CaseUrl("1", "767", "1/2025", "0")) }
        }
    }
}
