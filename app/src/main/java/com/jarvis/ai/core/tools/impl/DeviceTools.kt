package com.jarvis.ai.core.tools.impl

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.provider.Settings
import com.jarvis.ai.core.tools.ParamSpec
import com.jarvis.ai.core.tools.Tool
import com.jarvis.ai.core.tools.ToolRegistry
import com.jarvis.ai.core.tools.ToolResult
import com.jarvis.ai.di.AppContainer

/**
 * Device hardware controls: torch, volume, brightness, system panels,
 * battery telemetry.
 */
object DeviceTools {

    fun register(context: Context, container: AppContainer, registry: ToolRegistry) {
        registry.register(FlashlightTool(context))
        registry.register(VolumeTool(context))
        registry.register(BrightnessTool(context))
        registry.register(OpenSettingsPanelTool(context))
        registry.register(BatteryStatusTool(context))
    }
}

/** Tracks torch state across the process; camera manager gives no getter. */
internal object TorchController {
    @Volatile
    var isOn: Boolean = false
        private set

    private var cameraId: String? = null

    fun attach(context: Context) {
        if (cameraId != null) return
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        try {
            manager.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(id: String, enabled: Boolean) {
                    if (id == cameraId) isOn = enabled
                }
            }, null)
        } catch (_: Exception) {
            // State tracking degrades to toggle-only.
        }
    }

    fun set(context: Context, on: Boolean): Boolean {
        attach(context)
        val id = cameraId ?: return false
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            manager.setTorchMode(id, on)
            isOn = on
            true
        } catch (_: Exception) {
            false
        }
    }
}

internal class FlashlightTool(private val context: Context) : Tool {
    override val name = "flashlight"
    override val description = "Turn the flashlight on or off. Params: state (string): on, off, or toggle."
    override val parameters = listOf(
        ParamSpec("state", "string", required = false, description = "on | off | toggle, default toggle")
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val state = params["state"]?.trim()?.lowercase() ?: "toggle"
        val target = when (state) {
            "on", "true", "1" -> true
            "off", "false", "0" -> false
            else -> !TorchController.isOn
        }
        return if (TorchController.set(context, target)) {
            ToolResult(true, if (target) "Torch on, sir." else "Torch off, sir.")
        } else {
            ToolResult(false, "This handset has no controllable flash unit, sir.")
        }
    }
}

internal class VolumeTool(private val context: Context) : Tool {
    override val name = "set_volume"
    override val description = "Control media volume. Params: action (string): up, down, mute, unmute, or set; level (int): 0-100 when action=set."
    override val parameters = listOf(
        ParamSpec("action", "string", description = "up | down | mute | unmute | set"),
        ParamSpec("level", "int", required = false, description = "0-100 when action=set")
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val action = params["action"]?.trim()?.lowercase()
            ?: return ToolResult(false, "A volume action is required, sir.")
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val step = (max / 10).coerceAtLeast(1)

        when (action) {
            "up" -> audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI
            )
            "down" -> audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI
            )
            "mute" -> {
                audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI
                )
                return ToolResult(true, "Muted, sir.")
            }
            "unmute" -> {
                audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI
                )
                return ToolResult(true, "Sound restored, sir.")
            }
            "set" -> {
                val percent = params["level"]?.trim()?.removeSuffix("%")?.toIntOrNull()
                    ?: return ToolResult(false, "A level between 0 and 100, sir.")
                val target = ((percent.coerceIn(0, 100) / 100.0) * max).toInt().coerceIn(0, max)
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
                return ToolResult(true, "Volume at $percent percent, sir.")
            }
            else -> return ToolResult(false, "Unsupported volume action: $action")
        }
        val newLevel = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val percent = (newLevel * 100) / max
        return ToolResult(true, "Volume $percent percent, sir.")
    }
}

internal class BrightnessTool(private val context: Context) : Tool {
    override val name = "set_brightness"
    override val description = "Control screen brightness. Params: action (string): up, down, or set; level (int): 0-100 when action=set."
    override val parameters = listOf(
        ParamSpec("action", "string", description = "up | down | set"),
        ParamSpec("level", "int", required = false, description = "0-100 when action=set")
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        if (!Settings.System.canWrite(context)) {
            return ToolResult(
                false,
                "I need modify-settings permission, sir. Open Settings and grant Jarvis write access.",
                dataForLlm = "failed: WRITE_SETTINGS permission not granted"
            )
        }
        val action = params["action"]?.trim()?.lowercase()
            ?: return ToolResult(false, "A brightness action is required, sir.")
        val current = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (_: Exception) {
            128
        }
        val max = 255
        val target = when (action) {
            "up" -> (current + max / 4).coerceAtMost(max)
            "down" -> (current - max / 4).coerceAtLeast(2)
            "set" -> {
                val percent = params["level"]?.trim()?.removeSuffix("%")?.toIntOrNull()
                    ?: return ToolResult(false, "A level between 0 and 100, sir.")
                ((percent.coerceIn(1, 100) / 100.0) * max).toInt().coerceIn(2, max)
            }
            else -> return ToolResult(false, "Unsupported brightness action: $action")
        }
        try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                target
            )
        } catch (_: Exception) {
            return ToolResult(false, "The system declined the brightness change, sir.")
        }
        val percent = (target * 100) / max
        return ToolResult(true, "Brightness at $percent percent, sir.")
    }
}

internal class OpenSettingsPanelTool(private val context: Context) : Tool {
    override val name = "open_settings"
    override val description = "Open a system settings panel. Params: target (string): wifi, bluetooth, internet, volume, or display."
    override val parameters = listOf(
        ParamSpec("target", "string", description = "wifi | bluetooth | internet | volume | display")
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val target = params["target"]?.trim()?.lowercase()
            ?: return ToolResult(false, "Which settings panel, sir?")
        val intent = when (target) {
            "wifi", "wi-fi", "wireless" -> android.content.Intent(Settings.Panel.ACTION_WIFI)
            "bluetooth" -> android.content.Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "internet", "connectivity", "data" -> android.content.Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            "volume", "sound" -> android.content.Intent(Settings.Panel.ACTION_VOLUME)
            "display", "screen" -> android.content.Intent(Settings.ACTION_DISPLAY_SETTINGS)
            else -> android.content.Intent(Settings.ACTION_SETTINGS)
        }
        return try {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ToolResult(true, "Opening $target settings, sir.")
        } catch (_: Exception) {
            ToolResult(false, "That panel is not available on this device, sir.")
        }
    }
}

internal class BatteryStatusTool(private val context: Context) : Tool {
    override val name = "battery_status"
    override val description = "Report battery level, charging state, and temperature."
    override val parameters = emptyList<ParamSpec>()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            ?: return ToolResult(false, "Battery telemetry is unavailable, sir.")
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        if (level < 0 || scale <= 0) {
            return ToolResult(false, "Battery telemetry is unavailable, sir.")
        }
        val percent = (level * 100) / scale
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val summary = "$percent%, " + if (charging) "on charge" else "on battery"
        val tempText = if (temp > 0) ", core at ${temp / 10.0}°C" else ""
        return ToolResult(
            true,
            "Power reserves at $percent percent, ${if (charging) "charging" else "running on battery"}, sir.",
            dataForLlm = "battery: $summary$tempText"
        )
    }
}
