package com.jarvis.ai.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Exact-alarm scheduling with pragmatic degradation: when the exact-alarm
 * permission is missing (Android 14+ gates it behind a settings toggle), we
 * fall back to inexact alarms rather than failing outright.
 */
class AlarmScheduler(
    private val context: Context,
    private val store: PersistedAlarms
) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /**
     * Schedules [title]/[body] to fire at [triggerAtMs]. Returns the request
     * code, or null when scheduling failed entirely.
     */
    fun scheduleAlarm(triggerAtMs: Long, title: String, body: String): Int? {
        if (triggerAtMs <= System.currentTimeMillis() - 1000) return null
        val requestCode = (triggerAtMs / 1000L).toInt()
        val pendingIntent = buildPendingIntent(requestCode, title, body)
        val exactAllowed = canScheduleExact()
        try {
            if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm rejected: ${e.message}")
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "Inexact alarm also failed: ${e2.message}")
                return null
            }
        }
        store.add(PersistedAlarms.Entry(requestCode, triggerAtMs, title, body))
        return requestCode
    }

    fun cancel(requestCode: Int) {
        val entry = store.load().firstOrNull { it.requestCode == requestCode }
        if (entry != null) {
            val pendingIntent = buildPendingIntent(requestCode, entry.title, entry.body)
            alarmManager.cancel(pendingIntent)
        }
        store.remove(requestCode)
    }

    fun listPending(): List<PersistedAlarms.Entry> = store.future()

    private fun buildPendingIntent(requestCode: Int, title: String, body: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_TITLE, title)
            putExtra(AlarmReceiver.EXTRA_BODY, body)
            putExtra(AlarmReceiver.EXTRA_NOTIFICATION_ID, requestCode)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "AlarmScheduler"
    }
}
