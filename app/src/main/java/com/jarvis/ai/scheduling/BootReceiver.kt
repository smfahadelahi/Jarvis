package com.jarvis.ai.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms all future scheduled items after a device reboot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val scheduler = AlarmScheduler(context, PersistedAlarms(context))
        scheduler.listPending().forEach { entry ->
            scheduler.scheduleAlarm(entry.triggerAtMs, entry.title, entry.body)
        }
    }
}
