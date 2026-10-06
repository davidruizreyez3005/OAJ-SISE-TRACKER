package mx.sisetracker.data.check

import android.Manifest
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.data.cases.CaseRepository
import mx.sisetracker.data.db.CaseEntity
import mx.sisetracker.data.db.SiseDatabase
import mx.sisetracker.data.lookup.LookupRepository
import mx.sisetracker.data.net.PortalError
import mx.sisetracker.data.settings.SettingsStore
import mx.sisetracker.testing.FakeSiseClient
import mx.sisetracker.testing.Fixtures
import mx.sisetracker.testing.InMemoryPreferences
import mx.sisetracker.testing.inMemoryDatabase
import mx.sisetracker.testing.parseCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class DailyCheckTest {
    private val neun = "40612904"
    private val url = CaseUrl("1", "767", "1183/2025", "0")
    private val page = parseCase(Fixtures.CASE_1183)
    private val caseGet = "GET ${url.toUrl()}"
    private val sintesis38 =
        "GET https://www.dgej.cjf.gob.mx/siseinternet/Actuaria/VerAcuerdo.aspx?listaAcOrd=38&listaCatOrg=767&listaNeun=40612904&listaAsuId=1&listaExped=1183/2025&listaFAuto=31/08/2026&listaFPublicacion=01/09/2026"

    private val client = FakeSiseClient().apply {
        onGet = { if ("VerAcuerdo" in it) Fixtures.load(Fixtures.SINTESIS_1183_38) else Fixtures.load(Fixtures.CASE_1183) }
    }
    private val settings = SettingsStore(InMemoryPreferences())
    private val notified = mutableListOf<Pair<String, Int>>()
    private var now = TimeUnit.DAYS.toMillis(100)
    private lateinit var db: SiseDatabase
    private lateinit var repository: CaseRepository
    private lateinit var check: DailyCheck

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repository = CaseRepository(db, client, LookupRepository(client))
        check = DailyCheck(repository, settings, { case, count -> notified += case.expediente to count }, now = { now })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `new acuerdos are notified once per case and truncated ones get their sintesis`() = runTest {
        // Saved before ordenes 4 (complete résumé) and 38 (truncated) were published.
        repository.save(url, page.copy(acuerdos = page.acuerdos.filterNot { it.orden == 4 || it.orden == 38 }))

        val outcome = check.run()

        assertEquals(DailyCheck.Outcome.Done(checkedCases = 1, notifiedCases = 1), outcome)
        assertEquals(listOf("1183/2025" to 2), notified)
        // One case page, then only the truncated new acuerdo's síntesis.
        assertEquals(listOf(caseGet, sintesis38), client.requests)
        assertTrue(db.caseDao().getAcuerdo(neun, 38)?.sintesis.orEmpty().startsWith("Ciudad de México"))
        assertEquals(now, settings.lastBackgroundCheckAt.first())
    }

    @Test
    fun `no new acuerdos means no notification and no sintesis requests`() = runTest {
        repository.save(url, page)

        check.run()

        assertTrue(notified.isEmpty())
        assertEquals(listOf(caseGet), client.requests)
    }

    @Test
    fun `at most one check per day`() = runTest {
        repository.save(url, page)
        check.run()
        now += TimeUnit.HOURS.toMillis(12)

        assertEquals(DailyCheck.Outcome.Skipped, check.run())
        assertEquals(1, client.requests.size)

        now += TimeUnit.HOURS.toMillis(12)
        check.run()
        assertEquals(2, client.requests.size)
    }

    @Test
    fun `the interval setting spaces checks further`() = runTest {
        repository.save(url, page)
        settings.setCheckIntervalDays(3)
        check.run()
        now += TimeUnit.DAYS.toMillis(2)

        assertEquals(DailyCheck.Outcome.Skipped, check.run())
        assertEquals(1, client.requests.size)
    }

    @Test
    fun `a disabled check makes no requests`() = runTest {
        repository.save(url, page)
        settings.setDailyCheckEnabled(false)

        assertEquals(DailyCheck.Outcome.Skipped, check.run())
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun `no connection stops the run instead of trying every case`() = runTest {
        repository.save(url, page)
        repository.save(CaseUrl("11", "18", "293/2026", "0"), page.copy(neun = "42423129", expediente = "293/2026"))
        client.onGet = { throw IOException("offline") }

        assertEquals(DailyCheck.Outcome.Stopped(PortalError.NETWORK), check.run())
        assertEquals(1, client.requests.size)
    }

    @Test
    fun `the notification shows only expediente, organo and count, privately`() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val case = CaseEntity(
            neun, "767", "1", "0", "1183/2025",
            "Juzgado Sexto de Distrito en Materia Penal en la Ciudad de México", "Amparo Indirecto",
            "", 5, url.toUrl(), 0, 0,
        )

        SystemNewAcuerdosNotifier(context).notifyNewAcuerdos(case, 2)

        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = shadowOf(manager).allNotifications.single()
        val extras = notification.extras
        assertEquals("2 acuerdos nuevos en 1183/2025", extras.getString(NotificationCompat.EXTRA_TITLE))
        assertEquals(case.organoName, extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString())
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, notification.visibility)
        assertEquals(null, notification.publicVersion)
        assertFalse(extras.keySet().any { it == NotificationCompat.EXTRA_BIG_TEXT })
    }

    @Test
    fun `the schedule needs a network, runs every interval and can be turned off`() {
        val context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val workManager = WorkManager.getInstance(context)
        val scheduler = DailyCheckScheduler { workManager }

        scheduler.apply(enabled = true, intervalDays = 2, replace = false)
        val info = workManager.getWorkInfosForUniqueWork(DailyCheckScheduler.WORK_NAME).get().single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        assertEquals(TimeUnit.DAYS.toMillis(2), info.periodicityInfo?.repeatIntervalMillis)

        scheduler.apply(enabled = false, intervalDays = 2, replace = true)
        assertEquals(
            WorkInfo.State.CANCELLED,
            workManager.getWorkInfosForUniqueWork(DailyCheckScheduler.WORK_NAME).get().single().state,
        )
    }
}
