package com.jarvis.ai.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.ai.data.StoredSettings
import com.jarvis.ai.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SettingsUiState(
    val settings: StoredSettings = StoredSettings(
        modelPath = "",
        wakeWordEnabled = false,
        picovoiceKey = "",
        ttsEnabled = true,
        speechRate = 1.0f,
        voicePitch = 1.0f,
        city = "Seoul",
        modeName = "HUD"
    ),
    val modelStatusLine: String = "LANGUAGE CORE — NOT LOADED",
    val importing: Boolean = false,
    val importProgress: String? = null,
    val toast: String? = null
)

/**
 * Settings state holder. Persists via [com.jarvis.ai.data.SettingsRepository]
 * and applies live changes to the engines.
 */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val settings = container.settings

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(settings = settings.snapshot()) }
        }
        viewModelScope.launch {
            container.llmEngine.status.collect { status ->
                val line = when (status) {
                    is com.jarvis.ai.core.llm.LlmStatus.Unloaded -> "LANGUAGE CORE — NOT LOADED"
                    is com.jarvis.ai.core.llm.LlmStatus.Loading -> "LANGUAGE CORE — LOADING…"
                    is com.jarvis.ai.core.llm.LlmStatus.Ready -> "CORE ONLINE · ${status.backend.uppercase()}"
                    is com.jarvis.ai.core.llm.LlmStatus.Error -> "CORE FAULT — ${status.message.take(48)}"
                }
                _uiState.update { it.copy(modelStatusLine = line) }
            }
        }
    }

    fun setTtsEnabled(enabled: Boolean) = launchSetting { settings.setTtsEnabled(enabled) }

    fun setSpeechRate(rate: Float) = launchSetting { settings.setSpeechRate(rate) }

    fun setVoicePitch(pitch: Float) = launchSetting { settings.setVoicePitch(pitch) }

    fun setWakeWordEnabled(enabled: Boolean) = launchSetting {
        settings.setWakeWordEnabled(enabled)
    }

    fun setPicovoiceKey(key: String) = launchSetting { settings.setPicovoiceKey(key) }

    fun setCity(city: String) = launchSetting { settings.setCity(city) }

    /**
     * Copies a picked model file into private storage and registers it.
     * Large files (~600MB-1GB for Gemma-3 1B int4) — progress is surfaced.
     */
    fun importModel(context: Context, uri: Uri) {
        _uiState.update { it.copy(importing = true, importProgress = "Copying model…") }
        viewModelScope.launch {
            try {
                val target = withContext(Dispatchers.IO) {
                    val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
                    val outFile = File(modelsDir, "gemma3-1b-it-int4.task")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        outFile.outputStream().use { output ->
                            val buffer = ByteArray(1 shl 20)
                            var copied = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                copied += read
                                if (copied % (128L shl 20) < (1 shl 20)) {
                                    _uiState.update {
                                        it.copy(importProgress = "Copied ${copied / (1 shl 20)} MB…")
                                    }
                                }
                            }
                        }
                    } ?: throw IllegalStateException("Cannot open selected file")
                    outFile.absolutePath
                }
                settings.setModelPath(target)
                _uiState.update { state ->
                    state.copy(
                        importing = false,
                        importProgress = null,
                        toast = "Model imported. Loading…",
                        settings = state.settings.copy(modelPath = target)
                    )
                }
                reloadModel()
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(importing = false, importProgress = null, toast = "Import failed: ${t.message}")
                }
            }
        }
    }

    fun reloadModel() {
        viewModelScope.launch {
            val path = settings.modelPath.firstOrNull() ?: return@launch
            if (path.isNotBlank()) container.llmEngine.load(path)
        }
    }

    fun consumeToast() {
        _uiState.update { it.copy(toast = null) }
    }

    private fun launchSetting(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            _uiState.update { it.copy(settings = settings.snapshot()) }
        }
    }
}
