package com.anchor.adhd.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anchor.adhd.desktop.ai.DesktopAiEngine
import com.anchor.adhd.desktop.ai.LlamaServerStatus
import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
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
import kotlinx.coroutines.launch
import java.util.UUID

data class DesktopChatMessage(
    val id: String = UUID.randomUUID().toString(),
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
        AiToolTab("Break Down", "Decompose daunting tasks into 2-minute starters", Icons.Default.AutoAwesome),
        AiToolTab("Brain Dump", "Unload racing thoughts into structured tasks", Icons.AutoMirrored.Filled.FormatListBulleted),
        AiToolTab("Triage", "Order tasks by quick wins vs heavy focus", Icons.Default.FilterList),
        AiToolTab("Replan", "Rescue a derailed day without guilt", Icons.Default.Refresh),
        AiToolTab("Ask Anchor", "Calm, practical ADHD grounding advice", Icons.AutoMirrored.Filled.Chat),
    )

/**
 * Windows 11 On-Device Harbor Captain Studio.
 * Provides instant task breakdown, brain dump extraction, smart triage,
 * and derailed day replanning powered 100% locally by Claude-Opus-Fable5 (Q8_0).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesktopAiScreen(
    db: AnchorDesktopDatabase,
    initialTask: String = "",
    onStartFocus: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSyllabus: (() -> Unit)? = null,
    initialTool: Int = 0,
) {
    val tokens = LocalAnchorTokens.current
    val captainTextColor = if (tokens.isDark) tokens.captain else tokens.captainInk
    val scope = rememberCoroutineScope()
    val tasks by db.tasks.collectAsState()
    val syllabusItems by db.syllabusItems.collectAsState()
    val engineStatus by DesktopAiEngine.engineStatus.collectAsState()
    val isModelReady = engineStatus is LlamaServerStatus.Ready

    var selectedTool by remember(initialTool) { mutableIntStateOf(initialTool.coerceIn(0, AI_TOOLS.lastIndex)) }

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
                    text =
                        "Hello! I'm Harbor Captain, your calm executive partner. " +
                            "When you're stuck, frozen, or overwhelmed, tell me what you're facing. " +
                            "We'll find one tiny 2-minute step to break the inertia.",
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

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(tokens.paper),
    ) {
        val compactLayout = maxWidth < 680.dp || maxHeight < 520.dp
        val horizontalPadding = if (compactLayout) 16.dp else AnchorSpacing.screenHorizontalDesktop
        val verticalPadding = if (compactLayout) 12.dp else 20.dp
        val sectionSpacing = if (compactLayout) 10.dp else 16.dp
        val pendingSyllabusCount = syllabusItems.count { !it.isCompleted }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
        ) {
            // Top Header Row
            if (compactLayout) {
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
                        Text(
                            text = "Harbor Captain",
                            style = MaterialTheme.typography.titleLarge,
                            color = tokens.ink,
                            modifier = Modifier.semantics { heading() },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Surface(
                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                            color = if (isModelReady) tokens.captainSoft else tokens.paper2,
                            shadowElevation = 0.dp,
                            tonalElevation = 0.dp,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (isModelReady) captainTextColor else tokens.muted,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = if (isModelReady) "Offline AI" else "Heuristic ready",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isModelReady) captainTextColor else tokens.muted,
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    if (onOpenSyllabus != null) {
                        AnchorQuietButton(
                            label = "Syllabus ($pendingSyllabusCount)",
                            onClick = onOpenSyllabus,
                            outlined = false,
                            tint = tokens.primary,
                        )
                    }
                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Harbor Captain",
                                style = MaterialTheme.typography.headlineMedium,
                                color = tokens.ink,
                                modifier = Modifier.semantics { heading() },
                            )
                            Surface(
                                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                color = if (isModelReady) tokens.captainSoft else tokens.paper2,
                                shadowElevation = 0.dp,
                                tonalElevation = 0.dp,
                                modifier = Modifier.align(Alignment.CenterVertically),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = if (isModelReady) captainTextColor else tokens.muted,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = if (isModelReady) "Claude-Opus-Fable5 (offline)" else "Heuristic ready",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isModelReady) captainTextColor else tokens.muted,
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Grounded tools to bypass task paralysis and build momentum.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.muted,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Syllabus connected: $pendingSyllabusCount pending",
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = tokens.sage,
                        )
                        if (onOpenSyllabus != null) {
                            AnchorQuietButton(
                                label = "Open syllabus",
                                onClick = onOpenSyllabus,
                                outlined = true,
                                tint = tokens.ink,
                            )
                        }
                    }
                }
            }

            // 5 Captain Tool Selector Chips (single scrollable row keeps compact height clean)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AI_TOOLS.forEachIndexed { index, tool ->
                    AnchorChip(
                        label = tool.title,
                        selected = selectedTool == index,
                        onClick = { selectedTool = index },
                        captainAccent = true,
                        leadingIcon = {
                            Icon(
                                imageVector = tool.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }

            AnchorHairline()

            // Active Tool Canvas directly on flat paper canvas (zero nested bordered boxes)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) {
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
                            compactLayout = compactLayout,
                            onBreakdown = { runBreakdownForGranularity(granularity) },
                            onAddAllToMilestones = {
                                scope.launch {
                                    breakdownSteps.forEach { step ->
                                        db.insertTask(step, 5)
                                    }
                                    breakdownSavedMessage = "Added ${breakdownSteps.size} micro-steps to Milestones."
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
                            compactLayout = compactLayout,
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
                                    extractedTasks.forEach { task ->
                                        db.insertTask(task, 25)
                                    }
                                    brainDumpSavedMessage = "Saved ${extractedTasks.size} tasks to Milestones."
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
                            compactLayout = compactLayout,
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
                                        text =
                                            "Fresh start. What's on your mind right now? " +
                                                "We can break down a daunting task, prioritize your list, or find a 2-minute starter.",
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BreakdownToolCanvas(
    input: String,
    onInputChange: (String) -> Unit,
    granularity: DesktopAiEngine.DesktopBreakdownGranularity,
    onGranularityChange: (DesktopAiEngine.DesktopBreakdownGranularity) -> Unit,
    steps: List<String>,
    isLoading: Boolean,
    savedMessage: String?,
    compactLayout: Boolean,
    onBreakdown: () -> Unit,
    onAddAllToMilestones: () -> Unit,
    onStartFocus: (String) -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    val captainTextColor = if (tokens.isDark) tokens.captain else tokens.captainInk
    val captainButtonContent = if (tokens.isDark) tokens.captainInk else tokens.surface
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(if (compactLayout) 10.dp else 14.dp),
    ) {
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
                        text =
                            if (compactLayout) {
                                "Enter a daunting task…"
                            } else {
                                "Enter a task that feels heavy (e.g., \"Write biology lab report\")"
                            },
                        color = tokens.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                colors = captainTextFieldColors(),
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

            CaptainSolidButton(
                onClick = onBreakdown,
                enabled = input.isNotBlank() && !isLoading,
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = captainButtonContent,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refining…", style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Break down", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Granularity Selector
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Structure level",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.muted,
            )
            DesktopAiEngine.DesktopBreakdownGranularity.entries.forEach { option ->
                val label =
                    when (option) {
                        DesktopAiEngine.DesktopBreakdownGranularity.MILD -> "Mild (2–3 steps)"
                        DesktopAiEngine.DesktopBreakdownGranularity.NORMAL -> "Normal (3–5 steps)"
                        DesktopAiEngine.DesktopBreakdownGranularity.SPICY -> "Deep (5–7 steps)"
                    }
                AnchorChip(
                    label = label,
                    selected = granularity == option,
                    onClick = { onGranularityChange(option) },
                    captainAccent = true,
                )
            }
        }

        if (savedMessage != null) {
            Surface(
                color = tokens.sageSoft,
                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = tokens.sage,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = savedMessage,
                        color = tokens.sage,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Steps List or Empty State
        if (steps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                HarborEmptyState(
                    title = "Ready to break a task down",
                    message = "Enter any task above to generate frictionless starter steps.",
                )
            }
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Grounded micro-steps",
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "First step removes starting friction",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.muted,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnchorQuietButton(
                        label = "Add to Milestones",
                        onClick = onAddAllToMilestones,
                        outlined = true,
                        tint = tokens.ink,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                    HarborCtaButton(
                        onClick = { onStartFocus(steps.first()) },
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Focus on step 1", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(steps) { index, step ->
                        if (index == 0) {
                            AnchorWell(
                                tone = AnchorWellTone.Captain,
                                contentPadding = PaddingValues(14.dp),
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(AnchorSpacing.radiusWell))
                                        .clickable(role = Role.Button) { onStartFocus(step) },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(tokens.captain),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "1",
                                            style =
                                                MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontFeatureSettings = "tnum",
                                                ),
                                            color = captainButtonContent,
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = "2-minute physical starter",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = captainTextColor,
                                        )
                                        Text(
                                            text = step,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = tokens.ink,
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Focus on $step",
                                        tint = captainTextColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        } else {
                            AnchorListRow(
                                title = step,
                                onClick = { onStartFocus(step) },
                                minHeight = 44.dp,
                                showDivider = index < steps.lastIndex,
                                leading = {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(tokens.paper2),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            style =
                                                MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontFeatureSettings = "tnum",
                                                ),
                                            color = tokens.ink2,
                                        )
                                    }
                                },
                                trailing = {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Focus on $step",
                                        tint = tokens.muted,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            )
                        }
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    style = harborScrollbarStyle(),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrainDumpToolCanvas(
    input: String,
    onInputChange: (String) -> Unit,
    extracted: List<String>,
    isLoading: Boolean,
    savedMessage: String?,
    compactLayout: Boolean,
    onParse: () -> Unit,
    onRemoveExtracted: (String) -> Unit,
    onAddAllToMilestones: () -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    val captainButtonContent = if (tokens.isDark) tokens.captainInk else tokens.surface
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(if (compactLayout) 10.dp else 14.dp),
    ) {
        AnchorSectionHeader(
            title = "Brain dump",
            trailingLabel = "Unload racing thoughts",
        )

        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            placeholder = {
                Text(
                    text =
                        "Paste everything cluttering your mind: tasks, groceries, emails, worries…\n" +
                            "e.g., finish chem lab, email professor, get cat food, submit code review",
                    color = tokens.muted,
                )
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 84.dp, max = if (compactLayout) 104.dp else 140.dp),
            shape = RoundedCornerShape(AnchorSpacing.radiusChip),
            colors = captainTextFieldColors(),
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CaptainSolidButton(
                onClick = onParse,
                enabled = input.isNotBlank() && !isLoading,
                modifier = Modifier.heightIn(min = 40.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = captainButtonContent,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Extracting tasks…", style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Extract discrete tasks", style = MaterialTheme.typography.labelLarge)
                }
            }

            if (extracted.isNotEmpty()) {
                HarborCtaButton(
                    onClick = onAddAllToMilestones,
                    modifier = Modifier.heightIn(min = 40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add all (${extracted.size}) to Milestones", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (savedMessage != null) {
            Text(
                text = savedMessage,
                color = tokens.sage,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        if (extracted.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                ) {
                    itemsIndexed(extracted) { index, task ->
                        AnchorListRow(
                            title = task,
                            minHeight = 44.dp,
                            showDivider = index < extracted.lastIndex,
                            leading = {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = tokens.captain,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            trailing = {
                                IconButton(
                                    onClick = { onRemoveExtracted(task) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove $task",
                                        tint = tokens.muted,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            },
                        )
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    style = harborScrollbarStyle(),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TriageToolCanvas(
    activeTasks: List<String>,
    triagedTasks: List<String>,
    isLoading: Boolean,
    onRunTriage: () -> Unit,
    onStartFocus: (String) -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    val captainTextColor = if (tokens.isDark) tokens.captain else tokens.captainInk
    val captainButtonContent = if (tokens.isDark) tokens.captainInk else tokens.surface
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Momentum triage",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "Sorts quick wins first to build momentum, followed by deeper focus targets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.muted,
                )
            }

            CaptainSolidButton(
                onClick = onRunTriage,
                enabled = activeTasks.isNotEmpty() && !isLoading,
                modifier = Modifier.heightIn(min = 40.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = captainButtonContent,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ordering…", style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Triage ${activeTasks.size} tasks",
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    )
                }
            }
        }

        val displayList = if (triagedTasks.isNotEmpty()) triagedTasks else activeTasks
        if (displayList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                HarborEmptyState(
                    title = "No active tasks to triage",
                    message = "Add tasks in Plan or capture a thought from Home first.",
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(displayList) { index, taskTitle ->
                        val isQuickWin = index == 0 && triagedTasks.isNotEmpty()
                        if (isQuickWin) {
                            AnchorWell(
                                tone = AnchorWellTone.Captain,
                                contentPadding = PaddingValues(14.dp),
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(AnchorSpacing.radiusWell))
                                        .clickable(role = Role.Button) { onStartFocus(taskTitle) },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        text = "#1",
                                        style =
                                            MaterialTheme.typography.labelLarge.copy(
                                                fontFeatureSettings = "tnum",
                                            ),
                                        color = captainTextColor,
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = "Recommended quick win",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = captainTextColor,
                                        )
                                        Text(
                                            text = taskTitle,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = tokens.ink,
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Focus on $taskTitle",
                                        tint = captainTextColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        } else {
                            AnchorListRow(
                                title = taskTitle,
                                onClick = { onStartFocus(taskTitle) },
                                minHeight = 44.dp,
                                showDivider = index < displayList.lastIndex,
                                leading = {
                                    Text(
                                        text = "#${index + 1}",
                                        style =
                                            MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontFeatureSettings = "tnum",
                                            ),
                                        color = tokens.muted,
                                    )
                                },
                                trailing = {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Focus on $taskTitle",
                                        tint = tokens.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            )
                        }
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    style = harborScrollbarStyle(),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReplanToolCanvas(
    activeTasks: List<String>,
    recommended: List<String>,
    deferred: List<String>,
    isLoading: Boolean,
    onRunReplan: () -> Unit,
    onStartFocus: (String) -> Unit,
) {
    val tokens = LocalAnchorTokens.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Day recovery",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "No guilt. Recommends 1–2 realistic tasks for today and defers the rest.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.muted,
                )
            }

            HarborCtaButton(
                onClick = onRunReplan,
                enabled = activeTasks.isNotEmpty() && !isLoading,
                modifier = Modifier.heightIn(min = 40.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = tokens.beaconInk,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rebalancing…", style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rescue today", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (recommended.isEmpty() && deferred.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                HarborEmptyState(
                    title = "Ready when the day shifts",
                    message = "Select Rescue today to gently narrow your commitments to 1 or 2 realistic priorities.",
                )
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val twoColumns = maxWidth >= 560.dp
                if (twoColumns) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        ReplanColumn(
                            title = "Do only these today",
                            countLabel = "${recommended.size}",
                            items = recommended,
                            isRecommended = true,
                            onStartFocus = onStartFocus,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        ReplanColumn(
                            title = "Deferred without guilt",
                            countLabel = "${deferred.size}",
                            items = deferred,
                            isRecommended = false,
                            onStartFocus = onStartFocus,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                } else {
                    val scrollState = rememberScrollState()
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            ReplanSectionInline(
                                title = "Do only these today",
                                countLabel = "${recommended.size}",
                                items = recommended,
                                isRecommended = true,
                                onStartFocus = onStartFocus,
                            )
                            ReplanSectionInline(
                                title = "Deferred without guilt",
                                countLabel = "${deferred.size}",
                                items = deferred,
                                isRecommended = false,
                                onStartFocus = onStartFocus,
                            )
                        }
                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(scrollState),
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            style = harborScrollbarStyle(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplanColumn(
    title: String,
    countLabel: String,
    items: List<String>,
    isRecommended: Boolean,
    onStartFocus: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAnchorTokens.current
    val listState = rememberLazyListState()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnchorSectionHeader(title = title, trailingLabel = countLabel)
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = 10.dp),
            ) {
                itemsIndexed(items) { index, taskTitle ->
                    AnchorListRow(
                        title = taskTitle,
                        trailingText = if (isRecommended) null else "Deferred",
                        onClick = if (isRecommended) ({ onStartFocus(taskTitle) }) else null,
                        minHeight = 44.dp,
                        showDivider = index < items.lastIndex,
                        leading =
                            if (isRecommended) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = tokens.sage,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else {
                                null
                            },
                        trailing =
                            if (isRecommended) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Focus on $taskTitle",
                                        tint = tokens.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            } else {
                                null
                            },
                    )
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                style = harborScrollbarStyle(),
            )
        }
    }
}

@Composable
private fun ReplanSectionInline(
    title: String,
    countLabel: String,
    items: List<String>,
    isRecommended: Boolean,
    onStartFocus: (String) -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AnchorSectionHeader(title = title, trailingLabel = countLabel)
        items.forEachIndexed { index, taskTitle ->
            AnchorListRow(
                title = taskTitle,
                trailingText = if (isRecommended) null else "Deferred",
                onClick = if (isRecommended) ({ onStartFocus(taskTitle) }) else null,
                minHeight = 44.dp,
                showDivider = index < items.lastIndex,
                leading =
                    if (isRecommended) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = tokens.sage,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else {
                        null
                    },
                trailing =
                    if (isRecommended) {
                        {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Focus on $taskTitle",
                                tint = tokens.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
            )
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
    compactLayout: Boolean,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    val captainTextColor = if (tokens.isDark) tokens.captain else tokens.captainInk
    val captainButtonContent = if (tokens.isDark) tokens.captainInk else tokens.surface
    val listState = rememberLazyListState()

    // Auto-scroll to bottom whenever a new message arrives or loading state changes
    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(if (compactLayout) 8.dp else 10.dp),
    ) {
        // Sticky Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Ask Harbor Captain",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    Surface(
                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                        color = tokens.captainSoft,
                        shadowElevation = 0.dp,
                        tonalElevation = 0.dp,
                    ) {
                        Text(
                            text = if (activeTaskCount > 0) "$activeTaskCount active tasks" else "Deck clear",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold,
                            color = captainTextColor,
                        )
                    }
                }
                if (!compactLayout) {
                    Text(
                        text = "Calm executive partner. Validates overwhelm and gives 2-minute physical starters.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.muted,
                    )
                }
            }

            AnchorQuietButton(
                label = "Clear chat",
                onClick = onClearChat,
                outlined = true,
                tint = tokens.ink,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }

        // Message Thread directly on canvas (borderless wells: paper2 for user, captainSoft for Captain)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    if (msg.isUser) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            AnchorWell(
                                tone = AnchorWellTone.Neutral,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(0.76f),
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = msg.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = tokens.ink,
                                    )
                                }
                            }
                        }
                    } else {
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
                                        .background(tokens.captainSoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = captainTextColor,
                                    modifier = Modifier.size(16.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            AnchorWell(
                                tone = AnchorWellTone.Captain,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f, fill = false).widthIn(max = 560.dp),
                            ) {
                                Text(
                                    text = "Harbor Captain",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = captainTextColor,
                                )
                                SelectionContainer {
                                    Text(
                                        text = msg.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = tokens.ink,
                                    )
                                }
                            }
                        }
                    }
                }

                // In-flight thinking indicator well
                if (isLoading) {
                    item(key = "thinking") {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(tokens.captainSoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = captainTextColor,
                                    modifier = Modifier.size(16.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            AnchorWell(
                                tone = AnchorWellTone.Captain,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier.widthIn(max = 360.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = captainTextColor,
                                        strokeWidth = 2.dp,
                                    )
                                    Text(
                                        text = "Captain is finding a gentle 2-minute step…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = captainTextColor,
                                        fontStyle = FontStyle.Italic,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                style = harborScrollbarStyle(),
            )
        }

        // Quick Starter Chips (single scrollable row stays tidy at 640x450)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
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
                AnchorChip(
                    label = chipText,
                    selected = false,
                    onClick = { onSendMessage(chipText) },
                    captainAccent = true,
                    enabled = !isLoading,
                )
            }
        }

        // Bottom Input Row (Enter sends, Shift+Enter inserts newline)
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
                        text = "Talk with Harbor Captain… (Enter to send)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                maxLines = 4,
                modifier =
                    Modifier
                        .weight(1f)
                        .onPreviewKeyEvent { event ->
                            if ((event.key == Key.Enter || event.key == Key.NumPadEnter) &&
                                !event.isShiftPressed &&
                                event.type == KeyEventType.KeyDown
                            ) {
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
                shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                colors = captainTextFieldColors(),
            )

            CaptainSolidButton(
                onClick = { onSendMessage(input) },
                enabled = input.isNotBlank() && !isLoading,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = captainButtonContent,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun CaptainSolidButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    val captainButtonContent = if (tokens.isDark) tokens.captainInk else tokens.surface

    Button(
        onClick = onClick,
        enabled = enabled,
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
        modifier = modifier,
        content = content,
    )
}

@Composable
private fun captainTextFieldColors() =
    with(LocalAnchorTokens.current) {
        OutlinedTextFieldDefaults.colors(
            focusedBorderColor = captain,
            unfocusedBorderColor = fieldEdge,
            focusedTextColor = ink,
            unfocusedTextColor = ink,
            focusedContainerColor = surface,
            unfocusedContainerColor = surface,
            focusedPlaceholderColor = muted,
            unfocusedPlaceholderColor = muted,
            focusedLabelColor = captain,
            unfocusedLabelColor = muted,
            cursorColor = captain,
        )
    }

@Composable
private fun harborScrollbarStyle() =
    with(LocalAnchorTokens.current) {
        defaultScrollbarStyle().copy(
            unhoverColor = rule,
            hoverColor = primary,
        )
    }
