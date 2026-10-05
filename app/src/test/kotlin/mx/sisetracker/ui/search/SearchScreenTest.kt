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
        onGet = { Fixtures.load(Fixtures.CASE_1183) }
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

    @Test
    fun `a lookup shows the case preview`() {
        runBlocking {
            settings.addRecentOrgano(RecentOrgano("767", "Juzgado Sexto de Distrito en Materia Penal en la Ciudad de México", "1"))
        }
        setContent()

        compose.onNodeWithText("Juzgado Sexto de Distrito en Materia Penal en la Ciudad de México").performClick()
        compose.waitUntil(5_000) { client.requests.size == 1 }
        compose.onNodeWithText("Tipo de asunto").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Amparo Indirecto")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Amparo Indirecto").performClick()
        compose.onNode(hasSetTextAction() and hasText("Número de expediente")).performTextInput("1183/2025")
        compose.onNodeWithText("Buscar").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("28 acuerdos")).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText("Último acuerdo publicado: 01/09/2026").performScrollTo().assertExists()
        assertEquals(1, client.requests.count { it.startsWith("GET") })
        compose.onRoot().saveScreenshot("search_found_light")
    }

    @Test
    fun `renders in the dark theme`() {
        setContent(darkTheme = true)

        compose.onNodeWithText("Circuito").assertExists()
        compose.onRoot().saveScreenshot("search_empty_dark")
    }
}
