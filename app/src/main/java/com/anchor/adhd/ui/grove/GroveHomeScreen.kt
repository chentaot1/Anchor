package com.anchor.adhd.ui.grove

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anchor.adhd.data.model.CheckInTag
import com.anchor.adhd.data.model.EnergyLevel
import com.anchor.adhd.domain.CheckInCodec
import com.anchor.adhd.domain.GroveLevel
import com.anchor.adhd.service.FocusTimerState
import com.anchor.adhd.ui.components.AnchorBeaconPlate
import com.anchor.adhd.ui.components.AnchorChip
import com.anchor.adhd.ui.components.AnchorHairline
import com.anchor.adhd.ui.components.AnchorListRow
import com.anchor.adhd.ui.components.AnchorQuietButton
import com.anchor.adhd.ui.components.AnchorSectionHeader
import com.anchor.adhd.ui.components.AnchorTodayTideStrip
import com.anchor.adhd.ui.components.AnchorWell
import com.anchor.adhd.ui.components.CelebrationEffect
import com.anchor.adhd.ui.components.CustomDurationDialog
import com.anchor.adhd.ui.components.HarborCtaButton
import com.anchor.adhd.ui.components.HarborEmptyState
import com.anchor.adhd.ui.copy.AppCopy
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens
import com.anchor.adhd.ui.vm.AnchorViewModel
import java.time.LocalTime

private fun homeGreetingForHour(hourOfDay: Int): String =
    when (hourOfDay) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

private fun formatAndroidHomeSummary(sessionsToday: Int, minutesToday: Int): String {
    val safeSessions = sessionsToday.coerceAtLeast(0)
    val safeMinutes = minutesToday.coerceAtLeast(0)
    return when {
        safeSessions > 0 -> {
            val word = if (safeSessions == 1) "session" else "sessions"
            "$safeSessions $word today, $safeMinutes min focused"
        }
        safeMinutes > 0 -> "$safeMinutes min focused today"
        else -> "One small step at a time"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroveHomeScreen(
    vm: AnchorViewModel,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onOpenInbox: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onPanicDump: () -> Unit = onOpenChat,
    onOpenThink: () -> Unit = onOpenChat
) {
    val tokens = LocalAnchorTokens.current
    val weekly by vm.weeklySummary.collectAsState()
    val focusCountToday by vm.focusCountToday.collectAsState()
    val todayFocusMinutes by vm.todayFocusMinutes.collectAsState()
    val now by vm.nowTask.collectAsState()
    val later by vm.laterToday.collectAsState()
    val replan by vm.replanWithTasks.collectAsState()
    val dueSoon by vm.assignmentsDueSoon.collectAsState()
    val dailyStrategy by vm.dailyStrategyCard.collectAsState()
    val energy by vm.energy.collectAsState()
    val checkInTags by vm.checkInTags.collectAsState()
    val workload by vm.workload.collectAsState()
    val focusDurations by vm.focusDurations.collectAsState()
    val timerState by vm.timerState.collectAsState()

    var showAllLater by remember { mutableStateOf(false) }
    var showCelebration by remember { mutableStateOf(false) }
    var showCustomDurationDialog by remember { mutableStateOf(false) }
    var selectedWorkMinutes by remember(focusDurations.first) { mutableIntStateOf(focusDurations.first) }

    val vitalityStage = GroveLevel.vitalityStageForWeeklySessions(weekly.focusSessions)
    val stageLabel = GroveLevel.vitalityLabel(vitalityStage)
    val greeting = remember { homeGreetingForHour(LocalTime.now().hour) }
    val isSessionActive = timerState.phase != FocusTimerState.Phase.IDLE
    val isWorkPhase = timerState.phase != FocusTimerState.Phase.BREAK
    val statusAccent = if (isSessionActive && !isWorkPhase) tokens.sage else tokens.beaconPlateCta
    val statusLabel =
        when {
            isSessionActive && isWorkPhase -> "Focus active"
            isSessionActive && !isWorkPhase -> "Break active"
            else -> "Next action"
        }

    LaunchedEffect(Unit) {
        vm.openMorningRitualIfNeeded()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.paper)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = AnchorSpacing.screenHorizontal, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.headlineMedium,
                        color = tokens.ink,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = formatAndroidHomeSummary(focusCountToday, todayFocusMinutes),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = tokens.muted
                    )
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = tokens.ink2
                    )
                }
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = AnchorSpacing.screenHorizontal)
                    .padding(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(AnchorSpacing.sectionGap)
            ) {
                // 1. Today's Tide real-data progress strip
                AnchorTodayTideStrip(
                    todayMinutes = todayFocusMinutes,
                    goalMinutes = focusDurations.first.coerceAtLeast(15),
                    stageLabel = stageLabel
                )

                // 2. Committed Deep-Harbor Next Action Plate
                AnchorBeaconPlate(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusAccent)
                            )
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = statusAccent,
                                maxLines = 1
                            )
                        }

                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(AnchorSpacing.radiusChip))
                                .clickable(role = Role.Button, onClickLabel = "Choose focus duration") {
                                    showCustomDurationDialog = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                                color = tokens.beaconPlatePill,
                                border = BorderStroke(1.dp, tokens.beaconPlateBorder),
                                shadowElevation = 0.dp,
                                tonalElevation = 0.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Timer,
                                        contentDescription = null,
                                        tint = tokens.beaconPlateInk2,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${selectedWorkMinutes}m",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFeatureSettings = "tnum"
                                        ),
                                        color = tokens.beaconPlateInk,
                                        maxLines = 1
                                    )
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = null,
                                        tint = tokens.beaconPlateInk2,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }

                    val title = now.task?.title ?: now.routine?.name ?: "Ready to anchor"
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.beaconPlateInk,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    val firstStep = now.task?.notes
                        ?.lineSequence()
                        ?.map(String::trim)
                        ?.firstOrNull { it.isNotEmpty() }
                        ?: now.routine?.cue?.takeIf { it.isNotBlank() }
                    if (firstStep != null) {
                        Text(
                            text = firstStep,
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.beaconPlateInk2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isSessionActive) {
                        val totalSecs = (if (isWorkPhase) timerState.totalWorkSeconds else timerState.breakMinutes * 60).coerceAtLeast(1)
                        val remainingSecs = timerState.remainingSeconds.coerceAtLeast(0)
                        val tideProgress = (1f - remainingSecs.toFloat() / totalSecs).coerceIn(0f, 1f)
                        val mins = remainingSecs / 60
                        val secs = remainingSecs % 60
                        val timeText = "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isWorkPhase) "Focus tide" else "Break tide",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.beaconPlateInk2
                            )
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                                color = tokens.beaconPlateInk
                            )
                        }

                        LinearProgressIndicator(
                            progress = { tideProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(AnchorSpacing.radiusPill)),
                            color = statusAccent,
                            trackColor = tokens.beaconPlatePill
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    if (isSessionActive) {
                        val remainingMin = (timerState.remainingSeconds / 60).coerceAtLeast(1)
                        val phaseLabel = if (isWorkPhase) "Open focus" else "Open break"
                        HarborCtaButton(
                            onClick = { vm.openFocusTab() },
                            onBeaconPlate = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$phaseLabel ($remainingMin min left)",
                                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")
                            )
                        }
                    } else {
                        HarborCtaButton(
                            onClick = { vm.startFocus(selectedWorkMinutes, focusDurations.second, now.task?.id) },
                            onBeaconPlate = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Start $selectedWorkMinutes min focus",
                                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")
                            )
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        now.task?.let { task ->
                            AnchorQuietButton(
                                label = "Mark done",
                                onClick = {
                                    vm.completeTask(task.id)
                                    showCelebration = true
                                },
                                outlined = false,
                                tint = tokens.beaconPlateInk2
                            )
                        }
                        val currentTask = now.task
                        AnchorQuietButton(
                            label = if (currentTask != null) "Break down with Captain" else "Ask Captain",
                            onClick = {
                                if (currentTask != null) {
                                    vm.openTaskDetail(currentTask)
                                    vm.breakdownTask(currentTask.title)
                                } else {
                                    onOpenChat()
                                }
                            },
                            outlined = false,
                            tint = tokens.beaconPlateCaptain,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                // 3. Quick start duration presets (Level 2 tonal well)
                AnchorWell(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnchorSectionHeader(
                        title = "Quick start",
                        trailingLabel = "Session length"
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(3 to "3m", 15 to "15m", 25 to "25m", 50 to "50m").forEach { (minutes, label) ->
                            AnchorChip(
                                label = label,
                                selected = selectedWorkMinutes == minutes,
                                onClick = { selectedWorkMinutes = minutes }
                            )
                        }
                        AnchorChip(
                            label = "Custom",
                            selected = selectedWorkMinutes !in listOf(3, 15, 25, 50),
                            onClick = { showCustomDurationDialog = true }
                        )
                    }
                }

                // 4. Later today & replan (Level 1 hairline rows directly on canvas)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AnchorSectionHeader(
                        title = "Today's plan",
                        trailingLabel = "Inbox",
                        onTrailingClick = onOpenInbox
                    )

                    if (later.isEmpty() && replan.isEmpty() && dueSoon.isEmpty()) {
                        HarborEmptyState(
                            title = "Nothing else queued today",
                            message = "Capture a thought or pull a task from your inbox whenever you are ready.",
                            actionLabel = "Open inbox",
                            onAction = onOpenInbox
                        )
                    } else {
                        val visibleLater = if (showAllLater) later else later.take(3)
                        visibleLater.forEachIndexed { index, item ->
                            val hasMoreRows = index < visibleLater.lastIndex || (later.size <= 3 && replan.isNotEmpty())
                            AnchorListRow(
                                title = item.title,
                                subtitle = item.notes
                                    .lineSequence()
                                    .map(String::trim)
                                    .firstOrNull { it.isNotEmpty() },
                                trailingText = "${item.durationMinutes}m",
                                minHeight = 48.dp,
                                showDivider = hasMoreRows,
                                trailing = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        AnchorQuietButton(
                                            label = "Focus",
                                            onClick = { vm.startFocus(workMinutes = item.durationMinutes, taskId = item.id) },
                                            outlined = false,
                                            tint = tokens.primary
                                        )
                                        AnchorQuietButton(
                                            label = "Done",
                                            onClick = {
                                                vm.completeTask(item.id)
                                                showCelebration = true
                                            },
                                            outlined = false,
                                            tint = tokens.muted
                                        )
                                    }
                                }
                            )
                        }

                        if (later.size > 3) {
                            AnchorQuietButton(
                                label = if (showAllLater) "Show fewer" else "Show all (${later.size})",
                                onClick = { showAllLater = !showAllLater },
                                outlined = false,
                                tint = tokens.primary
                            )
                            if (replan.isNotEmpty()) {
                                AnchorHairline()
                            }
                        }

                        if (replan.isNotEmpty()) {
                            AnchorListRow(
                                title = "${replan.size} missed ${if (replan.size == 1) "block" else "blocks"} to replan",
                                subtitle = "Open Replan in Plan to move or clear",
                                onClick = { vm.openReplanTab() },
                                minHeight = 48.dp,
                                showDivider = false,
                                trailing = {
                                    AnchorQuietButton(
                                        label = "Replan",
                                        onClick = { vm.openReplanTab() },
                                        outlined = false,
                                        tint = tokens.primary
                                    )
                                }
                            )
                        }

                        if (dueSoon.isNotEmpty()) {
                            Text(
                                text = "Due soon: ${dueSoon.joinToString { it.title }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.muted,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }

                // 5. Daily check-in well
                AnchorWell(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AnchorSectionHeader(
                        title = AppCopy.CHECK_IN_SECTION,
                        trailingLabel = "${workload.plannedMinutes}/${workload.capacityMinutes} min"
                    )
                    Text(
                        text = AppCopy.CHECK_IN_ACTIVATION_HINT,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.muted
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EnergyLevel.entries.forEach { level ->
                            AnchorChip(
                                label = CheckInCodec.activationLabel(level),
                                selected = energy == level,
                                onClick = { vm.setEnergy(level) }
                            )
                        }
                    }
                    Text(
                        text = AppCopy.CHECK_IN_TAGS_HINT,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.muted
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CheckInTag.entries.forEach { tag ->
                            AnchorChip(
                                label = CheckInCodec.tagLabel(tag),
                                selected = tag in checkInTags,
                                onClick = { vm.toggleCheckInTag(tag) }
                            )
                        }
                    }
                }

                // 6. Daily strategy well
                dailyStrategy?.let { card ->
                    AnchorWell(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnchorSectionHeader(
                            title = AppCopy.STRATEGY_TODAY,
                            trailingLabel = AppCopy.STRATEGY_TODAY_HINT
                        )
                        Text(
                            text = card.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = tokens.ink
                        )
                        Text(
                            text = card.body.lines().firstOrNull().orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        AnchorQuietButton(
                            label = "Read and act",
                            onClick = { vm.showCbt(card) },
                            outlined = true,
                            tint = tokens.primary
                        )
                    }
                }

                // 7. Optional Fun Links strip
                FunLinksStrip(
                    vm = vm,
                    onOpenSettings = onOpenSettings
                )
            }
        }

        val captureContentColor = if (tokens.isDark) tokens.captain else tokens.captainInk
        ExtendedFloatingActionButton(
            onClick = onPanicDump,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(AnchorSpacing.screenHorizontal)
                .pointerInput(onOpenThink) {
                    detectTapGestures(onLongPress = { onOpenThink() })
                },
            shape = RoundedCornerShape(AnchorSpacing.radiusButton),
            containerColor = tokens.captainSoft,
            contentColor = captureContentColor,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Capture",
                style = MaterialTheme.typography.labelLarge
            )
        }

        if (showCustomDurationDialog) {
            CustomDurationDialog(
                initialMinutes = selectedWorkMinutes,
                onDismiss = { showCustomDurationDialog = false },
                onConfirm = { customMins ->
                    selectedWorkMinutes = customMins
                    showCustomDurationDialog = false
                }
            )
        }

        CelebrationEffect(active = showCelebration, onFinished = { showCelebration = false })
    }
}
