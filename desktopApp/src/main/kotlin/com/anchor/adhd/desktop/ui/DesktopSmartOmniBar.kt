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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.adhd.desktop.ai.DesktopSmartRouter
import com.anchor.adhd.desktop.ai.ResetType
import com.anchor.adhd.desktop.ai.SmartRouteResult
import com.anchor.adhd.desktop.theme.AnchorColors
import com.anchor.adhd.desktop.theme.AnchorSpacing
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Universal Smart AI Omni-Bar.
 *
 * Eliminates executive navigation friction:
 * Users don't have to categorize their thoughts or pick a menu. They type or paste anything,
 * and the router interprets whether it's a task breakdown, brain dump, timer command,
 * blocker rule, or emotional overwhelm reset.
 */
@Composable
fun DesktopSmartOmniBar(
    onStartFocusWithTask: (taskTitle: String, minutes: Int) -> Unit,
    onSaveTasksToInbox: (List<String>) -> Unit,
    onApplyBlockerRule: (target: String, enable: Boolean) -> Unit,
    onResetData: ((ResetType) -> Unit)? = null,
    onNavigateToSyllabus: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var rawInput by remember { mutableStateOf("") }
    var activeResult by remember { mutableStateOf<SmartRouteResult?>(null) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    fun processInput() {
        val text = rawInput.trim()
        if (text.isBlank()) return

        // 1. Immediately cancel any prior in-flight background LLM enrichment job
        // so previous prompt contexts never leak or overwrite the new prompt!
        activeJob?.cancel()

        // 2. Clear input immediately so user sees a clean state for subsequent queries
        rawInput = ""

        // 3. Instant deterministic routing in <2ms
        val immediate = DesktopSmartRouter.routeQuick(text)
        activeResult = immediate

        // 4. Asynchronous LLM enrichment only for intents that benefit from deep decomposition
        if (immediate is SmartRouteResult.TaskBreakdown || immediate is SmartRouteResult.BrainDump) {
            isProcessing = true
            activeJob =
                scope.launch {
                    try {
                        val enriched = DesktopSmartRouter.routeAsync(text)
                        activeResult = enriched
                    } finally {
                        isProcessing = false
                    }
                }
        }
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
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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

                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { rawInput = it },
                    placeholder = {
                        Text(
                            text = "Tell Anchor anything — e.g. \"25m timer\", \"syllabus\", \"Write lab report\", \"block discord\"...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.45f),
                        )
                    },
                    trailingIcon = {
                        if (rawInput.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    rawInput = ""
                                    activeJob?.cancel()
                                    activeResult = null
                                },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear input",
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
                                    activeJob?.cancel()
                                    activeResult = null
                                    rawInput = ""
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
                    keyboardActions = KeyboardActions(onDone = { processInput() }),
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { processInput() },
                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = AnchorColors.HarborAi,
                            contentColor = Color(0xFF1B0F33),
                        ),
                    modifier = Modifier.height(40.dp),
                ) {
                    if (isProcessing) {
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
                    Text(if (isProcessing) "Thinking..." else "Route", fontWeight = FontWeight.Bold)
                }
            }
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = result.headline,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }

                            IconButton(
                                onClick = {
                                    activeJob?.cancel()
                                    activeResult = null
                                    rawInput = ""
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

                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = {
                                                activeJob?.cancel()
                                                val remainingSteps = result.microSteps.drop(1)
                                                if (remainingSteps.isNotEmpty()) {
                                                    onSaveTasksToInbox(remainingSteps)
                                                }
                                                onStartFocusWithTask(
                                                    result.step1Starter.ifBlank { result.taskTitle },
                                                    result.suggestedMinutes,
                                                )
                                                activeResult = null
                                                rawInput = ""
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
                                            onClick = {
                                                activeJob?.cancel()
                                                onSaveTasksToInbox(listOf(result.taskTitle) + result.microSteps)
                                                activeResult = null
                                                rawInput = ""
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

                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = {
                                                activeJob?.cancel()
                                                onSaveTasksToInbox(result.tasks)
                                                onStartFocusWithTask(result.primaryTask, 25)
                                                activeResult = null
                                                rawInput = ""
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
                                            onClick = {
                                                activeJob?.cancel()
                                                onSaveTasksToInbox(result.tasks)
                                                activeResult = null
                                                rawInput = ""
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
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = {
                                            activeJob?.cancel()
                                            onStartFocusWithTask(result.recommendedTask, result.suggestedMinutes)
                                            activeResult = null
                                            rawInput = ""
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
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = {
                                            activeJob?.cancel()
                                            onStartFocusWithTask(result.taskTitle, result.durationMinutes)
                                            activeResult = null
                                            rawInput = ""
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
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = {
                                            activeJob?.cancel()
                                            onApplyBlockerRule(result.target, result.enable)
                                            activeResult = null
                                            rawInput = ""
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
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = {
                                            activeJob?.cancel()
                                            onSaveTasksToInbox(listOf(result.noteTitle))
                                            activeResult = null
                                            rawInput = ""
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

                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = {
                                                activeJob?.cancel()
                                                onResetData?.invoke(result.resetType)
                                                activeResult = null
                                                rawInput = ""
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
                                                    ResetType.RESET_STREAK -> "Confirm: Reset Streak to Day 1"
                                                    ResetType.FACTORY_RESET -> "Confirm: Factory Reset"
                                                },
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                activeResult = null
                                                rawInput = ""
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
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = {
                                            activeJob?.cancel()
                                            onNavigateToSyllabus?.invoke()
                                            activeResult = null
                                            rawInput = ""
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
