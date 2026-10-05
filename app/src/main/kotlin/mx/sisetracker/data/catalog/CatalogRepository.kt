package mx.sisetracker.data.catalog

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.sisetracker.core.FormOption
import mx.sisetracker.core.SearchFormParser
import mx.sisetracker.core.SearchRequests
import mx.sisetracker.data.net.SiseClient

/**
 * The search screen's dropdown options. Loaded on demand, one órgano (or one
 * of its tipos) at a time, and cached for [TTL_MILLIS] (hard rule 3).
 *
 * Circuits and órganos come with milestone 4, once their pages are captured as
 * fixtures.
 */
class CatalogRepository(
    private val dao: CatalogDao,
    private val client: SiseClient,
    private val now: () -> Long = System::currentTimeMillis,
    private val parsing: CoroutineDispatcher = Dispatchers.Default,
) {
    /**
     * Tipos de asunto of [organismo]: from the cache, or one request for the
     * portal's search form (step C). Empty if the portal lists none, which
     * usually means a wrong organismo.
     */
    suspend fun tiposDeAsunto(circuito: String, circuitoName: String, organismo: String): List<FormOption> {
        val cached = dao.tiposAsunto(organismo)
        if (cached.isNotEmpty() && cached.all { isFresh(it.fetchedAt) }) {
            return cached.map { FormOption(it.id, it.name, it.position, selected = false) }
        }
        return fetchForm(circuito, circuitoName, organismo)
    }

    /**
     * Tipos de procedimiento of one tipo de asunto: from the cache, or one
     * request re-rendering the form for that tipo (step D). Only call it for
     * tipos that show the procedimiento row.
     */
    suspend fun tiposDeProcedimiento(
        circuito: String,
        circuitoName: String,
        organismo: String,
        tipoAsunto: String,
    ): List<FormOption> {
        val cached = dao.tiposProcedimiento(organismo, tipoAsunto)
        if (cached.isNotEmpty() && cached.all { isFresh(it.fetchedAt) }) {
            return cached.map { FormOption(it.id, it.name, it.position, selected = false) }
        }
        var fields = dao.formFields(organismo)
        if (fields.isEmpty()) {
            // The cache was cleared since the tipos were shown: reload the form first.
            fetchForm(circuito, circuitoName, organismo)
            fields = dao.formFields(organismo)
        }
        val hiddenFields = fields.associate { it.name to it.value }
        val html = client.postForm(SearchRequests.loadProcedimientos(hiddenFields, tipoAsunto))
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

    /** "Actualizar catálogos": forget everything; options reload the next time they're needed. */
    suspend fun clear() = dao.clear()

    private suspend fun fetchForm(circuito: String, circuitoName: String, organismo: String): List<FormOption> {
        val html = client.postForm(SearchRequests.loadForm(circuito, circuitoName, organismo))
        val form = withContext(parsing) { SearchFormParser.parse(html) }
        if (form.tipoAsuntoOptions.isEmpty()) return emptyList()
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
