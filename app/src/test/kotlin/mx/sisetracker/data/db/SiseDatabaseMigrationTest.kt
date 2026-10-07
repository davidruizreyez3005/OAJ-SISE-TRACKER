package mx.sisetracker.data.db

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import mx.sisetracker.testing.ftsOrdenes
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Saved cases are user data: a version 1 database, built from the exported
 * schema `schemas/…/1.json`, must open as version 2 with everything kept,
 * full-text search included.
 */
@RunWith(RobolectricTestRunner::class)
class SiseDatabaseMigrationTest {
    private val context = RuntimeEnvironment.getApplication()
    private val name = "migration-test.db"
    private var room: SiseDatabase? = null

    @After
    fun tearDown() {
        room?.close()
        context.deleteDatabase(name)
    }

    private fun createVersion1() {
        val schema = JSONObject(File("schemas/mx.sisetracker.data.db.SiseDatabase/1.json").readText())
            .getJSONObject("database")
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    entity.optJSONArray("contentSyncTriggers")?.let { triggers ->
                        for (t in 0 until triggers.length()) db.execSQL(triggers.getString(t))
                    }
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                db.execSQL(
                    "INSERT INTO cases (neun, organismoId, tipoAsuntoId, tipoProcedimiento, expediente, organoName, " +
                        "tipoAsuntoName, noControlOcc, partyCount, caseUrl, addedAt, lastCheckedAt) VALUES " +
                        "('40612904', '767', '1', '0', '1183/2025', 'Juzgado Sexto', 'Amparo Indirecto', " +
                        "'20255739005400076/2025', 5, 'https://example/case', 1000, 1000)",
                )
                db.execSQL(
                    "INSERT INTO acuerdos (neun, orden, numero, fechaAuto, fechaPublicacion, tipoCuaderno, resumen, " +
                        "sintesis, verAcuerdoUrl, firstSeenAt, seen) VALUES ('40612904', 38, '28', 20696, 20697, " +
                        "'Principal', 'Tribunal colegiado acusa recibo', NULL, 'https://example/38', 1000, 1)",
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build(),
        )
        helper.writableDatabase.close()
    }

    @Test
    fun `version 1 migrates to 2 keeping cases, acuerdos and search`() = runTest {
        createVersion1()

        val db = Room.databaseBuilder(context, SiseDatabase::class.java, name)
            .addMigrations(*SiseDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
            .also { room = it }
        val dao = db.caseDao()

        assertEquals("1183/2025", dao.getCase("40612904")?.expediente)
        val acuerdo = checkNotNull(dao.getAcuerdo("40612904", 38))
        assertEquals(LocalDate.ofEpochDay(20697), acuerdo.fechaPublicacion)
        assertEquals(listOf(38), db.ftsOrdenes("colegiado"))

        // Version 2 takes an unpublished acuerdo.
        dao.insertAcuerdos(listOf(acuerdo.copy(orden = 40, fechaPublicacion = null, resumen = "Pieza postal devuelta")))
        assertNull(dao.getAcuerdo("40612904", 40)?.fechaPublicacion)
        assertEquals(listOf(40), db.ftsOrdenes("postal"))
    }
}
