package com.jarvis.ai.ui.hud

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jarvis.ai.core.AgentState
import com.jarvis.ai.core.ChatMessage
import com.jarvis.ai.ui.theme.HudMono
import com.jarvis.ai.ui.theme.HudStatusStyle
import com.jarvis.ai.ui.theme.JarvisBackground
import com.jarvis.ai.ui.theme.JarvisPrimary
import com.jarvis.ai.ui.theme.JarvisSecondary
import com.jarvis.ai.ui.theme.JarvisSurface
import com.jarvis.ai.ui.theme.JarvisSurfaceHigh
import com.jarvis.ai.ui.theme.JarvisTextDim

/**
 * The main HUD screen: status header, mode selector, conversation transcript,
 * quick actions, and the voice orb / text input dock.
 */
@Composable
fun HudScreen(
    viewModel: HudViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll on new content.
    LaunchedEffect(state.messages.size, state.partial) {
        val target = state.messages.size - 1 + (if (state.partial != null) 1 else 0)
        if (target >= 0) listState.animateScrollToItem(target.coerceAtLeast(0))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(JarvisBackground, JarvisSurface.copy(alpha = 0.65f), JarvisBackground)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            // ── Header ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "J.A.R.V.I.S.",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = HudMono),
                    color = JarvisPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = JarvisTextDim
                    )
                }
            }

            StatusLine(modelLine = state.modelLine)

            Spacer(modifier = Modifier.height(10.dp))

            ModeChips(
                current = state.mode,
                onSelect = viewModel::setMode
            )

            if (state.hint != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.hint!!,
                    style = HudStatusStyle.copy(fontFamily = HudMono),
                    color = JarvisSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Transcript ─────────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
                state.partial?.let { partial ->
                    item(key = "partial") {
                        MessageBubble(
                            ChatMessage(role = ChatMessage.Role.JARVIS, text = partial, isStreaming = true)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Quick actions ──────────────────────────────────────────────
            QuickActionsRow(onAction = viewModel::quickAction)

            Spacer(modifier = Modifier.height(10.dp))

            // ── Input dock ─────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text("Type a command, sir…", color = JarvisTextDim, style = MaterialTheme.typography.bodyMedium)
                    },
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisPrimary.copy(alpha = 0.6f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = JarvisSurfaceHigh.copy(alpha = 0.5f),
                        unfocusedContainerColor = JarvisSurfaceHigh.copy(alpha = 0.35f)
                    ),
                    maxLines = 4
                )
                Spacer(modifier = Modifier.width(10.dp))
                OrbButton(
                    state = state.agentState,
                    listening = state.listening,
                    onClick = viewModel::onOrbPressed
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.onTextSubmitted(inputText)
                            inputText = ""
                        }
                    }
                ) {
                    Icon(
                        Icons.Rounded.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank()) JarvisPrimary else JarvisTextDim
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    val isUser = message.role == ChatMessage.Role.USER
    val isSystem = message.role == ChatMessage.Role.SYSTEM
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .background(
                    when {
                        isUser -> JarvisPrimary.copy(alpha = 0.10f)
                        isSystem -> Color.Transparent
                        else -> JarvisSurfaceHigh.copy(alpha = 0.6f)
                    },
                    RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 14.dp,
                        bottomStart = 14.dp,
                        bottomEnd = 14.dp
                    )
                )
                .let { base ->
                    if (!isSystem) base.padding(horizontal = 12.dp, vertical = 9.dp)
                    else base.padding(horizontal = 4.dp, vertical = 2.dp)
                }
        ) {
            if (isSystem) {
                Text(
                    text = message.text,
                    style = HudStatusStyle.copy(fontFamily = HudMono),
                    color = JarvisSecondary.copy(alpha = 0.8f)
                )
            } else {
                Text(
                    text = message.text + if (message.isStreaming) " ▊" else "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) JarvisPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
