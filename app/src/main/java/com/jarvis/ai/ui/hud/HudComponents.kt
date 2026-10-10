package com.jarvis.ai.ui.hud

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.jarvis.ai.core.AgentState
import com.jarvis.ai.core.AgentMode
import com.jarvis.ai.ui.theme.HudMono
import com.jarvis.ai.ui.theme.HudStatusStyle
import com.jarvis.ai.ui.theme.JarvisError
import com.jarvis.ai.ui.theme.JarvisPrimary
import com.jarvis.ai.ui.theme.JarvisSecondary
import com.jarvis.ai.ui.theme.JarvisTertiary

/**
 * HUD visual components: the reactive core orb, mode selector, status line,
 * and quick actions. All visuals are hand-drawn Canvas — no image assets.
 */

private fun stateColor(state: AgentState): Color = when (state) {
    AgentState.IDLE -> JarvisPrimary
    AgentState.LISTENING -> JarvisSecondary
    AgentState.THINKING -> Color(0xFFB388FF)
    AgentState.EXECUTING -> JarvisSecondary
    AgentState.SPEAKING -> JarvisTertiary
    AgentState.ERROR -> JarvisError
}

@Composable
fun OrbButton(state: AgentState, listening: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = stateColor(state)
    val active = state != AgentState.IDLE
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .size(72.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Pulsing rings while active.
        if (active) {
            Canvas(modifier = Modifier.size(96.dp)) {
                val ringRadius = size.minDimension / 2f * (0.45f + pulse * 0.4f)
                drawCircle(
                    color = color.copy(alpha = (1f - pulse) * 0.45f),
                    radius = ringRadius,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        // Containment ring.
        Canvas(modifier = Modifier.size(72.dp)) {
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(color.copy(alpha = 0.4f), color, color.copy(alpha = 0.4f))
                ),
                radius = size.minDimension / 2f - 1.dp.toPx(),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
            // Tick marks, purely for gravitas.
            for (i in 0 until 12) {
                val angle = Math.toRadians((i * 30).toDouble())
                val outer = Offset(
                    x = (center.x + (size.minDimension / 2f) * kotlin.math.cos(angle) * 0.92f).toFloat(),
                    y = (center.y + (size.minDimension / 2f) * kotlin.math.sin(angle) * 0.92f).toFloat()
                )
                val inner = Offset(
                    x = (center.x + (size.minDimension / 2f) * kotlin.math.cos(angle) * 0.84f).toFloat(),
                    y = (center.y + (size.minDimension / 2f) * kotlin.math.sin(angle) * 0.84f).toFloat()
                )
                drawLine(color.copy(alpha = 0.35f), inner, outer, strokeWidth = 1.dp.toPx())
            }
        }
        // Core.
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    Brush.radialGradient(listOf(color, color.copy(alpha = 0.55f))),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (state == AgentState.SPEAKING || state == AgentState.THINKING) {
                    Icons.Rounded.Stop
                } else {
                    Icons.Rounded.Mic
                },
                contentDescription = "Jarvis orb",
                tint = Color(0xFF00131A),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ModeChips(current: AgentMode, onSelect: (AgentMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AgentMode.entries.forEach { mode ->
            val selected = mode == current
            Box(
                modifier = Modifier
                    .border(
                        width = if (selected) 1.5.dp else 0.75.dp,
                        color = if (selected) JarvisPrimary else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .background(
                        if (selected) JarvisPrimary.copy(alpha = 0.12f) else Color.Transparent,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = mode.label,
                    style = HudStatusStyle,
                    color = if (selected) JarvisPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StatusLine(modelLine: String, modifier: Modifier = Modifier) {
    val ready = modelLine.startsWith("CORE ONLINE")
    val fault = modelLine.startsWith("CORE FAULT") || modelLine.startsWith("LANGUAGE")
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val dotColor = when {
            ready -> JarvisTertiary
            fault -> JarvisSecondary
            else -> JarvisPrimary
        }
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(dotColor)
        }
        Text(
            text = modelLine,
            style = HudStatusStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun QuickActionsRow(onAction: (HudViewModel.QuickAction) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HudViewModel.QuickAction.entries.forEach { action ->
            Box(
                modifier = Modifier
                    .border(0.75.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                    .clickable { onAction(action) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = action.label,
                    style = HudStatusStyle.copy(fontSize = 10.sp),
                    color = JarvisPrimary.copy(alpha = 0.85f)
                )
            }
        }
    }
}
