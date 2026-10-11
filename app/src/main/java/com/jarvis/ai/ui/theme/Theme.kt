package com.jarvis.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── HUD palette ─────────────────────────────────────────────────────────────

val JarvisBackground = Color(0xFF050A12)
val JarvisSurface = Color(0xFF0A1420)
val JarvisSurfaceHigh = Color(0xFF10202E)
val JarvisPrimary = Color(0xFF35E0FF)   // containment cyan
val JarvisSecondary = Color(0xFFFFC24B) // diagnostic amber
val JarvisTertiary = Color(0xFF59F5C8)  // nominal green
val JarvisError = Color(0xFFFF5D5D)
val JarvisOutline = Color(0xFF1E3A4C)
val JarvisTextPrimary = Color(0xFFD8EFFF)
val JarvisTextDim = Color(0xFF6C8FA3)

val HudMono: FontFamily = FontFamily.Monospace

private val DarkColors = darkColorScheme(
    primary = JarvisPrimary,
    onPrimary = Color(0xFF00131A),
    secondary = JarvisSecondary,
    onSecondary = Color(0xFF1A1200),
    tertiary = JarvisTertiary,
    background = JarvisBackground,
    onBackground = JarvisTextPrimary,
    surface = JarvisSurface,
    onSurface = JarvisTextPrimary,
    surfaceVariant = JarvisSurfaceHigh,
    onSurfaceVariant = JarvisTextDim,
    error = JarvisError,
    outline = JarvisOutline
)

/** App typography with a monospace HUD label style bolted on. */
val JarvisTypography = Typography().let { base ->
    base.copy(
        labelSmall = base.labelSmall.copy(fontFamily = HudMono, letterSpacing = 1.2.sp),
        labelMedium = base.labelMedium.copy(fontFamily = HudMono, letterSpacing = 1.2.sp)
    )
}

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    // The HUD is dark by design; light system theme is deliberately ignored.
    MaterialTheme(
        colorScheme = DarkColors,
        typography = JarvisTypography,
        content = content
    )
}

/** Status text style shared by HUD readouts. */
val HudStatusStyle = TextStyle(
    fontFamily = HudMono,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    letterSpacing = 1.4.sp
)
