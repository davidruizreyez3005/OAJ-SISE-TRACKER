package mx.sisetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CaseDao {
    // "Mis expedientes": cases with recent activity first.
    @Query(
        """
        SELECT c.*,
            (SELECT COUNT(*) FROM acuerdos a WHERE a.neun = c.neun) AS acuerdoCount,
            (SELECT COUNT(*) FROM acuerdos a WHERE a.neun = c.neun AND a.seen = 0) AS unseenCount,
            (SELECT MAX(a.fechaPublicacion) FROM acuerdos a WHERE a.neun = c.neun) AS latestPublicacion
        FROM cases c
        ORDER BY latestPublicacion IS NULL, latestPublicacion DESC, c.addedAt DESC
        """,
    )
    abstract fun observeSummaries(): Flow<List<CaseSummary>>

    @Query(
        """
        SELECT DISTINCT organismoId, organoName, tipoAsuntoId, tipoAsuntoName
        FROM cases ORDER BY addedAt DESC
        """,
    )
    abstract fun observeSavedOrganos(): Flow<List<SavedOrgano>>

    @Query("SELECT neun FROM cases")
    abstract fun observeNeuns(): Flow<List<String>>

    @Query("SELECT * FROM cases WHERE neun = :neun")
    abstract fun observeCase(neun: String): Flow<CaseEntity?>

    @Query("SELECT * FROM cases WHERE neun = :neun")
    abstract suspend fun getCase(neun: String): CaseEntity?

    @Query("SELECT * FROM cases ORDER BY addedAt")
    abstract suspend fun getCases(): List<CaseEntity>

    @Upsert
    abstract suspend fun upsertCase(case: CaseEntity)

    // Deleting a case cascades to its rows, and the FTS triggers follow.
    @Query("DELETE FROM cases WHERE neun = :neun")
    abstract suspend fun deleteCase(neun: String)

    @Query("SELECT * FROM acuerdos WHERE neun = :neun ORDER BY orden DESC")
    abstract fun observeAcuerdos(neun: String): Flow<List<AcuerdoEntity>>

    @Query("SELECT * FROM acuerdos WHERE neun = :neun AND orden = :orden")
    abstract fun observeAcuerdo(neun: String, orden: Int): Flow<AcuerdoEntity?>

    @Query("SELECT * FROM acuerdos WHERE neun = :neun AND orden = :orden")
    abstract suspend fun getAcuerdo(neun: String, orden: Int): AcuerdoEntity?

    @Query("SELECT * FROM acuerdos WHERE neun = :neun")
    abstract suspend fun getAcuerdos(neun: String): List<AcuerdoEntity>

    @Insert
    abstract suspend fun insertAcuerdos(acuerdos: List<AcuerdoEntity>)

    @Update
    abstract suspend fun updateAcuerdos(acuerdos: List<AcuerdoEntity>)

    @Query("UPDATE acuerdos SET sintesis = :sintesis WHERE neun = :neun AND orden = :orden")
    abstract suspend fun setSintesis(neun: String, orden: Int, sintesis: String)

    @Query("UPDATE acuerdos SET seen = 1 WHERE neun = :neun AND seen = 0")
    abstract suspend fun markSeen(neun: String)

    @Query("SELECT * FROM resoluciones WHERE neun = :neun ORDER BY position")
    abstract fun observeResoluciones(neun: String): Flow<List<ResolucionEntity>>

    @Query("SELECT * FROM asuntos_relacionados WHERE neun = :neun ORDER BY position")
    abstract fun observeRelacionados(neun: String): Flow<List<AsuntoRelacionadoEntity>>

    @Query("SELECT * FROM captura WHERE neun = :neun ORDER BY position")
    abstract fun observeCaptura(neun: String): Flow<List<CapturaEntryEntity>>

    @Query("DELETE FROM resoluciones WHERE neun = :neun")
    abstract suspend fun deleteResoluciones(neun: String)

    @Query("DELETE FROM asuntos_relacionados WHERE neun = :neun")
    abstract suspend fun deleteRelacionados(neun: String)

    @Query("DELETE FROM captura WHERE neun = :neun")
    abstract suspend fun deleteCaptura(neun: String)

    @Insert
    abstract suspend fun insertResoluciones(rows: List<ResolucionEntity>)

    @Insert
    abstract suspend fun insertRelacionados(rows: List<AsuntoRelacionadoEntity>)

    @Insert
    abstract suspend fun insertCaptura(rows: List<CapturaEntryEntity>)
}
