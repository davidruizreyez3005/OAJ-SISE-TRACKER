package mx.sisetracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey
import java.time.LocalDate

// Saved cases. Everything here is case text: it never leaves the device
// (hard rule 6), which the manifest's backup rules enforce.

@Entity(tableName = "cases")
data class CaseEntity(
    /** NEUN, the stable unique case ID. */
    @PrimaryKey val neun: String,
    val organismoId: String,
    val tipoAsuntoId: String,
    val tipoProcedimiento: String,
    val expediente: String,
    val organoName: String,
    val tipoAsuntoName: String,
    val noControlOcc: String,
    /** Number of party panels in "Captura de Información". */
    val partyCount: Int,
    /** The case page URL this case was saved from, used to refresh it. */
    val caseUrl: String,
    val addedAt: Long,
    val lastCheckedAt: Long,
)

@Entity(
    tableName = "acuerdos",
    primaryKeys = ["neun", "orden"],
    foreignKeys = [ForeignKey(CaseEntity::class, ["neun"], ["neun"], onDelete = ForeignKey.CASCADE)],
)
data class AcuerdoEntity(
    val neun: String,
    /** The acuerdo's key (DoVerAcuerdo's 2nd argument), with gaps. */
    val orden: Int,
    /** The displayed "No.". */
    val numero: String,
    val fechaAuto: LocalDate,
    /** Null while the acuerdo isn't published; a refresh fills it in (database version 2). */
    val fechaPublicacion: LocalDate?,
    val tipoCuaderno: String,
    val resumen: String,
    /** The full síntesis, once fetched. Null until then, or when the résumé is already complete. */
    val sintesis: String?,
    val verAcuerdoUrl: String,
    val firstSeenAt: Long,
    /** False for acuerdos found by a refresh until the user opens the case: the "Nuevo" badge. */
    val seen: Boolean,
)

/** Full-text index over résumé and síntesis, kept in sync with `acuerdos` by Room's triggers. */
@Fts4(
    contentEntity = AcuerdoEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = ["remove_diacritics=1"],
)
@Entity(tableName = "acuerdos_fts")
data class AcuerdoFtsEntity(
    val resumen: String,
    val sintesis: String?,
)

@Entity(
    tableName = "resoluciones",
    primaryKeys = ["neun", "position"],
    foreignKeys = [ForeignKey(CaseEntity::class, ["neun"], ["neun"], onDelete = ForeignKey.CASCADE)],
)
data class ResolucionEntity(
    val neun: String,
    val position: Int,
    /** The NEUN shown in the row (the case's own, so far). */
    val asuntoNeun: String,
    val fechaIngreso: LocalDate,
    val tema: String,
    /** Cleartext http:// document link, opened in the browser, never fetched. */
    val archivoUrl: String?,
)

@Entity(
    tableName = "asuntos_relacionados",
    primaryKeys = ["neun", "position"],
    foreignKeys = [ForeignKey(CaseEntity::class, ["neun"], ["neun"], onDelete = ForeignKey.CASCADE)],
)
data class AsuntoRelacionadoEntity(
    val neun: String,
    val position: Int,
    val relatedNeun: String,
    val expediente: String,
    /** `"{órgano} - {tipo asunto}"`, as shown. */
    val organo: String,
    val fechaRelacion: LocalDate,
)

@Entity(
    tableName = "captura",
    primaryKeys = ["neun", "position"],
    foreignKeys = [ForeignKey(CaseEntity::class, ["neun"], ["neun"], onDelete = ForeignKey.CASCADE)],
)
data class CapturaEntryEntity(
    val neun: String,
    val position: Int,
    val section: String,
    @ColumnInfo(name = "group_index") val group: Int,
    val label: String,
    val value: String,
)

/** A row of "Mis expedientes". */
data class CaseSummary(
    @Embedded val case: CaseEntity,
    val acuerdoCount: Int,
    val unseenCount: Int,
    val latestPublicacion: LocalDate?,
)

/** One hit of the full-text search over saved acuerdos. */
data class AcuerdoSearchResult(
    val neun: String,
    val orden: Int,
    val numero: String,
    val fechaPublicacion: LocalDate?,
    val expediente: String,
    val organoName: String,
    /** A short excerpt, with each match between [SnippetMarkers.START] and [SnippetMarkers.END]. */
    val snippet: String,
)

/** Control characters around the matches in a search snippet; they never occur in case text. */
object SnippetMarkers {
    const val START = "\u0002"
    const val END = "\u0003"
}

/** An órgano known from a saved case, with the tipo de asunto it was saved under. */
data class SavedOrgano(
    val organismoId: String,
    val organoName: String,
    val tipoAsuntoId: String,
    val tipoAsuntoName: String,
)
