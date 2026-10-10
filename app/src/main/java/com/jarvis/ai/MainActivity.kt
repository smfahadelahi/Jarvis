package com.jarvis.ai

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.ai.di.appContainer
import com.jarvis.ai.ui.hud.HudScreen
import com.jarvis.ai.ui.hud.HudViewModel
import com.jarvis.ai.ui.settings.SettingsScreen
import com.jarvis.ai.ui.settings.SettingsViewModel
import com.jarvis.ai.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = appContainer()
        setContent {
            JarvisTheme {
                AppRoot(container = container)
            }
        }
    }
}

private enum class Screen { HUD, SETTINGS }

@Composable
private fun AppRoot(container: com.jarvis.ai.di.AppContainer) {
    var screen by remember { mutableStateOf(Screen.HUD) }

    // Core voice permissions, requested once on entry.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> /* HUD guards mic availability at call time. */ }

    LaunchedEffect(Unit) {
        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(wanted.toTypedArray())
    }

    androidx.compose.animation.Crossfade(
        targetState = screen,
        label = "root-nav",
        modifier = Modifier.fillMaxSize()
    ) { target ->
        when (target) {
            Screen.HUD -> {
                val hudViewModel: HudViewModel = viewModel { HudViewModel(container) }
                HudScreen(
                    viewModel = hudViewModel,
                    onOpenSettings = { screen = Screen.SETTINGS }
                )
            }
            Screen.SETTINGS -> {
                val settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel(container) }
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onBack = { screen = Screen.HUD }
                )
            }
        }
    }
}
