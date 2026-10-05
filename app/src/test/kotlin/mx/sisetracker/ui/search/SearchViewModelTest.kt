package mx.sisetracker.ui.search

import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.core.FormOption
import mx.sisetracker.data.catalog.CatalogRepository
import mx.sisetracker.data.lookup.LookupRepository
import mx.sisetracker.data.net.PortalError
import mx.sisetracker.data.settings.RecentOrgano
import mx.sisetracker.data.settings.SettingsStore
import mx.sisetracker.testing.FakeCatalogDao
import mx.sisetracker.testing.FakeSiseClient
import mx.sisetracker.testing.Fixtures
import mx.sisetracker.testing.InMemoryPreferences
import mx.sisetracker.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val client = FakeSiseClient().apply {
        onPost = { Fixtures.load(Fixtures.SEARCH_FORM_1183) }
        onGet = { Fixtures.load(Fixtures.CASE_1183) }
    }
    private val settings = SettingsStore(InMemoryPreferences())

    private val juzgado = "Juzgado Sexto de Distrito en Materia Penal en la Ciudad de México"
    private val caseUrl1183 =
        "GET https://www.dgej.cjf.gob.mx/siseinternet/reportes/vercaptura.aspx?tipoasunto=1&organismo=767&expediente=1183/2025&tipoprocedimiento=0"

    private fun TestScope.viewModel(): SearchViewModel {
        val viewModel = SearchViewModel(
            CatalogRepository(FakeCatalogDao(), client, now = { 0L }, parsing = main.dispatcher),
            LookupRepository(client, main.dispatcher),
            settings,
        )
        advanceUntilIdle()
        return viewModel
    }

    private fun tipo(value: String) = FormOption(value, "Tipo $value", position = 0, selected = false)

    @Test
    fun `starts with the last circuit used`() = runTest(main.dispatcher) {
        settings.setLastCircuito("7")

        assertEquals("7", viewModel().state.value.circuito)
    }

    @Test
    fun `a recent organo fills the form and loads its tipos once`() = runTest(main.dispatcher) {
        settings.addRecentOrgano(RecentOrgano("767", juzgado, circuito = "1"))
        val viewModel = viewModel()

        viewModel.onOrganoSelected(viewModel.state.value.knownOrganos.single())
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("1", state.circuito)
        assertEquals(juzgado, state.organoText)
        assertEquals(10, (state.tiposAsunto as Loadable.Loaded).value.size)
        assertEquals(1, client.requests.size)
        assertTrue(client.requests.single().endsWith("Organismo=767&Buscar=Buscar&Circuito=1&CircuitoName="))
    }

    @Test
    fun `a typed organismo loads tipos only when the dropdown opens`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        viewModel.onCircuitoChange("1")

        viewModel.onOrganoTextChange("767")
        advanceUntilIdle()
        assertTrue(client.requests.isEmpty())
        assertEquals("767", viewModel.state.value.organo?.id)

        viewModel.onTiposAsuntoRequested()
        advanceUntilIdle()
        assertEquals(1, client.requests.size)
    }

    @Test
    fun `buscar fetches the case once and shows the preview`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        viewModel.onCircuitoChange("1")
        viewModel.onOrganoTextChange("767")
        viewModel.onTiposAsuntoRequested()
        advanceUntilIdle()
        viewModel.onTipoAsuntoSelected(tipo("1"))
        viewModel.onExpedienteChange("1183/2025")

        viewModel.onSearch()
        assertEquals(LookupState.Loading, viewModel.state.value.lookup)
        assertFalse("One lookup per tap", viewModel.state.value.canSearch)
        viewModel.onSearch()
        advanceUntilIdle()

        val state = viewModel.state.value
        val found = state.lookup as LookupState.Found
        assertEquals(CaseUrl("1", "767", "1183/2025", "0"), found.url)
        assertEquals("40612904", found.page.neun)
        assertEquals(caseUrl1183, client.requests.last())
        assertEquals(1, client.requests.count { it.startsWith("GET") })
        // The bare number now shows the órgano's name, which is remembered.
        assertEquals(juzgado, state.organoText)
        assertEquals(RecentOrgano("767", juzgado, "1"), settings.recentOrganos.first().first())
        assertEquals("1", settings.lastCircuito.first())
    }

    @Test
    fun `an unknown expediente is not found`() = runTest(main.dispatcher) {
        client.onGet = { Fixtures.load(Fixtures.CASE_NOT_FOUND) }
        val viewModel = viewModel()
        viewModel.onCircuitoChange("1")
        viewModel.onOrganoTextChange("767")
        viewModel.onTiposAsuntoRequested()
        advanceUntilIdle()
        viewModel.onTipoAsuntoSelected(tipo("1"))
        viewModel.onExpedienteChange("99999/2025")

        viewModel.onSearch()
        advanceUntilIdle()

        assertEquals(LookupState.NotFound, viewModel.state.value.lookup)
        assertTrue(settings.recentOrganos.first().isEmpty())
    }

    @Test
    fun `a tipo with procedimientos loads them and needs one`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        viewModel.onCircuitoChange("1")
        viewModel.onOrganoTextChange("767")
        viewModel.onTiposAsuntoRequested()
        advanceUntilIdle()
        viewModel.onExpedienteChange("12/2026")

        viewModel.onTipoAsuntoSelected(tipo("9"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.showsProcedimiento)
        assertTrue(client.requests.last().endsWith("TipoAsunto=9&Expediente=&Accion=2"))
        val procedimientos = (state.tiposProcedimiento as Loadable.Loaded).value
        assertEquals(9, procedimientos.size)
        assertFalse(state.canSearch)

        viewModel.onTipoProcedimientoSelected(procedimientos.single { it.value == "3042" })
        viewModel.onSearch()
        advanceUntilIdle()
        assertTrue(client.requests.last().endsWith("tipoasunto=9&organismo=767&expediente=12/2026&tipoprocedimiento=3042"))
    }

    @Test
    fun `a network failure is reported`() = runTest(main.dispatcher) {
        client.onPost = { throw IOException("offline") }
        val viewModel = viewModel()
        viewModel.onCircuitoChange("1")
        viewModel.onOrganoTextChange("767")

        viewModel.onTiposAsuntoRequested()
        advanceUntilIdle()

        assertEquals(Loadable.Failed(PortalError.NETWORK), viewModel.state.value.tiposAsunto)
    }

    @Test
    fun `expediente keeps the portal's maxlength and warns on unusual shapes`() = runTest(main.dispatcher) {
        val viewModel = viewModel()

        viewModel.onExpedienteChange("1183/2025")
        assertFalse(viewModel.state.value.expedienteWarning)

        viewModel.onExpedienteChange("12345678901234567890")
        assertEquals(15, viewModel.state.value.expediente.length)
        assertTrue(viewModel.state.value.expedienteWarning)
    }
}
