package mx.sisetracker.data.crawl

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import mx.sisetracker.core.Circuitos
import mx.sisetracker.core.OrganoListParser
import mx.sisetracker.core.SearchFormParser
import mx.sisetracker.core.SiseParseException
import mx.sisetracker.core.TipoProcedimientoRule
import mx.sisetracker.data.capture.CaptureStore
import mx.sisetracker.data.catalog.CatalogRepository
import mx.sisetracker.data.net.PortalError
import mx.sisetracker.data.net.PortalResult
import mx.sisetracker.data.net.portalCall
import mx.sisetracker.data.settings.CrawlStatus
import mx.sisetracker.data.settings.SettingsStore

/**
 * The opt-in catalog crawl (Ajustes → "Recorrer todos los circuitos"): walks
 * every circuit's órgano list (step B), every órgano's search form (step C)
 * and the Accion=2 reload of every tipo that shows the procedimiento row
 * (step D), saving each page to the [CaptureStore]. Catalog pages only:
 * never case pages or síntesis (hard rule 6).
 *
 * Slow on purpose: at least [INTERVAL_MILLIS] after each request, on top of
 * the shared request queue's 2 s. The saved pages are the progress: a page
 * already saved is read from the store instead of requested, so a run can
 * stop at any point (time budget, the user turning it off, an error) and the
 * next one resumes where it left off, and nothing is ever fetched twice.
 */
class CatalogCrawl(
    private val catalog: CatalogRepository,
    private val captures: CaptureStore,
    private val settings: SettingsStore,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val now: () -> Long = System::currentTimeMillis,
    private val parsing: CoroutineDispatcher = Dispatchers.Default,
) {
    sealed interface Outcome {
        /** Every page is saved. */
        data object Finished : Outcome

        /** The run's time budget is spent; the next run continues. */
        data object OutOfTime : Outcome

        /** The user turned the crawl off. */
        data object Stopped : Outcome

        /** A request failed (no connection, server error…); the next run retries it. */
        data class Failed(val error: PortalError) : Outcome
    }

    /** Walks until done, stopped, failed or [budgetMillis] have passed (checked before each request). */
    suspend fun run(budgetMillis: Long): Outcome {
        val deadline = now() + budgetMillis
        for (circuito in Circuitos.all) {
            settings.setCrawlStatus(CrawlStatus(CrawlStatus.State.RUNNING, circuito.num))
            walkCircuito(circuito.num, deadline)?.let { return it }
        }
        return Outcome.Finished
    }

    /** Null when the whole circuit is saved; otherwise why this run ends. */
    private suspend fun walkCircuito(circuito: String, deadline: Long): Outcome? {
        val organos = when (
            val list = page("circuitos_cir$circuito", deadline, { OrganoListParser.parse(it).organos }) {
                catalog.reloadOrganos(circuito)
            }
        ) {
            is Page.End -> return list.outcome
            is Page.Got -> list.value.orEmpty()
        }
        for (organo in organos) {
            val tipos = when (
                val form = page("expedienteytipo_form_${organo.id}", deadline, { SearchFormParser.parse(it).tipoAsuntoOptions }) {
                    catalog.reloadTiposDeAsunto(circuito, organo.id)
                }
            ) {
                is Page.End -> return form.outcome
                is Page.Got -> form.value.orEmpty()
            }
            for (tipo in tipos.filter { TipoProcedimientoRule.isShown(it.value) }) {
                val reload = page("expedienteytipo_accion2_${organo.id}_tipo${tipo.value}", deadline, { }) {
                    catalog.tiposDeProcedimiento(circuito, organo.id, tipo.value, reload = true)
                }
                if (reload is Page.End) return reload.outcome
            }
        }
        return null
    }

    private sealed interface Page<out T> {
        /** The page's content; null if it didn't parse (it's saved anyway, and skipped). */
        data class Got<T>(val value: T?) : Page<T>

        data class End(val outcome: Outcome) : Page<Nothing>
    }

    /** The page saved as [name], or one request for it ([fetch] saves it), followed by the crawl's pause. */
    private suspend fun <T> page(name: String, deadline: Long, parse: (String) -> T, fetch: suspend () -> T): Page<T> {
        captures.read(name)?.let { html -> return Page.Got(parseOrNull(html, parse)) }
        if (!settings.crawlEnabled.first()) return Page.End(Outcome.Stopped)
        if (now() >= deadline) return Page.End(Outcome.OutOfTime)
        val result = portalCall { fetch() }
        pause(INTERVAL_MILLIS)
        return when (result) {
            is PortalResult.Ok -> Page.Got(result.value)
            // Saved before parsing, so it's kept for inspection; move on.
            is PortalResult.Failed -> if (result.error == PortalError.UNEXPECTED_PAGE) {
                Page.Got(null)
            } else {
                Page.End(Outcome.Failed(result.error))
            }
        }
    }

    private suspend fun <T> parseOrNull(html: String, parse: (String) -> T): T? = withContext(parsing) {
        try {
            parse(html)
        } catch (e: SiseParseException) {
            null
        }
    }

    companion object {
        /** Pause after each request: five times the app's 2 s minimum. */
        const val INTERVAL_MILLIS = 10_000L
    }
}
