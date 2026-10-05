package mx.sisetracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
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
    version = 1,
)
@TypeConverters(DateConverters::class)
abstract class SiseDatabase : RoomDatabase() {
    abstract fun caseDao(): CaseDao

    companion object {
        const val NAME = "sise.db"
    }
}

/** Dates are stored as epoch days, so they sort and compare in SQL. */
class DateConverters {
    @TypeConverter
    fun toEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun fromEpochDay(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)
}
