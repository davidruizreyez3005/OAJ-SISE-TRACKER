package mx.sisetracker.ui.search

import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.core.Circuitos
import mx.sisetracker.core.OrganoKind
import mx.sisetracker.core.FormOption
import mx.sisetracker.data.catalog.CatalogRepository
import mx.sisetracker.data.lookup.LookupRepository
import mx.sisetracker.data.net.PortalError
import mx.sisetracker.data.settings.RecentOrgano
import mx.sisetracker.data.settings.SettingsStore
import mx.sisetracker.data.db.SavedOrgano
import mx.sisetracker.ui.SearchRoute
import mx.sisetracker.testing.FakeCatalogDao
import mx.sisetracker.testing.FakeSavedCases
import mx.sisetracker.testing.FakeSiseClient
import mx.sisetracker.testing.Fixtures
import mx.sisetracker.testing.InMemoryPreferences
import mx.sisetracker.testing.MainDispatcherRule
import mx.sisetracker.testing.parseCase
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
        onGet = { url -> Fixtures.load(if ("circuitos.asp" in url) Fixtures.ORGANOS_CIR1 else Fixtures.CASE_1183) }
    }
    private val primerCircuito = Circuitos.all.first()
    private val organosRequest = "GET https://www.dgej.cjf.gob.mx/internet/expedientes/circuitos.asp?Cir=1&Exp=1"
    private val settings = SettingsStore(InMemoryPreferences())
    private val savedCases = FakeSavedCases()

    private val juzgado = "Juzgado Sexto de Distrito en Materia Penal en la Ciudad de México"
    private val caseUrl1183 =
        "GET https://www.dgej.cjf.gob.mx/siseinternet/reportes/vercaptura.aspx?tipoasunto=1&organismo=767&expediente=1183/2025&tipoprocedimiento=0"

    private val catalogDao = FakeCatalogDao()

    private fun TestScope.viewModel(prefill: SearchRoute = SearchRoute()): SearchViewModel {
        val viewModel = SearchViewModel(
            CatalogRepository(catalogDao, client, now = { 0L }, parsing = main.dispatcher),
            LookupRepository(client, main.dispatcher),
            savedCases,
            settings,
            prefill,
        )
        advanceUntilIdle()
        return viewModel
    }

    private fun tipo(value: String) = FormOption(value, "Tipo $value", position = 0, selected = false)

    @Test
    fun `starts with the last circuit used, without a request`() = runTest(main.dispatcher) {
        settings.setLastCircuito("7")

        val state = viewModel().state.value

        assertEquals("7", state.circuito)
        assertEquals("Séptimo Circuito Veracruz de Ignacio de la Llave", state.circuitoLabel)
        assertEquals(Loadable.Idle, state.organos)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun `a last circuit that isn't in the list is ignored`() = runTest(main.dispatcher) {
        settings.setLastCircuito("99")

        assertEquals("", viewModel().state.value.circuito)
    }

    @Test
    fun `picking a circuit loads its organos once and remembers it`() = runTest(main.dispatcher) {
        val viewModel = viewModel()

        viewModel.onCircuitoSelected(primerCircuito)
        advanceUntilIdle()
        viewModel.onOrganosRequested()
        viewModel.onCircuitoSelected(primerCircuito)
        advanceUntilIdle()

        assertEquals(listOf(organosRequest), client.requests)
        val organos = (viewModel.state.value.organos as Loadable.Loaded).value
        assertEquals(184, organos.size)
        assertTrue(organos.all { it.circuito == "1" })
        assertEquals("1", settings.lastCircuito.first())
    }

    @Test
    fun `the remembered circuit's organos load when the organo field opens`() = runTest(main.dispatcher) {
        settings.setLastCircuito("1")
        val viewModel = viewModel()
        assertTrue(client.requests.isEmpty())

        viewModel.onOrganosRequested()
        advanceUntilIdle()

        assertEquals(listOf(organosRequest), client.requests)
        assertEquals(184, (viewModel.state.value.organos as Loadable.Loaded).value.size)
    }

    @Test
    fun `cached organos show without a request`() = runTest(main.dispatcher) {
        CatalogRepository(catalogDao, client, now = { 0L }, parsing = main.dispatcher).organos("1")
        client.requests.clear()
        settings.setLastCircuito("1")

        val viewModel = viewModel()

        assertEquals(184, (viewModel.state.value.organos as Loadable.Loaded).value.size)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun `the kind chips and typing filter the circuit's organos`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        viewModel.onCircuitoSelected(primerCircuito)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.showsKindFilter)
        assertEquals(184, viewModel.state.value.organoSuggestions.size)

        viewModel.onKindFilterChange(OrganoKind.TRIBUNALES)
        assertEquals(93, viewModel.state.value.organoSuggestions.size)

        viewModel.onOrganoTextChange("segundo penal")
        assertEquals(
            listOf("18"),
            viewModel.state.value.organoSuggestions.filter { "Colegiado en Materia Penal" in it.name }.map { it.id },
        )

        viewModel.onKindFilterChange(OrganoKind.OTROS)
        viewModel.onOrganoTextChange("comision de disciplina")
        assertEquals(listOf("6316"), viewModel.state.value.organoSuggestions.map { it.id })
    }

    @Test
    fun `a typed organismo number takes its name from the circuit's list`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        viewModel.onCircuitoSelected(primerCircuito)
        advanceUntilIdle()

        viewModel.onOrganoTextChange("767")

        assertEquals(KnownOrgano("767", juzgado, "1"), viewModel.state.value.organo)
    }

    @Test
    fun `changing the circuit clears an organo from another one`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        viewModel.onCircuitoSelected(primerCircuito)
        advanceUntilIdle()
        viewModel.onOrganoSelected((viewModel.state.value.organos as Loadable.Loaded).value.single { it.id == "767" })
        advanceUntilIdle()

        viewModel.onCircuitoSelected(Circuitos.all[1])
        advanceUntilIdle()

        assertEquals("2", viewModel.state.value.circuito)
        assertEquals(null, viewModel.state.value.organo)
        assertEquals(Loadable.Idle, viewModel.state.value.tiposAsunto)
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
        // Step C needs the CircuitoName, which comes with the circuit's órgano list.
        assertEquals(2, client.requests.size)
        assertEquals(organosRequest, client.requests.first())
        assertTrue(client.requests.last().endsWith("Organismo=767&Buscar=Buscar&Circuito=1&CircuitoName=PRIMER+CIRCUITO"))
        assertEquals(184, (state.organos as Loadable.Loaded).value.size)
    }

    @Test
    fun `a typed organismo loads tipos only when the dropdown opens`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        viewModel.onCircuitoSelected(primerCircuito)

        advanceUntilIdle()
        client.requests.clear()
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
        viewModel.onCircuitoSelected(primerCircuito)
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
        assertEquals(1, client.requests.count { "vercaptura.aspx" in it })
        // The bare number now shows the órgano's name, which is remembered.
        assertEquals(juzgado, state.organoText)
        assertEquals(RecentOrgano("767", juzgado, "1"), settings.recentOrganos.first().first())
        assertEquals("1", settings.lastCircuito.first())
    }

    @Test
    fun `an unknown expediente is not found`() = runTest(main.dispatcher) {
        client.onGet = { Fixtures.load(Fixtures.CASE_NOT_FOUND) }
        val viewModel = viewModel()
        viewModel.onCircuitoSelected(primerCircuito)
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
        viewModel.onCircuitoSelected(primerCircuito)
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
        viewModel.onCircuitoSelected(primerCircuito)
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

    private fun TestScope.foundViewModel(): SearchViewModel {
        val viewModel = viewModel()
        viewModel.onCircuitoSelected(primerCircuito)
        viewModel.onOrganoTextChange("767")
        viewModel.onTiposAsuntoRequested()
        advanceUntilIdle()
        viewModel.onTipoAsuntoSelected(tipo("1"))
        viewModel.onExpedienteChange("1183/2025")
        viewModel.onSearch()
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `guardar stores the previewed page without another request`() = runTest(main.dispatcher) {
        val viewModel = foundViewModel()
        val requests = client.requests.size

        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(requests, client.requests.size)
        assertEquals("40612904", savedCases.saved.keys.single())
        assertEquals("40612904", viewModel.state.value.openCase)
        viewModel.onCaseOpened()
        assertEquals(null, viewModel.state.value.openCase)
    }

    @Test
    fun `a case that's already saved offers to open it`() = runTest(main.dispatcher) {
        savedCases.saved["40612904"] = parseCase(Fixtures.CASE_1183)

        val viewModel = foundViewModel()

        assertTrue((viewModel.state.value.lookup as LookupState.Found).alreadySaved)
        viewModel.onOpenSaved()
        assertEquals("40612904", viewModel.state.value.openCase)
    }

    @Test
    fun `a related case pre-fills what known names match exactly, with no request`() = runTest(main.dispatcher) {
        savedCases.organos.value = listOf(
            SavedOrgano("18", "Segundo Tribunal Colegiado en Materia Penal del Primer Circuito", "11", "Amparo en revisión"),
        )

        val viewModel = viewModel(
            SearchRoute(
                expediente = "293/2026",
                organoName = "Segundo Tribunal Colegiado en Materia Penal del Primer Circuito",
                tipoAsuntoName = "Amparo en revisión",
            ),
        )

        val state = viewModel.state.value
        assertEquals("293/2026", state.expediente)
        assertEquals("18", state.organo?.id)
        assertEquals(FormOption("11", "Amparo en revisión", 0, false), state.tipoAsunto)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun `a related case pre-fills circuit and organo from the cached catalog`() = runTest(main.dispatcher) {
        CatalogRepository(catalogDao, client, now = { 0L }, parsing = main.dispatcher).organos("1")
        client.requests.clear()

        val viewModel = viewModel(
            SearchRoute(
                expediente = "293/2026",
                organoName = "Segundo Tribunal Colegiado en Materia Penal del Primer Circuito",
                tipoAsuntoName = "Amparo en revisión",
            ),
        )

        val state = viewModel.state.value
        assertEquals("1", state.circuito)
        assertEquals(KnownOrgano("18", "Segundo Tribunal Colegiado en Materia Penal del Primer Circuito", "1"), state.organo)
        // No tipos cached for it, so the user picks the tipo.
        assertEquals(null, state.tipoAsunto)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun `a related case with an unknown organo only pre-fills the expediente`() = runTest(main.dispatcher) {
        val viewModel = viewModel(
            SearchRoute(expediente = "293/2026", organoName = "Otro Tribunal", tipoAsuntoName = "Amparo en revisión"),
        )

        val state = viewModel.state.value
        assertEquals("293/2026", state.expediente)
        assertEquals(null, state.organo)
        assertEquals(null, state.tipoAsunto)
    }
}
