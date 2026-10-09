package com.anchor.adhd.ui.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anchor.adhd.data.model.BreakdownGranularity
import com.anchor.adhd.ui.chat.ChatScreen
import com.anchor.adhd.ui.copy.AppCopy
import com.anchor.adhd.ui.theme.AnchorColors
import com.anchor.adhd.ui.vm.AnchorViewModel

private data class AiTool(val title: String, val description: String, val inputHint: String = "", val action: String = "")
private val AI_TOOLS = listOf(
    AiTool("Break Down", "Turn a daunting task into small, physical steps.", "What task feels difficult to start?", "Break Down"),
    AiTool("Brain Dump", "Unload racing thoughts into individual tasks.", "Tasks, errands, worries - anything on your mind", "Extract Tasks"),
    AiTool("Triage", "Order today's inbox: quick wins first, then heavy focus.", action = "Triage Inbox"),
    AiTool("Replan", "Rescue missed tasks without guilt. Keep a realistic next step and defer the rest.", action = "Rescue Today"),
    AiTool("Ask Anchor", "A calm, practical executive coach. Ask for help or a two-minute starter.")
)

private fun BreakdownGranularity.displayLabel(): String = when (this) {
    BreakdownGranularity.MILD -> "Mild (2–3)"
    BreakdownGranularity.NORMAL -> "Normal (3–5)"
    BreakdownGranularity.SPICY -> "Spicy (5–7)"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiScreen(vm: AnchorViewModel, modifier: Modifier = Modifier) {
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

    Column(modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Executive Function Studio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(when {
                modelReady && vm.aiNativeRuntime -> "MiniCPM5 Q8_0 - offline AI"
                modelReady -> AppCopy.AI_STUB_FILE_ONLY
                vm.aiNativeRuntime -> AppCopy.AI_STUB
                else -> AppCopy.AI_NO_NATIVE
            }, style = MaterialTheme.typography.labelMedium, color = AnchorColors.HarborAi)
        }
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AI_TOOLS.forEachIndexed { index, tool ->
                FilterChip(selected = selectedTool == index, onClick = { selectedTool = index },
                    label = { Text(tool.title, maxLines = 1) })
            }
        }
        toolState.SaveableStateProvider(selectedTool) {
            if (selectedTool == 4) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { vm.clearChat() }, enabled = !chatBusy) { Text("Clear Chat") }
                }
                ChatScreen(vm, Modifier.weight(1f).fillMaxWidth())
            } else {
                // Separate scroll positions keep each tool's input and result together.
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(current.description, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selectedTool < 2) {
                        OutlinedTextField(value = input,
                            onValueChange = { if (selectedTool == 0) breakdownInput = it else brainDumpInput = it },
                            modifier = Modifier.fillMaxWidth(), label = { Text(current.inputHint) }, minLines = 3, maxLines = 8)
                        if (selectedTool == 0) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BreakdownGranularity.entries.forEach { option ->
                                    FilterChip(
                                        selected = granularity == option,
                                        onClick = { vm.setBreakdownGranularity(option) },
                                        label = { Text(option.displayLabel()) }
                                    )
                                }
                            }
                        }
                    } else {
                        val count = if (selectedTool == 2) inbox.size else replanQueue.count { it.second != null }
                        Text(if (count > 0) "$count tasks ready" else if (selectedTool == 2)
                            "Your inbox is clear. Capture a task from Home or open More > Plan."
                            else "No missed tasks to rescue. Your planning tools are in More > Plan.",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(onClick = {
                        when (selectedTool) {
                            0 -> vm.breakdownTask(breakdownInput.trim())
                            1 -> vm.brainDump(brainDumpInput.trim())
                            2 -> vm.triageInbox()
                            3 -> vm.replanAssist()
                        }
                    }, enabled = !loading && !chatBusy && when (selectedTool) {
                        0, 1 -> input.isNotBlank()
                        2 -> inbox.isNotEmpty()
                        else -> replanQueue.any { it.second != null }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(current.action) }
                    if (loading) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Text(AppCopy.AI_RUNNING)
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    when (selectedTool) {
                        0 -> preview?.let { result ->
                            PreviewCard("Grounded micro-steps") {
                                Text("First physical action", color = AnchorColors.HarborAi, style = MaterialTheme.typography.labelLarge)
                                Text(result.next_action, fontWeight = FontWeight.SemiBold)
                                result.steps.forEachIndexed { index, step ->
                                    val minutes = result.minutes_estimate.getOrNull(index)
                                    Text("${index + 1}. $step" + (minutes?.let { " ($it min)" } ?: ""))
                                }
                                result.if_then?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                Button(onClick = { vm.applyAiPreview() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("Add to Inbox") }
                                FilledTonalButton(onClick = { vm.applyAiPreviewAndStart() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("Add + Focus for 10 min") }
                            }
                        }
                        1 -> brainDump?.let { result ->
                            PreviewCard("Extracted tasks") {
                                result.tasks.forEachIndexed { index, task -> Text("${index + 1}. $task") }
                                Button(onClick = { vm.applyBrainDump() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("Add All to Inbox") }
                            }
                        }
                        2 -> triage?.let { result ->
                            PreviewCard("Momentum order") {
                                result.ordered_task_titles.forEachIndexed { index, task -> Text("${index + 1}. $task") }
                                result.rationale?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                Button(onClick = { vm.applyTriageOrder() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("Apply Inbox Order") }
                            }
                        }
                        3 -> replan?.let { result ->
                            PreviewCard("A realistic recovery") {
                                result.message?.let { Text(it) }
                                result.recommended_titles.forEach { Text("Today: $it") }
                                result.defer_titles.forEach { Text("Someday: $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Button(onClick = { vm.applyReplanAi() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("Apply Suggestions") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, AnchorColors.HarborAi.copy(alpha = 0.35f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = AnchorColors.HarborAi)
            content()
        }
    }
}
