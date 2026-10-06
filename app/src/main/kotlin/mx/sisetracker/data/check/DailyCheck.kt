package mx.sisetracker.data.check

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import mx.sisetracker.core.Resumen
import mx.sisetracker.data.cases.CaseRepository
import mx.sisetracker.data.cases.RefreshResult
import mx.sisetracker.data.net.PortalError
import mx.sisetracker.data.net.PortalResult
import mx.sisetracker.data.net.portalCall
import mx.sisetracker.data.settings.SettingsStore

/**
 * The background check for new acuerdos (hard rule 3: at most one per day).
 * It refreshes each saved case through the polite queue, fetches the síntesis
 * of new acuerdos whose résumé is truncated, and notifies once per case.
 */
class DailyCheck(
    private val cases: CaseRepository,
    private val settings: SettingsStore,
    private val notifier: NewAcuerdosNotifier,
    private val now: () -> Long = System::currentTimeMillis,
) {
    sealed interface Outcome {
        /** Disabled, or the last check was too recent. No requests were made. */
        data object Skipped : Outcome

        data class Done(val checkedCases: Int, val notifiedCases: Int) : Outcome

        /** No connection: the rest waits for the next check rather than retrying. */
        data class Stopped(val error: PortalError) : Outcome
    }

    suspend fun run(): Outcome {
        if (!settings.dailyCheckEnabled.first()) return Outcome.Skipped
        val intervalMillis = TimeUnit.DAYS.toMillis(settings.checkIntervalDays.first().toLong())
        val now = now()
        val last = settings.lastBackgroundCheckAt.first()
        // WorkManager can run a periodic job early in its window; the margin
        // still keeps two checks from happening within the same day.
        if (last != null && now - last < intervalMillis - EARLY_MARGIN_MILLIS) return Outcome.Skipped
        settings.setLastBackgroundCheckAt(now)

        var checked = 0
        var notified = 0
        for (case in cases.getCases()) {
            val refreshed = when (val result = portalCall { cases.refresh(case.neun) }) {
                is PortalResult.Failed -> {
                    if (result.error == PortalError.NETWORK) return Outcome.Stopped(result.error)
                    continue
                }
                is PortalResult.Ok -> result.value
            }
            checked++
            val newAcuerdos = (refreshed as? RefreshResult.Updated)?.newAcuerdos.orEmpty()
            if (newAcuerdos.isEmpty()) continue

            notifier.notifyNewAcuerdos(case, cases.unseenOrdenes(case.neun).size)
            notified++
            for (acuerdo in newAcuerdos.filter { Resumen.isTruncated(it.resumen) }) {
                val sintesis = portalCall { cases.sintesis(case.neun, acuerdo.orden) }
                if (sintesis is PortalResult.Failed && sintesis.error == PortalError.NETWORK) {
                    return Outcome.Stopped(sintesis.error)
                }
            }
        }
        return Outcome.Done(checked, notified)
    }

    companion object {
        val EARLY_MARGIN_MILLIS: Long = TimeUnit.HOURS.toMillis(4)
    }
}
