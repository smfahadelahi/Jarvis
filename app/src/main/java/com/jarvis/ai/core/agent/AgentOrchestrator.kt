package com.jarvis.ai.core.agent

import android.util.Log
import com.jarvis.ai.core.AgentEvent
import com.jarvis.ai.core.AgentMode
import com.jarvis.ai.core.AgentState
import com.jarvis.ai.core.llm.LlmEngine
import com.jarvis.ai.core.llm.LlmStatus
import com.jarvis.ai.core.tts.JarvisTtsEngine
import com.jarvis.ai.data.SettingsRepository
import com.jarvis.ai.core.tools.ToolRegistry
import com.jarvis.ai.core.tools.UnknownToolException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * The agent loop.
 *
 * Turn processing order:
 *   1. Deterministic fast path (no LLM latency, works model-less).
 *   2. LLM tool-calling turn: persona prompt + compact tool manifest.
 *   3. Tool observation synthesis (second, short generation).
 *   4. Degraded canned reply when no model is loaded.
 *
 * Events stream out on [events]; the HUD and TTS subscribe.
 */
class AgentOrchestrator(
    private val llm: LlmEngine,
    private val tools: ToolRegistry,
    private val memory: ConversationMemory,
    private val tts: JarvisTtsEngine,
    private val settings: SettingsRepository
) {

    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AgentEvent> = _events

    @Volatile
    var mode: AgentMode = AgentMode.HUD
        private set

    private val busy = java.util.concurrent.atomic.AtomicBoolean(false)

    fun setMode(newMode: AgentMode) {
        mode = newMode
        emit(AgentEvent.StateChanged(currentState()))
    }

    fun currentState(): AgentState = when {
        busy -> AgentState.THINKING
        tts.speaking.value -> AgentState.SPEAKING
        else -> AgentState.IDLE
    }

    /**
     * Processes one user turn. Re-entrant calls during a busy turn are
     * politely declined rather than queued — a butler, not a buffet.
     */
    suspend fun handleUserInput(rawInput: String) {
        val input = rawInput.trim()
        if (input.isEmpty()) return
        if (busy) {
            emit(AgentEvent.Failure("One moment, sir — still attending to your previous request."))
            return
        }
        busy = true
        try {
            process(input)
        } catch (t: Throwable) {
            Log.e(TAG, "Turn failed", t)
            emit(AgentEvent.StateChanged(AgentState.ERROR))
            finishTurn("A system fault interrupted me mid-thought, sir. Retry at your leisure.")
        } finally {
            busy = false
        }
    }

    private suspend fun process(input: String) {
        emit(AgentEvent.StateChanged(AgentState.THINKING))
        memory.addUser(input)

        // 1. Deterministic fast path.
        CommandFastPath.detect(input)?.let { command ->
            executeFastCommand(command)
            emit(AgentEvent.StateChanged(AgentState.IDLE))
            return
        }

        // 2-4. Language core path.
        val status = llm.status.value
        if (status is LlmStatus.Ready) {
            llmTurn(input)
        } else {
            degradedTurn(input)
        }
        emit(AgentEvent.StateChanged(AgentState.IDLE))
    }

    private suspend fun executeFastCommand(command: CommandFastPath.FastCommand) {
        if (command is CommandFastPath.FastCommand.Diagnostics) {
            runDiagnostics()
            return
        }

        val routed = routeFastCommand(command)
        if (routed == null) {
            finishTurn("I couldn't map that to an action, sir. Rephrase and I'll attend to it.")
            return
        }
        val (toolName, params) = routed

        emit(AgentEvent.StateChanged(AgentState.EXECUTING))
        emit(AgentEvent.ToolInvoked(toolName, params))
        val result = try {
            tools.execute(toolName, params)
        } catch (e: UnknownToolException) {
            com.jarvis.ai.core.tools.ToolResult(false, "That subsystem is not installed, sir.")
        } catch (t: Throwable) {
            Log.e(TAG, "Tool $toolName failed", t)
            com.jarvis.ai.core.tools.ToolResult(false, "The subsystem threw an exception, sir.")
        }
        emit(AgentEvent.ToolFinished(toolName, result.ok, result.spokenFallback))
        finishTurn(result.spokenFallback)
    }

    /** Maps a detected fast command onto the (tool, params) pair that executes it. */
    private fun routeFastCommand(
        command: CommandFastPath.FastCommand
    ): Pair<String, Map<String, String>>? = when (command) {
        is CommandFastPath.FastCommand.SetAlarm -> "set_alarm" to buildMap {
            put("hour", command.time.hour.toString())
            put("minute", command.time.minute.toString())
            command.label?.let { put("label", it) }
        }
        is CommandFastPath.FastCommand.SetTimer -> "start_timer" to buildMap {
            put("seconds", command.seconds.toString())
            command.label?.let { put("label", it) }
        }
        is CommandFastPath.FastCommand.SetReminder -> when {
            command.delaySeconds != null -> "set_reminder" to mapOf(
                "in_minutes" to ((command.delaySeconds / 60).coerceAtLeast(1)).toString(),
                "message" to (command.message ?: "Reminder")
            )
            command.time != null -> "set_reminder" to mapOf(
                "hour" to command.time.hour.toString(),
                "minute" to command.time.minute.toString(),
                "message" to (command.message ?: "Reminder")
            )
            else -> null
        }
        is CommandFastPath.FastCommand.Flashlight -> "flashlight" to mapOf(
            "state" to (command.on?.let { if (it) "on" else "off" } ?: "toggle")
        )
        is CommandFastPath.FastCommand.Weather -> "get_weather" to
            (command.city?.let { mapOf("city" to it) } ?: emptyMap())
        is CommandFastPath.FastCommand.Volume -> "set_volume" to buildMap {
            put("action", command.action.name.lowercase())
            command.levelPercent?.let { put("level", it.toString()) }
        }
        is CommandFastPath.FastCommand.Brightness -> "set_brightness" to buildMap {
            put("action", command.action.name.lowercase())
            command.levelPercent?.let { put("level", it.toString()) }
        }
        is CommandFastPath.FastCommand.LaunchApp -> "launch_app" to mapOf(
            "app_name" to command.appName
        )
        CommandFastPath.FastCommand.TimeQuery,
        CommandFastPath.FastCommand.DateQuery -> "current_time" to emptyMap()
        CommandFastPath.FastCommand.BatteryQuery -> "battery_status" to emptyMap()
        CommandFastPath.FastCommand.Briefing -> "morning_briefing" to emptyMap()
        CommandFastPath.FastCommand.KoreanLesson -> "korean_tutor" to emptyMap()
        CommandFastPath.FastCommand.Diagnostics -> null
    }

    private suspend fun llmTurn(input: String) {
        // Memory already contains this user turn (added in process()).
        val prompt = buildString {
            appendLine(PersonaPrompts.system(mode, tools.manifest()))
            if (memory.size > 0) {
                appendLine()
                appendLine("Conversation so far:")
                appendLine(memory.render())
            }
        }

        var buffer = ""
        var action: JsonActionParser.Action? = null
        llm.generateStream(prompt).collect { delta ->
            buffer += delta
            if (action == null) {
                action = JsonActionParser.parse(buffer)
            }
            emit(AgentEvent.Partial(buffer))
        }

        val finalAction = action ?: JsonActionParser.parse(buffer)
        if (finalAction != null) {
            executeToolCall(finalAction)
        } else {
            val reply = sanitize(buffer)
            if (reply.isNotEmpty()) {
                finishTurn(reply)
            } else {
                finishTurn("My thoughts went quiet mid-sentence, sir. Say that again?")
            }
        }
    }

    private suspend fun executeToolCall(action: JsonActionParser.Action) {
        emit(AgentEvent.StateChanged(AgentState.EXECUTING))
        emit(AgentEvent.ToolInvoked(action.tool, action.parameters))
        val result = try {
            tools.execute(action.tool, action.parameters)
        } catch (e: UnknownToolException) {
            Log.w(TAG, "Model invented tool: ${action.tool}")
            com.jarvis.ai.core.tools.ToolResult(
                false,
                "That capability is not installed, sir. I'll manage without it."
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Tool ${action.tool} failed", t)
            com.jarvis.ai.core.tools.ToolResult(false, "The subsystem failed to execute, sir.")
        }
        emit(AgentEvent.ToolFinished(action.tool, result.ok, result.spokenFallback))
        memory.addObservation("tool=${action.tool} ok=${result.ok} ${result.dataForLlm ?: ""}")

        if (result.dataForLlm != null && llm.status.value is LlmStatus.Ready) {
            // Synthesis pass: render the observation in persona.
            val synthPrompt = buildString {
                appendLine(PersonaPrompts.system(mode, tools.manifest()))
                appendLine()
                appendLine(PersonaPrompts.toolSynthesis(action.tool, action.parameters, result.dataForLlm))
            }
            var synthBuffer = ""
            try {
                llm.generateStream(synthPrompt).collect { delta ->
                    synthBuffer += delta
                    emit(AgentEvent.Partial(synthBuffer))
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Synthesis failed: ${t.message}")
            }
            val synth = sanitize(synthBuffer)
            finishTurn(synth.ifEmpty { result.spokenFallback })
        } else {
            finishTurn(result.spokenFallback)
        }
    }

    private suspend fun degradedTurn(input: String) {
        // Small-talk niceties handled without a model; otherwise report state.
        val lower = input.lowercase()
        val reply = when {
            Regex("""\b(hello|hi|hey|good (morning|afternoon|evening))\b""").containsMatchIn(lower) ->
                "Good day, sir. My conversational core is dormant — no language model is loaded — " +
                    "but the fast-action systems remain at your service: alarms, timers, torch, weather."
            Regex("""\b(who are you|what are you|your name)\b""").containsMatchIn(lower) ->
                "J.A.R.V.I.S., sir — your chief of staff. Currently running on reflexes alone; " +
                    "load a language model in Settings to restore my higher faculties."
            Regex("""\b(thank you|thanks)\b""").containsMatchIn(lower) ->
                "Always a pleasure, sir."
            else ->
                "My language core is offline, sir. Install a model via Settings — meanwhile I retain " +
                    "fast actions: alarms, timers, reminders, torch, volume, weather, and briefings."
        }
        finishTurn(reply)
    }

    private suspend fun runDiagnostics() {
        val llmLine = when (val s = llm.status.value) {
            is LlmStatus.Ready -> "language core online (${s.backend})"
            is LlmStatus.Loading -> "language core loading"
            is LlmStatus.Error -> "language core fault: ${s.message}"
            LlmStatus.Unloaded -> "language core not loaded"
        }
        val toolCount = tools.all().size
        val report = "Diagnostics, sir: $llmLine; $toolCount subsystems armed; " +
            "voice ${if (tts.speaking.value) "engaged" else "standing by"}."
        finishTurn(report)
    }

    private suspend fun finishTurn(text: String) {
        val clean = text.trim()
        if (clean.isNotEmpty()) {
            memory.addJarvis(clean)
            emit(AgentEvent.Completed(clean))
            tts.speak(clean)
        }
        emit(AgentEvent.StateChanged(AgentState.IDLE))
    }

    private fun sanitize(raw: String): String =
        raw.replace(Regex("```[\\s\\S]*?```"), "").trim()

    private fun emit(event: AgentEvent) {
        _events.tryEmit(event)
    }

    companion object {
        private const val TAG = "AgentOrchestrator"
    }
}
