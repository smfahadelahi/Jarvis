package com.jarvis.ai.scheduling

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Durable ledger of scheduled items so [BootReceiver] can re-arm them after
 * a reboot (AlarmManager does not survive one).
 */
class PersistedAlarms(context: Context) {

    data class Entry(
        val requestCode: Int,
        val triggerAtMs: Long,
        val title: String,
        val body: String
    )

    private val file = File(context.filesDir, "scheduled_items.json")

    @Synchronized
    fun add(entry: Entry) {
        val all = load().toMutableList()
        all.removeAll { it.requestCode == entry.requestCode }
        all.add(entry)
        save(all)
    }

    @Synchronized
    fun remove(requestCode: Int) {
        save(load().toMutableList().apply {
            removeAll { it.requestCode == requestCode }
        })
    }

    @Synchronized
    fun load(): List<Entry> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                Entry(
                    requestCode = obj.optInt("requestCode"),
                    triggerAtMs = obj.optLong("triggerAtMs"),
                    title = obj.optString("title"),
                    body = obj.optString("body")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Items still in the future — the only ones worth re-arming. */
    @Synchronized
    fun future(nowMs: Long = System.currentTimeMillis()): List<Entry> =
        load().filter { it.triggerAtMs > nowMs }

    @Synchronized
    private fun save(entries: List<Entry>) {
        try {
            val array = JSONArray()
            entries.forEach { entry ->
                array.put(
                    JSONObject()
                        .put("requestCode", entry.requestCode)
                        .put("triggerAtMs", entry.triggerAtMs)
                        .put("title", entry.title)
                        .put("body", entry.body)
                )
            }
            file.writeText(array.toString())
        } catch (_: Exception) {
            // Ledger loss is survivable; alarms already armed still fire.
        }
    }
}
