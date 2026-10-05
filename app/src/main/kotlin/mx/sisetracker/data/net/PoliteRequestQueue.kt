package mx.sisetracker.data.net

import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Every portal request goes through this queue (hard rule 3): one at a time,
 * and each starts at least [minIntervalMillis] after the previous one ended.
 * Catalog loads, lookups, refreshes and the daily check all share it.
 */
class PoliteRequestQueue(
    private val minIntervalMillis: Long = MIN_INTERVAL_MILLIS,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private val mutex = Mutex()
    private var lastFinishedAt: Long? = null

    suspend fun <T> run(request: suspend () -> T): T = mutex.withLock {
        lastFinishedAt?.let { last ->
            val wait = last + minIntervalMillis - now()
            if (wait > 0) delay(wait)
        }
        try {
            request()
        } finally {
            lastFinishedAt = now()
        }
    }

    companion object {
        const val MIN_INTERVAL_MILLIS = 2_000L
    }
}
