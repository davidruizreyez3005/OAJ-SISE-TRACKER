package mx.sisetracker.testing

import mx.sisetracker.data.catalog.CatalogDao
import mx.sisetracker.data.catalog.SearchFormFieldEntity
import mx.sisetracker.data.catalog.TipoAsuntoEntity
import mx.sisetracker.data.catalog.TipoProcedimientoEntity

/** In-memory [CatalogDao]; its @Transaction methods run as plain methods. */
class FakeCatalogDao : CatalogDao() {
    val tipos = mutableListOf<TipoAsuntoEntity>()
    val procedimientos = mutableListOf<TipoProcedimientoEntity>()
    val fields = mutableListOf<SearchFormFieldEntity>()

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
