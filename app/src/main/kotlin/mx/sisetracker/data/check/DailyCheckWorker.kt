package mx.sisetracker.data.check

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import mx.sisetracker.SiseApp

/** Runs [DailyCheck]. Never retries: a failed check waits for the next period. */
class DailyCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as SiseApp).container.dailyCheck.run()
        return Result.success()
    }
}

/** Keeps the periodic check in step with the settings. */
class DailyCheckScheduler(private val workManager: () -> WorkManager) {

    /**
     * Schedules the check every [intervalDays] days (at least one), only with
     * a network connection, and the first one an interval from now. [replace]
     * applies new settings to an existing schedule; otherwise it's kept.
     */
    fun apply(enabled: Boolean, intervalDays: Int, replace: Boolean) {
        val manager = workManager()
        if (!enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val days = intervalDays.coerceAtLeast(1).toLong()
        val request = PeriodicWorkRequestBuilder<DailyCheckWorker>(days, TimeUnit.DAYS)
            .setInitialDelay(days, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        manager.enqueueUniquePeriodicWork(
            WORK_NAME,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        const val WORK_NAME = "daily_check"
    }
}
