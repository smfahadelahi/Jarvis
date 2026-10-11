package com.jarvis.ai.ui.hud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.ai.core.AgentEvent
import com.jarvis.ai.core.AgentMode
import com.jarvis.ai.core.AgentState
import com.jarvis.ai.core.ChatMessage
import com.jarvis.ai.core.asr.AndroidSpeechRecognizer
import com.jarvis.ai.data.SettingsRepository
import com.jarvis.ai.di.AppContainer
import com.jarvis.ai.core.llm.LlmStatus
import com.jarvis.ai.core.tts.JarvisTtsEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HudUiState(
    val messages: List<ChatMessage> = emptyList(),
    val partial: String? = null,
    val agentState: AgentState = AgentState.IDLE,
    val mode: AgentMode = AgentMode.HUD,
    val modelLine: String = "LANGUAGE CORE — NOT LOADED",
    val modelReady: Boolean = false,
    val listening: Boolean = false,
    val micAvailable: Boolean = true,
    val hint: String? = null
)

/**
 * HUD state machine. Owns the voice loop:
 *   orb press → ASR → transcript → orchestrator → streaming reply → TTS.
 */
class HudViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val settings: SettingsRepository = container.settings
    private val asr: AndroidSpeechRecognizer = container.speech
    private val tts: JarvisTtsEngine = container.tts
    private val orchestrator = container.orchestrator

    private val _uiState = MutableStateFlow(
        HudUiState(
            messages = listOf(
                ChatMessage(
                    role = ChatMessage.Role.JARVIS,
                    text = "Systems online, sir. Awaiting your instructions."
                )
            )
        )
    )
    val uiState: StateFlow<HudUiState> = _uiState

    init {
        observeOrchestrator()
        observeSpeech()
        observeModelStatus()
        observeTts()
        observeWakeWord()
        refreshVoiceConfig()
    }

    // ── Public intents ─────────────────────────────────────────────────────

    fun onOrbPressed() {
        val state = _uiState.value
        when {
            state.agentState == AgentState.SPEAKING -> tts.stop()
            state.listening -> asr.stop()
            else -> startListening()
        }
    }

    fun onTextSubmitted(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        appendMessage(ChatMessage(role = ChatMessage.Role.USER, text = clean))
        viewModelScope.launch { orchestrator.handleUserInput(clean) }
    }

    fun setMode(mode: AgentMode) {
        orchestrator.setMode(mode)
        _uiState.update { it.copy(mode = mode) }
        viewModelScope.launch { settings.setModeName(mode.name) }
    }

    fun quickAction(action: QuickAction) {
        onTextSubmitted(action.command)
    }

    fun micPermissionGranted() {
        _uiState.update { it.copy(micAvailable = true) }
        refreshVoiceConfig()
    }

    fun startListeningIfIdle() {
        if (_uiState.value.agentState == AgentState.IDLE && !_uiState.value.listening) {
            startListening()
        }
    }

    // ── Voice loop ─────────────────────────────────────────────────────────

    private fun startListening() {
        if (!_uiState.value.micAvailable) {
            _uiState.update { it.copy(hint = "Microphone permission required, sir.") }
            return
        }
        if (!asr.isAvailable) {
            _uiState.update { it.copy(hint = "No speech recognition service on this device, sir.") }
            return
        }
        _uiState.update { it.copy(listening = true, agentState = AgentState.LISTENING, partial = null) }
        asr.start()
    }

    private fun stopListening() {
        _uiState.update { it.copy(listening = false, partial = null) }
        if (_uiState.value.agentState == AgentState.LISTENING) {
            _uiState.update { it.copy(agentState = AgentState.IDLE) }
        }
    }

    // ── Observers ──────────────────────────────────────────────────────────

    private fun observeOrchestrator() {
        viewModelScope.launch {
            orchestrator.events.collect { event ->
                when (event) {
                    is AgentEvent.StateChanged -> _uiState.update {
                        it.copy(agentState = event.state)
                    }
                    is AgentEvent.Partial -> _uiState.update { it.copy(partial = event.text) }
                    is AgentEvent.Completed -> _uiState.update { state ->
                        state.copy(
                            messages = state.messages + ChatMessage(
                                role = ChatMessage.Role.JARVIS,
                                text = event.text
                            ),
                            partial = null
                        )
                    }
                    is AgentEvent.ToolInvoked -> _uiState.update { state ->
                        state.copy(
                            messages = state.messages + ChatMessage(
                                role = ChatMessage.Role.SYSTEM,
                                text = "▸ ${event.toolName}(${event.params.entries.joinToString { "${it.key}=${it.value}" }})"
                            )
                        )
                    }
                    is AgentEvent.ToolFinished -> Unit // surfaced via Completed text
                    is AgentEvent.Failure -> _uiState.update { state ->
                        state.copy(
                            messages = state.messages + ChatMessage(
                                role = ChatMessage.Role.JARVIS,
                                text = event.message
                            ),
                            partial = null
                        )
                    }
                }
            }
        }
    }

    private fun observeSpeech() {
        viewModelScope.launch {
            asr.events.collect { event ->
                when (event) {
                    is AndroidSpeechRecognizer.AsrEvent.Listening -> Unit
                    is AndroidSpeechRecognizer.AsrEvent.Partial ->
                        _uiState.update { it.copy(partial = event.text) }
                    is AndroidSpeechRecognizer.AsrEvent.Final -> {
                        _uiState.update { it.copy(listening = false, partial = null) }
                        onTextSubmitted(event.text)
                    }
                    is AndroidSpeechRecognizer.AsrEvent.Failed -> {
                        stopListening()
                        _uiState.update { it.copy(hint = event.message) }
                    }
                }
            }
        }
    }

    private fun observeModelStatus() {
        viewModelScope.launch {
            container.llmEngine.status.collect { status ->
                when (status) {
                    is LlmStatus.Unloaded -> _uiState.update {
                        it.copy(modelLine = "LANGUAGE CORE — NOT LOADED", modelReady = false)
                    }
                    is LlmStatus.Loading -> _uiState.update {
                        it.copy(modelLine = "LANGUAGE CORE — LOADING…", modelReady = false)
                    }
                    is LlmStatus.Ready -> _uiState.update {
                        it.copy(modelLine = "CORE ONLINE · ${status.backend.uppercase()}", modelReady = true)
                    }
                    is LlmStatus.Error -> _uiState.update {
                        it.copy(modelLine = "CORE FAULT — ${status.message.take(32)}", modelReady = false)
                    }
                }
            }
        }
        // Restore saved mode preference.
        viewModelScope.launch {
            val saved = settings.modeName.first()
            val mode = AgentMode.fromName(saved)
            orchestrator.setMode(mode)
            _uiState.update { it.copy(mode = mode) }
        }
    }

    private fun observeTts() {
        viewModelScope.launch {
            tts.speaking.collect { speaking ->
                if (speaking) {
                    _uiState.update {
                        if (it.agentState != AgentState.LISTENING) {
                            it.copy(agentState = AgentState.SPEAKING)
                        } else it
                    }
                } else {
                    _uiState.update {
                        if (it.agentState == AgentState.SPEAKING) it.copy(agentState = AgentState.IDLE) else it
                    }
                }
            }
        }
    }

    private fun observeWakeWord() {
        viewModelScope.launch {
            container.wakeWordEvents.collect {
                startListeningIfIdle()
            }
        }
    }

    private fun refreshVoiceConfig() {
        viewModelScope.launch {
            val s = settings.snapshot()
            tts.configure(s.ttsEnabled, s.speechRate, s.voicePitch)
        }
    }

    private fun appendMessage(message: ChatMessage) {
        _uiState.update { it.copy(messages = it.messages + message) }
    }

    enum class QuickAction(val label: String, val command: String) {
        BRIEFING("Briefing", "Prepare my briefing."),
        WEATHER("Weather", "What's the weather?"),
        ALARMS("Schedule", "List my alarms."),
        KOREAN("Korean Lesson", "Give me a Korean lesson."),
        DIAGNOSTICS("Diagnostics", "Run diagnostics.")
    }
}
