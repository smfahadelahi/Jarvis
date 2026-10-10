package com.jarvis.ai.core.agent

/**
 * Deterministic fast-path command detection.
 *
 * High-frequency commands (alarms, timers, torch, weather...) bypass the
 * language core entirely. Two benefits:
 *  1. Sub-100ms latency — no inference round trip.
 *  2. Jarvis remains useful before any model is loaded.
 *
 * Pure Kotlin; returns an intent description, execution stays in the
 * orchestrator/tool layer.
 */
object CommandFastPath {

    sealed interface FastCommand {
        data class SetAlarm(val time: TimeParser.TimeOfDay, val label: String?) : FastCommand
        data class SetReminder(
            val delaySeconds: Int?,
            val time: TimeParser.TimeOfDay?,
            val message: String?
        ) : FastCommand

        data class SetTimer(val seconds: Int, val label: String?) : FastCommand
        data class Flashlight(val on: Boolean?) : FastCommand
        data class Weather(val city: String?) : FastCommand
        data class Volume(val action: VolumeAction, val levelPercent: Int?) : FastCommand
        data class Brightness(val action: BrightnessAction, val levelPercent: Int?) : FastCommand
        data class LaunchApp(val appName: String) : FastCommand
        data object TimeQuery : FastCommand
        data object DateQuery : FastCommand
        data object BatteryQuery : FastCommand
        data object Briefing : FastCommand
        data object KoreanLesson : FastCommand
        data object Diagnostics : FastCommand
    }

    enum class VolumeAction { UP, DOWN, MUTE, UNMUTE, SET }
    enum class BrightnessAction { UP, DOWN, SET }

    fun detect(rawInput: String): FastCommand? {
        val text = rawInput.lowercase().trim()
        if (text.isEmpty()) return null

        // --- Alarms (before generic time queries) ---
        if (Regex("""\b(alarm|wake me up|wake me)\b""").containsMatchIn(text)) {
            if (Regex("""\b(cancel|remove|delete|list|show)\b""").containsMatchIn(text)) return null
            TimeParser.parseClockTime(text)?.let { time ->
                return FastCommand.SetAlarm(time, extractLabel(text))
            }
        }

        // --- Reminders ---
        if (Regex("""\b(remind me|reminder)\b""").containsMatchIn(text)) {
            val duration = TimeParser.parseDurationSeconds(text)
            val clock = TimeParser.parseClockTime(text)
            if (duration != null || clock != null) {
                return FastCommand.SetReminder(duration, clock, extractLabel(text))
            }
        }

        // --- Timers ---
        if (Regex("""\btimer\b""").containsMatchIn(text)) {
            TimeParser.parseDurationSeconds(text)?.let { seconds ->
                return FastCommand.SetTimer(seconds, extractLabel(text))
            }
            // "5 minute timer" — number before the word timer
            val reversed = Regex("""(\d+|one|two|three|four|five|ten|fifteen|twenty|thirty|forty five|forty-five)\s*(hours?|hrs?|minutes?|mins?|seconds?|secs?)\s*(timer|countdown)""")
                .find(text)
            if (reversed != null) {
                TimeParser.parseDurationSeconds(
                    reversed.groupValues[1] + " " + reversed.groupValues[2]
                )?.let { return FastCommand.SetTimer(it, null) }
            }
        }

        // --- Flashlight ---
        if (Regex("""\b(flashlight|torch)\b""").containsMatchIn(text)) {
            val on = when {
                Regex("""\b(off|kill|out|disable)\b""").containsMatchIn(text) -> false
                Regex("""\b(on|enable|light up)\b""").containsMatchIn(text) -> true
                else -> null // toggle
            }
            return FastCommand.Flashlight(on)
        }

        // --- Volume ---
        if (Regex("""\bvolume\b""").containsMatchIn(text)) {
            val level = Regex("""(\d{1,3})\s*%""").find(text)?.groupValues?.get(1)?.toIntOrNull()
            val action = when {
                level != null -> VolumeAction.SET
                Regex("""\bup|raise|increase|louder""").containsMatchIn(text) -> VolumeAction.UP
                Regex("""\bdown|lower|decrease|quieter""").containsMatchIn(text) -> VolumeAction.DOWN
                Regex("""\bmute""").containsMatchIn(text) -> VolumeAction.MUTE
                Regex("""\bunmute""").containsMatchIn(text) -> VolumeAction.UNMUTE
                else -> return null
            }
            return FastCommand.Volume(action, level)
        }

        // --- Brightness ---
        if (Regex("""\bbrightness\b|\bdim\b(?!.*light)""").containsMatchIn(text)) {
            val level = Regex("""(\d{1,3})\s*%""").find(text)?.groupValues?.get(1)?.toIntOrNull()
            val action = when {
                level != null -> BrightnessAction.SET
                Regex("""\bup|raise|increase|brighter|brighten""").containsMatchIn(text) -> BrightnessAction.UP
                Regex("""\bdown|lower|decrease|dim""").containsMatchIn(text) -> BrightnessAction.DOWN
                else -> return null
            }
            return FastCommand.Brightness(action, level)
        }

        // --- Weather ---
        if (Regex("""\bweather\b|\braining\b|\btemperature outside\b""").containsMatchIn(text)) {
            val city = Regex("""(?:weather|temperature)\s*(?:in|for|at)\s+([a-z\s]+?)(?:\?|$|\s(?:today|tomorrow|now))""")
                .find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
            return FastCommand.Weather(city)
        }

        // --- App launching ---
        val launch = Regex("""(?:open|launch|start|run)\s+(?:the\s+)?app\s+(.+)|(?:open|launch|start)\s+(?:up\s+)?([a-z0-9\s]+?)(?:\s+app)?(?:\?|$)""")
            .find(text)
        if (launch != null && !Regex("""\b(settings|panel|wifi|wi-fi|bluetooth|calendar|alarm|browser|website|file)\b""").containsMatchIn(text)) {
            val name = (launch.groupValues[2].ifBlank { launch.groupValues[1] }).trim()
            if (name.isNotBlank() && name.length <= 30) return FastCommand.LaunchApp(name)
        }

        // --- Time / date ---
        if (Regex("""\bwhat(?:'s| is)? the time\b|\bwhat time is it\b|\bcurrent time\b|\bthe time,? please\b""").containsMatchIn(text)) {
            return FastCommand.TimeQuery
        }
        if (Regex("""\bwhat(?:'s| is)? (?:the |today's )?date\b|\bwhat day is it\b|\btoday's date\b""").containsMatchIn(text)) {
            return FastCommand.DateQuery
        }

        // --- Battery ---
        if (Regex("""\bbattery\b|\bcharge level\b""").containsMatchIn(text)) {
            return FastCommand.BatteryQuery
        }

        // --- Briefing ---
        if (Regex("""\b(briefing|brief me|status report|morning report|daily summary|good morning)\b""").containsMatchIn(text)) {
            return FastCommand.Briefing
        }

        // --- Korean tutor ---
        if (Regex("""\bkorean\b""").containsMatchIn(text) &&
            Regex("""\b(lesson|teach|word|phrase|practice|tutor|study|learn|vocabulary)""").containsMatchIn(text)
        ) {
            return FastCommand.KoreanLesson
        }

        // --- Diagnostics ---
        if (Regex("""\b(diagnostics|diagnostic|system check|self test|self-test|status check)\b""").containsMatchIn(text)) {
            return FastCommand.Diagnostics
        }

        return null
    }

    private fun extractLabel(text: String): String? {
        val m = Regex("""(?:labeled|called|named|for)\s+([a-z0-9\s]{2,30}?)(?:\s+(?:at|in|by|tomorrow|today)|$)""")
            .find(text)
        return m?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }
}
