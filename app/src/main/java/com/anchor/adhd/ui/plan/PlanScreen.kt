@file:OptIn(ExperimentalLayoutApi::class)

package com.anchor.adhd.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anchor.adhd.data.model.InboxState
import com.anchor.adhd.data.model.TaskEntity
import com.anchor.adhd.ui.components.AnchorCard
import com.anchor.adhd.ui.components.EmptyState
import com.anchor.adhd.ui.components.ItemSpacing
import com.anchor.adhd.ui.components.ScreenPadding
import com.anchor.adhd.ui.components.SectionHeader
import com.anchor.adhd.ui.components.SectionSpacing
import com.anchor.adhd.ui.components.DayTimeline
import com.anchor.adhd.ui.components.WorkloadBanner
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.anchor.adhd.ui.components.RoutineRunnerSheet
import com.anchor.adhd.ui.copy.AppCopy
import com.anchor.adhd.ui.vm.AnchorViewModel
import androidx.compose.material3.ExperimentalMaterial3Api
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import com.anchor.adhd.ui.components.CapacityGauge
import com.anchor.adhd.ui.theme.AnchorColors
import com.anchor.adhd.ui.theme.AnchorSpacing
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment

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
    onToggleListView: () -> Unit = {}
) {
    if (timelineFirst) {
        PlanTimelineFirst(vm, modifier, onOpenInbox, showListView, onToggleListView)
        return
    }
    var tab by remember { mutableIntStateOf(initialTab) }
    LaunchedEffect(initialTab) { tab = initialTab }
    val tabs = listOf("Timeline", "Inbox", "Replan", "More")

    Column(modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = tab,
            edgePadding = ScreenPadding,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = tab == i,
                    onClick = {
                        tab = i
                        onTabChange(i)
                    },
                    text = { Text(title) }
                )
            }
        }
        when (tab) {
            0 -> TimelineTab(vm, Modifier.weight(1f))
            1 -> InboxTab(vm, Modifier.weight(1f))
            2 -> ReplanTab(vm, Modifier.weight(1f))
            3 -> MoreTab(vm, Modifier.weight(1f))
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
    onToggleListView: () -> Unit
) {
    val replan by vm.replanWithTasks.collectAsState()
    val planningSettings by vm.planningSettings.collectAsState()
    var showInbox by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    val inboxSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val moreSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (showInbox) {
        ModalBottomSheet(onDismissRequest = { showInbox = false }, sheetState = inboxSheetState) {
            InboxTab(vm, Modifier.fillMaxWidth())
        }
    }
    if (showMoreSheet) {
        ModalBottomSheet(onDismissRequest = { showMoreSheet = false }, sheetState = moreSheetState) {
            var sheetTab by remember { mutableIntStateOf(0) }
            val sheetTabs = listOf("Replan", "Routines & Assignments")
            Column(Modifier.fillMaxWidth().height(500.dp)) {
                androidx.compose.material3.TabRow(
                    selectedTabIndex = sheetTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    sheetTabs.forEachIndexed { i, title ->
                        Tab(
                            selected = sheetTab == i,
                            onClick = { sheetTab = i },
                            text = { Text(title) }
                        )
                    }
                }
                when (sheetTab) {
                    0 -> ReplanTab(vm, Modifier.weight(1f))
                    1 -> MoreTab(vm, Modifier.weight(1f))
                }
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AnchorSpacing.screenHorizontal, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = planningSettings.todayOnlyMode,
                    onClick = { vm.setTodayOnlyMode(!planningSettings.todayOnlyMode) },
                    label = { Text("Today only") }
                )
                TextButton(onClick = { showMoreSheet = true }) {
                    Text("Manage")
                }
                FilterChip(
                    selected = showListView,
                    onClick = onToggleListView,
                    label = { Text(if (showListView) "List" else "Timeline") }
                )
            }
            if (replan.isNotEmpty()) {
                TextButton(
                    onClick = { showMoreSheet = true },
                    modifier = Modifier.padding(horizontal = AnchorSpacing.screenHorizontal)
                ) {
                    Text("${replan.size} items to replan")
                }
            }
            if (showListView) {
                PlanCompactListTab(vm, Modifier.weight(1f))
            } else {
                TimelineTab(vm, Modifier.weight(1f))
            }
        }
        FloatingActionButton(
            onClick = { showInbox = true; onOpenInbox() },
            containerColor = AnchorColors.HarborPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(AnchorSpacing.screenHorizontal)
        ) {
            Text("+")
        }
    }
}

@Composable
private fun PlanCompactListTab(vm: AnchorViewModel, modifier: Modifier) {
    val scheduled by vm.scheduled.collectAsState()
    val nowTask by vm.nowTask.collectAsState()
    val timeFmt = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val zone = remember { ZoneId.systemDefault() }

    Column(modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
        SectionHeader("Today", "Compact list")
        if (scheduled.isEmpty()) {
            EmptyState("Nothing scheduled. Tap + to capture.")
        } else {
            scheduled.forEach { task ->
                val start = task.scheduledStartMillis
                val timeLabel = start?.let {
                    timeFmt.format(Instant.ofEpochMilli(it).atZone(zone))
                } ?: "Unscheduled"
                AnchorCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.openTaskDetail(task) }
                ) {
                    Text(timeLabel, style = MaterialTheme.typography.labelMedium, color = AnchorColors.HarborPrimary)
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (task.id == nowTask.task?.id) AnchorColors.HarborAction else MaterialTheme.colorScheme.onSurface
                    )
                    Text("${task.durationMinutes} min", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TimelineTab(vm: AnchorViewModel, modifier: Modifier) {
    val scheduled by vm.scheduled.collectAsState()
    val calendar by vm.calendarToday.collectAsState()
    val workload by vm.workload.collectAsState()
    val weekly by vm.weeklySummary.collectAsState()
    val activeFocusTaskId by vm.activeFocusTaskId.collectAsState()
    val nowTask by vm.nowTask.collectAsState()
    var scheduleTask by remember { mutableStateOf<TaskEntity?>(null) }
    var hour by remember { mutableStateOf("9") }
    var minute by remember { mutableStateOf("0") }

    Column(modifier.padding(ScreenPadding)) {
        WorkloadBanner(
            workload = workload,
            estimateErrorPercent = weekly.avgEstimateErrorPercent.takeIf { it > 20 },
            modifier = Modifier.padding(bottom = ItemSpacing)
        )
        SectionHeader("Today", "Tap a block to reschedule")
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
            onLockedDragAttempt = { vm.notifyLockedTaskDrag() }
        )
        scheduleTask?.let { task ->
            AnchorCard(Modifier.padding(top = ItemSpacing)) {
                Text("Schedule: ${task.title}", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        hour, { hour = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hour (0–23)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        minute, { minute = it.filter(Char::isDigit).take(2) },
                        label = { Text("Min (0–59)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Button(onClick = {
                    vm.scheduleTask(
                        task.id,
                        (hour.toIntOrNull() ?: 9).coerceIn(0, 23),
                        (minute.toIntOrNull() ?: 0).coerceIn(0, 59),
                        task.durationMinutes
                    )
                    scheduleTask = null
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Set time")
                }
            }
        }
    }
}

@Composable
private fun InboxTab(vm: AnchorViewModel, modifier: Modifier) {
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

    Column(modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
        OutlinedTextField(
            newTask, { newTask = it },
            label = { Text("Quick capture") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Task or note") }
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InboxState.entries.filter { !todayOnly || it == InboxState.TODAY }.forEach { state ->
                val label = when (state) {
                    InboxState.WAITING -> "Later"
                    else -> state.name.lowercase().replaceFirstChar { it.uppercase() }
                }
                FilterChip(
                    selected = filter == state,
                    onClick = { filter = state },
                    label = { Text(label) }
                )
            }
        }
        if (filter == InboxState.TODAY) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = scheduleMode == "all_day",
                    onClick = { scheduleMode = "all_day" },
                    label = { Text("All day") }
                )
                FilterChip(
                    selected = scheduleMode == "pick_time",
                    onClick = { scheduleMode = "pick_time" },
                    label = { Text("Pick a time") }
                )
            }
            if (scheduleMode == "pick_time") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = captureHour,
                        onValueChange = { captureHour = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hour (0–23)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = captureMinute,
                        onValueChange = { captureMinute = it.filter(Char::isDigit).take(2) },
                        label = { Text("Min (0–59)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        }
        Button(
            onClick = {
                if (newTask.isNotBlank()) {
                    val startMillis = if (filter == InboxState.TODAY && scheduleMode == "pick_time") {
                        val h = (captureHour.toIntOrNull() ?: 9).coerceIn(0, 23)
                        val m = (captureMinute.toIntOrNull() ?: 0).coerceIn(0, 59)
                        val zone = java.time.ZoneId.systemDefault()
                        LocalDate.now().atTime(h, m).atZone(zone).toInstant().toEpochMilli()
                    } else {
                        null
                    }
                    vm.addInbox(newTask, filter, startMillis)
                    newTask = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = newTask.isNotBlank()
        ) {
            Text("Add to ${filter.name.lowercase()}")
        }

        val list = when (filter) {
            InboxState.TODAY -> inbox
            InboxState.SOMEDAY -> someday
            InboxState.WAITING -> waiting
        }
        if (list.isEmpty()) {
            EmptyState(AppCopy.INBOX_EMPTY)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(list, key = { it.id }) { task ->
                TaskRow(task, vm, aiLoading)
            }
        }
        FilledTonalButton(
            onClick = { vm.triageInbox() },
            enabled = !aiLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("AI triage inbox")
        }
    }
}

@Composable
private fun TaskRow(task: TaskEntity, vm: AnchorViewModel, aiLoading: Boolean = false) {
    AnchorCard {
        Text(task.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.clickable { vm.openTaskDetail(task) })
        if (!task.notes.isBlank()) {
            Text(task.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { vm.completeTask(task.id) }) { Text("Done") }
            TextButton(
                onClick = { vm.breakdownTask(task.title) },
                enabled = !aiLoading
            ) { Text("Break down") }
            TextButton(onClick = { vm.scheduleTask(task.id, 9, 0, task.durationMinutes) }) { Text("9 am") }
            TextButton(onClick = { vm.moveInbox(task.id, InboxState.SOMEDAY) }) { Text("Someday") }
        }
    }
}

@Composable
private fun ReplanTab(vm: AnchorViewModel, modifier: Modifier) {
    val replan by vm.replanWithTasks.collectAsState()
    val aiPreview by vm.replanAiPreview.collectAsState()
    val aiLoading by vm.aiLoading.collectAsState()

    Column(modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
        SectionHeader("Replan queue", AppCopy.REPLAN_SECTION)
        if (replan.isEmpty()) {
            EmptyState(AppCopy.REPLAN_EMPTY)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(replan, key = { it.first.id }) { (item, task) ->
                AnchorCard {
                    Text(task?.title ?: "Task #${item.taskId}", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        task?.let { t ->
                            TextButton(onClick = { vm.replanRescheduleToday(item.id, t.id, t.durationMinutes) }) {
                                Text("Today")
                            }
                            TextButton(onClick = { vm.replanDeferSomeday(item.id, t.id) }) { Text("Someday") }
                        }
                        TextButton(onClick = { vm.dismissReplan(item.id) }) { Text("Dismiss") }
                    }
                }
            }
        }
        Button(
            onClick = { vm.replanAssist() },
            enabled = !aiLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("AI replan assistant")
        }
        aiPreview?.let { preview ->
            AnchorCard(Modifier.padding(top = ItemSpacing)) {
                preview.message?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                }
                preview.recommended_titles.forEach { Text("→ Today: $it", style = MaterialTheme.typography.bodySmall) }
                preview.defer_titles.forEach { Text("→ Someday: $it", style = MaterialTheme.typography.bodySmall) }
                Button(onClick = { vm.applyReplanAi() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Apply suggestions")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreTab(vm: AnchorViewModel, modifier: Modifier) {
    val assignments by vm.assignments.collectAsState()
    val routines by vm.routines.collectAsState()
    var assignTitle by remember { mutableStateOf("") }
    var course by remember { mutableStateOf("") }
    var dueDays by remember { mutableStateOf("7") }
    var showSyllabusDialog by remember { mutableStateOf(false) }
    var syllabusText by remember { mutableStateOf("") }
    var runningRoutine by remember { mutableStateOf<com.anchor.adhd.data.model.RoutineEntity?>(null) }
    var showRoutineForm by remember { mutableStateOf(false) }
    var routineName by remember { mutableStateOf("") }
    var routineCue by remember { mutableStateOf("") }
    var routineIfThen by remember { mutableStateOf("") }
    var routineSteps by remember { mutableStateOf("") }

    Column(
        modifier.padding(ScreenPadding).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing)
    ) {
        SectionHeader("Assignments")
        OutlinedTextField(assignTitle, { assignTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(course, { course = it }, label = { Text("Course") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            dueDays, { dueDays = it.filter { ch -> ch.isDigit() }.take(3) },
            label = { Text("Due in (days)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (assignTitle.isNotBlank()) {
                        val days = dueDays.toIntOrNull()?.coerceAtLeast(0) ?: 7
                        val due = System.currentTimeMillis() + days * 86_400_000L
                        vm.addAssignment(assignTitle, course, due)
                        assignTitle = ""
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = assignTitle.isNotBlank()
            ) {
                Text("Add assignment")
            }
            FilledTonalButton(
                onClick = { showSyllabusDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Text("Paste syllabus")
            }
        }
        assignments.forEach { a ->
            AnchorCard {
                Text(a.title, style = MaterialTheme.typography.titleSmall)
                Text(a.course, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.completeAssignment(a.id) }) { Text("Complete") }
                    TextButton(onClick = { vm.deleteAssignment(a.id) }) { Text("Delete") }
                }
            }
        }

        SectionHeader("Routines")
        if (!showRoutineForm) {
            TextButton(onClick = { showRoutineForm = true }) { Text("Add routine") }
        } else {
            AnchorCard {
                OutlinedTextField(routineName, { routineName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(routineCue, { routineCue = it }, label = { Text("Cue") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    routineIfThen, { routineIfThen = it },
                    label = { Text("If-then (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = {
                        vm.generateIfThenForRoutine(routineName, routineCue) { generated ->
                            generated?.let { routineIfThen = it }
                        }
                    },
                    enabled = routineName.isNotBlank()
                ) { Text("AI generate if-then") }
                OutlinedTextField(
                    routineSteps, { routineSteps = it },
                    label = { Text("Steps (one per line)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Button(
                    onClick = {
                        vm.createRoutine(
                            routineName,
                            routineCue,
                            routineIfThen.ifBlank { null },
                            routineSteps.lines().filter { it.isNotBlank() }
                        )
                        routineName = ""
                        routineCue = ""
                        routineIfThen = ""
                        routineSteps = ""
                        showRoutineForm = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = routineName.isNotBlank()
                ) { Text("Save routine") }
            }
        }
        if (routines.isEmpty()) EmptyState(AppCopy.ROUTINES_EMPTY)
        routines.forEach { r ->
            AnchorCard {
                Text(r.name, style = MaterialTheme.typography.titleSmall)
                Text(r.ifThen ?: r.cue, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { runningRoutine = r }) { Text("Run routine") }
                    TextButton(onClick = { vm.deleteRoutine(r.id) }) { Text("Delete") }
                }
            }
        }
    }

    if (showSyllabusDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSyllabusDialog = false },
            title = { Text("Paste syllabus") },
            text = {
                OutlinedTextField(
                    value = syllabusText,
                    onValueChange = { syllabusText = it },
                    label = { Text("Syllabus text or schedule table") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 12
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.importSyllabusText(syllabusText)
                        syllabusText = ""
                        showSyllabusDialog = false
                    },
                    enabled = syllabusText.isNotBlank()
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSyllabusDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    runningRoutine?.let { routine ->
        RoutineRunnerModal(routine, vm) { runningRoutine = null }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineRunnerModal(
    routine: com.anchor.adhd.data.model.RoutineEntity,
    vm: AnchorViewModel,
    onDismiss: () -> Unit
) {
    val steps by vm.routineSteps(routine.id).collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        RoutineRunnerSheet(
            routine = routine,
            steps = steps,
            onStartFocus = {
                onDismiss()
                vm.startShortFocus()
            },
            onDismiss = onDismiss
        )
    }
}
