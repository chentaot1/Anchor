package com.anchor.adhd.ui.grove

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.anchor.adhd.ui.components.CustomDurationDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.anchor.adhd.service.FocusTimerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.anchor.adhd.domain.GroveLevel
import com.anchor.adhd.ui.components.CelebrationEffect
import com.anchor.adhd.ui.components.DailyCheckInSection
import com.anchor.adhd.ui.components.DailyStrategyCard
import com.anchor.adhd.ui.theme.AnchorColors
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.vm.AnchorViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroveHomeScreen(
    vm: AnchorViewModel,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onOpenForestGallery: () -> Unit = {},
    onOpenInbox: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onPanicDump: () -> Unit = onOpenChat,
    onOpenThink: () -> Unit = onOpenChat
) {
    val weekly by vm.weeklySummary.collectAsState()
    val trees by vm.treeCount.collectAsState()
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
    var restExpanded by remember { mutableStateOf(false) }
    var showCelebration by remember { mutableStateOf(false) }
    var quickCapture by remember { mutableStateOf("") }
    var showCustomDurationDialog by remember { mutableStateOf(false) }
    var selectedWorkMinutes by remember(focusDurations.first) { mutableIntStateOf(focusDurations.first) }
    val vitalityStage = GroveLevel.vitalityStageForWeeklySessions(weekly.focusSessions)

    LaunchedEffect(Unit) {
        vm.openMorningRitualIfNeeded()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Today", style = MaterialTheme.typography.headlineMedium)
                    Text("One small step at a time", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
            Column(
                Modifier.padding(horizontal = AnchorSpacing.screenHorizontal, vertical = AnchorSpacing.screenVertical).padding(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(AnchorSpacing.sectionGap)
            ) {
                // Living Maritime Beacon ("NOW" Card)
                val isSessionActive = timerState.phase != FocusTimerState.Phase.IDLE
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Soft ambient amber halo back-glow
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(4.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        AnchorColors.HarborAction.copy(alpha = if (isSessionActive) 0.18f else 0.08f),
                                        Color.Transparent
                                    )
                                ),
                                shape = RoundedCornerShape(24.dp)
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF141F32),
                                        Color(0xFF0D1522)
                                    )
                                )
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    Brush.verticalGradient(
                                        listOf(
                                            AnchorColors.HarborAction.copy(alpha = 0.55f),
                                            Color(0xFF1E2D45)
                                        )
                                    )
                                ),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(18.dp)
                    ) {
                        // Beacon Header Row: Pulsing Amber Anchor Dot + N O W Nautical Tracking Badge
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
                                        .background(AnchorColors.HarborAction, shape = CircleShape)
                                )
                                Text(
                                    "N O W",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 2.sp
                                    ),
                                    color = AnchorColors.HarborAction
                                )
                            }

                            if (isSessionActive) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AnchorColors.HarborAction.copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, AnchorColors.HarborAction.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        "FOCUS ACTIVE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        ),
                                        color = AnchorColors.HarborAction,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            } else {
                                Text(
                                    "ANCHOR BEACON",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 1.sp
                                    ),
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val title = now.task?.title ?: now.routine?.name ?: "Ready to Anchor"
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                lineHeight = 28.sp
                            ),
                            color = Color.White
                        )

                        // Live chronometer tide progress bar when active
                        if (isSessionActive) {
                            val totalSecs = timerState.totalWorkSeconds.coerceAtLeast(1)
                            val remainingSecs = timerState.remainingSeconds
                            val tideProgress = (1f - remainingSecs.toFloat() / totalSecs).coerceIn(0f, 1f)
                            Spacer(modifier = Modifier.height(14.dp))
                            LinearProgressIndicator(
                                progress = { tideProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = AnchorColors.HarborAction,
                                trackColor = Color(0xFF1E2D45)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Responsive action controls
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isSessionActive) {
                                val remainingMin = (timerState.remainingSeconds / 60).coerceAtLeast(1)
                                val phaseLabel = if (timerState.phase == FocusTimerState.Phase.WORK) "In Focus" else "In Break"
                                Button(
                                    onClick = { vm.openFocusTab() },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AnchorColors.HarborAction,
                                        contentColor = Color(0xFF0A0E18)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "$phaseLabel (${remainingMin}m)",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { vm.startFocus(selectedWorkMinutes, focusDurations.second, now.task?.id) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AnchorColors.HarborAction,
                                        contentColor = Color(0xFF0A0E18)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Start focus · ${selectedWorkMinutes} min",
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                OutlinedButton(
                                    onClick = { showCustomDurationDialog = true },
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, Color(0xFF26354D)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFFCBD5E1)
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Timer,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text("Duration", modifier = Modifier.padding(start = 6.dp))
                                }
                            }

                            now.task?.let { task ->
                                OutlinedButton(
                                    onClick = {
                                        vm.completeTask(task.id)
                                        showCelebration = true
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, Color(0xFF26354D)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFF94A3B8)
                                    )
                                ) {
                                    Text("Done")
                                }
                            }
                        }
                    }
                }
                FunLinksStrip(
                    vm = vm,
                    onOpenSettings = onOpenSettings
                )
                dailyStrategy?.let { card ->
                    DailyStrategyCard(card = card, onOpen = { vm.showCbt(card) })
                }
                TextButton(onClick = { restExpanded = !restExpanded }) {
                    Text(if (restExpanded) "Hide rest of today" else "Rest of today (${later.size + replan.size})")
                }
                if (restExpanded) {
                    if (replan.isNotEmpty()) {
                        TextButton(onClick = { vm.openReplanTab() }) {
                            Text("${replan.size} items to replan")
                        }
                    }
                    if (dueSoon.isNotEmpty()) {
                        Text("Due soon: ${dueSoon.joinToString { it.title }}", style = MaterialTheme.typography.bodySmall)
                    }
                    DailyCheckInSection(
                        activation = energy,
                        selectedTags = checkInTags,
                        onActivationChange = { vm.setEnergy(it) },
                        onTagToggle = { vm.toggleCheckInTag(it) }
                    )
                    Text(
                        workload.let { "Capacity: ${it.plannedMinutes}/${it.capacityMinutes} min" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onPanicDump,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(AnchorSpacing.screenHorizontal)
                .pointerInput(onOpenThink) {
                    detectTapGestures(onLongPress = { onOpenThink() })
                },
            containerColor = AnchorColors.HarborAction,
            contentColor = Color(0xFF0A0E18)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("Capture", fontWeight = FontWeight.Bold)
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
