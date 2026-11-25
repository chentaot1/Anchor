package com.anchor.adhd.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anchor.adhd.desktop.ai.DesktopAiEngine
import com.anchor.adhd.desktop.ai.LlamaServerStatus
import com.anchor.adhd.desktop.db.DesktopTask
import com.anchor.adhd.ui.components.AnchorChip
import com.anchor.adhd.ui.components.AnchorFocalPlate
import com.anchor.adhd.ui.components.AnchorListRow
import com.anchor.adhd.ui.components.AnchorQuietButton
import com.anchor.adhd.ui.components.AnchorSectionHeader
import com.anchor.adhd.ui.components.AnchorWell
import com.anchor.adhd.ui.components.AnchorWellTone
import com.anchor.adhd.ui.components.HarborCtaButton
import com.anchor.adhd.ui.components.HarborEmptyState
import com.anchor.adhd.ui.focus.FocusCanvas
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens

internal val FOCUS_TWO_COLUMN_BREAKPOINT = 860.dp

internal fun computeDesktopFocusProgress(
    remainingSeconds: Int,
    durationMinutes: Int,
): Float {
    val totalSeconds = (durationMinutes * 60).coerceAtLeast(1)
    return ((totalSeconds - remainingSeconds.coerceAtLeast(0)).toFloat() / totalSeconds).coerceIn(0f, 1f)
}

/**
 * Dedicated Full-Screen Focus Mode with Living Tide Circular Chronometer.
 * Features live countdown, physical micro-step checklist, duration steppers,
 * Floating Island & Fullscreen controls, and Airlock quick entry.
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
    isFloatingIslandActive: Boolean = false,
    onToggleFloatingIsland: (() -> Unit)? = null,
    onBreakDownWithCaptain: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAnchorTokens.current
    val captainLabelColor = if (tokens.isDark) tokens.captain else tokens.captainInk
    val progress = computeDesktopFocusProgress(remainingSeconds, durationMinutes)

    val engineStatus by DesktopAiEngine.engineStatus.collectAsState()
    val isModelReady = engineStatus is LlamaServerStatus.Ready

    // Micro-steps with instant heuristic fallback and background async Captain refinement
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

    val primaryCtaLabel =
        when {
            isFocusActive && isWorkPhase -> "Pause focus"
            isFocusActive -> "Pause break"
            !isWorkPhase -> "Resume break"
            hasOpenFocusSession -> "Resume focus"
            else -> "Start focus"
        }

    val focusHeadline =
        when {
            !isWorkPhase -> "Restorative break"
            currentTask != null -> currentTask.title
            else -> "Ready to anchor"
        }

    val supportingLine =
        when {
            !isWorkPhase -> "Step away for a quiet pause until the break finishes."
            currentTask == null -> "Select or add a task in Plan, or start a timed focus block."
            else ->
                currentTask.notes
                    .lineSequence()
                    .map(String::trim)
                    .firstOrNull { it.isNotEmpty() }
        }

    val canEndEarly = hasOpenFocusSession || isFocusActive || !isWorkPhase

    DesktopFocusPanels(
        modifier = modifier,
        timerPanel = { panelModifier, scrollable, minimumHeight, canvasSize ->
            FocusPanelColumn(
                modifier = panelModifier,
                scrollable = scrollable,
                minimumHeight = minimumHeight,
                horizontalPadding = if (scrollable) 16.dp else 4.dp,
                verticalPadding = if (scrollable) 8.dp else 4.dp,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Calm Session Complete Plate (single focal surface, zero emojis)
                if (showSessionCompleteBanner) {
                    SessionCompleteFocalPlate(
                        hasTask = currentTask != null,
                        breakDurationMinutes = breakDurationMinutes,
                        onMarkTaskCompleted = onMarkTaskCompleted,
                        onStartRestorativeBreak = onStartRestorativeBreak,
                        onDismiss = onDismissSessionCompleteBanner,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Shield Status, Fullscreen, and Floating Island controls
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FocusShieldStatusPill(
                        isFocusActive = isFocusActive,
                        isWorkPhase = isWorkPhase,
                    )

                    AnchorChip(
                        label = if (isFullscreen) "Exit fullscreen" else "Fullscreen (F11)",
                        selected = isFullscreen,
                        onClick = onToggleFullscreen,
                        leadingIcon = {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                        },
                    )

                    if (onToggleFloatingIsland != null) {
                        AnchorChip(
                            label = "Floating Island",
                            selected = isFloatingIslandActive,
                            onClick = onToggleFloatingIsland,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.OpenInFull,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                )
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (canvasSize >= 260.dp) 24.dp else 14.dp))

                // Centered Living Tide FocusCanvas with generous breathing room
                Box(
                    modifier = Modifier.size(canvasSize),
                    contentAlignment = Alignment.Center,
                ) {
                    FocusCanvas(
                        progress = progress,
                        isWorkPhase = isWorkPhase,
                        remainingSeconds = remainingSeconds.coerceAtLeast(0).toLong(),
                        taskTitle = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Spacer(modifier = Modifier.height(if (canvasSize >= 260.dp) 18.dp else 12.dp))

                // Atkinson task title + optional first physical step
                Text(
                    text = focusHeadline,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (!isWorkPhase) tokens.sage else tokens.ink,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp).semantics { heading() },
                )

                if (!supportingLine.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = supportingLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.muted,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }

                Spacer(modifier = Modifier.height(if (canvasSize >= 260.dp) 20.dp else 14.dp))

                // Single Primary HarborCtaButton (Start / Pause / Resume)
                HarborCtaButton(
                    onClick = onToggleFocus,
                    modifier =
                        Modifier
                            .widthIn(min = 200.dp)
                            .heightIn(min = 42.dp),
                ) {
                    Icon(
                        imageVector = if (isFocusActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = primaryCtaLabel,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Duration Steppers (-5m, Duration Picker, +5m, 3m Spark)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AnchorChip(
                        label = "-5m",
                        selected = false,
                        onClick = { onAdjustSeconds(-300) },
                    )

                    AnchorChip(
                        label = "${durationMinutes}m",
                        selected = false,
                        onClick = onOpenDurationPicker,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        },
                    )

                    AnchorChip(
                        label = "+5m",
                        selected = false,
                        onClick = { onAdjustSeconds(300) },
                    )

                    AnchorChip(
                        label = if (isWorkPhase && hasOpenFocusSession) "3m remaining" else "3m spark",
                        selected = false,
                        onClick = onStartSpark,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        },
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quiet Secondary Actions (End early / Reset, Airlock, Body Double)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AnchorQuietButton(
                        label = if (canEndEarly) "End early" else "Reset",
                        onClick = onResetTimer,
                        outlined = true,
                        tint = tokens.ink2,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )

                    AnchorQuietButton(
                        label = "Airlock reset",
                        onClick = onOpenAirlock,
                        outlined = true,
                        tint = captainLabelColor,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Air,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )

                    AnchorQuietButton(
                        label = "Body double",
                        onClick = ::launchOnTogetherBodyDouble,
                        outlined = false,
                        tint = tokens.muted,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Groups,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        },
        stepsPanel = { panelModifier, scrollable, minimumHeight ->
            FocusPanelColumn(
                modifier = panelModifier,
                scrollable = scrollable,
                minimumHeight = minimumHeight,
                horizontalPadding = 0.dp,
                verticalPadding = if (scrollable) 8.dp else 0.dp,
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Top,
            ) {
                val completedCount = microSteps.count { it.second }
                AnchorWell(
                    tone = AnchorWellTone.Neutral,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AnchorSectionHeader(
                        title = "5-minute micro-steps",
                        trailingLabel =
                            if (microSteps.isNotEmpty()) {
                                "$completedCount/${microSteps.size}"
                            } else {
                                null
                            },
                    )

                    if (isModelReady) {
                        Surface(
                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                            color = tokens.captainSoft,
                            border = null,
                            shadowElevation = 0.dp,
                            tonalElevation = 0.dp,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = tokens.captain,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = "Harbor Captain ready",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = captainLabelColor,
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    if (microSteps.isEmpty()) {
                        HarborEmptyState(
                            title = "No micro-steps yet",
                            message = "Add or select a task to generate calm 5-minute starter steps.",
                            actionLabel = if (onBreakDownWithCaptain != null) "Ask Captain" else null,
                            onAction = onBreakDownWithCaptain?.let { action -> { action("") } },
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            microSteps.indices.forEach { index ->
                                val (step, checked) = microSteps[index]
                                AnchorListRow(
                                    title = step,
                                    trailingText = if (checked) "Done" else null,
                                    onClick = { microSteps[index] = step to !checked },
                                    minHeight = 40.dp,
                                    showDivider = index < microSteps.lastIndex,
                                    checked = checked,
                                    leading = {
                                        Icon(
                                            imageVector =
                                                if (checked) {
                                                    Icons.Default.CheckCircle
                                                } else {
                                                    Icons.Outlined.RadioButtonUnchecked
                                                },
                                            contentDescription = null,
                                            tint = if (checked) tokens.sage else tokens.muted,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                )
                            }
                        }

                        if (onBreakDownWithCaptain != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            AnchorQuietButton(
                                label = if (currentTask != null) "Break down with Captain" else "Ask Captain",
                                onClick = { onBreakDownWithCaptain(currentTask?.title.orEmpty()) },
                                outlined = true,
                                tint = captainLabelColor,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SessionCompleteFocalPlate(
    hasTask: Boolean,
    breakDurationMinutes: Int,
    onMarkTaskCompleted: () -> Unit,
    onStartRestorativeBreak: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    AnchorFocalPlate(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = tokens.sage,
                modifier = Modifier.size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Focus session complete",
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.ink,
                )
                Text(
                    text = "Harbor vitality earned. Take a calm break or check off your task.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.muted,
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (hasTask) {
                AnchorQuietButton(
                    label = "Mark task completed",
                    onClick = onMarkTaskCompleted,
                    outlined = true,
                    tint = tokens.sage,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                    },
                )
            }
            AnchorQuietButton(
                label = "Start ${breakDurationMinutes}m break",
                onClick = onStartRestorativeBreak,
                outlined = true,
                tint = tokens.primary,
            )
            AnchorQuietButton(
                label = "Dismiss",
                onClick = onDismiss,
                outlined = false,
                tint = tokens.muted,
            )
        }
    }
}

@Composable
private fun FocusShieldStatusPill(
    isFocusActive: Boolean,
    isWorkPhase: Boolean,
) {
    val tokens = LocalAnchorTokens.current
    val containerColor = if (isFocusActive) tokens.sageSoft else tokens.paper2
    val borderColor = if (isFocusActive) tokens.sage else tokens.rule
    val contentColor = if (isFocusActive) tokens.sage else tokens.ink2
    val statusText =
        when {
            isFocusActive && !isWorkPhase -> "Restorative break active"
            isFocusActive -> "Distraction shield active"
            else -> "Shield on standby"
        }

    Surface(
        modifier = Modifier.heightIn(min = 40.dp),
        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun launchOnTogetherBodyDouble() {
    try {
        Runtime.getRuntime().exec(arrayOf("cmd", "/c", "start", "steam://rungameid/3707400"))
    } catch (_: Exception) {
    }
}

/** Stack panels in narrow windows; keep every action reachable in short windows. */
@Composable
private fun DesktopFocusPanels(
    modifier: Modifier,
    timerPanel: @Composable (Modifier, Boolean, Dp, Dp) -> Unit,
    stepsPanel: @Composable (Modifier, Boolean, Dp) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= FOCUS_TWO_COLUMN_BREAKPOINT
        val canvasSize =
            when {
                maxHeight < 520.dp -> 204.dp
                !wide -> 236.dp
                maxHeight < 660.dp -> 244.dp
                else -> 284.dp
            }
        if (wide) {
            val minimumHeight = (maxHeight - 40.dp).coerceAtLeast(0.dp)
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = AnchorSpacing.screenHorizontalDesktop, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                timerPanel(Modifier.weight(1.12f).fillMaxHeight(), true, minimumHeight, canvasSize)
                stepsPanel(Modifier.weight(0.88f).fillMaxHeight(), true, minimumHeight)
            }
        } else {
            val scroll = rememberScrollState()
            Box(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(AnchorSpacing.sectionGap),
                ) {
                    timerPanel(Modifier.fillMaxWidth(), false, 0.dp, canvasSize)
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
    modifier: Modifier,
    scrollable: Boolean,
    minimumHeight: Dp,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (scrollable) {
        val scroll = rememberScrollState()
        Box(modifier) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(scroll)
                        .heightIn(min = minimumHeight)
                        .padding(start = horizontalPadding, end = horizontalPadding + 10.dp, top = verticalPadding, bottom = verticalPadding),
                horizontalAlignment = horizontalAlignment,
                verticalArrangement = verticalArrangement,
                content = content,
            )
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scroll),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
    } else {
        Column(
            modifier = modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}
