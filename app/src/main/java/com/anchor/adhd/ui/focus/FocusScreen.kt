package com.anchor.adhd.ui.focus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anchor.adhd.service.FocusBlockService
import com.anchor.adhd.service.FocusTimerState
import com.anchor.adhd.ui.components.AnchorChip
import com.anchor.adhd.ui.components.AnchorQuietButton
import com.anchor.adhd.ui.components.AnchorSectionHeader
import com.anchor.adhd.ui.components.AnchorWell
import com.anchor.adhd.ui.components.CustomDurationDialog
import com.anchor.adhd.ui.components.HarborCtaButton
import com.anchor.adhd.ui.components.RaindropAnimation
import com.anchor.adhd.ui.copy.AppCopy
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens
import com.anchor.adhd.ui.vm.AnchorViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun computeFocusProgress(
    active: Boolean,
    isWorkPhase: Boolean,
    remainingSeconds: Int,
    totalWorkSeconds: Int,
    breakMinutes: Int,
): Float {
    if (!active) return 0f
    val totalPhaseSeconds = (if (isWorkPhase) totalWorkSeconds else breakMinutes * 60).coerceAtLeast(1)
    return (1f - remainingSeconds.coerceAtLeast(0).toFloat() / totalPhaseSeconds).coerceIn(0f, 1f)
}

internal fun resolveFocusFirstStep(
    active: Boolean,
    isWorkPhase: Boolean,
    timerTaskId: Long?,
    nowTaskId: Long?,
    nowTaskNotes: String?,
): String? {
    if (!isWorkPhase) return null
    val matchesActiveTask = !active || (timerTaskId != null && timerTaskId == nowTaskId)
    if (!matchesActiveTask) return null
    return nowTaskNotes
        ?.lineSequence()
        ?.map(String::trim)
        ?.firstOrNull { it.isNotEmpty() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusScreen(
    vm: AnchorViewModel,
    modifier: Modifier = Modifier,
    onOpenAirlock: (() -> Unit)? = null,
) {
    val tokens = LocalAnchorTokens.current
    val timer by vm.timerState.collectAsState()
    val now by vm.nowTask.collectAsState()
    val focusDurations by vm.focusDurations.collectAsState()
    val blockedAttempts by vm.blockedAttempts.collectAsState()
    val active = timer.phase != FocusTimerState.Phase.IDLE
    val isWorkPhase = timer.phase == FocusTimerState.Phase.WORK || !active
    val (workMin, breakMin) = focusDurations
    var pickerMinutes by rememberSaveable(workMin) { mutableIntStateOf(workMin) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var showRaindrop by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val blockerEnabled = FocusBlockService.isEnabled()
    val captainLabelColor = if (tokens.isDark) tokens.captain else tokens.captainInk

    val progress = computeFocusProgress(
        active = active,
        isWorkPhase = isWorkPhase,
        remainingSeconds = timer.remainingSeconds,
        totalWorkSeconds = timer.totalWorkSeconds,
        breakMinutes = timer.breakMinutes,
    )

    val remainingSecs = if (active) timer.remainingSeconds.coerceAtLeast(0).toLong() else (pickerMinutes * 60L)
    val currentTaskTitle = when {
        active && !isWorkPhase -> "Step away until the break finishes"
        active -> timer.taskTitle ?: now.task?.takeIf { timer.taskId != null && timer.taskId == it.id }?.title ?: "Deep focus"
        else -> now.task?.title ?: "Ready to anchor"
    }
    val firstStep = resolveFocusFirstStep(
        active = active,
        isWorkPhase = isWorkPhase,
        timerTaskId = timer.taskId,
        nowTaskId = now.task?.id,
        nowTaskNotes = now.task?.notes,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.paper),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AnchorSpacing.screenHorizontal, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        active && isWorkPhase -> "Focus in progress"
                        active -> "Restorative break"
                        else -> "Focus"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = tokens.ink,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                IconButton(
                    onClick = { vm.openAppBlocker() },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = "App protection",
                        tint = if (blockerEnabled) tokens.sage else tokens.ink2,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AnchorSpacing.screenHorizontal)
                    .padding(top = 8.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AnchorSpacing.sectionGap),
            ) {
                // Centered Living Tide chronometer with generous breathing room
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    FocusCanvas(
                        progress = progress,
                        isWorkPhase = isWorkPhase,
                        remainingSeconds = remainingSecs,
                        taskTitle = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Task title + optional starter step + blocker status indicator
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = currentTaskTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (active && !isWorkPhase) tokens.sage else tokens.ink,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!firstStep.isNullOrBlank()) {
                        Text(
                            text = firstStep,
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.muted,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(AnchorSpacing.radiusPill))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Open app protection",
                                onClick = { vm.openAppBlocker() },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                            color = if (blockerEnabled) tokens.sageSoft else tokens.paper2,
                            border = BorderStroke(1.dp, if (blockerEnabled) tokens.sage else tokens.rule),
                            shadowElevation = 0.dp,
                            tonalElevation = 0.dp,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Shield,
                                    contentDescription = null,
                                    tint = if (blockerEnabled) tokens.sage else tokens.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = if (blockerEnabled) {
                                        "App protection on ($blockedAttempts blocked)"
                                    } else {
                                        "Set up app protection"
                                    },
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFeatureSettings = "tnum",
                                    ),
                                    color = if (blockerEnabled) tokens.sage else tokens.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                if (active) {
                    HarborCtaButton(
                        onClick = {
                            showRaindrop = true
                            vm.requestEndFocus(true)
                            scope.launch {
                                delay(900)
                                showRaindrop = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isWorkPhase) "Complete session" else "Finish break",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    if (isWorkPhase || onOpenAirlock != null) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (isWorkPhase) {
                                AnchorQuietButton(
                                    label = AppCopy.FOCUS_END_EARLY,
                                    onClick = { vm.requestEndFocus(false) },
                                    outlined = false,
                                    tint = tokens.muted,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                )
                            }
                            if (onOpenAirlock != null) {
                                AnchorQuietButton(
                                    label = "Airlock reset",
                                    onClick = onOpenAirlock,
                                    outlined = true,
                                    tint = captainLabelColor,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Air,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                )
                            }
                        }
                    }
                } else {
                    val standardOptions = listOf(10, 20, 30, 45)
                    val isCustom = pickerMinutes !in standardOptions

                    AnchorWell(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AnchorSectionHeader(
                            title = AppCopy.FOCUS_SESSION_LENGTH,
                            trailingLabel = "$breakMin min break",
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            standardOptions.forEach { mins ->
                                AnchorChip(
                                    label = "$mins min",
                                    selected = pickerMinutes == mins,
                                    onClick = { pickerMinutes = mins },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                )
                            }
                            AnchorChip(
                                label = if (isCustom) "$pickerMinutes min (Custom)" else "Custom",
                                selected = isCustom,
                                onClick = { showCustomDialog = true },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }

                    HarborCtaButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.startFocus(pickerMinutes, breakMin, now.task?.id)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Start $pickerMinutes min focus",
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        )
                    }

                    val currentTask = now.task
                    if (currentTask != null || onOpenAirlock != null) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (currentTask != null) {
                                AnchorQuietButton(
                                    label = "Break down with Captain",
                                    onClick = {
                                        vm.openTaskDetail(currentTask)
                                        vm.breakdownTask(currentTask.title)
                                    },
                                    outlined = false,
                                    tint = captainLabelColor,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                )
                            }
                            if (onOpenAirlock != null) {
                                AnchorQuietButton(
                                    label = "Airlock reset",
                                    onClick = onOpenAirlock,
                                    outlined = true,
                                    tint = captainLabelColor,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Air,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showCustomDialog) {
            CustomDurationDialog(
                initialMinutes = pickerMinutes,
                onDismiss = { showCustomDialog = false },
                onConfirm = { customMins ->
                    pickerMinutes = customMins
                    showCustomDialog = false
                },
            )
        }

        RaindropAnimation(active = showRaindrop, modifier = Modifier.fillMaxSize())
    }
}
