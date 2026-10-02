package com.anchor.adhd.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.anchor.adhd.desktop.theme.AnchorColors
import com.anchor.adhd.desktop.theme.AnchorSpacing
import com.anchor.adhd.desktop.theme.AnchorTheme

/**
 * Windows 11 Floating Island Micro-HUD.
 * A lightweight, draggable, always-on-top ambient anchor widget that stays visible
 * while coding, reading, or researching without obscuring the desktop workspace.
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
        title = "Anchor Island",
        alwaysOnTop = true,
        undecorated = true,
        transparent = true,
        resizable = false,
    ) {
        AnchorTheme {
            WindowDraggableArea {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(AnchorSpacing.radiusPill)),
                    color = AnchorColors.HarborDock.copy(alpha = 0.94f),
                    border =
                        BorderStroke(
                            1.dp,
                            if (isFocusActive) AnchorColors.HarborGrowth.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.15f),
                        ),
                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Left: Pulsing status indicator
                        Box(
                            modifier =
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isFocusActive) {
                                            AnchorColors.HarborGrowth.copy(alpha = 0.35f)
                                        } else {
                                            Color.White.copy(alpha = 0.08f)
                                        },
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Anchor,
                                contentDescription = null,
                                tint = if (isFocusActive) AnchorColors.HarborFoliage else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Center: Task title + Live timer
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = taskTitle.ifBlank { "Deep Focus" },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val mins = remainingSeconds / 60
                            val secs = remainingSeconds % 60
                            val timeFormatted = String.format("%02d:%02d", mins, secs)
                            Text(
                                text = if (isFocusActive) "$timeFormatted remaining" else "Paused",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = if (isFocusActive) AnchorColors.HarborAction else Color.White.copy(alpha = 0.5f),
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Right: Controls
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Play/Pause button
                            IconButton(
                                onClick = onToggleFocus,
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    imageVector = if (isFocusActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isFocusActive) "Pause" else "Play",
                                    tint = if (isFocusActive) AnchorColors.HarborAction else AnchorColors.HarborPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                            // Maximize back to full Anchor app
                            IconButton(
                                onClick = onExpandMainApp,
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInFull,
                                    contentDescription = "Expand Anchor",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(15.dp),
                                )
                            }

                            // Close HUD button
                            IconButton(
                                onClick = onCloseHud,
                                modifier = Modifier.size(26.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close HUD",
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
