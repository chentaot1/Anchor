package com.anchor.adhd.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anchor.adhd.data.model.BreakdownGranularity
import com.anchor.adhd.ui.chat.ChatScreen
import com.anchor.adhd.ui.components.AnchorChip
import com.anchor.adhd.ui.components.AnchorHairline
import com.anchor.adhd.ui.components.AnchorQuietButton
import com.anchor.adhd.ui.components.AnchorWell
import com.anchor.adhd.ui.components.AnchorWellTone
import com.anchor.adhd.ui.components.HarborCtaButton
import com.anchor.adhd.ui.components.HarborEmptyState
import com.anchor.adhd.ui.copy.AppCopy
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens
import com.anchor.adhd.ui.vm.AnchorViewModel

private data class AiTool(
    val title: String,
    val description: String,
    val inputHint: String = "",
    val action: String = "",
)

private val AI_TOOLS = listOf(
    AiTool(
        title = "Break Down",
        description = "Turn a daunting task into small, physical steps.",
        inputHint = "What task feels difficult to start?",
        action = "Break down",
    ),
    AiTool(
        title = "Brain Dump",
        description = "Unload racing thoughts into individual tasks.",
        inputHint = "Tasks, errands, worries — anything on your mind",
        action = "Extract tasks",
    ),
    AiTool(
        title = "Triage",
        description = "Order today's inbox: quick wins first, then heavy focus.",
        action = "Triage inbox",
    ),
    AiTool(
        title = "Replan",
        description = "Rescue missed tasks without guilt. Keep a realistic next step and defer the rest.",
        action = "Rescue today",
    ),
    AiTool(
        title = "Ask Anchor",
        description = "A calm, practical executive coach. Ask for help or a two-minute starter.",
    ),
)

private fun BreakdownGranularity.displayLabel(): String = when (this) {
    BreakdownGranularity.MILD -> "Mild (2–3)"
    BreakdownGranularity.NORMAL -> "Normal (3–5)"
    BreakdownGranularity.SPICY -> "Spicy (5–7)"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiScreen(vm: AnchorViewModel, modifier: Modifier = Modifier) {
    val tokens = LocalAnchorTokens.current
    val captainTextColor = if (tokens.isDark) tokens.captain else tokens.captainInk
    val captainButtonContent = if (tokens.isDark) tokens.captainInk else tokens.surface

    val preview by vm.aiPreview.collectAsState()
    val brainDump by vm.brainDumpPreview.collectAsState()
    val triage by vm.triagePreview.collectAsState()
    val replan by vm.replanAiPreview.collectAsState()
    val inbox by vm.inboxToday.collectAsState()
    val replanQueue by vm.replanWithTasks.collectAsState()
    val loading by vm.aiLoading.collectAsState()
    val chatBusy by vm.chatBusy.collectAsState()
    val error by vm.aiError.collectAsState()
    val modelReady by vm.modelDownloaded.collectAsState()
    val granularity by vm.breakdownGranularity.collectAsState()

    LaunchedEffect(Unit) { vm.refreshModelStatus() }

    var selectedTool by rememberSaveable { mutableIntStateOf(0) }
    var breakdownInput by rememberSaveable { mutableStateOf("") }
    var brainDumpInput by rememberSaveable { mutableStateOf("") }
    val toolState = rememberSaveableStateHolder()
    val current = AI_TOOLS[selectedTool]
    val input = if (selectedTool == 0) breakdownInput else brainDumpInput

    val modelStatusLabel = when {
        modelReady && vm.aiNativeRuntime -> "MiniCPM5 Q8_0, offline AI"
        modelReady -> AppCopy.AI_STUB_FILE_ONLY
        vm.aiNativeRuntime -> AppCopy.AI_STUB
        else -> AppCopy.AI_NO_NATIVE
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.paper)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AnchorSpacing.screenHorizontal, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Harbor Captain",
                    style = MaterialTheme.typography.headlineMedium,
                    color = tokens.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = tokens.captain,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = modelStatusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.captain,
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AI_TOOLS.forEachIndexed { index, tool ->
                    AnchorChip(
                        label = tool.title,
                        selected = selectedTool == index,
                        onClick = { selectedTool = index },
                        captainAccent = true,
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }

        AnchorHairline()

        toolState.SaveableStateProvider(selectedTool) {
            if (selectedTool == 4) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AnchorSpacing.screenHorizontal, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnchorQuietButton(
                        label = "Clear chat",
                        onClick = { vm.clearChat() },
                        outlined = false,
                        tint = tokens.muted,
                        enabled = !chatBusy,
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
                ChatScreen(vm, Modifier.weight(1f).fillMaxWidth())
            } else {
                // Separate scroll positions keep each tool's input and result together.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AnchorSpacing.screenHorizontal, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = current.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.muted,
                    )

                    if (selectedTool < 2) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { if (selectedTool == 0) breakdownInput = it else brainDumpInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(current.inputHint) },
                            minLines = 3,
                            maxLines = 8,
                            shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = tokens.captain,
                                unfocusedBorderColor = tokens.fieldEdge,
                                focusedTextColor = tokens.ink,
                                unfocusedTextColor = tokens.ink,
                                focusedContainerColor = tokens.surface,
                                unfocusedContainerColor = tokens.surface,
                                focusedLabelColor = tokens.captain,
                                unfocusedLabelColor = tokens.muted,
                                cursorColor = tokens.captain,
                            ),
                        )

                        if (selectedTool == 0) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                BreakdownGranularity.entries.forEach { option ->
                                    AnchorChip(
                                        label = option.displayLabel(),
                                        selected = granularity == option,
                                        onClick = { vm.setBreakdownGranularity(option) },
                                        captainAccent = true,
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    )
                                }
                            }
                        }
                    } else {
                        val count = if (selectedTool == 2) inbox.size else replanQueue.count { it.second != null }
                        if (count > 0) {
                            AnchorWell {
                                Text(
                                    text = "$count ${if (count == 1) "task" else "tasks"} ready",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                    color = tokens.ink,
                                )
                            }
                        } else if (selectedTool == 2) {
                            HarborEmptyState(
                                title = "Your inbox is clear",
                                message = "Capture a task from Home or open Plan to add items.",
                            )
                        } else {
                            HarborEmptyState(
                                title = "No missed tasks to rescue",
                                message = "Your planning tools are available anytime in Plan.",
                            )
                        }
                    }

                    val canRun = !loading && !chatBusy && when (selectedTool) {
                        0, 1 -> input.isNotBlank()
                        2 -> inbox.isNotEmpty()
                        else -> replanQueue.any { it.second != null }
                    }

                    Button(
                        onClick = {
                            when (selectedTool) {
                                0 -> vm.breakdownTask(breakdownInput.trim())
                                1 -> vm.brainDump(brainDumpInput.trim())
                                2 -> vm.triageInbox()
                                3 -> vm.replanAssist()
                            }
                        },
                        enabled = canRun,
                        shape = RoundedCornerShape(AnchorSpacing.radiusButton),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.captain,
                            contentColor = captainButtonContent,
                            disabledContainerColor = tokens.captain.copy(alpha = 0.45f),
                            disabledContentColor = captainButtonContent.copy(alpha = 0.45f),
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp,
                            disabledElevation = 0.dp,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = current.action,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    if (loading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = tokens.captain,
                            )
                            Text(
                                text = AppCopy.AI_RUNNING,
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.muted,
                            )
                        }
                    }

                    error?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.clayRose,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }

                    when (selectedTool) {
                        0 -> preview?.let { result ->
                            CaptainPreviewWell(title = "Grounded micro-steps") {
                                Text(
                                    text = "First physical action",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = captainTextColor,
                                )
                                Text(
                                    text = result.next_action,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = tokens.ink,
                                )
                                AnchorHairline()
                                result.steps.forEachIndexed { index, step ->
                                    val minutes = result.minutes_estimate.getOrNull(index)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Text(
                                            text = "${index + 1}.",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontFeatureSettings = "tnum",
                                            ),
                                            color = captainTextColor,
                                        )
                                        Text(
                                            text = step,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = tokens.ink,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (minutes != null) {
                                            Text(
                                                text = "${minutes}m",
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontFeatureSettings = "tnum",
                                                ),
                                                color = tokens.muted,
                                            )
                                        }
                                    }
                                }
                                result.if_then?.let { cue ->
                                    Text(
                                        text = cue,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.ink2,
                                    )
                                }
                                HarborCtaButton(
                                    onClick = { vm.applyAiPreviewAndStart() },
                                    enabled = !loading,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                ) {
                                    Text(
                                        text = "Add and focus for 10 min",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                                AnchorQuietButton(
                                    label = "Add to Inbox",
                                    onClick = { vm.applyAiPreview() },
                                    outlined = true,
                                    tint = captainTextColor,
                                    enabled = !loading,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                )
                            }
                        }

                        1 -> brainDump?.let { result ->
                            CaptainPreviewWell(title = "Extracted tasks") {
                                result.tasks.forEachIndexed { index, task ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = "${index + 1}.",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontFeatureSettings = "tnum",
                                            ),
                                            color = captainTextColor,
                                        )
                                        Text(
                                            text = task,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = tokens.ink,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                                HarborCtaButton(
                                    onClick = { vm.applyBrainDump() },
                                    enabled = !loading,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                ) {
                                    Text(
                                        text = "Add all to Inbox",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }

                        2 -> triage?.let { result ->
                            CaptainPreviewWell(title = "Momentum order") {
                                result.ordered_task_titles.forEachIndexed { index, task ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = "${index + 1}.",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontFeatureSettings = "tnum",
                                            ),
                                            color = captainTextColor,
                                        )
                                        Text(
                                            text = task,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = tokens.ink,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                                result.rationale?.let { rationale ->
                                    Text(
                                        text = rationale,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.ink2,
                                    )
                                }
                                HarborCtaButton(
                                    onClick = { vm.applyTriageOrder() },
                                    enabled = !loading,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                ) {
                                    Text(
                                        text = "Apply Inbox order",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }

                        3 -> replan?.let { result ->
                            CaptainPreviewWell(title = "A realistic recovery") {
                                result.message?.let { note ->
                                    Text(
                                        text = note,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = tokens.ink,
                                    )
                                }
                                result.recommended_titles.forEach { title ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = "Today",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = captainTextColor,
                                        )
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = tokens.ink,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                                result.defer_titles.forEach { title ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = "Someday",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = tokens.muted,
                                        )
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = tokens.ink2,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                                HarborCtaButton(
                                    onClick = { vm.applyReplanAi() },
                                    enabled = !loading,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                ) {
                                    Text(
                                        text = "Apply suggestions",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptainPreviewWell(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    val captainTextColor = if (tokens.isDark) tokens.captain else tokens.captainInk

    AnchorWell(
        tone = AnchorWellTone.Captain,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = captainTextColor,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}
