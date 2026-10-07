package mx.sisetracker.data.crawl

import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mx.sisetracker.core.FormRequest
import mx.sisetracker.data.capture.CaptureStore
import mx.sisetracker.data.catalog.CatalogRepository
import mx.sisetracker.data.net.PortalError
import mx.sisetracker.data.settings.SettingsStore
import mx.sisetracker.testing.FakeCatalogDao
import mx.sisetracker.testing.FakeSiseClient
import mx.sisetracker.testing.Fixtures
import mx.sisetracker.testing.InMemoryPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogCrawlTest {
    /** Órganos whose form offers tipos 125/126 (the apelación tribunals of circuits 1 and 21). */
    private val apelacion = setOf("4343", "4344", "4341", "4342", "4366")

    private val client = FakeSiseClient().apply {
        // Cir=1 is the Primer Circuito; every other circuit answers with the Guerrero list.
        onGet = { url -> Fixtures.load(if (url.contains("Cir=1&")) Fixtures.ORGANOS_CIR1 else Fixtures.ORGANOS_CIR21) }
        onPost = { request -> form(request) }
    }
    private val settings = SettingsStore(InMemoryPreferences())
    private val root = Files.createTempDirectory("crawl").toFile()
    private val captures = CaptureStore(root.resolve("captures"), root.resolve("shared"), settings, "test")

    /** The catalog cache outlives each run, like catalog.db. */
    private val dao = FakeCatalogDao()

    private var clock = 0L
    private val pauses = mutableListOf<Long>()

    // 32 órgano lists, 206 distinct órganos (184 + 22 new in Guerrero), 2 Accion=2 reloads at each of 5 apelación tribunals.
    private val allPages = 32 + 206 + 10

    private fun form(request: FormRequest): String {
        val fields = request.fields.toMap()
        return when {
            fields["Accion"] == "2" -> Fixtures.load(Fixtures.ACCION2_4343_125)
            // The 4343 fixture is a Chrome save without hidden inputs; a live form has them (see form 500).
            fields["Organismo"] in apelacion -> Fixtures.load(Fixtures.FORM_4343).replace(
                "</form>",
                """<input type="hidden" name="Organismo" value="${fields["Organismo"]}"><input type="hidden" name="Accion" value="0"></form>""",
            )
            else -> Fixtures.load(Fixtures.SEARCH_FORM_1183)
        }
    }

    private fun TestScope.crawl() = CatalogCrawl(
        CatalogRepository(dao, client, now = { 0L }, parsing = StandardTestDispatcher(testScheduler), capture = captures),
        captures,
        settings,
        pause = { millis ->
            pauses += millis
            clock += millis
        },
        now = { clock },
        parsing = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun `walks every catalog page once, pausing 10 s after each request, and never a case page`() = runTest {
        settings.setCrawlEnabled(true)

        val outcome = crawl().run(budgetMillis = Long.MAX_VALUE / 2)

        assertEquals(CatalogCrawl.Outcome.Finished, outcome)
        assertEquals(allPages, client.requests.size)
        assertEquals(client.requests.size, client.requests.toSet().size)
        assertEquals(List(allPages) { CatalogCrawl.INTERVAL_MILLIS }, pauses)
        assertTrue(client.requests.none { "vercaptura" in it || "VerAcuerdo" in it })
        assertEquals(allPages, captures.count())
        assertTrue(captures.has("circuitos_cir32"))
        assertTrue(captures.has("expedienteytipo_form_4366"))
        assertTrue(captures.has("expedienteytipo_accion2_4366_tipo126"))
    }

    @Test
    fun `a run that runs out of time resumes where it left off`() = runTest {
        settings.setCrawlEnabled(true)

        assertEquals(CatalogCrawl.Outcome.OutOfTime, crawl().run(budgetMillis = 100_000))
        assertEquals(10, client.requests.size)

        assertEquals(CatalogCrawl.Outcome.Finished, crawl().run(budgetMillis = Long.MAX_VALUE / 2))
        assertEquals(allPages, client.requests.size)
        assertEquals(client.requests.size, client.requests.toSet().size)
    }

    @Test
    fun `turned off, it makes no request`() = runTest {
        assertEquals(CatalogCrawl.Outcome.Stopped, crawl().run(budgetMillis = Long.MAX_VALUE / 2))
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun `a failed request ends the run, and the next run retries it`() = runTest {
        settings.setCrawlEnabled(true)
        val pages = client.onGet
        client.onGet = { url -> if ("Cir=2&" in url) throw IOException("offline") else pages(url) }

        assertEquals(CatalogCrawl.Outcome.Failed(PortalError.NETWORK), crawl().run(budgetMillis = Long.MAX_VALUE / 2))
        val failedAt = client.requests.size

        client.onGet = pages
        assertEquals(CatalogCrawl.Outcome.Finished, crawl().run(budgetMillis = Long.MAX_VALUE / 2))
        // Only the failed page is asked for twice.
        assertEquals(allPages + 1, client.requests.size)
        assertTrue(client.requests[failedAt].contains("Cir=2&"))
    }

    @Test
    fun `a page that doesn't parse is kept and skipped`() = runTest {
        settings.setCrawlEnabled(true)
        val pages = client.onGet
        // Like an unmapped Cir: "Nombre Indefinido" and no Organismo select.
        client.onGet = { url -> if ("Cir=3&" in url) "<html><body>Nombre Indefinido</body></html>" else pages(url) }

        assertEquals(CatalogCrawl.Outcome.Finished, crawl().run(budgetMillis = Long.MAX_VALUE / 2))
        assertTrue(captures.has("circuitos_cir3"))
        assertEquals(allPages, client.requests.size)
    }
}
