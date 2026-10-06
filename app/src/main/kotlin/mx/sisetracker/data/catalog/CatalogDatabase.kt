package mx.sisetracker.data.catalog

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction

// The catalog cache: dropdown options the portal lists per circuit and per
// órgano, kept for 30 days. It's only a cache, so schema changes simply drop it.
// The circuit list itself is bundled (Circuitos.all); only each circuit's
// CircuitoName, read from its órgano list, is stored here.

/** A circuit whose órgano list has loaded. [portalName] is its `CircuitoName` ("PRIMER CIRCUITO"). */
@Entity(tableName = "circuitos")
data class CircuitoEntity(
    @PrimaryKey val num: String,
    val portalName: String?,
    val fetchedAt: Long,
)

@Entity(tableName = "organos", primaryKeys = ["circuito", "id"])
data class OrganoEntity(
    val circuito: String,
    val id: String,
    val name: String,
    /** [mx.sisetracker.core.OrganoKind] name, derived from [name]. */
    val kind: String,
    val position: Int,
    val fetchedAt: Long,
)

@Entity(tableName = "tipos_asunto", primaryKeys = ["organoId", "id"])
data class TipoAsuntoEntity(
    val organoId: String,
    val id: String,
    val name: String,
    val position: Int,
    val fetchedAt: Long,
)

@Entity(tableName = "tipos_procedimiento", primaryKeys = ["organoId", "tipoAsuntoId", "id"])
data class TipoProcedimientoEntity(
    val organoId: String,
    val tipoAsuntoId: String,
    val id: String,
    val name: String,
    val position: Int,
    val fetchedAt: Long,
)

/** The hidden fields of an órgano's search form, echoed back when loading procedimientos. */
@Entity(tableName = "search_form_fields", primaryKeys = ["organoId", "name"])
data class SearchFormFieldEntity(
    val organoId: String,
    val name: String,
    val value: String,
    val position: Int,
    val fetchedAt: Long,
)

@Dao
abstract class CatalogDao {
    @Query("SELECT * FROM circuitos WHERE num = :num")
    abstract suspend fun circuito(num: String): CircuitoEntity?

    @Query("SELECT * FROM organos WHERE circuito = :circuito ORDER BY position")
    abstract suspend fun organos(circuito: String): List<OrganoEntity>

    /** Every cached órgano, for matching a related case's órgano by name. */
    @Query("SELECT * FROM organos ORDER BY circuito, position")
    abstract suspend fun allOrganos(): List<OrganoEntity>

    @Query("SELECT * FROM tipos_asunto WHERE organoId = :organoId ORDER BY position")
    abstract suspend fun tiposAsunto(organoId: String): List<TipoAsuntoEntity>

    @Query("SELECT * FROM search_form_fields WHERE organoId = :organoId ORDER BY position")
    abstract suspend fun formFields(organoId: String): List<SearchFormFieldEntity>

    @Query("SELECT * FROM tipos_procedimiento WHERE organoId = :organoId AND tipoAsuntoId = :tipoAsuntoId ORDER BY position")
    abstract suspend fun tiposProcedimiento(organoId: String, tipoAsuntoId: String): List<TipoProcedimientoEntity>

    @Transaction
    open suspend fun replaceOrganos(circuito: CircuitoEntity, organos: List<OrganoEntity>) {
        deleteOrganos(circuito.num)
        insertCircuito(circuito)
        insertOrganos(organos)
    }

    @Transaction
    open suspend fun replaceForm(organoId: String, tipos: List<TipoAsuntoEntity>, fields: List<SearchFormFieldEntity>) {
        deleteTiposAsunto(organoId)
        deleteFormFields(organoId)
        insertTiposAsunto(tipos)
        insertFormFields(fields)
    }

    @Transaction
    open suspend fun replaceTiposProcedimiento(organoId: String, tipoAsuntoId: String, tipos: List<TipoProcedimientoEntity>) {
        deleteTiposProcedimiento(organoId, tipoAsuntoId)
        insertTiposProcedimiento(tipos)
    }

    @Transaction
    open suspend fun clear() {
        clearCircuitos()
        clearOrganos()
        clearTiposAsunto()
        clearTiposProcedimiento()
        clearFormFields()
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertCircuito(circuito: CircuitoEntity)

    @Insert
    protected abstract suspend fun insertOrganos(organos: List<OrganoEntity>)

    @Query("DELETE FROM organos WHERE circuito = :circuito")
    protected abstract suspend fun deleteOrganos(circuito: String)

    @Query("DELETE FROM circuitos")
    protected abstract suspend fun clearCircuitos()

    @Query("DELETE FROM organos")
    protected abstract suspend fun clearOrganos()

    @Insert
    protected abstract suspend fun insertTiposAsunto(tipos: List<TipoAsuntoEntity>)

    @Insert
    protected abstract suspend fun insertFormFields(fields: List<SearchFormFieldEntity>)

    @Insert
    protected abstract suspend fun insertTiposProcedimiento(tipos: List<TipoProcedimientoEntity>)

    @Query("DELETE FROM tipos_asunto WHERE organoId = :organoId")
    protected abstract suspend fun deleteTiposAsunto(organoId: String)

    @Query("DELETE FROM search_form_fields WHERE organoId = :organoId")
    protected abstract suspend fun deleteFormFields(organoId: String)

    @Query("DELETE FROM tipos_procedimiento WHERE organoId = :organoId AND tipoAsuntoId = :tipoAsuntoId")
    protected abstract suspend fun deleteTiposProcedimiento(organoId: String, tipoAsuntoId: String)

    @Query("DELETE FROM tipos_asunto")
    protected abstract suspend fun clearTiposAsunto()

    @Query("DELETE FROM tipos_procedimiento")
    protected abstract suspend fun clearTiposProcedimiento()

    @Query("DELETE FROM search_form_fields")
    protected abstract suspend fun clearFormFields()
}

@Database(
    entities = [
        CircuitoEntity::class,
        OrganoEntity::class,
        TipoAsuntoEntity::class,
        TipoProcedimientoEntity::class,
        SearchFormFieldEntity::class,
    ],
    version = 3,
)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
}
