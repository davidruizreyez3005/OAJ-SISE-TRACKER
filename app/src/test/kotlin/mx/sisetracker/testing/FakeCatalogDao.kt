package mx.sisetracker.testing

import mx.sisetracker.data.catalog.CatalogDao
import mx.sisetracker.data.catalog.CircuitoEntity
import mx.sisetracker.data.catalog.OrganoEntity
import mx.sisetracker.data.catalog.SearchFormFieldEntity
import mx.sisetracker.data.catalog.TipoAsuntoEntity
import mx.sisetracker.data.catalog.TipoProcedimientoEntity

/** In-memory [CatalogDao]; its @Transaction methods run as plain methods. */
class FakeCatalogDao : CatalogDao() {
    val circuitos = mutableMapOf<String, CircuitoEntity>()
    val organos = mutableListOf<OrganoEntity>()
    val tipos = mutableListOf<TipoAsuntoEntity>()
    val procedimientos = mutableListOf<TipoProcedimientoEntity>()
    val fields = mutableListOf<SearchFormFieldEntity>()

    override suspend fun circuito(num: String) = circuitos[num]

    override suspend fun organos(circuito: String) =
        organos.filter { it.circuito == circuito }.sortedBy { it.position }

    override suspend fun allOrganos() = organos.sortedWith(compareBy({ it.circuito }, { it.position }))

    override suspend fun insertCircuito(circuito: CircuitoEntity) {
        circuitos[circuito.num] = circuito
    }

    override suspend fun insertOrganos(organos: List<OrganoEntity>) {
        this.organos += organos
    }

    override suspend fun deleteOrganos(circuito: String) {
        organos.removeAll { it.circuito == circuito }
    }

    override suspend fun clearCircuitos() = circuitos.clear()

    override suspend fun clearOrganos() = organos.clear()

    override suspend fun tiposAsunto(organoId: String) =
        tipos.filter { it.organoId == organoId }.sortedBy { it.position }

    override suspend fun formFields(organoId: String) =
        fields.filter { it.organoId == organoId }.sortedBy { it.position }

    override suspend fun tiposProcedimiento(organoId: String, tipoAsuntoId: String) =
        procedimientos.filter { it.organoId == organoId && it.tipoAsuntoId == tipoAsuntoId }.sortedBy { it.position }

    override suspend fun insertTiposAsunto(tipos: List<TipoAsuntoEntity>) {
        this.tipos += tipos
    }

    override suspend fun insertFormFields(fields: List<SearchFormFieldEntity>) {
        this.fields += fields
    }

    override suspend fun insertTiposProcedimiento(tipos: List<TipoProcedimientoEntity>) {
        procedimientos += tipos
    }

    override suspend fun deleteTiposAsunto(organoId: String) {
        tipos.removeAll { it.organoId == organoId }
    }

    override suspend fun deleteFormFields(organoId: String) {
        fields.removeAll { it.organoId == organoId }
    }

    override suspend fun deleteTiposProcedimiento(organoId: String, tipoAsuntoId: String) {
        procedimientos.removeAll { it.organoId == organoId && it.tipoAsuntoId == tipoAsuntoId }
    }

    override suspend fun clearTiposAsunto() = tipos.clear()

    override suspend fun clearTiposProcedimiento() = procedimientos.clear()

    override suspend fun clearFormFields() = fields.clear()
}
