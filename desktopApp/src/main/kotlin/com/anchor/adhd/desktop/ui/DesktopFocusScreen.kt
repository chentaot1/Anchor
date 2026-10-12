package com.anchor.adhd.desktop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.adhd.desktop.ai.DesktopAiEngine
import com.anchor.adhd.desktop.db.DesktopTask
import com.anchor.adhd.desktop.theme.AnchorColors
import com.anchor.adhd.desktop.theme.AnchorSpacing
import com.anchor.adhd.ui.focus.FocusCanvas

/**
 * Dedicated Full-Screen Focus Mode with Circular Tide Ring Chronometer.
 * Features live countdown, physical micro-step checklist, steppers, and Airlock quick entry.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun DesktopFocusScreen(
    currentTask: DesktopTask?,
    isFocusActive: Boolean,
    remainingSeconds: Int,
    durationMinutes: Int,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onToggleFocus: () -> Unit,
    onAdjustSeconds: (Int) -> Unit,
    onStartSpark: () -> Unit,
    onResetTimer: () -> Unit,
    onOpenDurationPicker: () -> Unit,
    onOpenAirlock: () -> Unit,
    isWorkPhase: Boolean = true,
    hasOpenFocusSession: Boolean = false,
    showSessionCompleteBanner: Boolean = false,
    breakDurationMinutes: Int = 5,
    onMarkTaskCompleted: () -> Unit = {},
    onStartRestorativeBreak: () -> Unit = {},
    onDismissSessionCompleteBanner: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val totalSeconds = (durationMinutes * 60).coerceAtLeast(1)
    val progress = ((totalSeconds - remainingSeconds).toFloat() / totalSeconds).coerceIn(0f, 1f)

    val engineStatus by DesktopAiEngine.engineStatus.collectAsState()
    val isModelReady = engineStatus is com.anchor.adhd.desktop.ai.LlamaServerStatus.Ready

    // Micro-steps with instant heuristic fallback and background async AI refinement
    val microSteps =
        remember(currentTask?.title) {
            val steps = mutableStateListOf<Pair<String, Boolean>>()
            if (currentTask != null) {
                val generated = DesktopAiEngine.generateMicroSteps(currentTask.title)
                steps.addAll(generated.map { it to false })
            }
            steps
        }

    LaunchedEffect(currentTask?.title, isModelReady) {
        val taskTitle = currentTask?.title
        if (!taskTitle.isNullOrBlank() && isModelReady) {
            val aiSteps = DesktopAiEngine.generateMicroStepsAsync(taskTitle)
            if (aiSteps.isNotEmpty()) {
                val currentStrings = microSteps.map { it.first }
                if (aiSteps != currentStrings && microSteps.none { it.second }) {
                    // Only update if user hasn't already checked off any steps
                    microSteps.clear()
                    microSteps.addAll(aiSteps.map { it to false })
                }
            }
        }
    }

    // Tide wave animation
    val infiniteTransition = rememberInfiniteTransition(label = "tide_chronometer")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec =
            infiniteRepeatable(
                animation = tween(3500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "tide_wave_pulse",
    )

    DesktopFocusPanels(
        modifier = modifier,
        timerPanel = { panelModifier, scrollable, minimumHeight ->
            // Left Column: The Large Circular Tide Chronometer
            Surface(
                modifier =
                    panelModifier
                        .clip(RoundedCornerShape(AnchorSpacing.radiusScene)),
                color = AnchorColors.HarborDock.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, AnchorColors.HarborPrimary.copy(alpha = 0.25f)),
            ) {
                FocusPanelColumn(
                    scrollable = scrollable,
                    minimumHeight = minimumHeight,
                    padding = 24.dp,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Session Complete & Restorative Break Celebration Banner
                    if (showSessionCompleteBanner) {
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                            shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                            color = AnchorColors.HarborGrowth.copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, AnchorColors.HarborFoliage.copy(alpha = 0.55f)),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "⚓ Anchor Session Complete! Harbor Vitality Earned",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AnchorColors.HarborFoliage,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (currentTask != null) {
                                        Button(
                                            onClick = onMarkTaskCompleted,
                                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                            colors =
                                                ButtonDefaults.buttonColors(
                                                    containerColor = AnchorColors.HarborFoliage,
                                                    contentColor = Color(0xFF062115),
                                                ),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Mark Task Completed", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = onStartRestorativeBreak,
                                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                        border = BorderStroke(1.dp, AnchorColors.HarborPrimary.copy(alpha = 0.6f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AnchorColors.HarborPrimary),
                                    ) {
                                        Text(
                                            text = "Start ${breakDurationMinutes}m Restorative Break",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = onDismissSessionCompleteBanner,
                                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.75f)),
                                    ) {
                                        Text("Dismiss", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Shield Status & Fullscreen Immersion Row
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Shield Status Pill
                        Surface(
                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                            color = if (isFocusActive) Color(0xFF0F2B1D) else Color.White.copy(alpha = 0.08f),
                            border =
                                BorderStroke(
                                    1.dp,
                                    if (isFocusActive) AnchorColors.HarborGrowth else Color.White.copy(alpha = 0.15f),
                                ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = if (isFocusActive) AnchorColors.HarborFoliage else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text =
                                        if (!isWorkPhase && isFocusActive) {
                                            "Restorative Break Active"
                                        } else if (isFocusActive) {
                                            "Distraction Shield Active"
                                        } else {
                                            "Shield on Standby"
                                        },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFocusActive) AnchorColors.HarborFoliage else Color.White.copy(alpha = 0.8f),
                                )
                            }
                        }

                        // Fullscreen Immersion Toggle Pill
                        Surface(
                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                            color = if (isFullscreen) AnchorColors.HarborPrimary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                            border =
                                BorderStroke(
                                    1.dp,
                                    if (isFullscreen) AnchorColors.HarborPrimary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.15f),
                                ),
                            modifier = Modifier.clickable(role = Role.Button) { onToggleFullscreen() },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = if (isFullscreen) "Exit Fullscreen (F11)" else "Enter Fullscreen (F11)",
                                    tint = if (isFullscreen) AnchorColors.HarborPrimary else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isFullscreen) "Exit Fullscreen (F11)" else "Fullscreen (F11)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFullscreen) AnchorColors.HarborPrimary else Color.White.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Shared Maritime Circular Tide Ring Chronometer (FocusCanvas)
                    Box(
                        modifier = Modifier.size(300.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        FocusCanvas(
                            progress = progress,
                            isWorkPhase = isFocusActive && isWorkPhase,
                            remainingSeconds = remainingSeconds.toLong(),
                            taskTitle =
                                if (!isWorkPhase && isFocusActive) {
                                    "Restorative Break"
                                } else if (isFocusActive) {
                                    (currentTask?.title ?: "Deep Work")
                                } else {
                                    "Ready to Anchor"
                                },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Quick Steppers (-5m / +5m)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                            color = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.clickable(role = Role.Button) { onAdjustSeconds(-300) },
                        ) {
                            Text(
                                text = "-5m",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                            color = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.clickable(role = Role.Button) { onOpenDurationPicker() },
                        ) {
                            Text(
                                text = "${durationMinutes}m ✎",
                                style = MaterialTheme.typography.labelSmall,
                                color = AnchorColors.HarborAction,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                            color = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.clickable(role = Role.Button) { onAdjustSeconds(300) },
                        ) {
                            Text(
                                text = "+5m",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }

                        // An open work session keeps its elapsed work; a ready timer starts a new spark.
                        Surface(
                            shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                            color = AnchorColors.HarborGrowth.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, AnchorColors.HarborFoliage.copy(alpha = 0.4f)),
                            modifier =
                                Modifier.clickable(role = Role.Button, onClick = onStartSpark),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = AnchorColors.HarborFoliage,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (isWorkPhase && hasOpenFocusSession) "3m remaining" else "3m Spark",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AnchorColors.HarborFoliage,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Play / Pause / Reset Primary Controls
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            onClick = onResetTimer,
                            modifier =
                                Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Timer",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        Button(
                            onClick = onToggleFocus,
                            modifier =
                                Modifier
                                    .widthIn(min = 200.dp)
                                    .heightIn(min = 52.dp),
                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = if (isFocusActive) AnchorColors.HarborAction else AnchorColors.HarborPrimary,
                                    contentColor = Color(0xFF002A4A),
                                ),
                        ) {
                            Icon(
                                imageVector = if (isFocusActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isFocusActive && isWorkPhase -> "Pause Focus"
                                    isFocusActive -> "Pause Break"
                                    !isWorkPhase -> "Resume Break"
                                    hasOpenFocusSession -> "Resume Focus"
                                    else -> "Start Anchor"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Co-working Body Double Companion Launch
                    OutlinedButton(
                        onClick = {
                            try {
                                Runtime.getRuntime().exec(arrayOf("cmd", "/c", "start", "steam://rungameid/3707400"))
                            } catch (_: Exception) {
                            }
                        },
                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                        border = BorderStroke(1.dp, AnchorColors.HarborPrimary.copy(alpha = 0.35f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AnchorColors.HarborPrimary.copy(alpha = 0.9f)),
                    ) {
                        Text(
                            text = "🐾 Open On-Together Body Double",
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 12.sp,
                            color = AnchorColors.HarborPrimary,
                        )
                    }
                }
            }
        },
        stepsPanel = { panelModifier, scrollable, minimumHeight ->
            // Right Column: Active Task & Physical Micro-Steps
            Surface(
                modifier =
                    panelModifier
                        .clip(RoundedCornerShape(AnchorSpacing.radiusScene)),
                color = AnchorColors.HarborDock.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            ) {
                FocusPanelColumn(scrollable = scrollable, minimumHeight = minimumHeight, padding = 20.dp) {
                    // Active Anchor Title Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                        color = AnchorColors.HarborBackground.copy(alpha = 0.8f),
                        border = BorderStroke(1.dp, AnchorColors.HarborPrimary.copy(alpha = 0.25f)),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "CURRENT ANCHOR",
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 1.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = AnchorColors.HarborBeaconAmber,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = currentTask?.title ?: "Select or create a task to begin",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "5-MINUTE MICRO-STEPS",
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Bold,
                                color = AnchorColors.HarborPrimary,
                            )
                            if (isModelReady) {
                                Surface(
                                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                    color = AnchorColors.HarborAi.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, AnchorColors.HarborAi.copy(alpha = 0.4f)),
                                ) {
                                    Text(
                                        text = "Claude-Opus AI",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AnchorColors.HarborAi,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = onOpenAirlock,
                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                            contentPadding =
                                androidx.compose.foundation.layout
                                    .PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = BorderStroke(1.dp, AnchorColors.HarborAi.copy(alpha = 0.4f)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = AnchorColors.HarborAi,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Airlock", fontSize = 11.sp, color = AnchorColors.HarborAi)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Micro-Step Checklist
                    if (microSteps.isEmpty()) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No micro-steps yet.\nAdd a task to generate physical 5-minute steps.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.4f),
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            microSteps.indices.forEach { index ->
                                val (step, checked) = microSteps[index]
                                Surface(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(AnchorSpacing.radiusChip))
                                            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { microSteps[index] = step to it }),
                                    color =
                                        if (checked) {
                                            Color.White.copy(
                                                alpha = 0.03f,
                                            )
                                        } else {
                                            AnchorColors.HarborBackground.copy(alpha = 0.7f)
                                        },
                                    border =
                                        BorderStroke(
                                            1.dp,
                                            if (checked) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.12f),
                                        ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (checked) AnchorColors.HarborGrowth else Color.White.copy(alpha = 0.4f),
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = step,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (checked) Color.White.copy(alpha = 0.4f) else Color.White,
                                            textDecoration = if (checked) TextDecoration.LineThrough else null,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

/** Stack panels in narrow windows; keep every action reachable in short windows. */
@Composable
private fun DesktopFocusPanels(
    modifier: Modifier,
    timerPanel: @Composable (Modifier, Boolean, Dp) -> Unit,
    stepsPanel: @Composable (Modifier, Boolean, Dp) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= 880.dp) {
            val minimumHeight = (maxHeight - 48.dp).coerceAtLeast(0.dp)
            Row(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                timerPanel(Modifier.weight(1.1f).fillMaxHeight(), true, minimumHeight)
                stepsPanel(Modifier.weight(0.9f).fillMaxHeight(), true, minimumHeight)
            }
        } else {
            val scroll = rememberScrollState()
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    timerPanel(Modifier.fillMaxWidth(), false, 0.dp)
                    stepsPanel(Modifier.fillMaxWidth(), false, 0.dp)
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scroll),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun FocusPanelColumn(
    scrollable: Boolean,
    minimumHeight: Dp,
    padding: Dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (scrollable) {
        val scroll = rememberScrollState()
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(scroll).heightIn(min = minimumHeight).padding(padding),
                horizontalAlignment = horizontalAlignment,
                verticalArrangement = verticalArrangement,
                content = content,
            )
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scroll),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(6.dp),
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth().padding(padding),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}
