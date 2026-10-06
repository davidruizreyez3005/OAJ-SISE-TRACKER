package mx.sisetracker.data.catalog

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.sisetracker.core.Circuito
import mx.sisetracker.core.Circuitos
import mx.sisetracker.core.FormOption
import mx.sisetracker.core.Organo
import mx.sisetracker.core.OrganoListParser
import mx.sisetracker.core.SearchFormParser
import mx.sisetracker.core.SearchRequests
import mx.sisetracker.core.SearchText
import mx.sisetracker.core.SiseUrls
import mx.sisetracker.data.capture.PageCapture
import mx.sisetracker.data.net.SiseClient

/** A cached órgano, with the circuit whose list it came from. */
data class CatalogOrgano(val circuito: String, val organo: Organo)

/**
 * The search screen's dropdown options. The circuits are bundled; everything
 * else loads on demand, one circuit or órgano (or one of its tipos) at a time,
 * and is cached for [TTL_MILLIS] (hard rule 3).
 */
class CatalogRepository(
    private val dao: CatalogDao,
    private val client: SiseClient,
    private val now: () -> Long = System::currentTimeMillis,
    private val parsing: CoroutineDispatcher = Dispatchers.Default,
    /** Diagnostics: receives each catalog page as loaded (see CaptureStore). */
    private val capture: PageCapture = PageCapture.None,
) {
    /** The 32 circuits, in the OAJ's order. Never makes a request. */
    val circuitos: List<Circuito> get() = Circuitos.all

    /**
     * The órganos of [circuito]: from the cache, or one request for its
     * `circuitos.asp` page (step B), which also stores its `CircuitoName`.
     */
    suspend fun organos(circuito: String): List<Organo> =
        cachedOrganos(circuito) ?: fetchOrganos(circuito)

    /** The cached órganos of [circuito], or null if they aren't cached or are stale. Never makes a request. */
    suspend fun cachedOrganos(circuito: String): List<Organo>? {
        val cached = dao.organos(circuito)
        if (cached.isEmpty() || !cached.all { isFresh(it.fetchedAt) }) return null
        return cached.map { Organo(it.id, it.name, it.position) }
    }

    /** Cached órganos named exactly [name] (apart from whitespace runs). Never makes a request. */
    suspend fun findCachedOrganos(name: String): List<CatalogOrgano> =
        dao.allOrganos()
            .filter { isFresh(it.fetchedAt) && SearchText.sameName(it.name, name) }
            .map { CatalogOrgano(it.circuito, Organo(it.id, it.name, it.position)) }

    /** The circuits whose cached lists include [organismo]. Never makes a request. */
    suspend fun cachedCircuitosOf(organismo: String): List<String> =
        dao.allOrganos().filter { it.id == organismo && isFresh(it.fetchedAt) }.map { it.circuito }.distinct()

    /**
     * Tipos de asunto of [organismo]: from the cache, or one request for the
     * portal's search form (step C). That form needs the circuit's
     * `CircuitoName`, which comes with its órgano list, so the list loads
     * first if it isn't cached. Empty when the órgano has nothing searchable
     * on the portal (e.g. Secretaría General de Acuerdos), which is cached
     * like any other list.
     */
    suspend fun tiposDeAsunto(circuito: String, organismo: String): List<FormOption> {
        // The form's hidden fields mark a cached form, even one with no tipos.
        val fields = dao.formFields(organismo)
        if (fields.isNotEmpty() && fields.all { isFresh(it.fetchedAt) }) {
            return dao.tiposAsunto(organismo).map { FormOption(it.id, it.name, it.position, selected = false) }
        }
        return fetchForm(circuito, organismo)
    }

    /**
     * Tipos de procedimiento of one tipo de asunto: from the cache, or one
     * request re-rendering the form for that tipo (step D). Only call it for
     * tipos that show the procedimiento row.
     */
    suspend fun tiposDeProcedimiento(circuito: String, organismo: String, tipoAsunto: String): List<FormOption> {
        val cached = dao.tiposProcedimiento(organismo, tipoAsunto)
        if (cached.isNotEmpty() && cached.all { isFresh(it.fetchedAt) }) {
            return cached.map { FormOption(it.id, it.name, it.position, selected = false) }
        }
        var fields = dao.formFields(organismo)
        if (fields.isEmpty()) {
            // The cache was cleared since the tipos were shown: reload the form first.
            fetchForm(circuito, organismo)
            fields = dao.formFields(organismo)
        }
        val hiddenFields = fields.associate { it.name to it.value }
        val html = client.postForm(SearchRequests.loadProcedimientos(hiddenFields, tipoAsunto))
        capture.save("expedienteytipo_accion2_${organismo}_tipo$tipoAsunto", html)
        val options = withContext(parsing) { SearchFormParser.parse(html) }
            .tipoProcedimientoOptions
            .map { it.copy(selected = false) }
        val fetchedAt = now()
        dao.replaceTiposProcedimiento(
            organismo,
            tipoAsunto,
            options.map { TipoProcedimientoEntity(organismo, tipoAsunto, it.value, it.label, it.position, fetchedAt) },
        )
        return options
    }

    /** The cached tipos de asunto of [organismo], if fresh; never makes a request. */
    suspend fun cachedTiposDeAsunto(organismo: String): List<FormOption> {
        val cached = dao.tiposAsunto(organismo)
        if (cached.isEmpty() || !cached.all { isFresh(it.fetchedAt) }) return emptyList()
        return cached.map { FormOption(it.id, it.name, it.position, selected = false) }
    }

    /** "Actualizar catálogos": forget everything; options reload the next time they're needed. */
    suspend fun clear() = dao.clear()

    private suspend fun fetchOrganos(circuito: String): List<Organo> {
        val html = client.getFormPage(SiseUrls.circuitos(circuito))
        capture.save("circuitos_cir$circuito", html)
        val list = withContext(parsing) { OrganoListParser.parse(html) }
        val fetchedAt = now()
        dao.replaceOrganos(
            CircuitoEntity(circuito, list.circuitoName, fetchedAt),
            list.organos.map { OrganoEntity(circuito, it.id, it.name, it.kind.name, it.position, fetchedAt) },
        )
        return list.organos
    }

    /** The circuit's `CircuitoName`, loading its órgano list if needed; empty if the page doesn't show it. */
    private suspend fun circuitoName(circuito: String): String {
        val cached = dao.circuito(circuito)?.takeIf { isFresh(it.fetchedAt) && cachedOrganos(circuito) != null }
        if (cached == null) fetchOrganos(circuito)
        return dao.circuito(circuito)?.portalName.orEmpty()
    }

    private suspend fun fetchForm(circuito: String, organismo: String): List<FormOption> {
        val circuitoName = circuitoName(circuito)
        val html = client.postForm(SearchRequests.loadForm(circuito, circuitoName, organismo))
        capture.save("expedienteytipo_form_$organismo", html)
        val form = withContext(parsing) { SearchFormParser.parse(html) }
        val fetchedAt = now()
        dao.replaceForm(
            organismo,
            form.tipoAsuntoOptions.map { TipoAsuntoEntity(organismo, it.value, it.label, it.position, fetchedAt) },
            form.hiddenFields.entries.mapIndexed { position, (name, value) ->
                SearchFormFieldEntity(organismo, name, value, position, fetchedAt)
            },
        )
        // The user picks a tipo explicitly, so the page's own selection isn't kept.
        return form.tipoAsuntoOptions.map { it.copy(selected = false) }
    }

    private fun isFresh(fetchedAt: Long): Boolean = now() - fetchedAt < TTL_MILLIS

    companion object {
        val TTL_MILLIS: Long = TimeUnit.DAYS.toMillis(30)
    }
}
