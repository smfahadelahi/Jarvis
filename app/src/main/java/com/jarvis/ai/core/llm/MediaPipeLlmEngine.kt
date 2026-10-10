package com.jarvis.ai.core.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * MediaPipe GenAI (Google AI Edge) implementation of [LlmEngine].
 *
 * API surface verified against Google's reference sample:
 *  - engine:  LlmInference.LlmInferenceOptions.builder()...createFromOptions()
 *  - turn:    LlmInferenceSession + addQueryChunk + generateResponseAsync(listener)
 *  - partials are INCREMENTAL deltas (the reference app appends each callback).
 *
 * GPU backend is attempted first; on failure the engine silently falls back
 * to CPU so the butler still works on modest silicon.
 */
class MediaPipeLlmEngine(private val context: Context) : LlmEngine {

    private val _status = MutableStateFlow<LlmStatus>(LlmStatus.Unloaded)
    override val status: StateFlow<LlmStatus> = _status

    private val engine = AtomicReference<LlmInference?>(null)
    private val turnMutex = Mutex()

    @Volatile
    private var loadedPath: String? = null

    override suspend fun load(modelPath: String) {
        if (modelPath.isBlank()) {
            _status.value = LlmStatus.Unloaded
            return
        }
        val file = File(modelPath)
        if (!file.exists() || file.length() < MIN_MODEL_BYTES) {
            _status.value = LlmStatus.Error("Model file missing or too small: $modelPath")
            return
        }
        _status.value = LlmStatus.Loading
        close()
        loadedPath = modelPath

        val result: Pair<Boolean, String> = withContext(Dispatchers.Default) {
            try {
                engine.set(createEngine(modelPath, preferGpu = true))
                Pair(true, BACKEND_GPU)
            } catch (gpuFailure: Throwable) {
                Log.w(TAG, "GPU load failed (${gpuFailure.message}); retrying on CPU")
                try {
                    engine.set(createEngine(modelPath, preferGpu = false))
                    Pair(true, BACKEND_CPU)
                } catch (cpuFailure: Throwable) {
                    Log.e(TAG, "CPU load failed: ${cpuFailure.message}")
                    Pair(false, cpuFailure.message ?: "unknown error")
                }
            }
        }

        _status.value = if (result.first) {
            LlmStatus.Ready("${file.nameWithoutExtension} · ${result.second}")
        } else {
            LlmStatus.Error(result.second)
        }
    }

    private fun createEngine(path: String, preferGpu: Boolean): LlmInference {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(MAX_TOKENS)
            .apply {
                if (preferGpu) setPreferredBackend(LlmInference.Backend.GPU)
            }
            .build()
        return LlmInference.createFromOptions(context, options)
    }

    override fun generateStream(prompt: String): Flow<String> = callbackFlow {
        val current = engine.get()
        if (current == null) {
            trySend("")
            close()
            return@callbackFlow
        }

        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTemperature(TEMPERATURE)
            .setTopK(TOP_K)
            .setTopP(TOP_P)
            .setRandomSeed(RANDOM_SEED)
            .build()

        val listener = ProgressListener<String> { partial, done ->
            if (partial.isNotEmpty()) trySend(partial)
            if (done) close()
        }

        try {
            val session = LlmInferenceSession.createFromOptions(current, sessionOptions)
            session.addQueryChunk(prompt)
            val future = session.generateResponseAsync(listener)
            awaitClose {
                try {
                    future.cancel(true)
                } catch (_: Exception) {
                    // Generation already terminal.
                }
                try {
                    session.close()
                } catch (_: Exception) {
                    // Session already closed.
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Generation failed: ${t.message}")
            close()
        }
    }

    /** Serializes one full turn (generation + synthesis) at a time. */
    suspend fun <T> withEngineLock(block: suspend () -> T): T =
        turnMutex.withLock { block() }

    override fun close() {
        engine.getAndSet(null)?.let { current ->
            try {
                current.close()
            } catch (t: Throwable) {
                Log.w(TAG, "Engine close failed: ${t.message}")
            }
        }
    }

    fun isLoadedFor(path: String): Boolean =
        loadedPath == path && engine.get() != null

    companion object {
        private const val TAG = "MediaPipeLlmEngine"
        private const val MIN_MODEL_BYTES = 1_000_000L
        private const val MAX_TOKENS = 1024
        private const val TEMPERATURE = 0.6f
        private const val TOP_K = 40
        private const val TOP_P = 0.95f
        private const val RANDOM_SEED = 7
        private const val BACKEND_GPU = "GPU"
        private const val BACKEND_CPU = "CPU"
    }
}
