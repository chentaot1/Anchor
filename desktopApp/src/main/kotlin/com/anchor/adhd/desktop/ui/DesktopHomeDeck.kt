package com.anchor.adhd.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anchor.adhd.desktop.ai.DesktopRoutingSession
import com.anchor.adhd.desktop.ai.ResetType
import com.anchor.adhd.desktop.db.DesktopFunLink
import com.anchor.adhd.desktop.db.DesktopHarborState
import com.anchor.adhd.desktop.db.DesktopTask
import com.anchor.adhd.desktop.db.DesktopThoughtCaptureState
import com.anchor.adhd.domain.GroveLevel
import com.anchor.adhd.ui.components.AnchorChip
import com.anchor.adhd.ui.components.AnchorListRow
import com.anchor.adhd.ui.components.AnchorQuietButton
import com.anchor.adhd.ui.components.AnchorSectionHeader
import com.anchor.adhd.ui.components.AnchorWeeklyTide
import com.anchor.adhd.ui.components.AnchorWell
import com.anchor.adhd.ui.components.AnchorWellTone
import com.anchor.adhd.ui.components.HarborEmptyState
import com.anchor.adhd.ui.components.WeeklyTideDay
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens
import java.time.LocalTime

internal val HOME_TWO_COLUMN_BREAKPOINT = 860.dp

internal fun greetingForHour(hourOfDay: Int): String =
    when (hourOfDay) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

internal fun formatHomeSummary(
    todaySessionCount: Int,
    todayFocusMinutes: Int,
    streakDays: Int,
): String {
    val safeSessions = todaySessionCount.coerceAtLeast(0)
    val safeMinutes = todayFocusMinutes.coerceAtLeast(0)
    val safeStreak = streakDays.coerceAtLeast(0)
    return when {
        safeSessions > 0 -> {
            val sessionWord = if (safeSessions == 1) "session" else "sessions"
            "$safeSessions $sessionWord today, $safeMinutes min focused"
        }
        safeMinutes > 0 -> "$safeMinutes min focused today"
        safeStreak > 0 -> "Ready for your first session today, $safeStreak-day streak"
        else -> "One small step at a time"
    }
}

/** Home actions, quick start, weekly tide, quick capture, upcoming tasks, and optional break links. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun DesktopHomeDeck(
    routingSession: DesktopRoutingSession,
    canRunRouterAction: () -> Boolean,
    currentTask: DesktopTask?,
    tasks: List<DesktopTask>,
    funLinks: List<DesktopFunLink>,
    isFocusActive: Boolean,
    remainingSeconds: Int,
    durationMinutes: Int,
    todayFocusMinutes: Int,
    deflectedCount: Int,
    streakDays: Int,
    onStartFocus: () -> Unit,
    isWorkPhase: Boolean = true,
    hasOpenFocusSession: Boolean = false,
    onOpenDurationPicker: () -> Unit,
    onLaunchMomentumPreset: (Int) -> Unit,
    thoughtCaptureState: DesktopThoughtCaptureState,
    onThoughtDraftChange: (String) -> Unit,
    onCaptureThought: () -> Unit,
    onMakeTaskNow: (Long) -> Unit,
    onToggleTaskCompleted: (Long) -> Unit,
    onNavigateToPlan: () -> Unit,
    onStartFocusWithTask: suspend (taskTitle: String, minutes: Int) -> Unit = { _, _ -> },
    onSaveTasksToInbox: suspend (key: String, tasks: List<String>) -> Unit = { _, _ -> },
    onApplyBlockerRule: suspend (target: String, enable: Boolean) -> Unit = { _, _ -> },
    onResetData: ((ResetType) -> Unit)? = null,
    onNavigateToSyllabus: (() -> Unit)? = null,
    harborState: DesktopHarborState = DesktopHarborState(),
    weeklyFocusDays: List<WeeklyTideDay> = emptyList(),
    weeklySessionCount: Int = 0,
    todaySessionCount: Int = 0,
    onBreakDownWithCaptain: ((String) -> Unit)? = null,
    onOpenCaptain: (() -> Unit)? = null,
    hourOfDay: Int = LocalTime.now().hour,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAnchorTokens.current
    val effectiveStreak = if (streakDays > 0) streakDays else harborState.streakDays
    val effectiveWeeklySessions =
        if (weeklySessionCount > 0) {
            weeklySessionCount
        } else {
            weeklyFocusDays.count { it.minutes > 0 }
        }
    val stageLabel = GroveLevel.vitalityLabel(GroveLevel.vitalityStageForWeeklySessions(effectiveWeeklySessions))
    val totalWeeklyMinutes = weeklyFocusDays.sumOf { it.minutes }.coerceAtLeast(todayFocusMinutes)
    val captainAction: (() -> Unit)? =
        when {
            currentTask != null && onBreakDownWithCaptain != null -> ({ onBreakDownWithCaptain(currentTask.title) })
            onOpenCaptain != null -> onOpenCaptain
            else -> null
        }

    DesktopHomeLayout(
        modifier = modifier,
        header = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = greetingForHour(hourOfDay),
                        style = MaterialTheme.typography.headlineMedium,
                        color = tokens.ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = formatHomeSummary(todaySessionCount, todayFocusMinutes, effectiveStreak),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = tokens.muted,
                    )
                }

                DesktopSmartOmniBar(
                    routingSession = routingSession,
                    canRunAction = canRunRouterAction,
                    onStartFocusWithTask = onStartFocusWithTask,
                    onSaveTasksToInbox = onSaveTasksToInbox,
                    onApplyBlockerRule = onApplyBlockerRule,
                    onResetData = onResetData,
                    onNavigateToSyllabus = onNavigateToSyllabus,
                )
            }
        },
        leftPanel = { panelModifier ->
            Column(panelModifier, verticalArrangement = Arrangement.spacedBy(AnchorSpacing.sectionGap)) {
                // 1. Committed Deep-Harbor Next Action Plate
                DesktopNowCard(
                    currentTask = currentTask,
                    isFocusActive = isFocusActive,
                    isWorkPhase = isWorkPhase,
                    hasOpenFocusSession = hasOpenFocusSession,
                    remainingSeconds = remainingSeconds,
                    durationMinutes = durationMinutes,
                    onStartFocus = onStartFocus,
                    onOpenDurationPicker = onOpenDurationPicker,
                    onBreakDownWithCaptain = captainAction,
                )

                // 2. Quick start duration presets (Level 2 tonal well)
                AnchorWell(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnchorSectionHeader(
                        title = "Quick start",
                        trailingLabel = "Pick a pace",
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AnchorChip(
                            label = "3m spark",
                            selected = durationMinutes == 3,
                            onClick = { onLaunchMomentumPreset(3) },
                        )
                        AnchorChip(
                            label = "15m sprint",
                            selected = durationMinutes == 15,
                            onClick = { onLaunchMomentumPreset(15) },
                        )
                        AnchorChip(
                            label = "25m focus",
                            selected = durationMinutes == 25,
                            onClick = { onLaunchMomentumPreset(25) },
                        )
                        AnchorChip(
                            label = "50m flow",
                            selected = durationMinutes == 50,
                            onClick = { onLaunchMomentumPreset(50) },
                        )
                        AnchorChip(
                            label = "Custom",
                            selected = durationMinutes !in listOf(3, 15, 25, 50),
                            onClick = onOpenDurationPicker,
                        )
                    }
                }

                // 3. Today's plan (Level 1 hairline rows directly on canvas)
                val upcomingTasks = tasks.filter { !it.isCompleted && it.id != currentTask?.id }.take(3)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AnchorSectionHeader(
                        title = "Today's plan",
                        trailingLabel = "Open plan",
                        onTrailingClick = onNavigateToPlan,
                    )

                    if (upcomingTasks.isEmpty()) {
                        HarborEmptyState(
                            title = "All clear on deck",
                            message = "Capture a thought or add tasks in Plan whenever you are ready.",
                            actionLabel = "Open plan",
                            onAction = onNavigateToPlan,
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            upcomingTasks.forEachIndexed { index, task ->
                                val noteLine =
                                    task.notes
                                        .lineSequence()
                                        .map(String::trim)
                                        .firstOrNull { it.isNotEmpty() }
                                AnchorListRow(
                                    title = task.title,
                                    subtitle = noteLine,
                                    trailingText = "${task.durationMinutes}m",
                                    minHeight = 44.dp,
                                    showDivider = index < upcomingTasks.lastIndex,
                                    leading = {
                                        IconButton(
                                            onClick = { onToggleTaskCompleted(task.id) },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(
                                                imageVector =
                                                    if (task.isCompleted) {
                                                        Icons.Default.CheckCircle
                                                    } else {
                                                        Icons.Outlined.RadioButtonUnchecked
                                                    },
                                                contentDescription = "Complete ${task.title}",
                                                tint = if (task.isCompleted) tokens.sage else tokens.muted,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    },
                                    trailing = {
                                        AnchorQuietButton(
                                            label = "Make next",
                                            onClick = { onMakeTaskNow(task.id) },
                                            outlined = false,
                                            tint = tokens.primary,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        rightPanel = { panelModifier ->
            Column(panelModifier, verticalArrangement = Arrangement.spacedBy(AnchorSpacing.sectionGap)) {
                // 1. Real-data Weekly Tide bar chart
                AnchorWeeklyTide(
                    days = weeklyFocusDays,
                    stageLabel = stageLabel,
                    totalMinutes = totalWeeklyMinutes,
                    todayMinutes = todayFocusMinutes,
                    deflectedCount = deflectedCount,
                )

                // 2. Quick capture well
                AnchorWell(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AnchorSectionHeader(
                        title = "Quick capture",
                        trailingLabel = "Saves to inbox",
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = thoughtCaptureState.draft,
                            onValueChange = onThoughtDraftChange,
                            readOnly = thoughtCaptureState.isSaving,
                            placeholder = {
                                Text(
                                    text = "Capture a thought…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = tokens.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
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
                            shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onCaptureThought() }),
                        )

                        AnchorQuietButton(
                            label = if (thoughtCaptureState.isSaving) "Saving…" else "Capture",
                            onClick = onCaptureThought,
                            outlined = true,
                            tint = tokens.ink,
                            enabled = !thoughtCaptureState.isSaving && thoughtCaptureState.draft.isNotBlank(),
                            leadingIcon = {
                                if (thoughtCaptureState.isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = tokens.primary,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            },
                        )
                    }

                    thoughtCaptureState.feedback?.let { feedback ->
                        Text(
                            text = feedback,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (thoughtCaptureState.isError) tokens.clayRose else tokens.sage,
                        )
                    }
                }

                // 3. Harbor Captain contextual note (starboard teal well)
                HarborCaptainNoteWell(
                    currentTask = currentTask,
                    isFocusActive = isFocusActive,
                    hasOpenTasks = tasks.any { !it.isCompleted },
                    onAction = captainAction,
                )

                // 4. Optional Brain Breaks (Fun Links)
                if (funLinks.isNotEmpty()) {
                    DesktopFunLinks(links = funLinks)
                }
            }
        },
    )
}

@Composable
private fun HarborCaptainNoteWell(
    currentTask: DesktopTask?,
    isFocusActive: Boolean,
    hasOpenTasks: Boolean,
    onAction: (() -> Unit)?,
) {
    val tokens = LocalAnchorTokens.current
    val captainLabelColor = if (tokens.isDark) tokens.captain else tokens.captainInk
    val message =
        when {
            isFocusActive ->
                "Focus is underway. Drop stray thoughts in Quick capture so you can stay with one step."
            currentTask != null ->
                "Stuck starting \"${currentTask.title}\"? Break it into one concrete physical first step with Harbor Captain."
            !hasOpenTasks ->
                "Your deck is clear. Paste a messy brain dump into Harbor Captain to shape a calm plan."
            else ->
                "Not sure where to start? Ask Harbor Captain to sort today's tasks by effort."
        }
    val actionLabel = if (currentTask != null) "Open in Captain" else "Open Captain"

    AnchorWell(
        tone = AnchorWellTone.Captain,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = tokens.captain,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Harbor Captain",
                style = MaterialTheme.typography.titleSmall,
                color = captainLabelColor,
                maxLines = 1,
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.ink2,
        )
        if (onAction != null) {
            Spacer(modifier = Modifier.height(2.dp))
            AnchorQuietButton(
                label = actionLabel,
                onClick = onAction,
                outlined = true,
                tint = captainLabelColor,
            )
        }
    }
}

/** Scroll the entire deck, including expanded router results, without nested scroll regions. */
@Composable
private fun DesktopHomeLayout(
    modifier: Modifier,
    header: @Composable () -> Unit,
    leftPanel: @Composable (Modifier) -> Unit,
    rightPanel: @Composable (Modifier) -> Unit,
) {
    val scroll = rememberScrollState()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= HOME_TWO_COLUMN_BREAKPOINT
        val horizontalPadding = if (wide) AnchorSpacing.screenHorizontalDesktop else 16.dp
        val verticalPadding = if (wide) 20.dp else 14.dp
        Box(Modifier.fillMaxSize().padding(horizontal = horizontalPadding, vertical = verticalPadding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(AnchorSpacing.sectionGap),
            ) {
                header()
                if (wide) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        leftPanel(Modifier.weight(1.18f))
                        rightPanel(Modifier.weight(0.82f))
                    }
                } else {
                    leftPanel(Modifier.fillMaxWidth())
                    rightPanel(Modifier.fillMaxWidth())
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scroll),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
    }
}
