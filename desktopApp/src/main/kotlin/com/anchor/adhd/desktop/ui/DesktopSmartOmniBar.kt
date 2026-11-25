package com.anchor.adhd.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anchor.adhd.desktop.ai.DesktopRoutingSession
import com.anchor.adhd.desktop.ai.ResetType
import com.anchor.adhd.desktop.ai.SmartRouteResult
import com.anchor.adhd.ui.components.AnchorHairline
import com.anchor.adhd.ui.components.AnchorQuietButton
import com.anchor.adhd.ui.components.AnchorWell
import com.anchor.adhd.ui.components.AnchorWellTone
import com.anchor.adhd.ui.components.HarborCtaButton
import com.anchor.adhd.ui.theme.AnchorMotion
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens
import com.anchor.adhd.ui.theme.LocalReducedMotion

internal fun cleanRouteHeadline(raw: String): String = raw.trimStart { !it.isLetterOrDigit() && it != '"' && it != '\'' }.ifBlank { raw.trim() }

private fun routeResultIcon(result: SmartRouteResult): ImageVector =
    when (result) {
        is SmartRouteResult.TimerAction -> Icons.Default.Timer
        is SmartRouteResult.BlockerAction -> Icons.Default.Shield
        is SmartRouteResult.GroundingReset -> Icons.Default.Bolt
        is SmartRouteResult.TaskBreakdown -> Icons.Default.AutoAwesome
        is SmartRouteResult.BrainDump -> Icons.AutoMirrored.Filled.FormatListBulleted
        is SmartRouteResult.QuickNote -> Icons.Default.Check
        is SmartRouteResult.ResetDataAction -> Icons.Default.RestartAlt
        is SmartRouteResult.SyllabusAction -> Icons.Default.School
    }

/**
 * Universal Smart AI Omni-Bar.
 *
 * Eliminates executive navigation friction:
 * Users don't have to categorize their thoughts or pick a menu. They type or paste anything,
 * and Harbor Captain routes it to a task breakdown, brain dump, timer command,
 * blocker rule, or calm overwhelm reset.
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
    val tokens = LocalAnchorTokens.current
    val reducedMotion = LocalReducedMotion.current
    val captainTextColor = if (tokens.isDark) tokens.captain else tokens.captainInk
    val captainButtonContent = if (tokens.isDark) tokens.captainInk else tokens.surface

    val routingState by routingSession.state.collectAsState()
    val rawInput = routingState.draft
    val activeResult = routingState.result
    val resultId = routingState.resultId
    val isProcessing = routingState.isProcessing

    fun runAction(
        label: String,
        message: String,
        action: suspend (String) -> Unit,
    ) {
        routingSession.performAction(resultId, label, canRunAction, message, action)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Omni-Bar Input Plate
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = tokens.surface,
            contentColor = tokens.ink,
            shape = RoundedCornerShape(AnchorSpacing.radiusPlate),
            border = BorderStroke(1.dp, if (activeResult != null) tokens.captain else tokens.rule),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compact = maxWidth < 480.dp
                val shortPlaceholder = maxWidth < 680.dp
                Row(
                    modifier = Modifier.padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!compact) {
                        Box(
                            modifier =
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(tokens.captainSoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = captainTextColor,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    OutlinedTextField(
                        value = rawInput,
                        onValueChange = routingSession::edit,
                        placeholder = {
                            Text(
                                text =
                                    if (shortPlaceholder) {
                                        "Ask Captain or capture a task…"
                                    } else {
                                        "Ask Captain or capture a task — e.g. \"25m timer\", \"syllabus\", \"block discord\"…"
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingIcon = {
                            if (rawInput.isNotBlank()) {
                                IconButton(
                                    enabled = !routingState.isActing,
                                    onClick = { routingSession.clear() },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear router",
                                        tint = tokens.muted,
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
                                focusedTextColor = tokens.ink,
                                unfocusedTextColor = tokens.ink,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                cursorColor = tokens.captain,
                            ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { routingSession.submit() }),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { routingSession.submit() },
                        enabled = !routingState.isActing && rawInput.isNotBlank(),
                        shape = RoundedCornerShape(AnchorSpacing.radiusButton),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = tokens.captain,
                                contentColor = captainButtonContent,
                                disabledContainerColor = tokens.captain.copy(alpha = 0.45f),
                                disabledContentColor = captainButtonContent.copy(alpha = 0.45f),
                            ),
                        elevation =
                            ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 0.dp,
                                focusedElevation = 0.dp,
                                hoveredElevation = 0.dp,
                                disabledElevation = 0.dp,
                            ),
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) {
                        if (isProcessing && rawInput.isBlank()) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = captainButtonContent,
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
                        Text(
                            text = if (isProcessing && rawInput.isBlank()) "Thinking…" else "Route",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }

        routingState.actionLabel?.let { label ->
            Row(
                modifier =
                    Modifier
                        .padding(top = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = tokens.captain,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$label…",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.muted,
                )
            }
        }

        routingState.feedback?.let { feedback ->
            Text(
                text = feedback,
                style = MaterialTheme.typography.bodySmall,
                color = if (routingState.isActionError) tokens.clayRose else tokens.sage,
                modifier =
                    Modifier
                        .padding(top = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        // Active Smart Route Result Expansion (borderless Captain tonal well, zero nested cards)
        AnimatedVisibility(
            visible = activeResult != null,
            enter =
                if (reducedMotion) {
                    EnterTransition.None
                } else {
                    expandVertically(tween(AnchorMotion.SHORT_MS)) + fadeIn(tween(AnchorMotion.SHORT_MS))
                },
            exit =
                if (reducedMotion) {
                    ExitTransition.None
                } else {
                    shrinkVertically(tween(AnchorMotion.SHORT_MS)) + fadeOut(tween(AnchorMotion.SHORT_MS))
                },
        ) {
            val result = activeResult
            if (result != null) {
                AnchorWell(
                    tone = AnchorWellTone.Captain,
                    contentPadding = PaddingValues(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = routeResultIcon(result),
                                contentDescription = null,
                                tint = captainTextColor,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = cleanRouteHeadline(result.headline),
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.ink,
                            )
                        }

                        IconButton(
                            enabled = !routingState.isActing,
                            onClick = { routingSession.takeResult(resultId) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = captainTextColor,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    Text(
                        text = result.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.ink2,
                    )

                    // Dynamic Actions tailored to identified Intent
                    when (result) {
                        is SmartRouteResult.TaskBreakdown -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val shownSteps = result.microSteps.take(3)
                                shownSteps.forEachIndexed { index, step ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = "Step ${index + 1}:",
                                            style =
                                                MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontFeatureSettings = "tnum",
                                                ),
                                            color = captainTextColor,
                                        )
                                        Text(
                                            text = step,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = tokens.ink,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (index < shownSteps.lastIndex) {
                                        AnchorHairline()
                                    }
                                }

                                if (result.microSteps.size > 3) {
                                    Text(
                                        text = "+${result.microSteps.size - 3} more steps will be saved to your Milestones",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                        color = tokens.muted,
                                    )
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    HarborCtaButton(
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
                                        modifier = Modifier.heightIn(min = 40.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Start step 1 now (${result.suggestedMinutes}m)",
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }

                                    AnchorQuietButton(
                                        label = "Add to Milestones",
                                        enabled = !routingState.isActing,
                                        outlined = true,
                                        tint = captainTextColor,
                                        onClick = {
                                            runAction("Saving tasks", "Saved to Inbox.") { key ->
                                                onSaveTasksToInbox(key, listOf(result.taskTitle) + result.microSteps)
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        is SmartRouteResult.BrainDump -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                result.tasks.take(4).forEach { task ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = captainTextColor,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Text(
                                            text = task,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = tokens.ink,
                                        )
                                    }
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    HarborCtaButton(
                                        enabled = !routingState.isActing,
                                        onClick = {
                                            runAction("Preparing focus", "Focus started.") { key ->
                                                onSaveTasksToInbox(key, result.tasks)
                                                onStartFocusWithTask(result.primaryTask, 25)
                                            }
                                        },
                                        modifier = Modifier.heightIn(min = 40.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Save and anchor \"${result.primaryTask.take(20)}\"",
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }

                                    AnchorQuietButton(
                                        label = "Add all to inbox",
                                        enabled = !routingState.isActing,
                                        outlined = true,
                                        tint = captainTextColor,
                                        onClick = {
                                            runAction("Saving tasks", "Saved to Inbox.") { key ->
                                                onSaveTasksToInbox(key, result.tasks)
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        is SmartRouteResult.GroundingReset -> {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                HarborCtaButton(
                                    enabled = !routingState.isActing,
                                    onClick = {
                                        runAction("Preparing focus", "Focus started.") { _ ->
                                            onStartFocusWithTask(result.recommendedTask, result.suggestedMinutes)
                                        }
                                    },
                                    modifier = Modifier.heightIn(min = 40.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Start ${result.suggestedMinutes}m gentle reset",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }

                        is SmartRouteResult.TimerAction -> {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                HarborCtaButton(
                                    enabled = !routingState.isActing,
                                    onClick = {
                                        runAction("Preparing focus", "Focus started.") { _ ->
                                            onStartFocusWithTask(result.taskTitle, result.durationMinutes)
                                        }
                                    },
                                    modifier = Modifier.heightIn(min = 40.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Start ${result.durationMinutes}m focus now",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }

                        is SmartRouteResult.BlockerAction -> {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                HarborCtaButton(
                                    enabled = !routingState.isActing,
                                    onClick = {
                                        runAction("Updating shield", "Shield updated.") { _ ->
                                            onApplyBlockerRule(result.target, result.enable)
                                        }
                                    },
                                    modifier = Modifier.heightIn(min = 40.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (result.enable) "Block ${result.target}" else "Unblock ${result.target}",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }

                        is SmartRouteResult.QuickNote -> {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                HarborCtaButton(
                                    enabled = !routingState.isActing,
                                    onClick = {
                                        runAction("Saving tasks", "Saved to Inbox.") { key ->
                                            onSaveTasksToInbox(key, listOf(result.noteTitle))
                                        }
                                    },
                                    modifier = Modifier.heightIn(min = 40.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save to inbox", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }

                        is SmartRouteResult.ResetDataAction -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                                    color = tokens.clayRoseSoft,
                                    shadowElevation = 0.dp,
                                    tonalElevation = 0.dp,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = tokens.clayRose,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text =
                                                "Clean slate: resetting removes backlog pressure without judgment. " +
                                                    "Your AI model and local settings remain intact.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = tokens.ink,
                                        )
                                    }
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    val confirmLabel =
                                        when (result.resetType) {
                                            ResetType.CLEAR_TASKS -> "Confirm: Clear all tasks"
                                            ResetType.RESET_STREAK -> "Confirm: Clear focus history and streak"
                                            ResetType.FACTORY_RESET -> "Confirm: Factory reset"
                                        }
                                    if (result.resetType == ResetType.FACTORY_RESET) {
                                        Button(
                                            enabled = !routingState.isActing,
                                            onClick = {
                                                if (!routingSession.takeResult(resultId)) return@Button
                                                onResetData?.invoke(result.resetType)
                                            },
                                            shape = RoundedCornerShape(AnchorSpacing.radiusButton),
                                            colors =
                                                ButtonDefaults.buttonColors(
                                                    containerColor = tokens.clayRose,
                                                    contentColor = MaterialTheme.colorScheme.onError,
                                                    disabledContainerColor = tokens.clayRose.copy(alpha = 0.45f),
                                                    disabledContentColor = MaterialTheme.colorScheme.onError.copy(alpha = 0.45f),
                                                ),
                                            elevation =
                                                ButtonDefaults.buttonElevation(
                                                    defaultElevation = 0.dp,
                                                    pressedElevation = 0.dp,
                                                    focusedElevation = 0.dp,
                                                    hoveredElevation = 0.dp,
                                                    disabledElevation = 0.dp,
                                                ),
                                            modifier = Modifier.heightIn(min = 40.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RestartAlt,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(confirmLabel, style = MaterialTheme.typography.labelLarge)
                                        }
                                    } else {
                                        HarborCtaButton(
                                            enabled = !routingState.isActing,
                                            onClick = {
                                                if (!routingSession.takeResult(resultId)) return@HarborCtaButton
                                                onResetData?.invoke(result.resetType)
                                            },
                                            modifier = Modifier.heightIn(min = 40.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RestartAlt,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(confirmLabel, style = MaterialTheme.typography.labelLarge)
                                        }
                                    }

                                    AnchorQuietButton(
                                        label = "Cancel",
                                        enabled = !routingState.isActing,
                                        outlined = true,
                                        tint = captainTextColor,
                                        onClick = { routingSession.takeResult(resultId) },
                                    )
                                }
                            }
                        }

                        is SmartRouteResult.SyllabusAction -> {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                HarborCtaButton(
                                    enabled = !routingState.isActing,
                                    onClick = {
                                        if (!routingSession.takeResult(resultId)) return@HarborCtaButton
                                        onNavigateToSyllabus?.invoke()
                                    },
                                    modifier = Modifier.heightIn(min = 40.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.School,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Syllabus hub", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
