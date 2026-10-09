package com.anchor.adhd.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.adhd.desktop.ai.DesktopAiEngine
import com.anchor.adhd.desktop.ai.LlamaServerStatus
import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import com.anchor.adhd.desktop.theme.AnchorColors
import com.anchor.adhd.desktop.theme.AnchorSpacing
import kotlinx.coroutines.launch

data class DesktopChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis(),
)

private data class AiToolTab(
    val title: String,
    val description: String,
    val icon: ImageVector,
)

private val AI_TOOLS =
    listOf(
        AiToolTab("Break Down", "Decompose daunting tasks into 2-min starters", Icons.Default.AutoAwesome),
        AiToolTab("Brain Dump", "Unload racing thoughts into structured tasks", Icons.AutoMirrored.Filled.FormatListBulleted),
        AiToolTab("Triage", "Order tasks by quick wins vs heavy focus", Icons.Default.FilterList),
        AiToolTab("Replan", "Rescue a derailed day without guilt", Icons.Default.Refresh),
        AiToolTab("Ask Anchor", "Calm, practical ADHD grounding advice", Icons.AutoMirrored.Filled.Chat),
    )

/**
 * Windows 11 On-Device AI Executive Studio for ADHD.
 * Provides instant task breakdown, brain dump extraction, smart triage,
 * and derailed day replanning powered 100% locally by Claude-Opus-Fable5 (Q8_0).
 */
@Composable
fun DesktopAiScreen(
    db: AnchorDesktopDatabase,
    initialTask: String = "",
    onStartFocus: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSyllabus: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val tasks by db.tasks.collectAsState()
    val syllabusItems by db.syllabusItems.collectAsState()
    val engineStatus by DesktopAiEngine.engineStatus.collectAsState()
    val isModelReady = engineStatus is LlamaServerStatus.Ready

    var selectedTool by remember { mutableIntStateOf(0) }

    // Breakdown Tab State
    var breakdownInput by remember { mutableStateOf(initialTask) }
    var granularity by remember { mutableStateOf(DesktopAiEngine.DesktopBreakdownGranularity.NORMAL) }
    val breakdownSteps = remember { mutableStateListOf<String>() }
    var isBreakingDown by remember { mutableStateOf(false) }
    var breakdownSavedMessage by remember { mutableStateOf<String?>(null) }

    val runBreakdownForGranularity: (DesktopAiEngine.DesktopBreakdownGranularity) -> Unit = { targetGranularity ->
        val trimmed = breakdownInput.trim()
        if (trimmed.isNotBlank()) {
            breakdownSavedMessage = null
            val instant = DesktopAiEngine.generateMicroSteps(trimmed, targetGranularity)
            breakdownSteps.clear()
            breakdownSteps.addAll(instant)

            if (isModelReady || DesktopAiEngine.isModelReady()) {
                isBreakingDown = true
                scope.launch {
                    val aiSteps = DesktopAiEngine.generateMicroStepsAsync(trimmed, targetGranularity)
                    if (aiSteps.isNotEmpty()) {
                        breakdownSteps.clear()
                        breakdownSteps.addAll(aiSteps)
                    }
                    isBreakingDown = false
                }
            }
        }
    }

    // Brain Dump Tab State
    var brainDumpInput by remember { mutableStateOf("") }
    val extractedTasks = remember { mutableStateListOf<String>() }
    var isDumping by remember { mutableStateOf(false) }
    var brainDumpSavedMessage by remember { mutableStateOf<String?>(null) }

    // Triage Tab State
    val triagedTasks = remember { mutableStateListOf<String>() }
    var isTriaging by remember { mutableStateOf(false) }

    // Replan Tab State
    val recommendedTasks = remember { mutableStateListOf<String>() }
    val deferredTasks = remember { mutableStateListOf<String>() }
    var isReplanning by remember { mutableStateOf(false) }

    // Chat Tab State
    var chatInput by remember { mutableStateOf("") }
    val chatMessages =
        remember {
            mutableStateListOf(
                DesktopChatMessage(
                    isUser = false,
                    text = "Hello! I'm Anchor, your calm executive partner. When you're stuck, frozen, or overwhelmed, tell me what you're facing. We'll find one tiny 2-minute step to break the inertia.",
                ),
            )
        }
    var isChatting by remember { mutableStateOf(false) }

    // Auto-populate when navigating with initialTask
    LaunchedEffect(initialTask) {
        if (initialTask.isNotBlank()) {
            breakdownInput = initialTask
            selectedTool = 0
            if (breakdownSteps.isEmpty()) {
                val instant = DesktopAiEngine.generateMicroSteps(initialTask, granularity)
                breakdownSteps.clear()
                breakdownSteps.addAll(instant)

                if (isModelReady || DesktopAiEngine.isModelReady()) {
                    isBreakingDown = true
                    scope.launch {
                        val aiSteps = DesktopAiEngine.generateMicroStepsAsync(initialTask, granularity)
                        if (aiSteps.isNotEmpty()) {
                            breakdownSteps.clear()
                            breakdownSteps.addAll(aiSteps)
                        }
                        isBreakingDown = false
                    }
                }
            }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
    ) {
        // Top Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Executive Function Studio",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                        color = if (isModelReady) AnchorColors.HarborAi.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                        border =
                            BorderStroke(
                                1.dp,
                                if (isModelReady) AnchorColors.HarborAi.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.2f),
                            ),
                    ) {
                        Text(
                            text = if (isModelReady) "Claude-Opus-Fable5 (100% Offline)" else "Heuristic Ready",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isModelReady) AnchorColors.HarborAi else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Text(
                    text = "Grounded neurodivergent tools to bypass task paralysis and organize mental momentum",
                    style = MaterialTheme.typography.bodySmall,
                    color = AnchorColors.HarborPrimary.copy(alpha = 0.8f),
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Tool Selector Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AI_TOOLS.forEachIndexed { index, tool ->
                val isSelected = selectedTool == index
                Surface(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(AnchorSpacing.radiusCard))
                            .clickable { selectedTool = index },
                    color = if (isSelected) AnchorColors.HarborAi.copy(alpha = 0.18f) else AnchorColors.HarborDock,
                    border =
                        BorderStroke(
                            1.dp,
                            if (isSelected) AnchorColors.HarborAi.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f),
                        ),
                    shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = null,
                            tint = if (isSelected) AnchorColors.HarborAi else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = tool.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Syllabus connected · ${syllabusItems.count { !it.isCompleted }} pending items",
                color = AnchorColors.HarborGrowth,
                style = MaterialTheme.typography.bodySmall,
            )
            if (onOpenSyllabus != null) {
                OutlinedButton(onClick = onOpenSyllabus) { Text("Open Syllabus") }
            }
        }

        // Active Tool Canvas
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            shape = RoundedCornerShape(AnchorSpacing.radiusCard),
            color = AnchorColors.HarborDock.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                when (selectedTool) {
                    0 ->
                        BreakdownToolCanvas(
                            input = breakdownInput,
                            onInputChange = { breakdownInput = it },
                            granularity = granularity,
                            onGranularityChange = { newGranularity ->
                                granularity = newGranularity
                                if (breakdownSteps.isNotEmpty() && breakdownInput.isNotBlank()) {
                                    runBreakdownForGranularity(newGranularity)
                                }
                            },
                            steps = breakdownSteps,
                            isLoading = isBreakingDown,
                            savedMessage = breakdownSavedMessage,
                            onBreakdown = { runBreakdownForGranularity(granularity) },
                            onAddAllToMilestones = {
                                scope.launch {
                                    breakdownSteps.forEach { step ->
                                        db.insertTask(step, 5)
                                    }
                                    breakdownSavedMessage = "Added ${breakdownSteps.size} micro-steps to Milestones!"
                                }
                            },
                            onStartFocus = onStartFocus,
                        )

                    1 ->
                        BrainDumpToolCanvas(
                            input = brainDumpInput,
                            onInputChange = { brainDumpInput = it },
                            extracted = extractedTasks,
                            isLoading = isDumping,
                            savedMessage = brainDumpSavedMessage,
                            onParse = {
                                val trimmed = brainDumpInput.trim()
                                if (trimmed.isNotBlank()) {
                                    brainDumpSavedMessage = null
                                    val instant = DesktopAiEngine.parseBrainDump(trimmed)
                                    extractedTasks.clear()
                                    extractedTasks.addAll(instant)

                                    if (isModelReady || DesktopAiEngine.isModelReady()) {
                                        isDumping = true
                                        scope.launch {
                                            val parsed = DesktopAiEngine.parseBrainDumpAsync(trimmed)
                                            if (parsed.isNotEmpty()) {
                                                extractedTasks.clear()
                                                extractedTasks.addAll(parsed)
                                            }
                                            isDumping = false
                                        }
                                    }
                                }
                            },
                            onRemoveExtracted = { itemToRemove ->
                                extractedTasks.remove(itemToRemove)
                            },
                            onAddAllToMilestones = {
                                scope.launch {
                                    extractedTasks.forEach { t ->
                                        db.insertTask(t, 25)
                                    }
                                    brainDumpSavedMessage = "Saved ${extractedTasks.size} tasks to Milestones!"
                                }
                            },
                        )

                    2 ->
                        TriageToolCanvas(
                            activeTasks = tasks.filter { !it.isCompleted }.map { it.title },
                            triagedTasks = triagedTasks,
                            isLoading = isTriaging,
                            onRunTriage = {
                                val list = tasks.filter { !it.isCompleted }.map { it.title }
                                if (list.isNotEmpty()) {
                                    isTriaging = true
                                    scope.launch {
                                        val result = DesktopAiEngine.triageTasksAsync(list)
                                        triagedTasks.clear()
                                        triagedTasks.addAll(result)
                                        isTriaging = false
                                    }
                                }
                            },
                            onStartFocus = onStartFocus,
                        )

                    3 ->
                        ReplanToolCanvas(
                            activeTasks = tasks.filter { !it.isCompleted }.map { it.title },
                            recommended = recommendedTasks,
                            deferred = deferredTasks,
                            isLoading = isReplanning,
                            onRunReplan = {
                                val list = tasks.filter { !it.isCompleted }.map { it.title }
                                if (list.isNotEmpty()) {
                                    isReplanning = true
                                    scope.launch {
                                        val (rec, def) = DesktopAiEngine.replanTasksAsync(list)
                                        recommendedTasks.clear()
                                        recommendedTasks.addAll(rec)
                                        deferredTasks.clear()
                                        deferredTasks.addAll(def)
                                        isReplanning = false
                                    }
                                }
                            },
                            onStartFocus = onStartFocus,
                        )

                    4 ->
                        ChatToolCanvas(
                            messages = chatMessages,
                            input = chatInput,
                            onInputChange = { chatInput = it },
                            isLoading = isChatting,
                            activeTaskCount = tasks.count { !it.isCompleted },
                            onSendMessage = { text ->
                                val trimmed = text.trim()
                                if (trimmed.isNotBlank() && !isChatting) {
                                    chatMessages.add(DesktopChatMessage(isUser = true, text = trimmed))
                                    chatInput = ""
                                    isChatting = true
                                    scope.launch {
                                        val activeTasks =
                                            tasks.filter { !it.isCompleted }.joinToString("\n") { "- ${it.title}" }
                                        val history = chatMessages.map { (if (it.isUser) "user" else "assistant") to it.text }
                                        val ans = DesktopAiEngine.chatTurnAsync(history, activeTasks)
                                        chatMessages.add(DesktopChatMessage(isUser = false, text = ans))
                                        isChatting = false
                                    }
                                }
                            },
                            onClearChat = {
                                chatMessages.clear()
                                chatMessages.add(
                                    DesktopChatMessage(
                                        isUser = false,
                                        text = "Fresh start! What's on your mind right now? We can break down a daunting task, prioritize your list, or find a 2-minute starter.",
                                    ),
                                )
                            },
                        )
                }
            }
        }
    }
}

// ==========================================
// Tool Sub-Canvases
// ==========================================

@Composable
private fun BreakdownToolCanvas(
    input: String,
    onInputChange: (String) -> Unit,
    granularity: DesktopAiEngine.DesktopBreakdownGranularity,
    onGranularityChange: (DesktopAiEngine.DesktopBreakdownGranularity) -> Unit,
    steps: List<String>,
    isLoading: Boolean,
    savedMessage: String?,
    onBreakdown: () -> Unit,
    onAddAllToMilestones: () -> Unit,
    onStartFocus: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = {
                    Text(
                        "Enter a task that feels heavy (e.g., 'Write biology lab report' or 'Clean out garage')",
                        color = Color.White.copy(alpha = 0.4f),
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AnchorColors.HarborAi,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            if (input.isNotBlank() && !isLoading) {
                                onBreakdown()
                            }
                        },
                    ),
            )

            Button(
                onClick = onBreakdown,
                enabled = input.isNotBlank() && !isLoading,
                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                colors = ButtonDefaults.buttonColors(containerColor = AnchorColors.HarborAi, contentColor = Color.White),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refining...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Break Down", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Granularity Selector
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Structure Level:",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.width(10.dp))
            DesktopAiEngine.DesktopBreakdownGranularity.entries.forEach { g ->
                val label =
                    when (g) {
                        DesktopAiEngine.DesktopBreakdownGranularity.MILD -> "Mild (2-3 steps)"
                        DesktopAiEngine.DesktopBreakdownGranularity.NORMAL -> "Normal (3-5 steps)"
                        DesktopAiEngine.DesktopBreakdownGranularity.SPICY -> "Deep Structure (5-7 steps)"
                    }
                FilterChip(
                    selected = granularity == g,
                    onClick = { onGranularityChange(g) },
                    label = { Text(label, fontSize = 11.sp) },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AnchorColors.HarborAi.copy(alpha = 0.25f),
                            selectedLabelColor = AnchorColors.HarborAi,
                        ),
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }

        if (savedMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = AnchorColors.HarborGrowth.copy(alpha = 0.2f),
                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                border = BorderStroke(1.dp, AnchorColors.HarborGrowth.copy(alpha = 0.5f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = AnchorColors.HarborFoliage,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        savedMessage,
                        color = AnchorColors.HarborFoliage,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Steps List or Placeholder
        if (steps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Enter any task above to generate frictionless 5-minute micro-steps",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.4f),
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "GROUNDED MICRO-STEPS (FIRST STEP REMOVES FRICTION)",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.1.sp,
                    color = AnchorColors.HarborAction,
                    fontWeight = FontWeight.Bold,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onAddAllToMilestones,
                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                        border = BorderStroke(1.dp, AnchorColors.HarborPrimary.copy(alpha = 0.5f)),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = AnchorColors.HarborPrimary,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add to Milestones", fontSize = 12.sp, color = AnchorColors.HarborPrimary)
                    }

                    Button(
                        onClick = { onStartFocus(steps.first()) },
                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                        colors = ButtonDefaults.buttonColors(containerColor = AnchorColors.HarborAction, contentColor = Color.Black),
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Focus on Step 1", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(steps) { index, step ->
                    val isStarter = index == 0
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AnchorSpacing.radiusCard))
                                .clickable { onStartFocus(step) },
                        color = if (isStarter) AnchorColors.HarborBackground.copy(alpha = 0.9f) else AnchorColors.HarborDock,
                        border =
                            BorderStroke(
                                1.dp,
                                if (isStarter) AnchorColors.HarborAction.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.08f),
                            ),
                        shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (isStarter) AnchorColors.HarborAction else AnchorColors.HarborMist),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isStarter) Color.Black else Color.White,
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                if (isStarter) {
                                    Text(
                                        text = "2-MINUTE PHYSICAL STARTER",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AnchorColors.HarborAction,
                                    )
                                }
                                Text(
                                    text = step,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isStarter) FontWeight.Bold else FontWeight.Medium,
                                    color = Color.White,
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Focus",
                                tint = if (isStarter) AnchorColors.HarborAction else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrainDumpToolCanvas(
    input: String,
    onInputChange: (String) -> Unit,
    extracted: List<String>,
    isLoading: Boolean,
    savedMessage: String?,
    onParse: () -> Unit,
    onRemoveExtracted: (String) -> Unit,
    onAddAllToMilestones: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "MENTAL UNPACK (CHAOS INGESTION)",
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.1.sp,
            color = AnchorColors.HarborAi,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            placeholder = {
                Text(
                    "Paste everything cluttering your brain: tasks, grocery items, unread emails, worries...\n\ne.g., finish chem lab, email professor about extension, get cat food, submit code review",
                    color = Color.White.copy(alpha = 0.4f),
                )
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(130.dp),
            shape = RoundedCornerShape(AnchorSpacing.radiusCard),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AnchorColors.HarborAi,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onParse,
                enabled = input.isNotBlank() && !isLoading,
                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                colors = ButtonDefaults.buttonColors(containerColor = AnchorColors.HarborAi, contentColor = Color.White),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Extracting Tasks...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Extract Discrete Tasks", fontWeight = FontWeight.Bold)
                }
            }

            if (extracted.isNotEmpty()) {
                Button(
                    onClick = onAddAllToMilestones,
                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                    colors = ButtonDefaults.buttonColors(containerColor = AnchorColors.HarborPrimary, contentColor = Color(0xFF002A4A)),
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add All (${extracted.size}) to Milestones", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (savedMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                savedMessage,
                color = AnchorColors.HarborFoliage,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (extracted.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(extracted) { task ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = AnchorColors.HarborBackground.copy(alpha = 0.8f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = AnchorColors.HarborAi,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = task,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { onRemoveExtracted(task) },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove task",
                                    tint = Color.White.copy(alpha = 0.45f),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TriageToolCanvas(
    activeTasks: List<String>,
    triagedTasks: List<String>,
    isLoading: Boolean,
    onRunTriage: () -> Unit,
    onStartFocus: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "ADHD MOMENTUM TRIAGE",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.1.sp,
                    color = AnchorColors.HarborPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Sorts tasks: quick wins first to build dopamine, followed by heavy focus targets",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }

            Button(
                onClick = onRunTriage,
                enabled = activeTasks.isNotEmpty() && !isLoading,
                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                colors = ButtonDefaults.buttonColors(containerColor = AnchorColors.HarborPrimary, contentColor = Color(0xFF002A4A)),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF002A4A), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ordering...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Triage ${activeTasks.size} Tasks", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val displayList = if (triagedTasks.isNotEmpty()) triagedTasks else activeTasks
        if (displayList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No active milestones in your plan to triage!", color = Color.White.copy(alpha = 0.4f))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(displayList) { idx, t ->
                    val isQuickWin = idx == 0 && triagedTasks.isNotEmpty()
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AnchorSpacing.radiusCard))
                                .clickable { onStartFocus(t) },
                        color =
                            if (isQuickWin) {
                                AnchorColors.HarborGrowth.copy(
                                    alpha = 0.15f,
                                )
                            } else {
                                AnchorColors.HarborBackground.copy(alpha = 0.8f)
                            },
                        border =
                            BorderStroke(
                                1.dp,
                                if (isQuickWin) AnchorColors.HarborGrowth.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f),
                            ),
                        shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "#${idx + 1}",
                                fontWeight = FontWeight.Bold,
                                color = if (isQuickWin) AnchorColors.HarborFoliage else Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                if (isQuickWin) {
                                    Text(
                                        "RECOMMENDED QUICK WIN (MOMENTUM BUILDER)",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AnchorColors.HarborFoliage,
                                    )
                                }
                                Text(t, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            }
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = AnchorColors.HarborPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplanToolCanvas(
    activeTasks: List<String>,
    recommended: List<String>,
    deferred: List<String>,
    isLoading: Boolean,
    onRunReplan: () -> Unit,
    onStartFocus: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "DERAILED DAY RECOVERY",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.1.sp,
                    color = AnchorColors.HarborAction,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Zero guilt. Recommends 1-2 realistic tasks for today and defers the rest kindly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }

            Button(
                onClick = onRunReplan,
                enabled = activeTasks.isNotEmpty() && !isLoading,
                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                colors = ButtonDefaults.buttonColors(containerColor = AnchorColors.HarborAction, contentColor = Color.Black),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rebalancing...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rescue Today", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (recommended.isEmpty() && deferred.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Click 'Rescue Today' to gently downscale your commitments.", color = Color.White.copy(alpha = 0.4f))
            }
        } else {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Recommended
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "DO ONLY THESE TODAY (1-2 MAX)",
                        style = MaterialTheme.typography.labelSmall,
                        color = AnchorColors.HarborGrowth,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recommended) { r ->
                            Surface(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(AnchorSpacing.radiusCard))
                                        .clickable { onStartFocus(r) },
                                color = AnchorColors.HarborGrowth.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, AnchorColors.HarborGrowth.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = AnchorColors.HarborFoliage,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(r, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = AnchorColors.HarborFoliage,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Deferred
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "DEFERRED SAFELY WITHOUT GUILT",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(deferred) { d ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = AnchorColors.HarborBackground.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Deferred", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(d, color = Color.White.copy(alpha = 0.6f))
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
private fun ChatToolCanvas(
    messages: List<DesktopChatMessage>,
    input: String,
    onInputChange: (String) -> Unit,
    isLoading: Boolean,
    activeTaskCount: Int,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit,
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom whenever a new message arrives or loading state changes
    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Sticky Header Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "CALM ADHD EXECUTIVE COACH",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.1.sp,
                        color = AnchorColors.HarborAi,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                        color = AnchorColors.HarborAi.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, AnchorColors.HarborAi.copy(alpha = 0.3f)),
                    ) {
                        Text(
                            text = if (activeTaskCount > 0) "$activeTaskCount Tasks In Orbit" else "No Task Friction",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = AnchorColors.HarborAi,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Safe, warm executive partner. Validates overwhelm & gives frictionless 2-minute physical starters.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }

            OutlinedButton(
                onClick = onClearChat,
                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.8f)),
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clear Chat", fontSize = 11.sp)
            }
        }

        // Message Thread Canvas
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(AnchorSpacing.radiusCard))
                    .background(AnchorColors.HarborBackground.copy(alpha = 0.5f))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)), RoundedCornerShape(AnchorSpacing.radiusCard))
                    .padding(14.dp),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    if (msg.isUser) {
                        // User message bubble (Aligned Right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                                color = AnchorColors.HarborAi.copy(alpha = 0.22f),
                                border = BorderStroke(1.dp, AnchorColors.HarborAi.copy(alpha = 0.55f)),
                                modifier = Modifier.fillMaxWidth(0.75f).wrapContentWidth(Alignment.End),
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    SelectionContainer {
                                        Text(
                                            text = msg.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White,
                                            lineHeight = 20.sp,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Anchor message bubble (Aligned Left with Avatar)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(AnchorColors.HarborAi.copy(alpha = 0.18f))
                                        .border(BorderStroke(1.dp, AnchorColors.HarborAi.copy(alpha = 0.4f)), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = AnchorColors.HarborAi,
                                    modifier = Modifier.size(16.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Surface(
                                shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                                color = AnchorColors.HarborDock,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier.fillMaxWidth(0.85f),
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 6.dp),
                                    ) {
                                        Text(
                                            text = "Anchor Coach",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = AnchorColors.HarborAi,
                                        )
                                    }
                                    SelectionContainer {
                                        Text(
                                            text = msg.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White,
                                            lineHeight = 22.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // In-flight thinking indicator bubble
                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(AnchorColors.HarborAi.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = AnchorColors.HarborAi,
                                    modifier = Modifier.size(16.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Surface(
                                shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                                color = AnchorColors.HarborDock.copy(alpha = 0.8f),
                                border = BorderStroke(1.dp, AnchorColors.HarborAi.copy(alpha = 0.3f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = AnchorColors.HarborAi,
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Anchor is thinking of a gentle 2-minute step...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AnchorColors.HarborAi,
                                        fontStyle = FontStyle.Italic,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Starter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val suggestions =
                listOf(
                    "I'm frozen and can't start",
                    "Break down my assignment",
                    "I'm overwhelmed by my task list",
                    "Give me a 2-minute starter",
                )
            suggestions.forEach { chipText ->
                Surface(
                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                    color = AnchorColors.HarborDock,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.clickable(enabled = !isLoading) { onSendMessage(chipText) },
                ) {
                    Text(
                        text = chipText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bottom Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = {
                    Text(
                        "Talk with Anchor... (Press Enter to send)",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 13.sp,
                    )
                },
                maxLines = 4,
                modifier =
                    Modifier
                        .weight(1f)
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.Enter && !event.isShiftPressed && event.type == KeyEventType.KeyDown) {
                                if (input.isNotBlank() && !isLoading) {
                                    onSendMessage(input)
                                    true
                                } else {
                                    false
                                }
                            } else {
                                false
                            }
                        },
                shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AnchorColors.HarborAi,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
            )

            Button(
                onClick = { onSendMessage(input) },
                enabled = input.isNotBlank() && !isLoading,
                shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = AnchorColors.HarborAi,
                        contentColor = Color.White,
                        disabledContainerColor = AnchorColors.HarborAi.copy(alpha = 0.2f),
                        disabledContentColor = Color.White.copy(alpha = 0.3f),
                    ),
                modifier = Modifier.height(54.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
