package com.anchor.adhd.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.anchor.adhd.desktop.platform.DesktopAppIcon
import com.anchor.adhd.desktop.theme.AnchorTheme
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens

internal fun formatCountdownClock(remainingSeconds: Int): String {
    val safeSeconds = remainingSeconds.coerceAtLeast(0)
    val mins = safeSeconds / 60
    val secs = safeSeconds % 60
    return "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
}

/**
 * Windows 11 Floating Island Micro-HUD.
 * A lightweight, draggable, always-on-top ambient anchor widget that adapts to
 * Morning Harbor (Light) and Night Harbor (Dark) via [darkTheme] and [reducedMotion].
 */
@Composable
fun DesktopFloatingIslandWindow(
    visible: Boolean,
    taskTitle: String,
    isFocusActive: Boolean,
    remainingSeconds: Int,
    onToggleFocus: () -> Unit,
    onExpandMainApp: () -> Unit,
    onCloseHud: () -> Unit,
    isWorkPhase: Boolean = true,
    darkTheme: Boolean = false,
    reducedMotion: Boolean = false,
) {
    if (!visible) return

    val hudWindowState =
        rememberWindowState(
            size = DpSize(380.dp, 64.dp),
            position = WindowPosition(Alignment.TopCenter),
        )

    Window(
        onCloseRequest = onCloseHud,
        state = hudWindowState,
        icon = DesktopAppIcon.painter,
        title = "Anchor Island",
        alwaysOnTop = true,
        undecorated = true,
        transparent = true,
        resizable = false,
    ) {
        AnchorTheme(darkTheme = darkTheme, reducedMotion = reducedMotion) {
            WindowDraggableArea {
                DesktopFloatingIslandSurface(
                    taskTitle = taskTitle,
                    isFocusActive = isFocusActive,
                    isWorkPhase = isWorkPhase,
                    remainingSeconds = remainingSeconds,
                    onToggleFocus = onToggleFocus,
                    onExpandMainApp = onExpandMainApp,
                    onCloseHud = onCloseHud,
                )
            }
        }
    }
}

@Composable
internal fun DesktopFloatingIslandSurface(
    taskTitle: String,
    isFocusActive: Boolean,
    remainingSeconds: Int,
    onToggleFocus: () -> Unit,
    onExpandMainApp: () -> Unit,
    onCloseHud: () -> Unit,
    isWorkPhase: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAnchorTokens.current
    val shape = RoundedCornerShape(AnchorSpacing.radiusPill)
    val timeFormatted = formatCountdownClock(remainingSeconds)
    val statusText =
        when {
            isFocusActive && isWorkPhase -> "Focus"
            isFocusActive -> "Break"
            else -> "Paused"
        }

    Surface(
        modifier = modifier.fillMaxSize(),
        shape = shape,
        color = tokens.paper2,
        contentColor = tokens.ink,
        border = BorderStroke(1.dp, if (isFocusActive) tokens.sage else tokens.rule),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isFocusActive) tokens.sageSoft else tokens.paper3),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Anchor,
                    contentDescription = null,
                    tint = if (isFocusActive) tokens.sage else tokens.ink2,
                    modifier = Modifier.size(18.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = taskTitle.ifBlank { "Deep focus" },
                    style = MaterialTheme.typography.labelLarge,
                    color = tokens.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = timeFormatted,
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum",
                            ),
                        color = tokens.ink,
                        maxLines = 1,
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isFocusActive) tokens.sage else tokens.muted,
                        maxLines = 1,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(
                    onClick = onToggleFocus,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = if (isFocusActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isFocusActive) "Pause focus" else "Start focus",
                        tint = if (isFocusActive) tokens.sage else tokens.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                IconButton(
                    onClick = onExpandMainApp,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInFull,
                        contentDescription = "Expand Anchor",
                        tint = tokens.ink2,
                        modifier = Modifier.size(16.dp),
                    )
                }

                IconButton(
                    onClick = onCloseHud,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Floating Island",
                        tint = tokens.muted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
