package com.jarvis.ai.di

import android.content.Context
import com.jarvis.ai.core.agent.AgentOrchestrator
import com.jarvis.ai.core.agent.ConversationMemory
import com.jarvis.ai.core.asr.AndroidSpeechRecognizer
import com.jarvis.ai.core.llm.LlmEngine
import com.jarvis.ai.core.llm.MediaPipeLlmEngine
import com.jarvis.ai.core.services.WeatherService
import com.jarvis.ai.core.tools.ToolRegistry
import com.jarvis.ai.core.tools.Toolbox
import com.jarvis.ai.core.tts.JarvisTtsEngine
import com.jarvis.ai.data.SettingsRepository
import com.jarvis.ai.scheduling.AlarmScheduler
import com.jarvis.ai.scheduling.PersistedAlarms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/**
 * Hand-rolled dependency container. One instance per process, owned by
 * [com.jarvis.ai.JarvisApplication]. Deliberately not Hilt: the graph is
 * small, explicit, and free of annotation-processor build cost.
 */
class AppContainer(private val appContext: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings by lazy { SettingsRepository(appContext) }

    val llmEngine: LlmEngine by lazy { MediaPipeLlmEngine(appContext) }

    val tts by lazy { JarvisTtsEngine(appContext) }

    val speech by lazy { AndroidSpeechRecognizer(appContext) }

    val weather by lazy { WeatherService(appContext) }

    val alarmStore by lazy { PersistedAlarms(appContext) }

    val alarmScheduler by lazy { AlarmScheduler(appContext, alarmStore) }

    val toolRegistry: ToolRegistry by lazy { Toolbox.buildDefault(appContext, this) }

    val memory by lazy { ConversationMemory() }

    /** Wake-word pings from the background listener. */
    val wakeWordEvents = MutableSharedFlow<Unit>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val orchestrator: AgentOrchestrator by lazy {
        AgentOrchestrator(
            llm = llmEngine,
            tools = toolRegistry,
            memory = memory,
            tts = tts,
            settings = settings
        )
    }

    /** Applies TTS preferences to the engine; call on startup and on change. */
    fun applyVoiceSettings() {
        appScope.launch {
            val s = settings.snapshot()
            tts.configure(s.ttsEnabled, s.speechRate, s.voicePitch)
        }
    }

    /** Loads the configured model if one is set; safe to call repeatedly. */
    fun loadModelIfConfigured() {
        appScope.launch {
            val path = settings.modelPath.first()
            if (path.isNotBlank() && llmEngine.status.value !is com.jarvis.ai.core.llm.LlmStatus.Ready) {
                llmEngine.load(path)
            }
        }
    }
}

/** Process-wide accessor. */
private lateinit var globalContainer: AppContainer

fun initAppContainer(context: Context) {
    if (!::globalContainer.isInitialized) {
        globalContainer = AppContainer(context.applicationContext)
    }
}

fun Context.appContainer(): AppContainer {
    check(::globalContainer.isInitialized) {
        "AppContainer not initialized — JarvisApplication must run first."
    }
    return globalContainer
}
