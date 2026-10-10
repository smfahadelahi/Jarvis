package com.jarvis.ai.core.asr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Streaming speech-to-text built on the platform [SpeechRecognizer].
 *
 * On Android 12+ the platform recognizer runs on-device for supported
 * locales when EXTRA_PREFER_OFFLINE is set, keeping the entire voice loop
 * inside the handset.
 */
class AndroidSpeechRecognizer(private val context: Context) {

    sealed interface AsrEvent {
        data object Listening : AsrEvent
        data class Partial(val text: String) : AsrEvent
        data class Final(val text: String) : AsrEvent
        data class Failed(val code: Int, val message: String) : AsrEvent
    }

    private val _events = MutableSharedFlow<AsrEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<AsrEvent> = _events

    private val mainHandler = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(languageTag: String = "en-US") {
        if (!isAvailable) {
            _events.tryEmit(AsrEvent.Failed(ERROR_CLIENT, "Speech recognition unavailable on this device."))
            return
        }
        mainHandler.post {
            destroyRecognizer()
            val sr = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = sr
            sr.setRecognitionListener(listener)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    1
                )
            }
            sr.startListening(intent)
        }
    }

    fun stop() {
        mainHandler.post {
            recognizer?.stopListening()
        }
    }

    fun shutdown() {
        mainHandler.post { destroyRecognizer() }
    }

    private fun destroyRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _events.tryEmit(AsrEvent.Listening)
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            val message = describeError(error)
            _events.tryEmit(AsrEvent.Failed(error, message))
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) {
                _events.tryEmit(AsrEvent.Final(text))
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) {
                _events.tryEmit(AsrEvent.Partial(text))
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun describeError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio capture failed, sir."
        SpeechRecognizer.ERROR_CLIENT -> "Recognition client error."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing."
        SpeechRecognizer.ERROR_NETWORK -> "Recognition network error."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Recognition timed out."
        SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that, sir."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition engine busy."
        SpeechRecognizer.ERROR_SERVER -> "Recognition server error."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
        else -> "Recognition error $code."
    }
}
