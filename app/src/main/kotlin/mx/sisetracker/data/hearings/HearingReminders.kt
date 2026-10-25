package mx.sisetracker.data.hearings

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import mx.sisetracker.SiseApp
import mx.sisetracker.core.Audiencia
import mx.sisetracker.core.Audiencias
import mx.sisetracker.data.cases.CaseRepository
import mx.sisetracker.data.db.CaseEntity
import mx.sisetracker.data.settings.SettingsStore

/** Tells the user a saved case has a hearing coming up. */
fun interface HearingNotifier {
    fun notifyHearing(case: CaseEntity, audiencia: Audiencia, today: LocalDate)
}

/** When [HearingReminders] runs next. */
interface ReminderScheduler {
    fun scheduleAt(epochMillis: Long, now: Long)

    fun cancel()
}

/** One unique job that waits for the next reminder time. */
class HearingReminderScheduler(private val workManager: () -> WorkManager) : ReminderScheduler {
    override fun scheduleAt(epochMillis: Long, now: Long) {
        val request = OneTimeWorkRequestBuilder<HearingReminderWorker>()
            .setInitialDelay((epochMillis - now).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build()
        workManager().enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        workManager().cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "hearing_reminders"
    }
}

/** Posts the reminders that are due; [HearingReminders.update] schedules the next run. */
class HearingReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as SiseApp).container.hearingReminders.update()
        return Result.success()
    }
}

/**
 * Ajustes → "Recordar audiencias": a notification at [REMIND_AT] the day
 * before each hearing that a saved case's "Captura de Información" gives
 * (see [Audiencias]). It only reads saved data: no portal requests. Each
 * hearing date is announced once; a hearing whose time has passed isn't.
 */
class HearingReminders(
    private val cases: CaseRepository,
    private val settings: SettingsStore,
    private val notifier: HearingNotifier,
    private val scheduler: ReminderScheduler,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class Upcoming(val case: CaseEntity, val audiencia: Audiencia, val key: String)

    /** Posts the reminders that are due and schedules the next one. Safe to call after any change. */
    suspend fun update() {
        if (!settings.hearingReminders.first()) {
            scheduler.cancel()
            return
        }
        val zone = zone()
        val now = Instant.ofEpochMilli(now())
        val today = now.atZone(zone).toLocalDate()
        val upcoming = upcoming(today)
        val reminded = settings.remindedHearings.first()
        val done = mutableSetOf<String>()
        var next: Instant? = null
        for (hearing in upcoming) {
            if (hearing.key in reminded) continue
            val remindAt = hearing.audiencia.fecha.minusDays(1).atTime(REMIND_AT).atZone(zone).toInstant()
            if (now < remindAt) {
                next = next?.let { minOf(it, remindAt) } ?: remindAt
                continue
            }
            val startsAt = hearing.audiencia.hora?.let { hearing.audiencia.fecha.atTime(it).atZone(zone).toInstant() }
            if (startsAt == null || now < startsAt) notifier.notifyHearing(hearing.case, hearing.audiencia, today)
            done += hearing.key
        }
        val current = upcoming.mapTo(mutableSetOf()) { it.key }
        if (done.isNotEmpty() || !current.containsAll(reminded)) settings.addRemindedHearings(done, current)
        next?.let { scheduler.scheduleAt(it.toEpochMilli(), now.toEpochMilli()) } ?: scheduler.cancel()
    }

    /** Every saved case's hearings from [today] on. */
    private suspend fun upcoming(today: LocalDate): List<Upcoming> {
        val saved = cases.getCases().associateBy { it.neun }
        return cases.getHearings().flatMap { (neun, entries) ->
            val case = saved[neun] ?: return@flatMap emptyList()
            Audiencias.scheduled(entries)
                .filter { it.fecha >= today }
                .map { Upcoming(case, it, SettingsStore.hearingKey(neun, it.tipo.name, it.fecha.toString())) }
        }
    }

    companion object {
        val REMIND_AT: LocalTime = LocalTime.of(9, 0)
    }
}
