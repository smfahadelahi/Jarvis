package com.jarvis.ai.core.tools.impl

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.jarvis.ai.core.tools.ArithmeticEvaluator
import com.jarvis.ai.core.tools.ParamSpec
import com.jarvis.ai.core.tools.Tool
import com.jarvis.ai.core.tools.ToolRegistry
import com.jarvis.ai.core.tools.ToolResult
import com.jarvis.ai.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.util.Locale

/**
 * Knowledge and environment tools: time, weather, calendar, web search,
 * app launching, arithmetic, and the executive briefing.
 */
object KnowledgeTools {

    fun register(context: Context, container: AppContainer, registry: ToolRegistry) {
        registry.register(CurrentTimeTool())
        registry.register(CalculateTool())
        registry.register(GetWeatherTool(context, container))
        registry.register(ReadCalendarTool(context))
        registry.register(WebSearchTool(context))
        registry.register(LaunchAppTool(context))
        registry.register(MorningBriefingTool(context, container))
    }
}

internal class CurrentTimeTool : Tool {
    override val name = "current_time"
    override val description = "Report the current local date and time."
    override val parameters = emptyList()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val now = LocalDateTime.now()
        val time = now.format(DateTimeFormatter.ofPattern("HH:mm"))
        val date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH))
        return ToolResult(
            true,
            "It is $time, $date, sir.",
            dataForLlm = "current time: $time on $date (${ZoneId.systemDefault().id})"
        )
    }
}

internal class CalculateTool : Tool {
    override val name = "calculate"
    override val description = "Evaluate an arithmetic expression. Params: expression (string), e.g. \"(12+3)*4\"."
    override val parameters = listOf(
        ParamSpec("expression", "string", description = "arithmetic expression with + - * / % ^ ( )")
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val expression = params["expression"]?.trim()
            ?: return ToolResult(false, "An expression is required, sir.")
        return try {
            val result = ArithmeticEvaluator.evaluate(expression)
            val formatted = if (result == result.toLong().toDouble()) {
                result.toLong().toString()
            } else {
                String.format("%.6f", result).trimEnd('0').trimEnd('.')
            }
            ToolResult(true, "$expression = $formatted", dataForLlm = "$expression = $formatted")
        } catch (e: Exception) {
            ToolResult(false, "That expression does not parse, sir. (${e.message ?: "syntax error"})")
        }
    }
}

internal class GetWeatherTool(
    private val context: Context,
    private val container: AppContainer
) : Tool {
    override val name = "get_weather"
    override val description = "Current weather for a city. Params: city (string, optional — uses the configured default)."
    override val parameters = listOf(
        ParamSpec("city", "string", required = false, description = "city name")
    )

    override suspend fun execute(params: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val city = params["city"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: container.settings.cityOnce()
            ?: "Seoul"
        return@withContext try {
            val snapshot = container.weather.current(city)
            ToolResult(
                true,
                "${snapshot.city}: ${container.weather.describe(snapshot)}, sir.",
                dataForLlm = "weather in ${snapshot.city}: ${container.weather.describe(snapshot)}"
            )
        } catch (e: Exception) {
            ToolResult(false, "The weather service is not answering for \"$city\", sir.")
        }
    }
}

internal class ReadCalendarTool(private val context: Context) : Tool {
    override val name = "read_calendar"
    override val description = "Upcoming calendar events. Params: hours_ahead (int, optional, default 12)."
    override val parameters = listOf(
        ParamSpec("hours_ahead", "int", required = false, description = "lookahead window in hours")
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return ToolResult(
                false,
                "Calendar access is not granted, sir.",
                dataForLlm = "calendar unavailable: permission not granted"
            )
        }
        val hours = params["hours_ahead"]?.trim()?.toLongOrNull()?.coerceIn(1, 72) ?: 12L
        val now = System.currentTimeMillis()
        val limit = now + hours * 3600_000L

        return withContext(Dispatchers.IO) {
            val uri = CalendarContract.Instances.CONTENT_URI
                .buildUpon()
                .appendPath(now.toString())
                .appendPath(limit.toString())
                .build()
            val projection = arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.EVENT_LOCATION
            )
            val events = mutableListOf<String>()
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            try {
                context.contentResolver.query(uri, projection, null, null, CalendarContract.Instances.BEGIN)
                    ?.use { cursor ->
                        while (cursor.moveToNext() && events.size < 8) {
                            val title = cursor.getString(0) ?: "Untitled"
                            val begin = cursor.getLong(1)
                            val location = cursor.getString(2)
                            val time = LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(begin), ZoneId.systemDefault()
                            ).format(formatter)
                            events.add("- $time $title" + (location?.let { " (@$it)" } ?: ""))
                        }
                    }
            } catch (_: Exception) {
                return@withContext ToolResult(false, "The calendar provider refused the query, sir.")
            }
            if (events.isEmpty()) {
                ToolResult(
                    true,
                    "A clear board for the next $hours hours, sir.",
                    dataForLlm = "no calendar events in the next $hours hours"
                )
            } else {
                ToolResult(
                    true,
                    "${events.size} upcoming item(s), sir.",
                    dataForLlm = "calendar (next $hours hours):\n" + events.joinToString("\n")
                )
            }
        }
    }
}

internal class WebSearchTool(private val context: Context) : Tool {
    override val name = "web_search"
    override val description = "Run a web search in the browser. Params: query (string)."
    override val parameters = listOf(
        ParamSpec("query", "string", description = "search query")
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val query = params["query"]?.trim()
            ?: return ToolResult(false, "What shall I look up, sir?")
        return try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult(true, "Dispatched \"$query\" to the search engine, sir.")
        } catch (_: Exception) {
            try {
                val url = "https://duckduckgo.com/?q=" + Uri.encode(query)
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                ToolResult(true, "The browser is on it, sir.")
            } catch (_: Exception) {
                ToolResult(false, "No browser available on this device, sir.")
            }
        }
    }
}

internal class LaunchAppTool(private val context: Context) : Tool {
    override val name = "launch_app"
    override val description = "Launch an installed app by name. Params: app_name (string)."
    override val parameters = listOf(
        ParamSpec("app_name", "string", description = "app display name, e.g. Spotify")
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val wanted = params["app_name"]?.trim()?.lowercase()
            ?: return ToolResult(false, "Which app, sir?")
        if (wanted.isEmpty()) return ToolResult(false, "Which app, sir?")
        val pm = context.packageManager
        val matches = pm.getInstalledApplications(0).mapNotNull { appInfo ->
            val label = try {
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                null
            } ?: return@mapNotNull null
            if (label.lowercase().contains(wanted)) Pair(appInfo.packageName, label) else null
        }
        val best = matches.firstOrNull() ?: return ToolResult(false, "No app matching \"$wanted\" is installed, sir.")
        return try {
            context.startActivity(
                pm.getLaunchIntentForPackage(best.first)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?: return ToolResult(false, "\"${best.second}\" has no launcher activity, sir.")
            )
            ToolResult(true, "Launching ${best.second}, sir.")
        } catch (_: Exception) {
            ToolResult(false, "The system refused to launch ${best.second}, sir.")
        }
    }
}

internal class MorningBriefingTool(
    private val context: Context,
    private val container: AppContainer
) : Tool {
    override val name = "morning_briefing"
    override val description = "Assemble an executive briefing: time, battery, calendar, weather."
    override val parameters = emptyList()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val lines = mutableListOf<String>()
        val now = LocalDateTime.now()
        lines.add(
            "time: " + now.format(DateTimeFormatter.ofPattern("HH:mm EEEE d MMMM", Locale.ENGLISH))
        )

        // Battery
        runCatching {
            val battery = BatteryStatusTool(context).execute(emptyMap())
            battery.dataForLlm?.let { lines.add(it) }
        }

        // Calendar
        runCatching {
            val calendar = ReadCalendarTool(context).execute(mapOf("hours_ahead" to "12"))
            calendar.dataForLlm?.let { lines.add(it) }
        }

        // Weather
        runCatching {
            val city = container.settings.cityOnce() ?: "Seoul"
            val weather = GetWeatherTool(context, container).execute(mapOf("city" to city))
            weather.dataForLlm?.let { lines.add(it) }
        }

        val data = "briefing data:\n" + lines.joinToString("\n")
        val spokenFallback = buildString {
            append("Briefing, sir. ")
            lines.drop(1).forEach { line ->
                append(line.removePrefix("calendar (next 12 hours):").trim()).append(". ")
            }
        }
        return ToolResult(true, spokenFallback.trim(), dataForLlm = data)
    }
}
