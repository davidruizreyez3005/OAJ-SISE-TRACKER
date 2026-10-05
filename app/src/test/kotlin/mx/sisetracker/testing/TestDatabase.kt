package mx.sisetracker.testing

import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import mx.sisetracker.data.db.SiseDatabase
import org.robolectric.RuntimeEnvironment

fun inMemoryDatabase(): SiseDatabase =
    Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SiseDatabase::class.java)
        .allowMainThreadQueries()
        .build()

/** Ordenes of the acuerdos whose résumé or síntesis match an FTS4 query. */
fun SiseDatabase.ftsOrdenes(match: String): List<Int> {
    val query = SimpleSQLiteQuery(
        "SELECT a.orden FROM acuerdos a JOIN acuerdos_fts ON acuerdos_fts.rowid = a.rowid " +
            "WHERE acuerdos_fts MATCH ? ORDER BY a.orden",
        arrayOf(match),
    )
    return query(query).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getInt(0)) }
    }
}
