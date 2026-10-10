package com.jarvis.ai.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "jarvis_settings")

/** Immutable snapshot of user preferences. */
data class StoredSettings(
    val modelPath: String,
    val wakeWordEnabled: Boolean,
    val picovoiceKey: String,
    val ttsEnabled: Boolean,
    val speechRate: Float,
    val voicePitch: Float,
    val city: String,
    val modeName: String
)

/**
 * DataStore-backed preference layer. Single source of truth for every
 * configurable behavior in the app.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val MODEL_PATH = stringPreferencesKey("model_path")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val PICOVOICE_KEY = stringPreferencesKey("picovoice_key")
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val VOICE_PITCH = floatPreferencesKey("voice_pitch")
        val CITY = stringPreferencesKey("city")
        val MODE = stringPreferencesKey("mode")
    }

    val modelPath: Flow<String> = context.dataStore.data.map { it[Keys.MODEL_PATH] ?: "" }
    val wakeWordEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.WAKE_WORD_ENABLED] ?: false }
    val picovoiceKey: Flow<String> = context.dataStore.data.map { it[Keys.PICOVOICE_KEY] ?: "" }
    val ttsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.TTS_ENABLED] ?: true }
    val speechRate: Flow<Float> = context.dataStore.data.map { it[Keys.SPEECH_RATE] ?: 1.0f }
    val voicePitch: Flow<Float> = context.dataStore.data.map { it[Keys.VOICE_PITCH] ?: 1.0f }
    val city: Flow<String> = context.dataStore.data.map { it[Keys.CITY] ?: "Seoul" }
    val modeName: Flow<String> = context.dataStore.data.map { it[Keys.MODE] ?: "HUD" }

    suspend fun snapshot(): StoredSettings = StoredSettings(
        modelPath = modelPath.first(),
        wakeWordEnabled = wakeWordEnabled.first(),
        picovoiceKey = picovoiceKey.first(),
        ttsEnabled = ttsEnabled.first(),
        speechRate = speechRate.first(),
        voicePitch = voicePitch.first(),
        city = city.first(),
        modeName = modeName.first()
    )

    suspend fun setModelPath(value: String) = edit { it[Keys.MODEL_PATH] = value }
    suspend fun setWakeWordEnabled(value: Boolean) = edit { it[Keys.WAKE_WORD_ENABLED] = value }
    suspend fun setPicovoiceKey(value: String) = edit { it[Keys.PICOVOICE_KEY] = value.trim() }
    suspend fun setTtsEnabled(value: Boolean) = edit { it[Keys.TTS_ENABLED] = value }
    suspend fun setSpeechRate(value: Float) = edit { it[Keys.SPEECH_RATE] = value }
    suspend fun setVoicePitch(value: Float) = edit { it[Keys.VOICE_PITCH] = value }
    suspend fun setCity(value: String) = edit { it[Keys.CITY] = value.trim() }
    suspend fun setModeName(value: String) = edit { it[Keys.MODE] = value }

    /** One-shot read for tool contexts that don't want a Flow. */
    suspend fun cityOnce(): String? = city.first().takeIf { it.isNotBlank() }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }
}
