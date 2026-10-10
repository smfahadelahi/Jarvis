package com.jarvis.ai.core.agent

import java.time.LocalTime

/**
 * Natural-language time and duration parsing. Pure Kotlin, no Android
 * dependencies, fully unit-testable.
 *
 * Convention: a bare hour with no am/pm marker is interpreted as the morning
 * occurrence (07:00 -> 7 AM). This matches how people set alarms the vast
 * majority of the time, and it is deterministic — never a guessing game.
 */
object TimeParser {

    data class TimeOfDay(val hour: Int, val minute: Int)

    private val NUM_WORDS = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "noon" to 12, "midnight" to 0
    )

    /**
     * Parses a clock time out of [text], e.g. "at 7:30 pm", "quarter to six",
     * "at 19:00", "at seven o'clock". Returns null when no time is present.
     */
    fun parseClockTime(text: String): TimeOfDay? {
        val t = text.lowercase().trim()

        // Numeric with optional minutes and optional meridiem: 7, 7:30, 7.30, 0730
        val numeric = Regex("""(?:at\s+)?\b(\d{1,2})\s*[:.]?\s*(\d{2})?\s*(a\.?m\.?|p\.?m\.?)?\b""")
            .find(t)
        if (numeric != null) {
            val hourRaw = numeric.groupValues[1].toIntOrNull()
            val minuteRaw = numeric.groupValues[2].toIntOrNull() ?: 0
            val meridiem = normalizeMeridiem(numeric.groupValues[3])
            if (hourRaw != null && hourRaw in 0..23 && minuteRaw in 0..59) {
                // "19:00" style 24h input only when minutes exist or value > 12
                return if (meridiem != null) {
                    applyMeridiem(hourRaw, minuteRaw, meridiem)
                } else if (hourRaw > 12) {
                    TimeOfDay(hourRaw, minuteRaw)
                } else {
                    applyMeridiem(hourRaw, minuteRaw, "am")
                }
            }
        }

        // "half past seven", "quarter past/to six", "seven o'clock", word numbers
        for ((word, hour) in NUM_WORDS) {
            if (Regex("""\b$word\b""").containsMatchIn(t)) {
                val minutes = when {
                    Regex("""half past""").containsMatchIn(t) -> 30
                    Regex("""quarter past""").containsMatchIn(t) -> 15
                    Regex("""quarter to""").containsMatchIn(t) -> return TimeOfDay((hour - 1 + 24) % 24, 45)
                    else -> 0
                }
                val meridiem = normalizeMeridiem(Regex("""(a\.?m\.?|p\.?m\.?)""").find(t)?.value)
                val base = if (meridiem != null) {
                    applyMeridiem(hour, minutes, meridiem)
                } else {
                    applyMeridiem(hour, minutes, "am")
                }
                return base
            }
        }
        return null
    }

    /**
     * Parses a spoken duration, e.g. "20 minutes", "1 hour and 30 minutes",
     * "90 seconds". Returns total seconds, or null.
     */
    fun parseDurationSeconds(text: String): Int? {
        val t = text.lowercase().replace(" and ", " ")
        var total = 0
        var found = false
        for (match in Regex("""(\d+|a|an|one|two|three|four|five|six|ten|fifteen|twenty|thirty|forty five|forty-five)\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\b""")
            .findAll(t)) {
            val amount = match.groupValues[1]
            val unit = match.groupValues[2]
            val n = normalizeAmount(amount) ?: continue
            val seconds = when {
                unit.startsWith("h") -> n * 3600
                unit.startsWith("m") -> n * 60
                else -> n
            }
            total += seconds
            found = true
        }
        return if (found) total else null
    }

    private fun normalizeAmount(raw: String): Int? = when (raw) {
        "a", "an" -> 1
        "one" -> 1
        "two" -> 2
        "three" -> 3
        "four" -> 4
        "five" -> 5
        "six" -> 6
        "ten" -> 10
        "fifteen" -> 15
        "twenty" -> 20
        "thirty" -> 30
        "forty five", "forty-five" -> 45
        else -> raw.toIntOrNull()
    }

    private fun normalizeMeridiem(raw: String?): String? = when {
        raw == null -> null
        raw.replace(".", "").startsWith("a") -> "am"
        raw.replace(".", "").startsWith("p") -> "pm"
        else -> null
    }

    private fun applyMeridiem(hour: Int, minute: Int, meridiem: String): TimeOfDay {
        val h = when {
            meridiem == "am" && hour == 12 -> 0
            meridiem == "am" -> hour
            meridiem == "pm" && hour == 12 -> 12
            else -> hour + 12
        }
        return TimeOfDay(h % 24, minute)
    }

    /** Human-readable HH:MM. */
    fun TimeOfDay.format(): String = String.format("%02d:%02d", hour, minute)

    /** True when [target] has already passed today relative to [now]. */
    fun isPastToday(target: TimeOfDay, now: LocalTime): Boolean {
        val t = LocalTime.of(target.hour % 24, target.minute)
        return !t.isAfter(now)
    }
}
