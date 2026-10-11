package com.jarvis.ai.core.tools.impl

import android.content.Context
import com.jarvis.ai.core.tools.ParamSpec
import com.jarvis.ai.core.tools.Tool
import com.jarvis.ai.core.tools.ToolRegistry
import com.jarvis.ai.core.tools.ToolResult
import com.jarvis.ai.di.AppContainer
import java.time.Duration
import java.time.LocalDateTime

/**
 * Alarms, timers, reminders, and the alarm ledger.
 */
object ProductivityTools {

    fun register(context: Context, container: AppContainer, registry: ToolRegistry) {
        registry.register(SetAlarmTool(context, container))
        registry.register(SetTimerTool(context, container))
        registry.register(SetReminderTool(context, container))
        registry.register(ListAlarmsTool(container))
    }

    /** Shared next-occurrence resolution for clock-based schedules. */
    internal fun nextOccurrence(hour: Int, minute: Int): LocalDateTime {
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        if (!target.isAfter(now)) target = target.plusDays(1)
        return target
    }

    internal fun formatTime(hour: Int, minute: Int): String =
        String.format("%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    internal fun describeDuration(seconds: Int): String {
        val d = Duration.ofSeconds(seconds.toLong())
        val h = d.toHours()
        val m = d.toMinutesPart()
        val s = d.toSecondsPart()
        return buildString {
            if (h > 0) append("${h}h ")
            if (m > 0) append("${m}m ")
            if (s > 0 || isEmpty()) append("${s}s")
        }.trim()
    }
}

internal class SetAlarmTool(
    private val context: Context,
    private val container: AppContainer
) : Tool {
    override val name = "set_alarm"
    override val description = "Set the device alarm clock for a specific time."
    override val parameters = listOf(
        ParamSpec("hour", "int", description = "0-23"),
        ParamSpec("minute", "int", required = false, description = "0-59, default 0"),
        ParamSpec("label", "string", required = false, description = "alarm label")
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val hour = params["hour"]?.trim()?.toIntOrNull()
            ?: return ToolResult(false, "I need an hour for the alarm, sir.")
        val minute = params["minute"]?.trim()?.toIntOrNull() ?: 0
        if (hour !in 0..23 || minute !in 0..59) {
            return ToolResult(false, "That time is outside the realm of the possible, sir.")
        }
        val label = params["label"]?.trim()?.takeIf { it.isNotEmpty() } ?: "Alarm"
        val trigger = ProductivityTools.nextOccurrence(hour, minute)
        val scheduled = container.alarmScheduler.scheduleAlarm(
            triggerAtMs = trigger.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            title = label,
            body = "Alarm at ${ProductivityTools.formatTime(hour, minute)}"
        )
        return if (scheduled != null) {
            val dayWord = if (trigger.toLocalDate() != LocalDateTime.now().toLocalDate()) "tomorrow" else "today"
            ToolResult(
                true,
                "Alarm set for ${ProductivityTools.formatTime(hour, minute)} $dayWord, sir.",
                dataForLlm = "alarm scheduled at ${ProductivityTools.formatTime(hour, minute)} $dayWord (label: $label)"
            )
        } else {
            ToolResult(
                false,
                "I lack permission to schedule exact alarms, sir. Grant it in system settings.",
                dataForLlm = "failed: exact alarm permission missing"
            )
        }
    }
}

internal class SetTimerTool(
    private val context: Context,
    private val container: AppContainer
) : Tool {
    override val name = "start_timer"
    override val description = "Start a countdown timer for a number of seconds."
    override val parameters = listOf(
        ParamSpec("seconds", "int", description = "duration in seconds"),
        ParamSpec("label", "string", required = false, description = "timer label")
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val seconds = params["seconds"]?.trim()?.toIntOrNull()
            ?: params["minutes"]?.trim()?.toIntOrNull()?.times(60)
            ?: return ToolResult(false, "A duration would help, sir.")
        if (seconds <= 0 || seconds > 24 * 3600) {
            return ToolResult(false, "That timer duration is unreasonable, sir.")
        }
        val label = params["label"]?.trim()?.takeIf { it.isNotEmpty() } ?: "Timer"
        val trigger = System.currentTimeMillis() + seconds * 1000L
        val scheduled = container.alarmScheduler.scheduleAlarm(
            triggerAtMs = trigger,
            title = label,
            body = "Time's up — ${ProductivityTools.describeDuration(seconds)} elapsed."
        )
        return if (scheduled != null) {
            ToolResult(
                true,
                "Timer running — ${ProductivityTools.describeDuration(seconds)}, sir.",
                dataForLlm = "timer started for ${ProductivityTools.describeDuration(seconds)}"
            )
        } else {
            ToolResult(false, "I can't schedule timers without the exact-alarm permission, sir.")
        }
    }
}

internal class SetReminderTool(
    private val context: Context,
    private val container: AppContainer
) : Tool {
    override val name = "set_reminder"
    override val description = "Set a reminder that fires after N minutes or at a clock time. Params: in_minutes (int) or hour (int) + minute (int); message (string)."
    override val parameters = listOf(
        ParamSpec("in_minutes", "int", required = false, description = "fire after this many minutes"),
        ParamSpec("hour", "int", required = false, description = "clock hour 0-23 if not using in_minutes"),
        ParamSpec("minute", "int", required = false, description = "clock minute"),
        ParamSpec("message", "string", required = false, description = "reminder text")
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val message = params["message"]?.trim()?.takeIf { it.isNotEmpty() } ?: "Reminder"
        val triggerAt: Long
        val description: String
        val inMinutes = params["in_minutes"]?.trim()?.toIntOrNull()
        if (inMinutes != null && inMinutes > 0) {
            triggerAt = System.currentTimeMillis() + inMinutes * 60_000L
            description = "in $inMinutes minute(s)"
        } else {
            val hour = params["hour"]?.trim()?.toIntOrNull()
                ?: return ToolResult(false, "I need either a delay or a time for the reminder, sir.")
            val minute = params["minute"]?.trim()?.toIntOrNull() ?: 0
            if (hour !in 0..23 || minute !in 0..59) {
                return ToolResult(false, "That clock time won't parse, sir.")
            }
            val trigger = ProductivityTools.nextOccurrence(hour, minute)
            triggerAt = trigger.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            description = "at ${ProductivityTools.formatTime(hour, minute)}"
        }
        val scheduled = container.alarmScheduler.scheduleAlarm(triggerAt, message, "Reminder: $message")
        return if (scheduled != null) {
            ToolResult(
                true,
                "Reminder locked in $description, sir.",
                dataForLlm = "reminder \"$message\" scheduled $description"
            )
        } else {
            ToolResult(false, "Exact-alarm permission is required for reminders, sir.")
        }
    }
}

internal class ListAlarmsTool(private val container: AppContainer) : Tool {
    override val name = "list_alarms"
    override val description = "List currently scheduled alarms, timers, and reminders."
    override val parameters = emptyList<ParamSpec>()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val entries = container.alarmScheduler.listPending()
        if (entries.isEmpty()) {
            return ToolResult(true, "The ledger is empty, sir — nothing scheduled.")
        }
        val now = System.currentTimeMillis()
        val lines = entries.map { entry ->
            val remaining = Duration.ofMillis((entry.triggerAtMs - now).coerceAtLeast(0))
            val inText = if (remaining.toMinutes() >= 1) "in ${remaining.toMinutes()} min" else "imminently"
            "- ${entry.title}: $inText"
        }
        return ToolResult(
            true,
            "${entries.size} item(s) scheduled, sir.",
            dataForLlm = "scheduled items:\n" + lines.joinToString("\n")
        )
    }
}
