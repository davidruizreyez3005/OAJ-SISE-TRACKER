package mx.sisetracker.data.check

import android.Manifest
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

/**
 * One notification per case, replaced on the next check. Hard rule 6: it shows
 * only the expediente, the órgano and a count, never résumé or síntesis text,
 * and is VISIBILITY_PRIVATE, so the lock screen hides even that.
 */
class SystemNewAcuerdosNotifier(private val context: Context) : NewAcuerdosNotifier {

    override fun notifyNewAcuerdos(case: CaseEntity, unseenCount: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannel()
        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_CASE)
            .putExtra(MainActivity.EXTRA_NEUN, case.neun)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context,
            case.neun.hashCode(),
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
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
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(case.neun), notification)
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

        fun notificationId(neun: String): Int = neun.hashCode()
    }
}
