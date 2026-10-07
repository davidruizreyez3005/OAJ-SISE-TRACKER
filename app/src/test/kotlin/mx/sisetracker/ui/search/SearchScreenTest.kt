package mx.sisetracker.ui.search

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.runBlocking
import mx.sisetracker.data.catalog.CatalogRepository
import mx.sisetracker.data.lookup.LookupRepository
import mx.sisetracker.data.settings.RecentOrgano
import mx.sisetracker.data.settings.SettingsStore
import mx.sisetracker.testing.FakeCatalogDao
import mx.sisetracker.testing.FakeSavedCases
import mx.sisetracker.testing.FakeSiseClient
import mx.sisetracker.testing.Fixtures
import mx.sisetracker.testing.InMemoryPreferences
import mx.sisetracker.testing.saveScreenshot
import mx.sisetracker.ui.theme.SiseTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SearchScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val client = FakeSiseClient().apply {
        onPost = { Fixtures.load(Fixtures.SEARCH_FORM_1183) }
        onGet = { url -> Fixtures.load(if ("circuitos.asp" in url) Fixtures.ORGANOS_CIR1 else Fixtures.CASE_1183) }
    }
    private val settings = SettingsStore(InMemoryPreferences())

    private fun setContent(darkTheme: Boolean = false) {
        val viewModel = SearchViewModel(
            CatalogRepository(FakeCatalogDao(), client),
            LookupRepository(client),
            FakeSavedCases(),
            settings,
        )
        compose.setContent {
            SiseTrackerTheme(darkTheme = darkTheme) {
                SearchScreen(
                    onBack = {},
                    onOpenPortal = {},
                    onCaseLinkFound = {},
                    onOpenCase = {},
                    viewModel = viewModel,
                )
            }
        }
    }

    @Test
    fun `shows the portal's fields in order`() {
        setContent()

        listOf("Circuito", "Órgano", "Tipo de asunto", "Número de expediente", "Buscar", "Abrir en el portal")
            .forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertExists() }
        compose.onNodeWithText("Buscar").assertIsNotEnabled()
        compose.onRoot().saveScreenshot("search_empty_light")
    }

    @Test
    fun `the slash key types a slash`() {
        setContent()
        val expediente = hasSetTextAction() and hasText("Número de expediente")

        compose.onNode(expediente).performTextInput("1183")
        compose.onNodeWithText("/").performClick()
        compose.onNode(expediente).performTextInput("2025")

        compose.onNode(expediente).assertTextContains("1183/2025")
    }

    /** Picks the recent órgano 767, Amparo Indirecto and 1183/2025, then taps Buscar. */
    private fun searchFor1183() {
        runBlocking {
            settings.addRecentOrgano(RecentOrgano("767", "Juzgado Sexto de Distrito en Materia Penal en la Ciudad de México", "1"))
        }
        setContent()

        compose.onNodeWithText("Juzgado Sexto de Distrito en Materia Penal en la Ciudad de México").performClick()
        // The órgano's own tipos come from the bundled catalog: no request.
        compose.waitForIdle()
        compose.onNodeWithText("Tipo de asunto").performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("Amparo Indirecto")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Amparo Indirecto").performClick()
        compose.onNode(hasSetTextAction() and hasText("Número de expediente")).performTextInput("1183/2025")
        compose.onNodeWithText("Buscar").performScrollTo().performClick()
    }

    @Test
    fun `a lookup shows the case preview`() {
        searchFor1183()
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("28 acuerdos")).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText("Último acuerdo publicado: 01/09/2026").performScrollTo().assertExists()
        // One request in all: the case page itself.
        assertEquals(1, client.requests.size)
        assertTrue("vercaptura.aspx" in client.requests.single())
        compose.onRoot().saveScreenshot("search_found_light")
    }

    @Test
    fun `a page that doesn't parse says where it broke`() {
        client.onGet = { url ->
            val html = Fixtures.load(if ("circuitos.asp" in url) Fixtures.ORGANOS_CIR1 else Fixtures.CASE_1183)
            if ("circuitos.asp" in url) {
                html
            } else {
                // The first acuerdo's fecha del auto in a format the parser doesn't know.
                val grid = html.indexOf("id=\"grvAcuerdos\"")
                val date = checkNotNull(Regex(">(\\d{2})-(\\d{2})-(\\d{4})<").find(html, grid))
                html.replaceRange(date.range, ">${date.groupValues[3]}.${date.groupValues[2]}.${date.groupValues[1]}<")
            }
        }
        searchFor1183()
        val where = "Dónde: Acuerdos, fila 1, fecha del auto"
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText(where, substring = true)).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText("formato inesperado", substring = true).performScrollTo().assertExists()
        compose.onRoot().saveScreenshot("search_unexpected_page_light")
    }

    @Test
    fun `picking a circuit lists its organos, filtered by kind`() {
        setContent()

        compose.onNodeWithText("Circuito").performClick()
        // The menu shows each circuit's ordinal and state on two lines.
        compose.onNodeWithText("Ciudad de México").assertExists()
        compose.onNodeWithText("Primer Circuito").performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("Tribunales")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Tribunales").performClick()
        compose.onNodeWithText("Colegiados de Circuito").performClick()
        compose.onNodeWithText("Penal").performClick()
        compose.onNodeWithText("10 órganos coinciden", substring = true).assertExists()
        compose.onNode(hasSetTextAction() and hasText("Órgano")).performTextInput("segundo colegiado penal")

        compose.onNodeWithText("Segundo Tribunal Colegiado en Materia Penal del Primer Circuito").assertExists()
        assertEquals(1, client.requests.size)
        compose.onRoot().saveScreenshot("search_organos_light")
    }

    @Test
    fun `renders in the dark theme`() {
        setContent(darkTheme = true)

        compose.onNodeWithText("Circuito").assertExists()
        compose.onRoot().saveScreenshot("search_empty_dark")
    }

    private companion object {
        /** Parsing runs on Dispatchers.Default; the first Jsoup parse in a cold JVM can be slow. */
        const val TIMEOUT = 15_000L
    }
}
