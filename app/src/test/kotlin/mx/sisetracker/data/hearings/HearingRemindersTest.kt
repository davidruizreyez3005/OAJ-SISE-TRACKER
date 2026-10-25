package mx.sisetracker.data.hearings

import android.Manifest
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import mx.sisetracker.core.Audiencia
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.core.TipoAudiencia
import mx.sisetracker.data.cases.CaseRepository
import mx.sisetracker.data.db.SiseDatabase
import mx.sisetracker.data.lookup.LookupRepository
import mx.sisetracker.data.settings.SettingsStore
import mx.sisetracker.testing.FakeSiseClient
import mx.sisetracker.testing.Fixtures
import mx.sisetracker.testing.InMemoryPreferences
import mx.sisetracker.testing.inMemoryDatabase
import mx.sisetracker.testing.parseCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * 1183/2025's Captura de Información sets the audiencia incidental for
 * 05/12/2025 at 11:03 (the constitucional one was already held), so its
 * reminder is due at 9:00 on 04/12/2025.
 */
@RunWith(RobolectricTestRunner::class)
class HearingRemindersTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val client = FakeSiseClient()
    private val settings = SettingsStore(InMemoryPreferences())
    private val reminded = mutableListOf<Pair<String, Audiencia>>()
    private val scheduler = object : ReminderScheduler {
        var at: Long? = null
        var cancelled = false

        override fun scheduleAt(epochMillis: Long, now: Long) {
            at = epochMillis
            cancelled = false
        }

        override fun cancel() {
            at = null
            cancelled = true
        }
    }
    private var now = millis(2025, 12, 4, 8, 0)
    private lateinit var db: SiseDatabase
    private lateinit var reminders: HearingReminders

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        val cases = CaseRepository(db, client, LookupRepository(client))
        cases.save(CaseUrl("1", "767", "1183/2025", "0"), parseCase(Fixtures.CASE_1183))
        reminders = HearingReminders(
            cases,
            settings,
            { case, audiencia, _ -> reminded += case.expediente to audiencia },
            scheduler,
            zone = { zone },
            now = { now },
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `waits for 9 the day before, then reminds once`() = runTest {
        reminders.update()
        assertTrue(reminded.isEmpty())
        assertEquals(millis(2025, 12, 4, 9, 0), scheduler.at)

        now = millis(2025, 12, 4, 9, 0)
        reminders.update()
        reminders.update()

        assertEquals(
            listOf("1183/2025" to Audiencia(TipoAudiencia.INCIDENTAL, LocalDate.of(2025, 12, 5), LocalTime.of(11, 3))),
            reminded,
        )
        assertNull(scheduler.at)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun `a hearing whose time has passed isn't announced`() = runTest {
        now = millis(2025, 12, 5, 11, 30)

        reminders.update()

        assertTrue(reminded.isEmpty())
        assertTrue(scheduler.cancelled)
    }

    @Test
    fun `turning reminders off cancels them`() = runTest {
        settings.setHearingReminders(false)
        now = millis(2025, 12, 4, 10, 0)

        reminders.update()

        assertTrue(reminded.isEmpty())
        assertTrue(scheduler.cancelled)
    }

    @Test
    fun `the reminder shows the hearing, expediente and organo only, privately`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val case = checkNotNull(db.caseDao().getCase("40612904"))

        SystemHearingNotifier(context).notifyHearing(
            case,
            Audiencia(TipoAudiencia.INCIDENTAL, LocalDate.of(2025, 12, 5), LocalTime.of(11, 3)),
            today = LocalDate.of(2025, 12, 4),
        )

        val notification = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals("Audiencia incidental mañana a las 11:03", notification.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertEquals(
            "1183/2025 · Juzgado Sexto de Distrito en Materia Penal en la Ciudad de México",
            notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString(),
        )
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, notification.visibility)
        assertEquals(SystemHearingNotifier.CHANNEL_ID, notification.channelId)
    }
}
