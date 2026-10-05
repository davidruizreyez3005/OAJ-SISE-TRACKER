package mx.sisetracker.data.lookup

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.sisetracker.core.CaseLookup
import mx.sisetracker.core.CasePageParser
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.data.net.SiseClient

/**
 * Looks up one case: a single GET of its public page, through the polite
 * queue. Only ever called for an expediente the user typed, picked from a
 * related case or shared: never generated, guessed or iterated (hard rule 3).
 */
class LookupRepository(
    private val client: SiseClient,
    private val parsing: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun lookup(url: CaseUrl): CaseLookup {
        val html = client.getPage(url.toUrl())
        return withContext(parsing) { CasePageParser.parse(html) }
    }
}
