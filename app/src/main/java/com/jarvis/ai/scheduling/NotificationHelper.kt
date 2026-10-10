package com.jarvis.ai.scheduling

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.jarvis.ai.MainActivity
import com.jarvis.ai.R

/**
 * Notification plumbing: channel construction and the three notification
 * families Jarvis uses (alarms, wake-word service, general).
 */
object NotificationHelper {

    const val CHANNEL_ALARMS = "jarvis_alarms"
    const val CHANNEL_SERVICE = "jarvis_service"
    const val CHANNEL_GENERAL = "jarvis_general"

    const val NOTIFICATION_ID_SERVICE = 42

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val alarms = NotificationChannel(
            CHANNEL_ALARMS,
            context.getString(R.string.channel_alarms),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_alarms_desc)
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                null
            )
            setBypassDnd(false)
        }
        manager.createNotificationChannel(alarms)

        val service = NotificationChannel(
            CHANNEL_SERVICE,
            context.getString(R.string.channel_service),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_service_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(service)

        val general = NotificationChannel(
            CHANNEL_GENERAL,
            context.getString(R.string.channel_general),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(general)
    }

    /** Fires an alarm/timer/reminder notification with sound and vibration. */
    fun showAlarm(context: Context, title: String, body: String, notificationId: Int) {
        ensureChannels(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALARMS)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    /** Persistent low-priority notification for the wake-word listener. */
    fun showServiceNotification(context: Context): android.app.Notification {
        ensureChannels(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setContentTitle(context.getString(R.string.wake_service_notification_title))
            .setContentText(context.getString(R.string.wake_service_notification_text))
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    fun showGeneral(context: Context, title: String, body: String) {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_GENERAL)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
