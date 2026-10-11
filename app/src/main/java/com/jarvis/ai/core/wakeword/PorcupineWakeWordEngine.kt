package com.jarvis.ai.core.wakeword

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Wake-word detection abstraction.
 *
 * [PorcupineWakeWordEngine] listens continuously for the built-in "Jarvis"
 * keyword using Picovoice Porcupine (fully on-device, ~2MB resident, low
 * battery cost). When no Picovoice access key is configured, callers simply
 * use tap-to-talk and this engine is never constructed.
 */
class PorcupineWakeWordEngine(
    private val context: Context,
    private val accessKey: String,
    private val sensitivity: Float = 0.7f
) {

    private val _detections = MutableSharedFlow<Unit>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val detections: SharedFlow<Unit> = _detections

    private var manager: PorcupineManager? = null

    val isRunning: Boolean
        get() = manager != null

    @Synchronized
    fun start() {
        if (manager != null || accessKey.isBlank()) return
        try {
            manager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                .setSensitivity(sensitivity)
                .build(context, PorcupineManagerCallback { _detections.tryEmit(Unit) })
            manager?.start()
            Log.i(TAG, "Wake word engine started (keyword=jarvis)")
        } catch (e: PorcupineException) {
            Log.e(TAG, "Porcupine init failed: ${e.message}")
            manager = null
            throw e
        }
    }

    @Synchronized
    fun stop() {
        try {
            manager?.stop()
            manager?.delete()
        } catch (t: Throwable) {
            Log.w(TAG, "Porcupine stop failed: ${t.message}")
        } finally {
            manager = null
        }
    }

    companion object {
        private const val TAG = "WakeWord"
    }
}
