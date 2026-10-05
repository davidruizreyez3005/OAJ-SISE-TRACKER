package mx.sisetracker.data.cases

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import mx.sisetracker.core.Acuerdo
import mx.sisetracker.core.AcuerdoDiff
import mx.sisetracker.core.CaseLookup
import mx.sisetracker.core.CasePage
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.core.Resumen
import mx.sisetracker.core.SintesisPageParser
import mx.sisetracker.core.SiseParseException
import mx.sisetracker.core.VerAcuerdoUrl
import mx.sisetracker.data.db.AcuerdoEntity
import mx.sisetracker.data.db.AsuntoRelacionadoEntity
import mx.sisetracker.data.db.CapturaEntryEntity
import mx.sisetracker.data.db.CaseEntity
import mx.sisetracker.data.db.CaseSummary
import mx.sisetracker.data.db.ResolucionEntity
import mx.sisetracker.data.db.SavedOrgano
import mx.sisetracker.data.db.SiseDatabase
import mx.sisetracker.data.lookup.LookupRepository
import mx.sisetracker.data.net.SiseClient

sealed interface SaveResult {
    data class Saved(val neun: String) : SaveResult

    data object NotFound : SaveResult
}

sealed interface RefreshResult {
    /** The page was read; [newAcuerdos] are the ones published since the last check. */
    data class Updated(val newAcuerdos: List<AcuerdoEntity>) : RefreshResult

    /** The portal answered "not found" for a saved case: the saved data is kept as is. */
    data object NotFound : RefreshResult

    data object NotSaved : RefreshResult
}

/** What the search screen needs from saved cases. */
interface SavedCases {
    /** Órganos (and tipos de asunto) of saved cases: known IDs for the search screen. */
    fun observeSavedOrganos(): Flow<List<SavedOrgano>>

    suspend fun isSaved(neun: String): Boolean

    /** Saves a case whose page was already fetched; makes no request. */
    suspend fun save(url: CaseUrl, page: CasePage)
}

/**
 * Saved cases. Each portal request here is one GET through the polite queue:
 * a case page (save from a link, refresh) or a síntesis page.
 */
class CaseRepository(
    private val db: SiseDatabase,
    private val client: SiseClient,
    private val lookup: LookupRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val parsing: CoroutineDispatcher = Dispatchers.Default,
) : SavedCases {
    private val dao = db.caseDao()

    fun observeSummaries(): Flow<List<CaseSummary>> = dao.observeSummaries()

    override fun observeSavedOrganos(): Flow<List<SavedOrgano>> = dao.observeSavedOrganos()

    fun observeSavedNeuns(): Flow<Set<String>> = dao.observeNeuns().map { it.toSet() }

    fun observeCase(neun: String): Flow<CaseEntity?> = dao.observeCase(neun)

    fun observeAcuerdos(neun: String): Flow<List<AcuerdoEntity>> = dao.observeAcuerdos(neun)

    fun observeAcuerdo(neun: String, orden: Int): Flow<AcuerdoEntity?> = dao.observeAcuerdo(neun, orden)

    fun observeResoluciones(neun: String): Flow<List<ResolucionEntity>> = dao.observeResoluciones(neun)

    fun observeRelacionados(neun: String): Flow<List<AsuntoRelacionadoEntity>> = dao.observeRelacionados(neun)

    fun observeCaptura(neun: String): Flow<List<CapturaEntryEntity>> = dao.observeCaptura(neun)

    suspend fun getCases(): List<CaseEntity> = dao.getCases()

    override suspend fun isSaved(neun: String): Boolean = dao.getCase(neun) != null

    /**
     * Saves a case whose page was already fetched (the search preview), so it
     * makes no request. A case saved before is merged like a refresh.
     */
    override suspend fun save(url: CaseUrl, page: CasePage) {
        db.withTransaction {
            val existing = dao.getCase(page.neun)
            if (existing == null) insertNew(url, page) else merge(existing, url, page)
        }
    }

    /** Saves a case from a link (portal WebView, share, paste): fetches its page once. */
    suspend fun saveFromUrl(url: CaseUrl): SaveResult =
        when (val result = lookup.lookup(url)) {
            is CaseLookup.Found -> {
                save(url, result.page)
                SaveResult.Saved(result.page.neun)
            }
            CaseLookup.NotFound -> SaveResult.NotFound
        }

    /** Fetches a saved case's page once and stores what changed. */
    suspend fun refresh(neun: String): RefreshResult {
        val case = dao.getCase(neun) ?: return RefreshResult.NotSaved
        val url = CaseUrl.parse(case.caseUrl) ?: throw SiseParseException("Stored case URL is invalid: ${case.caseUrl}")
        return when (val result = lookup.lookup(url)) {
            is CaseLookup.Found -> {
                if (result.page.neun != neun) {
                    throw SiseParseException("The case page returned NEUN ${result.page.neun}, expected $neun")
                }
                RefreshResult.Updated(db.withTransaction { merge(case, url, result.page) })
            }
            CaseLookup.NotFound -> {
                dao.upsertCase(case.copy(lastCheckedAt = now()))
                RefreshResult.NotFound
            }
        }
    }

    /**
     * An acuerdo's full síntesis: the stored one, the résumé itself when it
     * isn't truncated (no request), or else fetched once, stored and indexed.
     */
    suspend fun sintesis(neun: String, orden: Int): String {
        val acuerdo = dao.getAcuerdo(neun, orden) ?: throw IllegalArgumentException("No acuerdo $orden in $neun")
        acuerdo.sintesis?.let { return it }
        if (!Resumen.isTruncated(acuerdo.resumen)) return acuerdo.resumen

        val html = client.getPage(acuerdo.verAcuerdoUrl)
        val sintesis = withContext(parsing) { SintesisPageParser.parse(html) }
        val expediente = dao.getCase(neun)?.expediente
        if (sintesis.expediente.isNotEmpty() && expediente != null && sintesis.expediente != expediente) {
            throw SiseParseException("Síntesis page is for ${sintesis.expediente}, expected $expediente")
        }
        if (sintesis.text.isEmpty()) return acuerdo.resumen
        dao.setSintesis(neun, orden, sintesis.text)
        return sintesis.text
    }

    /** Ordenes still marked new, before [markSeen] clears them. */
    suspend fun unseenOrdenes(neun: String): Set<Int> =
        dao.getAcuerdos(neun).filterNot { it.seen }.map { it.orden }.toSet()

    suspend fun markSeen(neun: String) = dao.markSeen(neun)

    suspend fun delete(neun: String) = dao.deleteCase(neun)

    private suspend fun insertNew(url: CaseUrl, page: CasePage) {
        val now = now()
        dao.upsertCase(caseEntity(url, page, addedAt = now, checkedAt = now))
        // Acuerdos already published when the case is saved aren't "new".
        dao.insertAcuerdos(page.acuerdos.map { it.toEntity(page.neun, firstSeenAt = now, seen = true) })
        replaceDetails(page)
    }

    /** Stores a fresh read of a saved case; returns the acuerdos that weren't stored yet. */
    private suspend fun merge(existing: CaseEntity, url: CaseUrl, page: CasePage): List<AcuerdoEntity> {
        val now = now()
        dao.upsertCase(caseEntity(url, page, addedAt = existing.addedAt, checkedAt = now))

        val stored = dao.getAcuerdos(page.neun).associateBy { it.orden }
        val added = AcuerdoDiff.newAcuerdos(stored.keys, page.acuerdos)
            .map { it.toEntity(page.neun, firstSeenAt = now, seen = false) }
        val changed = page.acuerdos.mapNotNull { acuerdo ->
            val old = stored[acuerdo.orden] ?: return@mapNotNull null
            val fresh = acuerdo.toEntity(page.neun, firstSeenAt = old.firstSeenAt, seen = old.seen)
                // A corrected résumé makes the stored síntesis stale.
                .copy(sintesis = old.sintesis.takeIf { old.resumen == acuerdo.resumen })
            fresh.takeIf { it != old }
        }
        // Acuerdos no longer on the page are kept: the app is a record of what was published.
        dao.insertAcuerdos(added)
        if (changed.isNotEmpty()) dao.updateAcuerdos(changed)
        replaceDetails(page)
        return added
    }

    private suspend fun replaceDetails(page: CasePage) {
        val neun = page.neun
        dao.deleteResoluciones(neun)
        dao.deleteRelacionados(neun)
        dao.deleteCaptura(neun)
        dao.insertResoluciones(
            page.resoluciones.mapIndexed { position, it ->
                ResolucionEntity(neun, position, it.neun, it.fechaIngreso, it.tema, it.archivoUrl)
            },
        )
        dao.insertRelacionados(
            page.asuntosRelacionados.mapIndexed { position, it ->
                AsuntoRelacionadoEntity(neun, position, it.neun, it.expediente, it.organo, it.fechaRelacion)
            },
        )
        dao.insertCaptura(
            page.captura.entries.mapIndexed { position, it ->
                CapturaEntryEntity(neun, position, it.section, it.group, it.label, it.value)
            },
        )
    }

    private fun caseEntity(url: CaseUrl, page: CasePage, addedAt: Long, checkedAt: Long) = CaseEntity(
        neun = page.neun,
        organismoId = url.organismo,
        tipoAsuntoId = url.tipoAsunto,
        tipoProcedimiento = url.tipoProcedimiento,
        expediente = page.expediente.ifEmpty { url.expediente },
        organoName = page.organoName,
        tipoAsuntoName = page.tipoAsuntoName,
        noControlOcc = page.noControlOcc,
        partyCount = page.captura.partyCount,
        caseUrl = url.toUrl(),
        addedAt = addedAt,
        lastCheckedAt = checkedAt,
    )

    private fun Acuerdo.toEntity(neun: String, firstSeenAt: Long, seen: Boolean) = AcuerdoEntity(
        neun = neun,
        orden = orden,
        numero = numero,
        fechaAuto = fechaAuto,
        fechaPublicacion = fechaPublicacion,
        tipoCuaderno = tipoCuaderno,
        resumen = resumen,
        sintesis = null,
        verAcuerdoUrl = VerAcuerdoUrl.build(this),
        firstSeenAt = firstSeenAt,
        seen = seen,
    )
}
