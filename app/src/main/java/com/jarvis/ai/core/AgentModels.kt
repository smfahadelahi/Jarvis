package com.jarvis.ai.core

/**
 * Canonical model types shared across the agent stack.
 */

/** Jarvis' three operating postures. */
enum class AgentMode(val label: String, val description: String) {
    HUD("HUD", "Rapid briefings, device control, and quick status checks. Answers stay short."),
    TACTICAL("TACTICAL", "Deep analysis, engineering, planning. Structured, rigorous output."),
    AUTONOMOUS("AUTONOMOUS", "Proactive execution. Anticipate follow-up needs and act decisively.");

    companion object {
        fun fromName(raw: String?): AgentMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: HUD
    }
}

/** What Jarvis is physically doing right now. Drives the HUD orb. */
enum class AgentState {
    IDLE,
    LISTENING,
    THINKING,
    EXECUTING,
    SPEAKING,
    ERROR
}

data class ChatMessage(
    val id: Long = System.nanoTime(),
    val role: Role,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
) {
    enum class Role { USER, JARVIS, SYSTEM }
}

/** Events emitted by the orchestrator while processing a turn. */
sealed interface AgentEvent {
    data class StateChanged(val state: AgentState) : AgentEvent
    data class Partial(val text: String) : AgentEvent
    data class Completed(val text: String) : AgentEvent
    data class ToolInvoked(val toolName: String, val params: Map<String, String>) : AgentEvent
    data class ToolFinished(val toolName: String, val ok: Boolean, val summary: String) : AgentEvent
    data class Failure(val message: String) : AgentEvent
}
