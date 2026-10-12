package com.anchor.adhd.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.adhd.desktop.ai.DesktopRoutingSession
import com.anchor.adhd.desktop.ai.ResetType
import com.anchor.adhd.desktop.ai.SmartRouteResult
import com.anchor.adhd.desktop.theme.AnchorColors
import com.anchor.adhd.desktop.theme.AnchorSpacing

/**
 * Universal Smart AI Omni-Bar.
 *
 * Eliminates executive navigation friction:
 * Users don't have to categorize their thoughts or pick a menu. They type or paste anything,
 * and the router interprets whether it's a task breakdown, brain dump, timer command,
 * blocker rule, or emotional overwhelm reset.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun DesktopSmartOmniBar(
    routingSession: DesktopRoutingSession,
    canRunAction: () -> Boolean,
    onStartFocusWithTask: suspend (taskTitle: String, minutes: Int) -> Unit,
    onSaveTasksToInbox: suspend (key: String, tasks: List<String>) -> Unit,
    onApplyBlockerRule: suspend (target: String, enable: Boolean) -> Unit,
    onResetData: ((ResetType) -> Unit)? = null,
    onNavigateToSyllabus: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val routingState by routingSession.state.collectAsState()
    val rawInput = routingState.draft
    val activeResult = routingState.result
    val resultId = routingState.resultId
    val isProcessing = routingState.isProcessing

    fun runAction(label: String, message: String, action: suspend (String) -> Unit) {
        routingSession.performAction(resultId, label, canRunAction, message, action)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Omni-Bar Input
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AnchorSpacing.radiusCard))
                    .border(
                        1.dp,
                        if (activeResult != null) AnchorColors.HarborAi else Color.White.copy(alpha = 0.18f),
                        RoundedCornerShape(AnchorSpacing.radiusCard),
                    ),
            color = AnchorColors.HarborDock.copy(alpha = 0.95f),
            shape = RoundedCornerShape(AnchorSpacing.radiusCard),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compact = maxWidth < 480.dp
                Row(
                    modifier = Modifier.padding(horizontal = if (compact) 12.dp else 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!compact) {
                        Box(
                            modifier =
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(AnchorColors.HarborAi.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = AnchorColors.HarborAi,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    OutlinedTextField(
                        value = rawInput,
                        onValueChange = routingSession::edit,
                        placeholder = {
                            Text(
                                text = if (compact) "Task, timer, or thought..." else "Tell Anchor anything — e.g. \"25m timer\", \"syllabus\", \"Write lab report\", \"block discord\"...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.45f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingIcon = {
                            if (rawInput.isNotBlank()) {
                                IconButton(
                                    enabled = !routingState.isActing,
                                    onClick = {
                                        routingSession.clear()
                                    },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear router",
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                        modifier =
                            Modifier
                                .weight(1f)
                                .onPreviewKeyEvent { event ->
                                    if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                                        routingSession.dismissResult()
                                        true
                                    } else {
                                        false
                                    }
                                },
                        singleLine = true,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                            ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { routingSession.submit() }),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { routingSession.submit() },
                        enabled = !routingState.isActing && rawInput.isNotBlank(),
                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = AnchorColors.HarborAi,
                                contentColor = Color(0xFF1B0F33),
                            ),
                        modifier = Modifier.height(40.dp),
                    ) {
                        if (isProcessing && rawInput.isBlank()) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color(0xFF1B0F33),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isProcessing && rawInput.isBlank()) "Thinking..." else "Route", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        routingState.actionLabel?.let { label ->
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("$label...", style = MaterialTheme.typography.bodySmall, color = AnchorColors.HarborPrimary)
            }
        }
        routingState.feedback?.let { feedback ->
            Text(
                text = feedback,
                style = MaterialTheme.typography.bodySmall,
                color = if (routingState.isActionError) Color(0xFFFFA6A6) else AnchorColors.HarborPrimary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // Active Smart Route Result Expansion Card
        AnimatedVisibility(
            visible = activeResult != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            val result = activeResult
            if (result != null) {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(AnchorSpacing.radiusCard))
                            .border(1.dp, AnchorColors.HarborAi.copy(alpha = 0.4f), RoundedCornerShape(AnchorSpacing.radiusCard)),
                    color = Color(0xFF161C2C),
                    shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = result.headline,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }

                            IconButton(
                                enabled = !routingState.isActing,
                                onClick = {
                                    routingSession.takeResult(resultId)
                                },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = result.detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Dynamic Action Buttons tailored to identified Intent
                        when (result) {
                            is SmartRouteResult.TaskBreakdown -> {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    result.microSteps.take(3).forEachIndexed { index, step ->
                                        Surface(
                                            shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                                            color = Color.White.copy(alpha = 0.05f),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    text = "Step ${index + 1}:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (index == 0) AnchorColors.HarborGrowth else AnchorColors.HarborPrimary,
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = step,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White,
                                                )
                                            }
                                        }
                                    }

                                    if (result.microSteps.size > 3) {
                                        Text(
                                            text = "+${result.microSteps.size - 3} more steps will be saved to your Milestones",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.55f),
                                            modifier = Modifier.padding(start = 4.dp),
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            enabled = !routingState.isActing,
                                            onClick = {
                                                runAction("Preparing focus", "Focus started.") { key ->
                                                    val remainingSteps = result.microSteps.drop(1)
                                                    if (remainingSteps.isNotEmpty()) {
                                                        onSaveTasksToInbox(key, remainingSteps)
                                                    }
                                                    onStartFocusWithTask(
                                                        result.step1Starter.ifBlank { result.taskTitle },
                                                        result.suggestedMinutes,
                                                    )
                                                }
                                            },
                                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                            colors =
                                                ButtonDefaults.buttonColors(
                                                    containerColor = AnchorColors.HarborGrowth,
                                                    contentColor = Color(0xFF0F2B1D),
                                                ),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Bolt,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Start Step 1 Now (${result.suggestedMinutes}m)", fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            enabled = !routingState.isActing,
                                            onClick = {
                                                runAction("Saving tasks", "Saved to Inbox.") { key ->
                                                    onSaveTasksToInbox(key, listOf(result.taskTitle) + result.microSteps)
                                                }
                                            },
                                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                        ) {
                                            Text("Add to Milestones", color = Color.White)
                                        }
                                    }
                                }
                            }

                            is SmartRouteResult.BrainDump -> {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    result.tasks.take(4).forEach { task ->
                                        Text(
                                            text = "• $task",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.85f),
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            enabled = !routingState.isActing,
                                            onClick = {
                                                runAction("Preparing focus", "Focus started.") { key ->
                                                    onSaveTasksToInbox(key, result.tasks)
                                                    onStartFocusWithTask(result.primaryTask, 25)
                                                }
                                            },
                                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                            colors =
                                                ButtonDefaults.buttonColors(
                                                    containerColor = AnchorColors.HarborPrimary,
                                                    contentColor = Color(0xFF002A4A),
                                                ),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Save & Anchor \"${result.primaryTask.take(20)}\"", fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            enabled = !routingState.isActing,
                                            onClick = {
                                                runAction("Saving tasks", "Saved to Inbox.") { key ->
                                                    onSaveTasksToInbox(key, result.tasks)
                                                }
                                            },
                                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                        ) {
                                            Text("Add All to Inbox", color = Color.White)
                                        }
                                    }
                                }
                            }

                            is SmartRouteResult.GroundingReset -> {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        enabled = !routingState.isActing,
                                        onClick = {
                                            runAction("Preparing focus", "Focus started.") { key ->
                                                onStartFocusWithTask(result.recommendedTask, result.suggestedMinutes)
                                            }
                                        },
                                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                        colors =
                                            ButtonDefaults.buttonColors(
                                                containerColor = AnchorColors.HarborGrowth,
                                                contentColor = Color(0xFF0F2B1D),
                                            ),
                                    ) {
                                        Icon(imageVector = Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Start 3m Gentle Reset", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            is SmartRouteResult.TimerAction -> {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        enabled = !routingState.isActing,
                                        onClick = {
                                            runAction("Preparing focus", "Focus started.") { key ->
                                                onStartFocusWithTask(result.taskTitle, result.durationMinutes)
                                            }
                                        },
                                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                        colors =
                                            ButtonDefaults.buttonColors(
                                                containerColor = AnchorColors.HarborPrimary,
                                                contentColor = Color(0xFF002A4A),
                                            ),
                                    ) {
                                        Icon(imageVector = Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Start ${result.durationMinutes}m Focus Now", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            is SmartRouteResult.BlockerAction -> {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        enabled = !routingState.isActing,
                                        onClick = {
                                            runAction("Updating shield", "Shield updated.") { key ->
                                                onApplyBlockerRule(result.target, result.enable)
                                            }
                                        },
                                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                        colors =
                                            ButtonDefaults.buttonColors(
                                                containerColor = AnchorColors.HarborAction,
                                                contentColor = Color.White,
                                            ),
                                    ) {
                                        Icon(imageVector = Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            if (result.enable) "Block ${result.target}" else "Unblock ${result.target}",
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }

                            is SmartRouteResult.QuickNote -> {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        enabled = !routingState.isActing,
                                        onClick = {
                                            runAction("Saving tasks", "Saved to Inbox.") { key ->
                                                onSaveTasksToInbox(key, listOf(result.noteTitle))
                                            }
                                        },
                                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                        colors =
                                            ButtonDefaults.buttonColors(
                                                containerColor = AnchorColors.HarborPrimary,
                                                contentColor = Color(0xFF002A4A),
                                            ),
                                    ) {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Save to Inbox", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            is SmartRouteResult.ResetDataAction -> {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                                        color = Color(0xFFFFA000).copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, Color(0xFFFFA000).copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = Color(0xFFFFA000),
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text =
                                                    "ADHD Clean Slate: Resetting removes backlog paralysis without judgment. " +
                                                        "Your AI model and local settings remain intact.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.85f),
                                                fontSize = 12.sp,
                                            )
                                        }
                                    }

                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            enabled = !routingState.isActing,
                                            onClick = {
                                                if (!routingSession.takeResult(resultId)) return@Button
                                                onResetData?.invoke(result.resetType)
                                            },
                                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                            colors =
                                                ButtonDefaults.buttonColors(
                                                    containerColor =
                                                        when (result.resetType) {
                                                            ResetType.CLEAR_TASKS -> Color(0xFFFFA000)
                                                            ResetType.RESET_STREAK -> Color(0xFF7E57C2)
                                                            ResetType.FACTORY_RESET -> Color(0xFFE53935)
                                                        },
                                                    contentColor = Color.White,
                                                ),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RestartAlt,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                when (result.resetType) {
                                                    ResetType.CLEAR_TASKS -> "Confirm: Clear All Tasks"
                                                    ResetType.RESET_STREAK -> "Confirm: Clear Focus History and Streak"
                                                    ResetType.FACTORY_RESET -> "Confirm: Factory Reset"
                                                },
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }

                                        OutlinedButton(
                                            enabled = !routingState.isActing,
                                            onClick = {
                                                routingSession.takeResult(resultId)
                                            },
                                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                        ) {
                                            Text("Cancel", color = Color.White)
                                        }
                                    }
                                }
                            }

                            is SmartRouteResult.SyllabusAction -> {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        enabled = !routingState.isActing,
                                        onClick = {
                                            if (!routingSession.takeResult(resultId)) return@Button
                                            onNavigateToSyllabus?.invoke()
                                        },
                                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                        colors =
                                            ButtonDefaults.buttonColors(
                                                containerColor = AnchorColors.HarborPrimary,
                                                contentColor = Color(0xFF002A4A),
                                            ),
                                    ) {
                                        Icon(imageVector = Icons.Default.School, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open Syllabus Hub", fontWeight = FontWeight.Bold)
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
