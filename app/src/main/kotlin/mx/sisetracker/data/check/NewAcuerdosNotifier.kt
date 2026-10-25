package mx.sisetracker.data.check

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import mx.sisetracker.MainActivity
import mx.sisetracker.R
import mx.sisetracker.data.db.CaseEntity

/** Tells the user a saved case has new acuerdos. */
fun interface NewAcuerdosNotifier {
    fun notifyNewAcuerdos(case: CaseEntity, unseenCount: Int)
}

/** Whether the app may post notifications (Android 13+ asks for it). */
fun Context.canPostNotifications(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** Posts [notification]; dropped if the permission was revoked since [canPostNotifications] was checked. */
fun Context.postNotification(id: Int, notification: Notification) {
    try {
        NotificationManagerCompat.from(this).notify(id, notification)
    } catch (e: SecurityException) {
        // Revoked meanwhile: nothing to show.
    }
}

/** Opens a saved case from a notification. */
fun caseContentIntent(context: Context, neun: String): PendingIntent {
    val open = Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_OPEN_CASE)
        .putExtra(MainActivity.EXTRA_NEUN, neun)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    return PendingIntent.getActivity(
        context,
        neun.hashCode(),
        open,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/**
 * One notification per case, replaced on the next check, grouped under a
 * summary when there are several. Hard rule 6: it shows only the expediente,
 * the órgano and a count, never résumé or síntesis text, and is
 * VISIBILITY_PRIVATE, so the lock screen hides even that.
 */
class SystemNewAcuerdosNotifier(private val context: Context) : NewAcuerdosNotifier {

    override fun notifyNewAcuerdos(case: CaseEntity, unseenCount: Int) {
        if (!context.canPostNotifications()) return
        createChannel()
        val title = context.resources.getQuantityString(
            R.plurals.notification_new_acuerdos,
            unseenCount,
            unseenCount,
            case.expediente,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(case.organoName)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setGroup(GROUP)
            .setContentIntent(caseContentIntent(context, case.neun))
            .setAutoCancel(true)
            .build()
        context.postNotification(notificationId(case.neun), notification)
        updateSummary()
    }

    /** "3 expedientes con acuerdos nuevos" over the group, from two cases on. */
    private fun updateSummary() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val count = manager.activeNotifications.count { it.notification.group == GROUP && it.id != SUMMARY_ID }
        if (count < 2) {
            manager.cancel(SUMMARY_ID)
            return
        }
        val open = PendingIntent.getActivity(
            context,
            SUMMARY_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = context.resources.getQuantityString(R.plurals.notification_summary_text, count, count)
        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_summary_title))
            .setContentText(text)
            .setStyle(NotificationCompat.InboxStyle().setSummaryText(text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.postNotification(SUMMARY_ID, summary)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "nuevos_acuerdos"
        const val GROUP = "mx.sisetracker.NUEVOS_ACUERDOS"
        const val SUMMARY_ID = 1

        fun notificationId(neun: String): Int = neun.hashCode()
    }
}
