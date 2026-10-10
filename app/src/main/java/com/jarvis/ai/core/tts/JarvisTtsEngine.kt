package com.jarvis.ai.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Voice output layer. Splits streamed text into sentences and queues them so
 * speech starts before the full response is generated.
 *
 * Prefers an en-GB voice to honor the butler aesthetic; degrades gracefully
 * to any English voice, then to the device default.
 */
class JarvisTtsEngine(context: Context) {

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking

    private var tts: TextToSpeech? = null
    private var ready = false
    private var enabled = true

    @Volatile
    private var speechRate: Float = 1.0f

    @Volatile
    private var pitch: Float = 1.0f

    private val pending = AtomicInteger(0)

    private val initListener = TextToSpeech.OnInitListener { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            selectVoice()
            applySpeechParams()
        }
    }

    init {
        tts = TextToSpeech(context.applicationContext, initListener)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (pending.decrementAndGet() <= 0) {
                    _speaking.value = false
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (pending.decrementAndGet() <= 0) {
                    _speaking.value = false
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (pending.decrementAndGet() <= 0) {
                    _speaking.value = false
                }
            }
        })
    }

    fun configure(enabled: Boolean, rate: Float, pitch: Float) {
        this.enabled = enabled
        this.speechRate = rate.coerceIn(0.5f, 2.0f)
        this.pitch = pitch.coerceIn(0.5f, 2.0f)
        applySpeechParams()
        if (!enabled) stop()
    }

    /** Speaks [text], sentence by sentence, in order. */
    fun speak(text: String) {
        if (!enabled || !ready) return
        val clean = text
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("[*_#`|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (clean.isEmpty()) return

        val sentences = splitIntoSentences(clean)
        if (sentences.isEmpty()) return

        _speaking.value = true
        for (sentence in sentences) {
            pending.incrementAndGet()
            tts?.speak(sentence, TextToSpeech.QUEUE_ADD, null, "jarvis-${System.nanoTime()}")
        }
    }

    fun stop() {
        pending.set(0)
        _speaking.value = false
        tts?.stop()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun applySpeechParams() {
        tts?.setSpeechRate(speechRate)
        tts?.setPitch(pitch)
    }

    private fun selectVoice() {
        val english = try {
            tts?.setLanguage(Locale.UK)
        } catch (_: Exception) {
            null
        }
        if (english == TextToSpeech.LANG_MISSING_DATA || english == TextToSpeech.LANG_NOT_SUPPORTED) {
            try {
                tts?.setLanguage(Locale.US)
            } catch (_: Exception) {
                // Engine default it is.
            }
        }
    }

    private fun splitIntoSentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
