package com.anchor.adhd.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.adhd.desktop.ai.DesktopAiEngine
import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import com.anchor.adhd.desktop.db.DesktopTask
import com.anchor.adhd.desktop.theme.AnchorColors
import com.anchor.adhd.desktop.theme.AnchorSpacing
import kotlinx.coroutines.launch

/**
 * Milestones & Task Planning Screen for Windows 11.
 */
@Composable
fun DesktopPlanScreen(
    db: AnchorDesktopDatabase,
    durationMinutes: Int,
    onSelectTaskForFocus: (DesktopTask) -> Unit,
    onBreakdownTask: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val tasks by db.tasks.collectAsState()
    var newTaskText by remember { mutableStateOf("") }
    var expandedTaskId by remember { mutableStateOf<Long?>(null) }
    var nanoStepsByTaskId by remember { mutableStateOf<Map<Long, List<String>>>(emptyMap()) }
    var loadingTaskId by remember { mutableStateOf<Long?>(null) }

    val submitNewTask = {
        if (newTaskText.isNotBlank()) {
            val titleToInsert = newTaskText.trim()
            newTaskText = ""
            scope.launch {
                db.insertTask(titleToInsert, durationMinutes)
            }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tasks.any { it.isCompleted }) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                db.deleteCompletedTasks()
                            }
                        },
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
                    onValueChange = { newTaskText = it },
                    placeholder = { Text("What milestone do you want to conquer next?", color = Color.White.copy(alpha = 0.4f)) },
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
                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                    colors = ButtonDefaults.buttonColors(containerColor = AnchorColors.HarborPrimary, contentColor = Color(0xFF002A4A)),
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Milestone", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Task List
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            shape = RoundedCornerShape(AnchorSpacing.radiusCard),
            color = AnchorColors.HarborDock.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tasks) { task ->
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
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { scope.launch { db.toggleTaskCompleted(task.id) } },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
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

                            if (!task.isCompleted) {
                                // Set as Next Action (Make NOW) Button
                                IconButton(
                                    onClick = { scope.launch { db.setNextActionTask(task.id) } },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = "Set as Next Action",
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
                                onClick = {
                                    if (expandedTaskId == task.id) {
                                        expandedTaskId = null
                                    } else {
                                        expandedTaskId = task.id
                                        if (!nanoStepsByTaskId.containsKey(task.id)) {
                                            scope.launch {
                                                loadingTaskId = task.id
                                                val steps = DesktopAiEngine.generateMicroStepsAsync(task.title)
                                                nanoStepsByTaskId = nanoStepsByTaskId + (task.id to steps)
                                                loadingTaskId = null
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.size(32.dp),
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
                                        contentDescription = "Nano-Step with AI",
                                        tint = if (expandedTaskId == task.id) AnchorColors.HarborGrowth else AnchorColors.HarborAi,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }

                            // Start Focus Button
                            IconButton(
                                onClick = { onSelectTaskForFocus(task) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Start Focus",
                                    tint = AnchorColors.HarborPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                            IconButton(
                                onClick = { scope.launch { db.deleteTask(task.id) } },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }

                    // Inline Nano-Step Sub-Card
                    if (expandedTaskId == task.id) {
                        val steps = nanoStepsByTaskId[task.id] ?: emptyList()
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "AI NANO-ACTIONS (<5 MIN ATOMS)",
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
                                    steps.take(4).forEachIndexed { index, step ->
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

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        // Add Steps as Sub-tasks Button
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    steps.forEach { step ->
                                                        db.insertTask(step, 5)
                                                    }
                                                    expandedTaskId = null
                                                }
                                            },
                                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                            colors =
                                                ButtonDefaults.buttonColors(
                                                    containerColor = AnchorColors.HarborGrowth.copy(alpha = 0.8f),
                                                    contentColor = Color.White,
                                                ),
                                        ) {
                                            Text("+ Add All as Milestones", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Start Step 1 Now Button
                                        if (steps.isNotEmpty()) {
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        db.insertTask(steps[0], 5)
                                                        val inserted = db.tasks.value.find { it.title == steps[0] }
                                                        if (inserted != null) {
                                                            onSelectTaskForFocus(inserted)
                                                        }
                                                    }
                                                },
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
    }
}
