package com.jarvis.ai.core.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over the on-device language core.
 *
 * The HUD, orchestrator, and tools depend on this interface only, so the
 * runtime backend (MediaPipe GenAI today, llama.cpp or LiteRT-LM tomorrow)
 * can be swapped without touching the rest of the stack.
 */
interface LlmEngine {

    /** Current lifecycle status of the engine. */
    val status: StateFlow<LlmStatus>

    /**
     * Loads a model from an absolute file path. Safe to call repeatedly;
     * replaces any previously loaded model.
     */
    suspend fun load(modelPath: String)

    /**
     * Streams a generation for [prompt]. Emits incremental text deltas and
     * completes when generation finishes.
     */
    fun generateStream(prompt: String): Flow<String>

    /** Releases native resources. */
    fun close()
}

sealed interface LlmStatus {
    /** No model loaded yet. */
    data object Unloaded : LlmStatus

    /** Model loading in progress. */
    data object Loading : LlmStatus

    /** Ready for inference. */
    data class Ready(val backend: String) : LlmStatus

    /** Load or inference failure; [message] is human-readable. */
    data class Error(val message: String) : LlmStatus
}
