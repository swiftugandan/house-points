package dev.housepoints.app.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.housepoints.app.MainActivity
import dev.housepoints.app.R
import dev.housepoints.contracts.InstantMs

/**
 * SPEC FR-43: a local reminder at each payday. Inexact alarms are enough (a few minutes late is fine) and
 * need no special permission; nothing touches the network.
 */
object PaydayReminder {
    private const val CHANNEL = "payday"
    private const val NOTIFICATION_ID = 1
    private const val REQUEST = 7

    fun schedule(context: Context, payday: InstantMs) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.set(AlarmManager.RTC_WAKEUP, payday.value, pending(context))
    }

    fun post(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.payday_channel), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.payday_channel_description) },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open = PendingIntent.getActivity(
            context, REQUEST, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Payday")
            .setContentText("Interest is in. Statements are ready.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST, Intent(context, PaydayReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Fires at payday; the app reschedules the next one whenever it recalculates. */
class PaydayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = PaydayReminder.post(context)
}
