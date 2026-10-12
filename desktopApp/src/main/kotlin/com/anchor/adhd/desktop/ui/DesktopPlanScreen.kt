package com.anchor.adhd.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.adhd.desktop.ai.DesktopMilestoneStepsState
import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import com.anchor.adhd.desktop.db.DesktopThoughtCaptureState
import com.anchor.adhd.desktop.db.DesktopTask
import com.anchor.adhd.desktop.db.DesktopTaskDeletion
import com.anchor.adhd.desktop.theme.AnchorColors
import com.anchor.adhd.desktop.theme.AnchorSpacing

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
    val tasks by db.tasks.collectAsState()
    val newTaskText = captureState.draft
    val isTaskSaveBusy = isAppActionBusy || captureState.isSaving
    val expandedTaskId = nanoState.expandedTaskId
    val loadingTaskId = nanoState.loadingTaskId
    val submitNewTask = {
        if (!nanoState.isSaving && !isTaskSaveBusy) onAddMilestone()
    }

    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            // Header
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column {
                    Text(
                        text = "Focus Milestones & Plan",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = "Organize tasks into bite-sized momentum anchors",
                        style = MaterialTheme.typography.bodySmall,
                        color = AnchorColors.HarborPrimary.copy(alpha = 0.8f),
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (tasks.any { it.isCompleted }) {
                        OutlinedButton(
                            onClick = {
                                onTaskChange("Clearing completed milestones") {
                                    val deleted = db.deleteCompletedTasks()
                                    if (deleted.tasks.isNotEmpty()) deletionHistory.add(deleted)
                                }
                            },
                            enabled = !nanoState.isSaving && !isTaskSaveBusy,
                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.85f)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Completed", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                        color = AnchorColors.HarborDock,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    ) {
                        Text(
                            text = "${tasks.count { it.isCompleted }} of ${tasks.size} completed",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = AnchorColors.HarborFoliage,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (deletionHistory.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                    color = AnchorColors.HarborBackground,
                    border = BorderStroke(1.dp, AnchorColors.HarborPrimary.copy(alpha = 0.3f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val deletedCount = deletionHistory.last().tasks.size
                        Text(
                            text = if (deletedCount == 1) "Task deleted" else "$deletedCount completed tasks cleared",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            enabled = !nanoState.isSaving && !isTaskSaveBusy,
                            onClick = {
                                val deleted = deletionHistory.lastOrNull()
                                if (deleted != null) onTaskChange("Restoring milestones") {
                                    db.restoreDeletedTasks(deleted)
                                    deletionHistory.remove(deleted)
                                }
                            },
                        ) { Text("Undo", color = AnchorColors.HarborPrimary) }
                        TextButton(
                            enabled = !nanoState.isSaving && !isTaskSaveBusy,
                            onClick = { deletionHistory.clear() },
                        ) { Text("Dismiss", color = Color.White.copy(alpha = 0.7f)) }
                    }
                }
            }

            // Add Task Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                color = AnchorColors.HarborDock,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newTaskText,
                        onValueChange = onDraftChange,
                        readOnly = captureState.isSaving,
                        enabled = !nanoState.isSaving && !isAppActionBusy,
                        placeholder = { Text("New milestone...", maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = 0.4f)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AnchorColors.HarborPrimary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                            ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitNewTask() }),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = { submitNewTask() },
                        enabled = newTaskText.isNotBlank() && !nanoState.isSaving && !isTaskSaveBusy,
                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                        colors = ButtonDefaults.buttonColors(containerColor = AnchorColors.HarborPrimary, contentColor = Color(0xFF002A4A)),
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (captureState.isSaving) "Saving..." else "Add Milestone", fontWeight = FontWeight.Bold)
                    }
                }
            }

            captureState.feedback?.let { feedback ->
                Text(feedback, style = MaterialTheme.typography.bodySmall,
                    color = if (captureState.isError) Color(0xFFFFB4AB) else AnchorColors.HarborFoliage,
                    modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
            }
            nanoState.feedback?.let { feedback ->
                Text(feedback, style = MaterialTheme.typography.bodySmall,
                    color = if (nanoState.isError) Color(0xFFFFB4AB) else AnchorColors.HarborFoliage,
                    modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
            }
            Spacer(modifier = Modifier.height(18.dp))

        }
        // The header, form, feedback, task cards, and complete previews share one scroll.
        items(tasks, key = { it.id }) { task ->
            @Composable fun TaskActions() {
                if (!task.isCompleted) {
                    // Set as Next Action (Make NOW) Button
                    IconButton(
                        onClick = { onTaskChange("Selecting next task") { db.setNextActionTask(task.id) } },
                        enabled = !nanoState.isSaving && !isTaskSaveBusy,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Make ${task.title} the next action",
                            tint =
                                if (task.isNextAction) {
                                    AnchorColors.HarborBeaconAmber
                                } else {
                                    Color.White.copy(alpha = 0.4f)
                                },
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // AI Nano-Step Button
                IconButton(
                    onClick = { onToggleNanoTask(task) },
                    enabled = !nanoState.isSaving && !isTaskSaveBusy,
                    modifier = Modifier.size(44.dp),
                ) {
                    if (loadingTaskId == task.id) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = AnchorColors.HarborAi,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Break down ${task.title}",
                            tint = if (expandedTaskId == task.id) AnchorColors.HarborGrowth else AnchorColors.HarborAi,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // Start Focus Button
                IconButton(
                    onClick = { onSelectTaskForFocus(task) },
                    enabled = !task.isCompleted && !nanoState.isSaving && !isTaskSaveBusy,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Focus on ${task.title}",
                        tint = AnchorColors.HarborPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                IconButton(
                    onClick = {
                        onTaskChange("Deleting milestone") {
                            val deleted = db.deleteTask(task.id)
                            if (deleted.tasks.isNotEmpty()) deletionHistory.add(deleted)
                        }
                    },
                    enabled = !nanoState.isSaving && !isTaskSaveBusy,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete ${task.title}",
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AnchorSpacing.radiusChip)),
                color =
                    if (task.isCompleted) {
                        Color.White.copy(
                            alpha = 0.04f,
                        )
                    } else {
                        AnchorColors.HarborBackground.copy(alpha = 0.8f)
                    },
                border =
                    BorderStroke(
                        1.dp,
                        if (!task.isCompleted && task.isNextAction) {
                            AnchorColors.HarborBeaconAmber.copy(alpha = 0.5f)
                        } else {
                            Color.White.copy(alpha = 0.08f)
                        },
                    ),
            ) {
                BoxWithConstraints(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    val compactTask = maxWidth < 480.dp
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onTaskChange("Updating milestone") { db.setTaskCompleted(task.id, !task.isCompleted) } },
                                enabled = !nanoState.isSaving && !isTaskSaveBusy,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = if (task.isCompleted) "Mark ${task.title} incomplete" else "Complete ${task.title}",
                                    tint = if (task.isCompleted) AnchorColors.HarborGrowth else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = task.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (task.isCompleted) Color.White.copy(alpha = 0.4f) else Color.White,
                                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (!task.isCompleted && task.isNextAction) {
                                        Surface(
                                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                            color = AnchorColors.HarborBeaconAmber.copy(alpha = 0.2f),
                                            border = BorderStroke(1.dp, AnchorColors.HarborBeaconAmber.copy(alpha = 0.5f)),
                                        ) {
                                            Text(
                                                text = "NOW",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AnchorColors.HarborBeaconAmber,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${task.durationMinutes}m duration • ${task.difficulty.lowercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.4f),
                                )
                            }

                            if (!compactTask) TaskActions()
                        }
                        if (compactTask) {
                            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TaskActions() }
                        }
                    }
                }
            }

            // Inline Nano-Step Sub-Card
            if (expandedTaskId == task.id) {
                val preview = nanoState.previews[task.id]
                val steps = preview?.steps.orEmpty()
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, start = 16.dp, end = 4.dp)
                            .clip(RoundedCornerShape(AnchorSpacing.radiusChip)),
                    color = Color(0xFF10281F).copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, AnchorColors.HarborGrowth.copy(alpha = 0.4f)),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "FIVE-MINUTE STEPS",
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Bold,
                                color = AnchorColors.HarborFoliage,
                            )
                            Text(
                                text = "Overcome activation dread",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (steps.isEmpty() && loadingTaskId == task.id) {
                            Text(
                                text = "Slicing task into frictionless micro-actions...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f),
                            )
                        } else {
                            steps.forEachIndexed { index, step ->
                                Row(
                                    modifier = Modifier.padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${index + 1}.",
                                        fontWeight = FontWeight.Bold,
                                        color = AnchorColors.HarborPrimary,
                                        fontSize = 12.sp,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = step,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.9f),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // Add Steps as Sub-tasks Button
                                Button(
                                    onClick = { preview?.let { if (!isTaskSaveBusy) onSaveNanoSteps(it.key, false) } },
                                    enabled = !nanoState.isSaving && !isTaskSaveBusy && !task.isCompleted && steps.isNotEmpty(),
                                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = AnchorColors.HarborGrowth.copy(alpha = 0.8f),
                                            contentColor = Color.White,
                                        ),
                                ) {
                                    Text(if (nanoState.isSaving) "Saving steps..." else "+ Add All as Milestones (${steps.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                // Start Step 1 Now Button
                                if (steps.isNotEmpty()) {
                                    Button(
                                        onClick = { preview?.let { if (!isTaskSaveBusy) onSaveNanoSteps(it.key, true) } },
                                        enabled = !nanoState.isSaving && !isTaskSaveBusy && !task.isCompleted,
                                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                        colors =
                                            ButtonDefaults.buttonColors(
                                                containerColor = AnchorColors.HarborAction,
                                                contentColor = Color.White,
                                            ),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Start Step 1 Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                OutlinedButton(
                                    onClick = { onBreakdownTask(task.title) },
                                    enabled = !nanoState.isSaving && !isTaskSaveBusy,
                                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                    border = BorderStroke(1.dp, AnchorColors.HarborAi.copy(alpha = 0.5f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AnchorColors.HarborAi),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Open in AI Studio", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
