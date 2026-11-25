package com.anchor.adhd.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anchor.adhd.desktop.ai.DesktopMilestonePreview
import com.anchor.adhd.desktop.ai.DesktopMilestoneStepsState
import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import com.anchor.adhd.desktop.db.DesktopTask
import com.anchor.adhd.desktop.db.DesktopTaskDeletion
import com.anchor.adhd.desktop.db.DesktopThoughtCaptureState
import com.anchor.adhd.ui.components.AnchorChip
import com.anchor.adhd.ui.components.AnchorHairline
import com.anchor.adhd.ui.components.AnchorListRow
import com.anchor.adhd.ui.components.AnchorQuietButton
import com.anchor.adhd.ui.components.AnchorSectionHeader
import com.anchor.adhd.ui.components.AnchorWell
import com.anchor.adhd.ui.components.AnchorWellTone
import com.anchor.adhd.ui.components.HarborCtaButton
import com.anchor.adhd.ui.components.HarborEmptyState
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens

private enum class DesktopPlanFilter {
    ALL,
    ACTIVE,
    COMPLETED,
}

internal fun formatTaskDifficulty(raw: String): String =
    when (raw.trim().uppercase()) {
        "EASY" -> "Light"
        "HARD", "DEEP" -> "Deep"
        "MEDIUM" -> "Steady"
        else -> {
            val normalized = raw.trim().lowercase()
            if (normalized.isEmpty()) "Steady" else normalized.replaceFirstChar { it.uppercase() }
        }
    }

/**
 * Milestones & Task Planning Screen for Windows 11.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun DesktopPlanScreen(
    db: AnchorDesktopDatabase,
    captureState: DesktopThoughtCaptureState,
    onDraftChange: (String) -> Unit,
    onAddMilestone: () -> Unit,
    deletionHistory: MutableList<DesktopTaskDeletion>,
    onTaskChange: (String, suspend () -> Unit) -> Unit,
    isAppActionBusy: Boolean,
    nanoState: DesktopMilestoneStepsState,
    onToggleNanoTask: (DesktopTask) -> Unit,
    onSaveNanoSteps: (String, Boolean) -> Unit,
    onSelectTaskForFocus: (DesktopTask) -> Unit,
    onBreakdownTask: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAnchorTokens.current
    val captainLabelColor = if (tokens.isDark) tokens.captain else tokens.captainInk
    val tasks by db.tasks.collectAsState()
    val newTaskText = captureState.draft
    val isTaskSaveBusy = isAppActionBusy || captureState.isSaving
    val controlsEnabled = !nanoState.isSaving && !isTaskSaveBusy
    val expandedTaskId = nanoState.expandedTaskId
    val loadingTaskId = nanoState.loadingTaskId
    var selectedFilter by remember { mutableStateOf(DesktopPlanFilter.ALL) }
    val listState = rememberLazyListState()

    val completedCount = remember(tasks) { tasks.count { it.isCompleted } }
    val activeCount = remember(tasks, completedCount) { tasks.size - completedCount }
    val displayedTasks =
        remember(tasks, selectedFilter) {
            when (selectedFilter) {
                DesktopPlanFilter.ALL -> tasks
                DesktopPlanFilter.ACTIVE -> tasks.filter { !it.isCompleted }
                DesktopPlanFilter.COMPLETED -> tasks.filter { it.isCompleted }
            }
        }

    val submitNewTask = {
        if (controlsEnabled && newTaskText.isNotBlank()) onAddMilestone()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compactLayout = maxWidth < 680.dp
        val stackEntryBar = maxWidth < 460.dp
        val compactRow = maxWidth < 600.dp
        val horizontalPadding = if (compactLayout) 16.dp else AnchorSpacing.screenHorizontalDesktop
        val verticalPadding = if (compactLayout) 16.dp else 20.dp

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = horizontalPadding,
                        end = horizontalPadding + 10.dp,
                        top = verticalPadding,
                        bottom = verticalPadding + 12.dp,
                    ),
            ) {
                item(key = "header") {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Plan",
                                style = MaterialTheme.typography.headlineMedium,
                                color = tokens.ink,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                text = "Organize milestones into calm, bite-sized actions",
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.muted,
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                modifier = Modifier.heightIn(min = 40.dp),
                                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                color = tokens.paper2,
                                border = null,
                                shadowElevation = 0.dp,
                                tonalElevation = 0.dp,
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "$completedCount of ${tasks.size} completed",
                                        style =
                                            MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontFeatureSettings = "tnum",
                                            ),
                                        color = if (tasks.isNotEmpty() && completedCount == tasks.size) tokens.sage else tokens.ink2,
                                    )
                                }
                            }

                            if (completedCount > 0) {
                                AnchorQuietButton(
                                    label = "Clear completed",
                                    onClick = {
                                        onTaskChange("Clearing completed milestones") {
                                            val deleted = db.deleteCompletedTasks()
                                            if (deleted.tasks.isNotEmpty()) deletionHistory.add(deleted)
                                        }
                                    },
                                    outlined = true,
                                    tint = tokens.ink2,
                                    enabled = controlsEnabled,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(AnchorSpacing.sectionGap))

                    if (deletionHistory.isNotEmpty()) {
                        val deletedCount = deletionHistory.last().tasks.size
                        AnchorWell(
                            modifier = Modifier.padding(bottom = 12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text =
                                        if (deletedCount == 1) {
                                            "Milestone deleted"
                                        } else {
                                            "$deletedCount completed milestones cleared"
                                        },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                    color = tokens.ink,
                                    modifier =
                                        Modifier
                                            .padding(vertical = 8.dp)
                                            .semantics { liveRegion = LiveRegionMode.Polite },
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    AnchorQuietButton(
                                        label = "Undo",
                                        onClick = {
                                            val deleted = deletionHistory.lastOrNull()
                                            if (deleted != null) {
                                                onTaskChange("Restoring milestones") {
                                                    db.restoreDeletedTasks(deleted)
                                                    deletionHistory.remove(deleted)
                                                }
                                            }
                                        },
                                        outlined = false,
                                        tint = tokens.primary,
                                        enabled = controlsEnabled,
                                    )
                                    AnchorQuietButton(
                                        label = "Dismiss",
                                        onClick = { deletionHistory.clear() },
                                        outlined = false,
                                        tint = tokens.muted,
                                        enabled = controlsEnabled,
                                    )
                                }
                            }
                        }
                    }

                    // Single Level-2 tonal well for milestone entry
                    AnchorWell(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (stackEntryBar) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                MilestoneEntryField(
                                    value = newTaskText,
                                    onValueChange = onDraftChange,
                                    readOnly = captureState.isSaving,
                                    enabled = !nanoState.isSaving && !isAppActionBusy,
                                    onSubmit = submitNewTask,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                HarborCtaButton(
                                    onClick = submitNewTask,
                                    enabled = newTaskText.isNotBlank() && controlsEnabled,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (captureState.isSaving) "Saving…" else "Add milestone",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                MilestoneEntryField(
                                    value = newTaskText,
                                    onValueChange = onDraftChange,
                                    readOnly = captureState.isSaving,
                                    enabled = !nanoState.isSaving && !isAppActionBusy,
                                    onSubmit = submitNewTask,
                                    modifier = Modifier.weight(1f),
                                )
                                HarborCtaButton(
                                    onClick = submitNewTask,
                                    enabled = newTaskText.isNotBlank() && controlsEnabled,
                                    modifier = Modifier.heightIn(min = 40.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (captureState.isSaving) "Saving…" else "Add milestone",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }

                        captureState.feedback?.let { feedback ->
                            Text(
                                text = feedback,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (captureState.isError) tokens.clayRose else tokens.sage,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                        nanoState.feedback?.let { feedback ->
                            Text(
                                text = feedback,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (nanoState.isError) tokens.clayRose else tokens.sage,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Status filter chips
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AnchorChip(
                            label = "All (${tasks.size})",
                            selected = selectedFilter == DesktopPlanFilter.ALL,
                            onClick = { selectedFilter = DesktopPlanFilter.ALL },
                        )
                        AnchorChip(
                            label = "Active ($activeCount)",
                            selected = selectedFilter == DesktopPlanFilter.ACTIVE,
                            onClick = { selectedFilter = DesktopPlanFilter.ACTIVE },
                        )
                        AnchorChip(
                            label = "Completed ($completedCount)",
                            selected = selectedFilter == DesktopPlanFilter.COMPLETED,
                            onClick = { selectedFilter = DesktopPlanFilter.COMPLETED },
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val sectionTitle =
                        when (selectedFilter) {
                            DesktopPlanFilter.ALL -> "Milestones"
                            DesktopPlanFilter.ACTIVE -> "Active milestones"
                            DesktopPlanFilter.COMPLETED -> "Completed milestones"
                        }
                    AnchorSectionHeader(
                        title = sectionTitle,
                        trailingLabel = "${displayedTasks.size}",
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }

                if (displayedTasks.isEmpty()) {
                    item(key = "empty") {
                        val (emptyTitle, emptyMessage) =
                            when {
                                tasks.isEmpty() ->
                                    "No milestones yet" to
                                        "Add your first milestone above to build a calm focus queue."
                                selectedFilter == DesktopPlanFilter.ACTIVE ->
                                    "All milestones completed" to
                                        "Every milestone on your deck is done. Add a new one whenever you are ready."
                                else ->
                                    "No completed milestones yet" to
                                        "Check off any milestone when you finish it to track progress here."
                            }
                        HarborEmptyState(
                            title = emptyTitle,
                            message = emptyMessage,
                            actionLabel = if (selectedFilter != DesktopPlanFilter.ALL) "Show all milestones" else null,
                            onAction =
                                if (selectedFilter != DesktopPlanFilter.ALL) {
                                    { selectedFilter = DesktopPlanFilter.ALL }
                                } else {
                                    null
                                },
                        )
                    }
                } else {
                    itemsIndexed(displayedTasks, key = { _, task -> task.id }) { index, task ->
                        val isExpanded = expandedTaskId == task.id
                        val isLoadingSteps = loadingTaskId == task.id
                        val difficultyLabel = formatTaskDifficulty(task.difficulty)
                        val subtitleText =
                            when {
                                task.isCompleted -> "Completed, $difficultyLabel"
                                task.isNextAction -> "Next action, $difficultyLabel"
                                else -> difficultyLabel
                            }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            AnchorListRow(
                                title = task.title,
                                subtitle = subtitleText,
                                trailingText = "${task.durationMinutes}m",
                                minHeight = 44.dp,
                                showDivider = false,
                                leading = {
                                    IconButton(
                                        onClick = {
                                            onTaskChange("Updating milestone") {
                                                db.setTaskCompleted(task.id, !task.isCompleted)
                                            }
                                        },
                                        enabled = controlsEnabled,
                                        modifier =
                                            Modifier
                                                .size(36.dp)
                                                .semantics {
                                                    role = Role.Checkbox
                                                    toggleableState = ToggleableState(task.isCompleted)
                                                    stateDescription = if (task.isCompleted) "Completed" else "Not completed"
                                                },
                                    ) {
                                        Icon(
                                            imageVector =
                                                if (task.isCompleted) {
                                                    Icons.Default.CheckCircle
                                                } else {
                                                    Icons.Outlined.RadioButtonUnchecked
                                                },
                                            contentDescription =
                                                if (task.isCompleted) {
                                                    "Mark ${task.title} incomplete"
                                                } else {
                                                    "Mark ${task.title} complete"
                                                },
                                            tint = if (task.isCompleted) tokens.sage else tokens.muted,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                },
                                trailing =
                                    if (!compactRow) {
                                        {
                                            MilestoneActionControls(
                                                task = task,
                                                controlsEnabled = controlsEnabled,
                                                isExpanded = isExpanded,
                                                isLoadingSteps = isLoadingSteps,
                                                alignColumns = true,
                                                captainLabelColor = captainLabelColor,
                                                onMakeNext = {
                                                    onTaskChange("Selecting next task") { db.setNextActionTask(task.id) }
                                                },
                                                onToggleCaptainSteps = { onToggleNanoTask(task) },
                                                onStartFocus = { onSelectTaskForFocus(task) },
                                                onDelete = {
                                                    onTaskChange("Deleting milestone") {
                                                        val deleted = db.deleteTask(task.id)
                                                        if (deleted.tasks.isNotEmpty()) deletionHistory.add(deleted)
                                                    }
                                                },
                                            )
                                        }
                                    } else {
                                        null
                                    },
                            )

                            if (compactRow) {
                                FlowRow(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 48.dp, bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    MilestoneActionControls(
                                        task = task,
                                        controlsEnabled = controlsEnabled,
                                        isExpanded = isExpanded,
                                        isLoadingSteps = isLoadingSteps,
                                        alignColumns = false,
                                        captainLabelColor = captainLabelColor,
                                        onMakeNext = {
                                            onTaskChange("Selecting next task") { db.setNextActionTask(task.id) }
                                        },
                                        onToggleCaptainSteps = { onToggleNanoTask(task) },
                                        onStartFocus = { onSelectTaskForFocus(task) },
                                        onDelete = {
                                            onTaskChange("Deleting milestone") {
                                                val deleted = db.deleteTask(task.id)
                                                if (deleted.tasks.isNotEmpty()) deletionHistory.add(deleted)
                                            }
                                        },
                                    )
                                }
                            }

                            if (isExpanded) {
                                CaptainMicroStepsWell(
                                    task = task,
                                    preview = nanoState.previews[task.id],
                                    isLoading = isLoadingSteps,
                                    isSaving = nanoState.isSaving,
                                    controlsEnabled = controlsEnabled,
                                    onSaveSteps = { key, startFirst ->
                                        if (!isTaskSaveBusy) onSaveNanoSteps(key, startFirst)
                                    },
                                    onOpenInCaptain = { onBreakdownTask(task.title) },
                                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 12.dp),
                                )
                            }

                            if (index < displayedTasks.lastIndex) {
                                AnchorHairline()
                            }
                        }
                    }
                }
            }

            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun MilestoneEntryField(
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean,
    enabled: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAnchorTokens.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        enabled = enabled,
        placeholder = {
            Text(
                text = "Add a milestone…",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = tokens.primary,
                unfocusedBorderColor = tokens.fieldEdge,
                focusedTextColor = tokens.ink,
                unfocusedTextColor = tokens.ink,
                focusedContainerColor = tokens.surface,
                unfocusedContainerColor = tokens.surface,
                cursorColor = tokens.primary,
            ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
    )
}

@Composable
private fun MilestoneActionControls(
    task: DesktopTask,
    controlsEnabled: Boolean,
    isExpanded: Boolean,
    isLoadingSteps: Boolean,
    alignColumns: Boolean,
    captainLabelColor: androidx.compose.ui.graphics.Color,
    onMakeNext: () -> Unit,
    onToggleCaptainSteps: () -> Unit,
    onStartFocus: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (!task.isCompleted) {
            IconButton(
                onClick = onMakeNext,
                enabled = controlsEnabled,
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Bolt,
                    contentDescription = "Make ${task.title} the next action",
                    tint = if (task.isNextAction) tokens.beacon else tokens.muted,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else if (alignColumns) {
            Spacer(modifier = Modifier.size(38.dp))
        }

        IconButton(
            onClick = onToggleCaptainSteps,
            enabled = controlsEnabled,
            modifier = Modifier.size(38.dp),
        ) {
            if (isLoadingSteps) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = tokens.captain,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = "Break down ${task.title} with Captain",
                    tint = if (isExpanded) tokens.captain else captainLabelColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        AnchorQuietButton(
            label = "Start focus",
            onClick = onStartFocus,
            outlined = false,
            tint = tokens.primary,
            enabled = !task.isCompleted && controlsEnabled,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
            },
        )

        IconButton(
            onClick = onDelete,
            enabled = controlsEnabled,
            modifier = Modifier.size(38.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Delete ${task.title}",
                tint = tokens.muted,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CaptainMicroStepsWell(
    task: DesktopTask,
    preview: DesktopMilestonePreview?,
    isLoading: Boolean,
    isSaving: Boolean,
    controlsEnabled: Boolean,
    onSaveSteps: (key: String, startFirst: Boolean) -> Unit,
    onOpenInCaptain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAnchorTokens.current
    val captainLabelColor = if (tokens.isDark) tokens.captain else tokens.captainInk
    val steps = preview?.steps.orEmpty()

    AnchorWell(
        modifier = modifier,
        tone = AnchorWellTone.Captain,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AnchorSectionHeader(
            title = "Captain micro-steps",
            trailingLabel = "5-minute starters",
        )

        if (steps.isEmpty() && isLoading) {
            Text(
                text = "Preparing calm starter actions…",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.ink2,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                steps.forEachIndexed { index, step ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "${index + 1}.",
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum",
                                ),
                            color = captainLabelColor,
                        )
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.ink,
                        )
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnchorQuietButton(
                    label = if (isSaving) "Saving steps…" else "Add all as milestones (${steps.size})",
                    onClick = { preview?.let { onSaveSteps(it.key, false) } },
                    outlined = true,
                    tint = captainLabelColor,
                    enabled = controlsEnabled && !task.isCompleted && steps.isNotEmpty(),
                )

                if (steps.isNotEmpty()) {
                    AnchorQuietButton(
                        label = "Start step 1 now",
                        onClick = { preview?.let { onSaveSteps(it.key, true) } },
                        outlined = true,
                        tint = tokens.primary,
                        enabled = controlsEnabled && !task.isCompleted,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                        },
                    )
                }

                AnchorQuietButton(
                    label = "Open in Captain",
                    onClick = onOpenInCaptain,
                    outlined = false,
                    tint = captainLabelColor,
                    enabled = controlsEnabled,
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
