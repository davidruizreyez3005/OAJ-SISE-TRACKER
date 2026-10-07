package mx.sisetracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate

@Database(
    entities = [
        CaseEntity::class,
        AcuerdoEntity::class,
        AcuerdoFtsEntity::class,
        ResolucionEntity::class,
        AsuntoRelacionadoEntity::class,
        CapturaEntryEntity::class,
    ],
    version = 2,
)
@TypeConverters(DateConverters::class)
abstract class SiseDatabase : RoomDatabase() {
    abstract fun caseDao(): CaseDao

    companion object {
        const val NAME = "sise.db"

        /** Every migration, in order: saved cases are user data and must survive updates. */
        val MIGRATIONS = arrayOf(Migration1To2)
    }
}

/**
 * Version 2 makes `acuerdos.fechaPublicacion` nullable (acuerdos not yet
 * published). SQLite can't relax NOT NULL in place, so the table is rebuilt,
 * keeping rowids, and the full-text index and its sync triggers are recreated
 * as Room declares them (see `schemas/…/2.json`).
 */
object Migration1To2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `acuerdos_new` (`neun` TEXT NOT NULL, `orden` INTEGER NOT NULL, " +
                "`numero` TEXT NOT NULL, `fechaAuto` INTEGER NOT NULL, `fechaPublicacion` INTEGER, " +
                "`tipoCuaderno` TEXT NOT NULL, `resumen` TEXT NOT NULL, `sintesis` TEXT, " +
                "`verAcuerdoUrl` TEXT NOT NULL, `firstSeenAt` INTEGER NOT NULL, `seen` INTEGER NOT NULL, " +
                "PRIMARY KEY(`neun`, `orden`), FOREIGN KEY(`neun`) REFERENCES `cases`(`neun`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "INSERT INTO `acuerdos_new` (rowid, neun, orden, numero, fechaAuto, fechaPublicacion, tipoCuaderno, " +
                "resumen, sintesis, verAcuerdoUrl, firstSeenAt, seen) SELECT rowid, neun, orden, numero, fechaAuto, " +
                "fechaPublicacion, tipoCuaderno, resumen, sintesis, verAcuerdoUrl, firstSeenAt, seen FROM `acuerdos`",
        )
        // Dropping the old table drops its triggers too; recreated below.
        db.execSQL("DROP TABLE `acuerdos`")
        db.execSQL("ALTER TABLE `acuerdos_new` RENAME TO `acuerdos`")
        FTS_TRIGGERS.forEach(db::execSQL)
        db.execSQL("INSERT INTO `acuerdos_fts`(`acuerdos_fts`) VALUES('rebuild')")
    }

    private val FTS_TRIGGERS = listOf(
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_acuerdos_fts_BEFORE_UPDATE BEFORE UPDATE ON `acuerdos` " +
            "BEGIN DELETE FROM `acuerdos_fts` WHERE `docid`=OLD.`rowid`; END",
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_acuerdos_fts_BEFORE_DELETE BEFORE DELETE ON `acuerdos` " +
            "BEGIN DELETE FROM `acuerdos_fts` WHERE `docid`=OLD.`rowid`; END",
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_acuerdos_fts_AFTER_UPDATE AFTER UPDATE ON `acuerdos` " +
            "BEGIN INSERT INTO `acuerdos_fts`(`docid`, `resumen`, `sintesis`) VALUES (NEW.`rowid`, NEW.`resumen`, " +
            "NEW.`sintesis`); END",
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_acuerdos_fts_AFTER_INSERT AFTER INSERT ON `acuerdos` " +
            "BEGIN INSERT INTO `acuerdos_fts`(`docid`, `resumen`, `sintesis`) VALUES (NEW.`rowid`, NEW.`resumen`, " +
            "NEW.`sintesis`); END",
    )
}

/** Dates are stored as epoch days, so they sort and compare in SQL. */
class DateConverters {
    @TypeConverter
    fun toEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun fromEpochDay(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)
}
