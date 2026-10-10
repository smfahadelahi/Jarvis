package com.jarvis.ai.ui.settings

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvis.ai.service.WakeWordService
import com.jarvis.ai.ui.theme.HudMono
import com.jarvis.ai.ui.theme.HudStatusStyle
import com.jarvis.ai.ui.theme.JarvisBackground
import com.jarvis.ai.ui.theme.JarvisPrimary
import com.jarvis.ai.ui.theme.JarvisSecondary
import com.jarvis.ai.ui.theme.JarvisTextDim

/**
 * Configuration surface: language core, voice, wake word, persona.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importModel(context, uri)
    }

    // Toast surfacing.
    state.toast?.let { message ->
        LaunchedEffect(message) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            viewModel.consumeToast()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = JarvisPrimary)
            }
            Text(
                text = "CONFIGURATION",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = HudMono),
                color = JarvisPrimary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Language core ──────────────────────────────────────────────────
        SettingsSection("LANGUAGE CORE")
        Text(
            text = state.modelStatusLine,
            style = HudStatusStyle,
            color = JarvisPrimary
        )
        Text(
            text = state.settings.modelPath.ifBlank { "No model registered." },
            style = MaterialTheme.typography.bodySmall,
            color = JarvisTextDim
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { modelPicker.launch(arrayOf("*/*")) },
                enabled = !state.importing,
                colors = ButtonDefaults.buttonColors(containerColor = JarvisPrimary)
            ) {
                Text("Import model", color = Color(0xFF00131A))
            }
            OutlinedButton(onClick = { viewModel.reloadModel() }) {
                Text("Reload", color = JarvisPrimary)
            }
        }
        if (state.importing) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(state.importProgress ?: "Working…", color = JarvisSecondary, style = HudStatusStyle)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Recommended: Gemma-3 1B IT, 4-bit (task bundle). See README for the download link.",
            style = MaterialTheme.typography.bodySmall,
            color = JarvisTextDim
        )

        SectionDivider()

        // ── Voice ──────────────────────────────────────────────────────────
        SettingsSection("VOICE OUTPUT")
        SettingSwitch(
            label = "Speak replies aloud",
            checked = state.settings.ttsEnabled,
            onCheckedChange = { viewModel.setTtsEnabled(it) }
        )
        SettingSlider(
            label = "Speech rate",
            value = state.settings.speechRate,
            onValueChange = { viewModel.setSpeechRate(it) }
        )
        SettingSlider(
            label = "Voice pitch",
            value = state.settings.voicePitch,
            onValueChange = { viewModel.setVoicePitch(it) }
        )

        SectionDivider()

        // ── Wake word ──────────────────────────────────────────────────────
        SettingsSection("WAKE WORD")
        SettingSwitch(
            label = "Listen for “Jarvis”",
            checked = state.settings.wakeWordEnabled,
            onCheckedChange = { enabled ->
                viewModel.setWakeWordEnabled(enabled)
                if (enabled && state.settings.picovoiceKey.isNotBlank()) {
                    WakeWordService.start(context)
                } else {
                    WakeWordService.stop(context)
                }
            }
        )
        var keyField by remember(state.settings.picovoiceKey) {
            mutableStateOf(state.settings.picovoiceKey)
        }
        OutlinedTextField(
            value = keyField,
            onValueChange = { keyField = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Picovoice AccessKey", color = JarvisTextDim) },
            placeholder = { Text("Free key at console.picovoice.ai", color = JarvisTextDim.copy(alpha = 0.5f)) },
            singleLine = true,
            trailingIcon = {
                if (keyField != state.settings.picovoiceKey) {
                    Text(
                        "SAVE",
                        style = HudStatusStyle,
                        color = JarvisSecondary,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clickableText { viewModel.setPicovoiceKey(keyField) }
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisPrimary.copy(alpha = 0.6f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        Text(
            text = "The built-in keyword “Jarvis” runs fully on-device. Requires RECORD_AUDIO; a persistent notification appears while listening.",
            style = MaterialTheme.typography.bodySmall,
            color = JarvisTextDim
        )

        SectionDivider()

        // ── Environment ────────────────────────────────────────────────────
        SettingsSection("ENVIRONMENT")
        var cityField by remember(state.settings.city) { mutableStateOf(state.settings.city) }
        OutlinedTextField(
            value = cityField,
            onValueChange = { cityField = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Default city (weather & briefing)", color = JarvisTextDim) },
            singleLine = true,
            trailingIcon = {
                if (cityField != state.settings.city) {
                    Text(
                        "SAVE",
                        style = HudStatusStyle,
                        color = JarvisSecondary,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clickableText { viewModel.setCity(cityField) }
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisPrimary.copy(alpha = 0.6f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        SectionDivider()

        // ── Permissions ────────────────────────────────────────────────────
        SettingsSection("PERMISSIONS")
        OutlinedButton(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Exact alarm permission", color = JarvisPrimary)
        }
        Text(
            text = exactAlarmState(context),
            style = MaterialTheme.typography.bodySmall,
            color = JarvisTextDim
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun exactAlarmState(context: Context): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return "Exact alarms available."
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return if (am.canScheduleExactAlarms()) "Exact alarms granted."
    else "Exact alarms NOT granted — alarms may drift a few minutes."
}

/** Inline clickable SAVE label. */
private fun Modifier.clickableText(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.pointerInputCompat(onClick)
    )

private fun Modifier.pointerInputCompat(onClick: () -> Unit): Modifier =
    this.then(
        androidx.compose.ui.Modifier.let { base ->
            base.then(
                androidx.compose.foundation.clickable(
                    interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                    indication = null,
                    onClick = onClick
                ).let { clickableModifier -> base.then(clickableModifier).let { this.then(clickableModifier) } }
            )
        }
    )

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        style = HudStatusStyle,
        color = JarvisTextDim,
        modifier = Modifier.padding(vertical = 10.dp)
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 18.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    )
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = JarvisPrimary,
                checkedThumbColor = Color(0xFF00131A)
            )
        )
    }
}

@Composable
private fun SettingSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = String.format("%.2fx", value),
                style = HudStatusStyle,
                color = JarvisSecondary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0.5f..1.6f,
            colors = SliderDefaults.colors(
                thumbColor = JarvisPrimary,
                activeTrackColor = JarvisPrimary.copy(alpha = 0.7f)
            )
        )
    }
}
