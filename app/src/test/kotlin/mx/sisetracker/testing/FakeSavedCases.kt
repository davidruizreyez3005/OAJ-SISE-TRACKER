package mx.sisetracker.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mx.sisetracker.core.CasePage
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.data.cases.SavedCases
import mx.sisetracker.data.db.SavedOrgano

/** In-memory saved cases for ViewModel tests. */
class FakeSavedCases : SavedCases {
    val saved = mutableMapOf<String, CasePage>()
    val organos = MutableStateFlow<List<SavedOrgano>>(emptyList())

    override fun observeSavedOrganos(): StateFlow<List<SavedOrgano>> = organos

    override suspend fun isSaved(neun: String): Boolean = neun in saved

    override suspend fun save(url: CaseUrl, page: CasePage) {
        saved[page.neun] = page
        organos.value += SavedOrgano(url.organismo, page.organoName, url.tipoAsunto, page.tipoAsuntoName)
    }
}
