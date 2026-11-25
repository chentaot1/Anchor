@file:OptIn(ExperimentalLayoutApi::class)

package com.anchor.adhd.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anchor.adhd.data.model.InboxState
import com.anchor.adhd.data.model.RoutineEntity
import com.anchor.adhd.data.model.TaskEntity
import com.anchor.adhd.ui.components.AnchorChip
import com.anchor.adhd.ui.components.AnchorHairline
import com.anchor.adhd.ui.components.AnchorListRow
import com.anchor.adhd.ui.components.AnchorQuietButton
import com.anchor.adhd.ui.components.AnchorSectionHeader
import com.anchor.adhd.ui.components.AnchorWell
import com.anchor.adhd.ui.components.AnchorWellTone
import com.anchor.adhd.ui.components.DayTimeline
import com.anchor.adhd.ui.components.HarborCtaButton
import com.anchor.adhd.ui.components.HarborEmptyState
import com.anchor.adhd.ui.components.HarborModalSheet
import com.anchor.adhd.ui.components.RoutineRunnerSheet
import com.anchor.adhd.ui.components.WorkloadBanner
import com.anchor.adhd.ui.copy.AppCopy
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens
import com.anchor.adhd.ui.vm.AnchorViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    vm: AnchorViewModel,
    initialTab: Int = 0,
    onTabChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    timelineFirst: Boolean = true,
    onOpenInbox: () -> Unit = {},
    showListView: Boolean = false,
    onToggleListView: () -> Unit = {},
) {
    if (timelineFirst) {
        PlanTimelineFirst(vm, modifier, onOpenInbox, showListView, onToggleListView)
        return
    }
    val tokens = LocalAnchorTokens.current
    val replan by vm.replanWithTasks.collectAsState()
    var tab by remember { mutableIntStateOf(initialTab) }
    LaunchedEffect(initialTab) { tab = initialTab }
    val tabs = listOf(
        "Timeline",
        "Inbox",
        if (replan.isNotEmpty()) "Replan (${replan.size})" else "Replan",
        "Assignments",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.paper)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AnchorSpacing.screenHorizontal, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Plan",
                style = MaterialTheme.typography.headlineMedium,
                color = tokens.ink,
                modifier = Modifier.semantics { heading() },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabs.forEachIndexed { index, title ->
                    AnchorChip(
                        label = title,
                        selected = tab == index,
                        onClick = {
                            tab = index
                            onTabChange(index)
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
        AnchorHairline()
        when (tab) {
            0 -> TimelineTab(vm, Modifier.weight(1f))
            1 -> InboxTab(vm, Modifier.weight(1f))
            2 -> ReplanTab(vm, Modifier.weight(1f))
            else -> MoreTab(vm, Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanTimelineFirst(
    vm: AnchorViewModel,
    modifier: Modifier,
    onOpenInbox: () -> Unit,
    showListView: Boolean,
    onToggleListView: () -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    val replan by vm.replanWithTasks.collectAsState()
    val planningSettings by vm.planningSettings.collectAsState()
    var showInbox by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var moreInitialSheetTab by remember { mutableIntStateOf(0) }

    if (showInbox) {
        HarborModalSheet(onDismissRequest = { showInbox = false }) {
            Column(Modifier.fillMaxWidth().height(560.dp)) {
                InboxTab(vm, Modifier.fillMaxSize())
            }
        }
    }

    if (showMoreSheet) {
        HarborModalSheet(onDismissRequest = { showMoreSheet = false }) {
            var sheetTab by remember(moreInitialSheetTab) { mutableIntStateOf(moreInitialSheetTab) }
            Column(Modifier.fillMaxWidth().height(560.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AnchorSpacing.screenHorizontal, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AnchorChip(
                        label = if (replan.isNotEmpty()) "Replan (${replan.size})" else "Replan",
                        selected = sheetTab == 0,
                        onClick = { sheetTab = 0 },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                    AnchorChip(
                        label = "Assignments & routines",
                        selected = sheetTab == 1,
                        onClick = { sheetTab = 1 },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
                AnchorHairline()
                when (sheetTab) {
                    0 -> ReplanTab(vm, Modifier.weight(1f))
                    else -> MoreTab(vm, Modifier.weight(1f))
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.paper)
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AnchorSpacing.screenHorizontal, end = 12.dp, top = 12.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Plan",
                    style = MaterialTheme.typography.headlineMedium,
                    color = tokens.ink,
                    modifier = Modifier.semantics { heading() },
                )
                AnchorQuietButton(
                    label = "Manage",
                    onClick = {
                        moreInitialSheetTab = 1
                        showMoreSheet = true
                    },
                    outlined = false,
                    tint = tokens.primary,
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = AnchorSpacing.screenHorizontal, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnchorChip(
                    label = "Timeline",
                    selected = !showListView,
                    onClick = { if (showListView) onToggleListView() },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                AnchorChip(
                    label = "List",
                    selected = showListView,
                    onClick = { if (!showListView) onToggleListView() },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                AnchorChip(
                    label = "Today only",
                    selected = planningSettings.todayOnlyMode,
                    onClick = { vm.setTodayOnlyMode(!planningSettings.todayOnlyMode) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }

            if (replan.isNotEmpty()) {
                val itemLabel = if (replan.size == 1) "item" else "items"
                AnchorWell(
                    modifier = Modifier
                        .padding(horizontal = AnchorSpacing.screenHorizontal, vertical = 6.dp)
                        .clip(RoundedCornerShape(AnchorSpacing.radiusWell))
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button) {
                            moreInitialSheetTab = 0
                            showMoreSheet = true
                        },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.EventRepeat,
                            contentDescription = null,
                            tint = tokens.beacon,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "${replan.size} $itemLabel to replan",
                            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                            color = tokens.ink,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "Review",
                            style = MaterialTheme.typography.labelLarge,
                            color = tokens.primary,
                        )
                    }
                }
            }

            if (showListView) {
                PlanCompactListTab(vm, Modifier.weight(1f)) {
                    showInbox = true
                    onOpenInbox()
                }
            } else {
                TimelineTab(vm, Modifier.weight(1f))
            }
        }

        HarborCtaButton(
            onClick = {
                showInbox = true
                onOpenInbox()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(AnchorSpacing.screenHorizontal)
                .heightIn(min = 48.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Capture", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PlanCompactListTab(
    vm: AnchorViewModel,
    modifier: Modifier,
    onCapture: () -> Unit = {},
) {
    val tokens = LocalAnchorTokens.current
    val scheduled by vm.scheduled.collectAsState()
    val workload by vm.workload.collectAsState()
    val weekly by vm.weeklySummary.collectAsState()
    val nowTask by vm.nowTask.collectAsState()
    val timeFmt = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.US) }
    val zone = remember { ZoneId.systemDefault() }

    Column(
        modifier = modifier.padding(horizontal = AnchorSpacing.screenHorizontal, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(AnchorSpacing.itemGap),
    ) {
        WorkloadBanner(
            workload = workload,
            estimateErrorPercent = weekly.avgEstimateErrorPercent.takeIf { it > 20 },
        )
        AnchorSectionHeader(
            title = "Today's blocks",
            trailingLabel = "${scheduled.size}",
        )
        if (scheduled.isEmpty()) {
            HarborEmptyState(
                title = "No blocks scheduled",
                message = AppCopy.SCHEDULE_EMPTY,
                actionLabel = "Capture",
                onAction = onCapture,
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 76.dp),
            ) {
                itemsIndexed(scheduled, key = { _, task -> task.id }) { index, task ->
                    val start = task.scheduledStartMillis
                    val timeLabel = start?.let {
                        timeFmt.format(Instant.ofEpochMilli(it).atZone(zone))
                    } ?: "All day"
                    val isNow = task.id == nowTask.task?.id
                    val subtitle = if (isNow) "$timeLabel, next action" else timeLabel
                    AnchorListRow(
                        title = task.title,
                        subtitle = subtitle,
                        trailingText = "${task.durationMinutes} min",
                        onClick = { vm.openTaskDetail(task) },
                        minHeight = 48.dp,
                        showDivider = index < scheduled.lastIndex,
                        leading = {
                            Icon(
                                imageVector = if (isNow) Icons.Outlined.Bolt else Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = if (isNow) tokens.beacon else tokens.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineTab(vm: AnchorViewModel, modifier: Modifier) {
    val tokens = LocalAnchorTokens.current
    val scheduled by vm.scheduled.collectAsState()
    val calendar by vm.calendarToday.collectAsState()
    val workload by vm.workload.collectAsState()
    val weekly by vm.weeklySummary.collectAsState()
    val activeFocusTaskId by vm.activeFocusTaskId.collectAsState()
    val nowTask by vm.nowTask.collectAsState()
    var scheduleTask by remember { mutableStateOf<TaskEntity?>(null) }
    var hour by remember { mutableStateOf("9") }
    var minute by remember { mutableStateOf("0") }

    Column(
        modifier = modifier.padding(horizontal = AnchorSpacing.screenHorizontal, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(AnchorSpacing.itemGap),
    ) {
        WorkloadBanner(
            workload = workload,
            estimateErrorPercent = weekly.avgEstimateErrorPercent.takeIf { it > 20 },
        )
        AnchorSectionHeader(
            title = "Today",
            trailingLabel = "Tap a block to edit",
        )
        DayTimeline(
            date = LocalDate.now(),
            scheduled = scheduled,
            calendarEvents = calendar,
            onTaskClick = { vm.openTaskDetail(it) },
            onCalendarEventClick = { vm.openCalendarEventDetail(it) },
            onTaskReschedule = { task, startMillis ->
                vm.rescheduleTask(task.id, startMillis, task.durationMinutes)
            },
            spotlightTaskId = nowTask.task?.id,
            lockedTaskId = activeFocusTaskId,
            onLockedDragAttempt = { vm.notifyLockedTaskDrag() },
            modifier = Modifier.weight(1f),
        )
        scheduleTask?.let { task ->
            AnchorWell(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Schedule: ${task.title}",
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.ink,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hour,
                        onValueChange = { hour = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hour (0-23)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                        colors = planFieldColors(),
                    )
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { minute = it.filter(Char::isDigit).take(2) },
                        label = { Text("Min (0-59)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                        colors = planFieldColors(),
                    )
                }
                HarborCtaButton(
                    onClick = {
                        vm.scheduleTask(
                            task.id,
                            (hour.toIntOrNull() ?: 9).coerceIn(0, 23),
                            (minute.toIntOrNull() ?: 0).coerceIn(0, 59),
                            task.durationMinutes,
                        )
                        scheduleTask = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text("Set time", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun InboxTab(vm: AnchorViewModel, modifier: Modifier) {
    val tokens = LocalAnchorTokens.current
    val captainLabelColor = if (tokens.isDark) tokens.captain else tokens.captainInk
    val inbox by vm.inboxTodaySorted.collectAsState()
    val someday by vm.inboxSomeday.collectAsState()
    val waiting by vm.inboxWaiting.collectAsState()
    val planningSettings by vm.planningSettings.collectAsState()
    val aiLoading by vm.aiLoading.collectAsState()
    var newTask by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(InboxState.TODAY) }
    val todayOnly = planningSettings.todayOnlyMode
    var scheduleMode by remember { mutableStateOf("all_day") }
    var captureHour by remember { mutableStateOf("9") }
    var captureMinute by remember { mutableStateOf("0") }

    LaunchedEffect(todayOnly) {
        if (todayOnly) filter = InboxState.TODAY
    }

    val filterLabel = when (filter) {
        InboxState.TODAY -> "today"
        InboxState.WAITING -> "later"
        InboxState.SOMEDAY -> "someday"
    }

    val list = when (filter) {
        InboxState.TODAY -> inbox
        InboxState.SOMEDAY -> someday
        InboxState.WAITING -> waiting
    }

    Column(
        modifier = modifier.padding(horizontal = AnchorSpacing.screenHorizontal, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(AnchorSpacing.itemGap),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AnchorSpacing.itemGap),
        ) {
            item(key = "quick-capture") {
                AnchorWell(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newTask,
                        onValueChange = { newTask = it },
                        label = { Text("Quick capture") },
                        placeholder = { Text("Task or note", color = tokens.muted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                        colors = planFieldColors(),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        InboxState.entries.filter { !todayOnly || it == InboxState.TODAY }.forEach { state ->
                            val label = when (state) {
                                InboxState.TODAY -> "Today"
                                InboxState.WAITING -> "Later"
                                InboxState.SOMEDAY -> "Someday"
                            }
                            AnchorChip(
                                label = label,
                                selected = filter == state,
                                onClick = { filter = state },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                    if (filter == InboxState.TODAY) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            AnchorChip(
                                label = "All day",
                                selected = scheduleMode == "all_day",
                                onClick = { scheduleMode = "all_day" },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                            AnchorChip(
                                label = "Pick a time",
                                selected = scheduleMode == "pick_time",
                                onClick = { scheduleMode = "pick_time" },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                        if (scheduleMode == "pick_time") {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                OutlinedTextField(
                                    value = captureHour,
                                    onValueChange = { captureHour = it.filter(Char::isDigit).take(2) },
                                    label = { Text("Hour (0-23)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                                    colors = planFieldColors(),
                                )
                                OutlinedTextField(
                                    value = captureMinute,
                                    onValueChange = { captureMinute = it.filter(Char::isDigit).take(2) },
                                    label = { Text("Min (0-59)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                                    colors = planFieldColors(),
                                )
                            }
                        }
                    }
                    HarborCtaButton(
                        onClick = {
                            if (newTask.isNotBlank()) {
                                val startMillis = if (filter == InboxState.TODAY && scheduleMode == "pick_time") {
                                    val h = (captureHour.toIntOrNull() ?: 9).coerceIn(0, 23)
                                    val m = (captureMinute.toIntOrNull() ?: 0).coerceIn(0, 59)
                                    val zone = ZoneId.systemDefault()
                                    LocalDate.now().atTime(h, m).atZone(zone).toInstant().toEpochMilli()
                                } else {
                                    null
                                }
                                vm.addInbox(newTask, filter, startMillis)
                                newTask = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        enabled = newTask.isNotBlank(),
                    ) {
                        Text("Add to $filterLabel", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            if (list.isEmpty()) {
                item(key = "empty") {
                    HarborEmptyState(
                        title = "Inbox is clear",
                        message = AppCopy.INBOX_EMPTY,
                    )
                }
            } else {
                itemsIndexed(list, key = { _, task -> task.id }) { index, task ->
                    InboxTaskRow(
                        task = task,
                        vm = vm,
                        aiLoading = aiLoading,
                        showDivider = index < list.lastIndex,
                    )
                }
            }
        }

        AnchorQuietButton(
            label = "Triage inbox with Captain",
            onClick = { vm.triageInbox() },
            outlined = true,
            tint = captainLabelColor,
            enabled = !aiLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )
    }
}

@Composable
private fun InboxTaskRow(
    task: TaskEntity,
    vm: AnchorViewModel,
    aiLoading: Boolean,
    showDivider: Boolean,
) {
    val tokens = LocalAnchorTokens.current
    val captainLabelColor = if (tokens.isDark) tokens.captain else tokens.captainInk
    Column(modifier = Modifier.fillMaxWidth()) {
        AnchorListRow(
            title = task.title,
            subtitle = task.notes.takeIf { it.isNotBlank() },
            trailingText = "${task.durationMinutes} min",
            onClick = { vm.openTaskDetail(task) },
            minHeight = 48.dp,
            showDivider = false,
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AnchorQuietButton(
                label = "Done",
                onClick = { vm.completeTask(task.id) },
                outlined = false,
                tint = tokens.sage,
                modifier = Modifier.heightIn(min = 48.dp),
            )
            AnchorQuietButton(
                label = "Break down",
                onClick = { vm.breakdownTask(task.title) },
                outlined = false,
                tint = captainLabelColor,
                enabled = !aiLoading,
                modifier = Modifier.heightIn(min = 48.dp),
            )
            AnchorQuietButton(
                label = "9 am",
                onClick = { vm.scheduleTask(task.id, 9, 0, task.durationMinutes) },
                outlined = false,
                tint = tokens.primary,
                modifier = Modifier.heightIn(min = 48.dp),
            )
            AnchorQuietButton(
                label = "Someday",
                onClick = { vm.moveInbox(task.id, InboxState.SOMEDAY) },
                outlined = false,
                tint = tokens.muted,
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        if (showDivider) {
            AnchorHairline()
        }
    }
}

@Composable
private fun ReplanTab(vm: AnchorViewModel, modifier: Modifier) {
    val tokens = LocalAnchorTokens.current
    val captainLabelColor = if (tokens.isDark) tokens.captain else tokens.captainInk
    val replan by vm.replanWithTasks.collectAsState()
    val aiPreview by vm.replanAiPreview.collectAsState()
    val aiLoading by vm.aiLoading.collectAsState()

    Column(
        modifier = modifier.padding(horizontal = AnchorSpacing.screenHorizontal, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(AnchorSpacing.itemGap),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AnchorSectionHeader(
                title = "Replan queue",
                trailingLabel = "${replan.size}",
            )
            Text(
                text = AppCopy.REPLAN_SECTION,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.muted,
            )
        }

        if (replan.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                HarborEmptyState(
                    title = "All caught up",
                    message = AppCopy.REPLAN_EMPTY,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(replan, key = { _, pair -> pair.first.id }) { index, (item, task) ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AnchorListRow(
                            title = task?.title ?: "Task #${item.taskId}",
                            trailingText = task?.let { "${it.durationMinutes} min" },
                            onClick = task?.let { t -> { vm.openTaskDetail(t) } },
                            minHeight = 48.dp,
                            showDivider = false,
                        )
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            task?.let { t ->
                                AnchorQuietButton(
                                    label = "Today",
                                    onClick = { vm.replanRescheduleToday(item.id, t.id, t.durationMinutes) },
                                    outlined = false,
                                    tint = tokens.primary,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                )
                                AnchorQuietButton(
                                    label = "Someday",
                                    onClick = { vm.replanDeferSomeday(item.id, t.id) },
                                    outlined = false,
                                    tint = tokens.muted,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                )
                            }
                            AnchorQuietButton(
                                label = "Dismiss",
                                onClick = { vm.dismissReplan(item.id) },
                                outlined = false,
                                tint = tokens.clayRose,
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                        if (index < replan.lastIndex) {
                            AnchorHairline()
                        }
                    }
                }
            }
        }

        AnchorQuietButton(
            label = "Ask Captain to replan",
            onClick = { vm.replanAssist() },
            outlined = true,
            tint = captainLabelColor,
            enabled = !aiLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )

        aiPreview?.let { preview ->
            AnchorWell(
                tone = AnchorWellTone.Captain,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnchorSectionHeader(title = "Captain suggestions")
                preview.message?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.ink,
                    )
                }
                preview.recommended_titles.forEach { title ->
                    Text(
                        text = "Today: $title",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.ink2,
                    )
                }
                preview.defer_titles.forEach { title ->
                    Text(
                        text = "Someday: $title",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.ink2,
                    )
                }
                HarborCtaButton(
                    onClick = { vm.applyReplanAi() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text("Apply suggestions", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreTab(vm: AnchorViewModel, modifier: Modifier) {
    val tokens = LocalAnchorTokens.current
    val captainLabelColor = if (tokens.isDark) tokens.captain else tokens.captainInk
    val assignments by vm.assignments.collectAsState()
    val routines by vm.routines.collectAsState()
    val dateFmt = remember { DateTimeFormatter.ofPattern("MMM d", Locale.US) }
    val zone = remember { ZoneId.systemDefault() }

    var assignTitle by remember { mutableStateOf("") }
    var course by remember { mutableStateOf("") }
    var dueDays by remember { mutableStateOf("7") }
    var showSyllabusDialog by remember { mutableStateOf(false) }
    var syllabusText by remember { mutableStateOf("") }
    var runningRoutine by remember { mutableStateOf<RoutineEntity?>(null) }
    var showRoutineForm by remember { mutableStateOf(false) }
    var routineName by remember { mutableStateOf("") }
    var routineCue by remember { mutableStateOf("") }
    var routineIfThen by remember { mutableStateOf("") }
    var routineSteps by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .padding(horizontal = AnchorSpacing.screenHorizontal, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AnchorSpacing.sectionGap),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AnchorSpacing.itemGap)) {
            AnchorSectionHeader(
                title = "Assignments",
                trailingLabel = "${assignments.size}",
            )
            AnchorWell(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = assignTitle,
                    onValueChange = { assignTitle = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                    colors = planFieldColors(),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = course,
                        onValueChange = { course = it },
                        label = { Text("Course") },
                        modifier = Modifier.weight(1.2f),
                        singleLine = true,
                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                        colors = planFieldColors(),
                    )
                    OutlinedTextField(
                        value = dueDays,
                        onValueChange = { dueDays = it.filter(Char::isDigit).take(3) },
                        label = { Text("Due in days") },
                        modifier = Modifier.weight(0.8f),
                        singleLine = true,
                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                        colors = planFieldColors(),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    HarborCtaButton(
                        onClick = {
                            if (assignTitle.isNotBlank()) {
                                val days = dueDays.toIntOrNull()?.coerceAtLeast(0) ?: 7
                                val due = System.currentTimeMillis() + days * 86_400_000L
                                vm.addAssignment(assignTitle, course, due)
                                assignTitle = ""
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        enabled = assignTitle.isNotBlank(),
                    ) {
                        Text(
                            text = "Add assignment",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    AnchorQuietButton(
                        label = "Paste syllabus",
                        onClick = { showSyllabusDialog = true },
                        outlined = true,
                        tint = tokens.primary,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }

            if (assignments.isEmpty()) {
                HarborEmptyState(
                    title = "No assignments yet",
                    message = "Add an assignment above or paste a course syllabus to import deadlines.",
                    actionLabel = "Paste syllabus",
                    onAction = { showSyllabusDialog = true },
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    assignments.forEachIndexed { index, assignment ->
                        val dueDateLabel = dateFmt.format(Instant.ofEpochMilli(assignment.dueAtMillis).atZone(zone))
                        AnchorListRow(
                            title = assignment.title,
                            subtitle = assignment.course.takeIf { it.isNotBlank() },
                            trailingText = dueDateLabel,
                            minHeight = 48.dp,
                            showDivider = false,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AnchorQuietButton(
                                label = "Complete",
                                onClick = { vm.completeAssignment(assignment.id) },
                                outlined = false,
                                tint = tokens.sage,
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                            AnchorQuietButton(
                                label = "Delete",
                                onClick = { vm.deleteAssignment(assignment.id) },
                                outlined = false,
                                tint = tokens.clayRose,
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                        if (index < assignments.lastIndex) {
                            AnchorHairline()
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(AnchorSpacing.itemGap)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnchorSectionHeader(
                    title = "Routines",
                    trailingLabel = "${routines.size}",
                    modifier = Modifier.weight(1f),
                )
                AnchorQuietButton(
                    label = if (showRoutineForm) "Cancel" else "Add routine",
                    onClick = { showRoutineForm = !showRoutineForm },
                    outlined = false,
                    tint = tokens.primary,
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
            if (showRoutineForm) {
                AnchorWell(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = routineName,
                        onValueChange = { routineName = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                        colors = planFieldColors(),
                    )
                    OutlinedTextField(
                        value = routineCue,
                        onValueChange = { routineCue = it },
                        label = { Text("Cue") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                        colors = planFieldColors(),
                    )
                    OutlinedTextField(
                        value = routineIfThen,
                        onValueChange = { routineIfThen = it },
                        label = { Text("If-then (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                        colors = planFieldColors(),
                    )
                    AnchorQuietButton(
                        label = "Ask Captain for if-then",
                        onClick = {
                            vm.generateIfThenForRoutine(routineName, routineCue) { generated ->
                                generated?.let { routineIfThen = it }
                            }
                        },
                        outlined = false,
                        tint = captainLabelColor,
                        enabled = routineName.isNotBlank(),
                        modifier = Modifier.heightIn(min = 48.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                    OutlinedTextField(
                        value = routineSteps,
                        onValueChange = { routineSteps = it },
                        label = { Text("Steps (one per line)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                        colors = planFieldColors(),
                    )
                    AnchorQuietButton(
                        label = "Save routine",
                        onClick = {
                            vm.createRoutine(
                                routineName,
                                routineCue,
                                routineIfThen.ifBlank { null },
                                routineSteps.lines().filter { it.isNotBlank() },
                            )
                            routineName = ""
                            routineCue = ""
                            routineIfThen = ""
                            routineSteps = ""
                            showRoutineForm = false
                        },
                        outlined = true,
                        tint = tokens.primary,
                        enabled = routineName.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    )
                }
            }

            if (routines.isEmpty() && !showRoutineForm) {
                HarborEmptyState(
                    title = "No routines yet",
                    message = AppCopy.ROUTINES_EMPTY,
                    actionLabel = "Add routine",
                    onAction = { showRoutineForm = true },
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    routines.forEachIndexed { index, routine ->
                        AnchorListRow(
                            title = routine.name,
                            subtitle = (routine.ifThen ?: routine.cue).takeIf { it.isNotBlank() },
                            minHeight = 48.dp,
                            showDivider = false,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AnchorQuietButton(
                                label = "Run routine",
                                onClick = { runningRoutine = routine },
                                outlined = false,
                                tint = tokens.primary,
                                modifier = Modifier.heightIn(min = 48.dp),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                            AnchorQuietButton(
                                label = "Delete",
                                onClick = { vm.deleteRoutine(routine.id) },
                                outlined = false,
                                tint = tokens.clayRose,
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                        if (index < routines.lastIndex) {
                            AnchorHairline()
                        }
                    }
                }
            }
        }
    }

    if (showSyllabusDialog) {
        AlertDialog(
            onDismissRequest = { showSyllabusDialog = false },
            containerColor = tokens.surface,
            titleContentColor = tokens.ink,
            textContentColor = tokens.ink2,
            shape = RoundedCornerShape(AnchorSpacing.radiusSheet),
            title = {
                Text(
                    text = "Paste syllabus",
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.ink,
                )
            },
            text = {
                OutlinedTextField(
                    value = syllabusText,
                    onValueChange = { syllabusText = it },
                    label = { Text("Syllabus text or schedule table") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 12,
                    shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                    colors = planFieldColors(),
                )
            },
            confirmButton = {
                HarborCtaButton(
                    onClick = {
                        vm.importSyllabusText(syllabusText)
                        syllabusText = ""
                        showSyllabusDialog = false
                    },
                    enabled = syllabusText.isNotBlank(),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Import", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                AnchorQuietButton(
                    label = "Cancel",
                    onClick = { showSyllabusDialog = false },
                    outlined = false,
                    tint = tokens.muted,
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            },
        )
    }

    runningRoutine?.let { routine ->
        RoutineRunnerModal(routine, vm) { runningRoutine = null }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineRunnerModal(
    routine: RoutineEntity,
    vm: AnchorViewModel,
    onDismiss: () -> Unit,
) {
    val steps by vm.routineSteps(routine.id).collectAsState()
    HarborModalSheet(onDismissRequest = onDismiss) {
        RoutineRunnerSheet(
            routine = routine,
            steps = steps,
            onStartFocus = {
                onDismiss()
                vm.startShortFocus()
            },
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun planFieldColors() =
    LocalAnchorTokens.current.let { tokens ->
        OutlinedTextFieldDefaults.colors(
            focusedBorderColor = tokens.primary,
            unfocusedBorderColor = tokens.fieldEdge,
            focusedTextColor = tokens.ink,
            unfocusedTextColor = tokens.ink,
            focusedContainerColor = tokens.surface,
            unfocusedContainerColor = tokens.surface,
            focusedLabelColor = tokens.primary,
            unfocusedLabelColor = tokens.muted,
            cursorColor = tokens.primary,
        )
    }
