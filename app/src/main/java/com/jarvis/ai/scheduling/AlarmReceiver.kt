package com.jarvis.ai.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires scheduled alarms, timers, and reminders as high-priority
 * notifications with sound.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Jarvis"
        val body = intent.getStringExtra(EXTRA_BODY) ?: "Your scheduled item, sir."
        val id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, (System.currentTimeMillis() and 0x7fffffff).toInt())
        NotificationHelper.showAlarm(context, title, body, id)
    }

    companion object {
        const val EXTRA_TITLE = "jarvis.extra.title"
        const val EXTRA_BODY = "jarvis.extra.body"
        const val EXTRA_NOTIFICATION_ID = "jarvis.extra.notification_id"
    }
}
