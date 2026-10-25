package mx.sisetracker.data.hearings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import java.time.LocalDate
import mx.sisetracker.R
import mx.sisetracker.core.Audiencia
import mx.sisetracker.data.check.canPostNotifications
import mx.sisetracker.data.check.caseContentIntent
import mx.sisetracker.data.check.postNotification
import mx.sisetracker.data.db.CaseEntity
import mx.sisetracker.ui.components.hearingWhen
import mx.sisetracker.ui.components.labelRes

/**
 * "Audiencia constitucional mañana a las 10:23", then the expediente and the
 * órgano. Hard rule 6: nothing else from the case, and VISIBILITY_PRIVATE.
 */
class SystemHearingNotifier(private val context: Context) : HearingNotifier {

    override fun notifyHearing(case: CaseEntity, audiencia: Audiencia, today: LocalDate) {
        if (!context.canPostNotifications()) return
        createChannel()
        val res = context.resources
        val title = res.getString(
            R.string.audiencia_reminder_title,
            res.getString(audiencia.tipo.labelRes()),
            hearingWhen(res, audiencia, today),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(res.getString(R.string.audiencia_reminder_text, case.expediente, case.organoName))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(caseContentIntent(context, case.neun))
            .setAutoCancel(true)
            .build()
        context.postNotification("hearing|${case.neun}|${audiencia.tipo}".hashCode(), notification)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.hearing_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.hearing_channel_description)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "audiencias"
    }
}
