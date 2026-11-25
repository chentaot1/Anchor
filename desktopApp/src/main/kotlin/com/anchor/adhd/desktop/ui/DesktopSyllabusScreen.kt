package com.anchor.adhd.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.anchor.adhd.desktop.ai.DesktopSyllabusParser
import com.anchor.adhd.desktop.ai.ParsedDeliverable
import com.anchor.adhd.desktop.ai.ParsedSyllabus
import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import com.anchor.adhd.desktop.db.DesktopSyllabusItem
import com.anchor.adhd.desktop.db.SyllabusItemType
import com.anchor.adhd.ui.components.AnchorChip
import com.anchor.adhd.ui.components.AnchorFocalPlate
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
import com.anchor.adhd.ui.theme.LocalReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

internal fun formatDeliverableType(type: SyllabusItemType): String =
    when (type) {
        SyllabusItemType.EXAM -> "Exam"
        SyllabusItemType.PROJECT -> "Project"
        SyllabusItemType.HOMEWORK -> "Homework"
        SyllabusItemType.READING -> "Reading"
    }

internal fun pickNextMajorDeliverable(items: List<DesktopSyllabusItem>): DesktopSyllabusItem? =
    items
        .filter { !it.isCompleted && (it.itemType == SyllabusItemType.EXAM || it.itemType == SyllabusItemType.PROJECT) }
        .minByOrNull { item -> item.dueDateMillis.takeIf { it > 0L } ?: Long.MAX_VALUE }

/**
 * Academic Harbor & Syllabus Ingestion Screen for Windows 11.
 *
 * Extracts exams, term papers, and assignments from PDFs, Word documents, images, or pasted text,
 * generates backward-chained prep milestones, and launches directly into Focus.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun DesktopSyllabusScreen(
    db: AnchorDesktopDatabase,
    onStartFocusWithTask: (taskTitle: String, durationMinutes: Int) -> Unit,
    modifier: Modifier = Modifier,
    onPlanWithAi: ((DesktopSyllabusItem) -> Unit)? = null,
) {
    val tokens = LocalAnchorTokens.current
    val captainLabelColor = if (tokens.isDark) tokens.captain else tokens.captainInk
    val scope = rememberCoroutineScope()
    val courses by db.courses.collectAsState()
    val syllabusItems by db.syllabusItems.collectAsState()

    var selectedCourseCode by remember { mutableStateOf<String?>(null) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var showAddDeliverableDialog by remember { mutableStateOf(false) }
    var showAirlockDialog by remember { mutableStateOf(false) }
    var stagedSyllabus by remember { mutableStateOf<ParsedSyllabus?>(null) }
    var isParsingFile by remember { mutableStateOf(false) }
    var expandedItemId by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()

    val displayedItems =
        remember(syllabusItems, selectedCourseCode) {
            if (selectedCourseCode == null) {
                syllabusItems
            } else {
                syllabusItems.filter { it.courseCode == selectedCourseCode }
            }
        }

    val nextMajorDeliverable = remember(syllabusItems) { pickNextMajorDeliverable(syllabusItems) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compactLayout = maxWidth < 680.dp
        val compactRow = maxWidth < 600.dp
        val horizontalPadding = if (compactLayout) 16.dp else AnchorSpacing.screenHorizontalDesktop
        val verticalPadding = if (compactLayout) 16.dp else 20.dp

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = horizontalPadding,
                        end = horizontalPadding + 10.dp,
                        top = verticalPadding,
                        bottom = verticalPadding + 12.dp,
                    ),
            ) {
                item(key = "header") {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Syllabus",
                                style = MaterialTheme.typography.headlineMedium,
                                color = tokens.ink,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                text = "Extract deadlines and build calm backward-chained prep steps",
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.muted,
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AnchorQuietButton(
                                label = if (isParsingFile) "Reading…" else "Upload syllabus",
                                onClick = {
                                    if (isParsingFile) return@AnchorQuietButton
                                    scope.launch(Dispatchers.IO) {
                                        isParsingFile = true
                                        try {
                                            val chooser =
                                                JFileChooser().apply {
                                                    dialogTitle = "Select Course Syllabus (PDF, Word, or Image)"
                                                    fileFilter =
                                                        FileNameExtensionFilter(
                                                            "Syllabus Files (*.pdf, *.docx, *.png, *.jpg, *.txt, *.md)",
                                                            "pdf",
                                                            "docx",
                                                            "png",
                                                            "jpg",
                                                            "jpeg",
                                                            "txt",
                                                            "md",
                                                            "csv",
                                                        )
                                                }
                                            val result = chooser.showOpenDialog(null)
                                            if (result == JFileChooser.APPROVE_OPTION && chooser.selectedFile != null) {
                                                val file = chooser.selectedFile
                                                val text = DesktopSyllabusParser.extractTextFromFile(file)
                                                val parsed = DesktopSyllabusParser.parseSyllabus(text)
                                                withContext(Dispatchers.Main) {
                                                    stagedSyllabus = parsed
                                                    showAirlockDialog = true
                                                }
                                            }
                                        } finally {
                                            isParsingFile = false
                                        }
                                    }
                                },
                                outlined = true,
                                tint = tokens.primary,
                                enabled = !isParsingFile,
                                leadingIcon = {
                                    if (isParsingFile) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            color = tokens.primary,
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.FileUpload,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                },
                            )

                            AnchorQuietButton(
                                label = "Paste syllabus",
                                onClick = { showPasteDialog = true },
                                outlined = true,
                                tint = tokens.ink,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentPaste,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )

                            AnchorQuietButton(
                                label = "Add deliverable",
                                onClick = { showAddDeliverableDialog = true },
                                outlined = false,
                                tint = tokens.primary,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(AnchorSpacing.sectionGap))

                    // Single AnchorFocalPlate for nearest upcoming major deliverable
                    if (nextMajorDeliverable != null) {
                        NextMajorDeliverableFocalPlate(
                            deliverable = nextMajorDeliverable,
                            captainLabelColor = captainLabelColor,
                            onStartFocus = {
                                val starter =
                                    nextMajorDeliverable.prepSteps.firstOrNull() ?: nextMajorDeliverable.title
                                onStartFocusWithTask(starter, 25)
                            },
                            onPlanWithCaptain = onPlanWithAi?.let { plan -> { plan(nextMajorDeliverable) } },
                        )
                        Spacer(modifier = Modifier.height(AnchorSpacing.sectionGap))
                    }

                    // Course Filter Chip Strip
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        item(key = "all-courses") {
                            AnchorChip(
                                label = "All courses (${syllabusItems.size})",
                                selected = selectedCourseCode == null,
                                onClick = { selectedCourseCode = null },
                            )
                        }
                        items(courses, key = { it.code }) { course ->
                            val count = syllabusItems.count { it.courseCode == course.code }
                            AnchorChip(
                                label = "${course.code} ($count)",
                                selected = selectedCourseCode == course.code,
                                onClick = { selectedCourseCode = course.code },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AnchorSectionHeader(
                            title =
                                if (selectedCourseCode == null) {
                                    "Deliverables"
                                } else {
                                    "$selectedCourseCode deliverables"
                                },
                            trailingLabel = "${displayedItems.size}",
                            modifier = Modifier.weight(1f),
                        )
                        selectedCourseCode?.let { activeCode ->
                            AnchorQuietButton(
                                label = "Remove course",
                                onClick = {
                                    scope.launch {
                                        db.deleteCourse(activeCode)
                                        selectedCourseCode = null
                                    }
                                },
                                outlined = false,
                                tint = tokens.clayRose,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                    )
                                },
                            )
                        }
                    }
                }

                if (displayedItems.isEmpty()) {
                    item(key = "empty") {
                        HarborEmptyState(
                            title = "No syllabus deliverables yet",
                            message = "Upload a syllabus file or paste course text to extract deadlines and prep steps.",
                            actionLabel = "Paste syllabus",
                            onAction = { showPasteDialog = true },
                        )
                    }
                } else {
                    itemsIndexed(displayedItems, key = { _, item -> item.id }) { index, item ->
                        SyllabusDeliverableRow(
                            item = item,
                            isExpanded = expandedItemId == item.id,
                            compactRow = compactRow,
                            showDivider = index < displayedItems.lastIndex,
                            onToggleExpand = {
                                expandedItemId = if (expandedItemId == item.id) null else item.id
                            },
                            onToggleComplete = {
                                scope.launch { db.toggleSyllabusItemCompleted(item.id) }
                            },
                            onDelete = {
                                scope.launch { db.deleteSyllabusItem(item.id) }
                            },
                            onStartFocus = { taskTitle, mins ->
                                onStartFocusWithTask(taskTitle, mins)
                            },
                            onPlanWithCaptain = onPlanWithAi?.let { plan -> { plan(item) } },
                            onAddStepToPlan = { step ->
                                scope.launch {
                                    db.insertTask("[${item.courseCode}] $step", 20)
                                }
                            },
                        )
                    }
                }
            }

            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
    }

    if (showPasteDialog) {
        PasteSyllabusDialog(
            onDismiss = { showPasteDialog = false },
            onParse = { rawText ->
                val parsed = DesktopSyllabusParser.parseSyllabus(rawText)
                stagedSyllabus = parsed
                showPasteDialog = false
                showAirlockDialog = true
            },
        )
    }

    if (showAddDeliverableDialog) {
        AddManualDeliverableDialog(
            defaultCourseCode = selectedCourseCode ?: courses.firstOrNull()?.code ?: "CS 101",
            onDismiss = { showAddDeliverableDialog = false },
            onAdd = { courseCode, title, itemType, dueDateText, weightPercent ->
                scope.launch {
                    val cleanCode = courseCode.trim().uppercase()
                    db.insertCourse(cleanCode, cleanCode)
                    val prepSteps = DesktopSyllabusParser.generateBackwardChainedPrepSteps(itemType, title)
                    db.insertSyllabusItem(
                        courseCode = cleanCode,
                        title = title.trim(),
                        itemType = itemType,
                        dueDateText = dueDateText.trim().ifBlank { "TBD" },
                        dueDateMillis = System.currentTimeMillis() + 7L * 86_400_000L,
                        weightPercent = weightPercent,
                        prepSteps = prepSteps,
                    )
                    showAddDeliverableDialog = false
                }
            },
        )
    }

    if (showAirlockDialog && stagedSyllabus != null) {
        SyllabusAirlockDialog(
            staged = stagedSyllabus!!,
            onDismiss = {
                showAirlockDialog = false
                stagedSyllabus = null
            },
            onCommit = { courseCode, courseName, selectedItems ->
                scope.launch {
                    db.insertCourse(courseCode, courseName)
                    for (deliv in selectedItems) {
                        db.insertSyllabusItem(
                            courseCode = courseCode,
                            title = deliv.title,
                            itemType = deliv.type,
                            dueDateText = deliv.dueDateText,
                            dueDateMillis = deliv.dueDateMillis,
                            weightPercent = deliv.weightPercent,
                            prepSteps = deliv.prepSteps,
                        )
                    }
                    showAirlockDialog = false
                    stagedSyllabus = null
                }
            },
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun NextMajorDeliverableFocalPlate(
    deliverable: DesktopSyllabusItem,
    captainLabelColor: androidx.compose.ui.graphics.Color,
    onStartFocus: () -> Unit,
    onPlanWithCaptain: (() -> Unit)?,
) {
    val tokens = LocalAnchorTokens.current
    val firstStep = deliverable.prepSteps.firstOrNull() ?: "Review course notes and outline scope"
    val metadataLine =
        buildString {
            append(deliverable.courseCode)
            append(", ")
            append(formatDeliverableType(deliverable.itemType))
            if (deliverable.weightPercent > 0) {
                append(", ${deliverable.weightPercent}%")
            }
            append(", due ${deliverable.dueDateText}")
        }

    AnchorFocalPlate(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Next major deliverable",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = tokens.primary,
            )
            Text(
                text = metadataLine,
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = tokens.muted,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = deliverable.title,
                style = MaterialTheme.typography.titleMedium,
                color = tokens.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Lead prep step: $firstStep",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HarborCtaButton(
                onClick = onStartFocus,
                modifier = Modifier.heightIn(min = 40.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Start focus",
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            if (onPlanWithCaptain != null) {
                AnchorQuietButton(
                    label = "Plan with Captain",
                    onClick = onPlanWithCaptain,
                    outlined = true,
                    tint = captainLabelColor,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SyllabusDeliverableRow(
    item: DesktopSyllabusItem,
    isExpanded: Boolean,
    compactRow: Boolean,
    showDivider: Boolean,
    onToggleExpand: () -> Unit,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    onStartFocus: (taskTitle: String, durationMinutes: Int) -> Unit,
    onPlanWithCaptain: (() -> Unit)?,
    onAddStepToPlan: (String) -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    val reducedMotion = LocalReducedMotion.current
    val captainLabelColor = if (tokens.isDark) tokens.captain else tokens.captainInk
    val (typeTint, typeBg) =
        when (item.itemType) {
            SyllabusItemType.EXAM -> tokens.clayRose to tokens.clayRoseSoft
            SyllabusItemType.PROJECT -> captainLabelColor to tokens.captainSoft
            SyllabusItemType.HOMEWORK -> tokens.primary to tokens.primarySoft
            SyllabusItemType.READING -> tokens.sage to tokens.sageSoft
        }

    val subtitle =
        buildString {
            append(item.courseCode)
            if (item.weightPercent > 0) {
                append(", ${item.weightPercent}%")
            }
            if (item.isCompleted) {
                append(", completed")
            }
        }

    @Composable
    fun RowActions(alignColumns: Boolean) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Surface(
                modifier = if (alignColumns) Modifier.widthIn(min = 78.dp) else Modifier,
                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                color = typeBg,
                border = null,
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = formatDeliverableType(item.itemType),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = typeTint,
                    )
                }
            }

            AnchorQuietButton(
                label = "Start focus",
                onClick = { onStartFocus(item.title, 25) },
                outlined = false,
                tint = tokens.primary,
                enabled = !item.isCompleted,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                },
            )

            if (item.prepSteps.isNotEmpty()) {
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse prep steps" else "Expand prep steps",
                        tint = tokens.ink2,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else if (alignColumns) {
                Spacer(modifier = Modifier.size(36.dp))
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete ${item.title}",
                    tint = tokens.muted,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        AnchorListRow(
            title = item.title,
            subtitle = subtitle,
            trailingText = item.dueDateText,
            minHeight = 44.dp,
            showDivider = false,
            checked = item.isCompleted,
            onClick = onToggleComplete,
            leading = {
                Icon(
                    imageVector =
                        if (item.isCompleted) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Outlined.RadioButtonUnchecked
                        },
                    contentDescription = null,
                    tint = if (item.isCompleted) tokens.sage else tokens.muted,
                    modifier = Modifier.size(20.dp),
                )
            },
            trailing =
                if (!compactRow) {
                    { RowActions(alignColumns = true) }
                } else {
                    null
                },
        )

        if (compactRow) {
            FlowRow(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RowActions(alignColumns = false)
            }
        }

        val prepContent: @Composable () -> Unit = {
            AnchorWell(
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 12.dp),
                tone = AnchorWellTone.Neutral,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AnchorSectionHeader(
                    title = "Backward-chained prep steps",
                    trailingLabel = "${item.prepSteps.size} steps",
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.prepSteps.forEachIndexed { index, step ->
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 8.dp),
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    style =
                                        MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFeatureSettings = "tnum",
                                        ),
                                    color = tokens.muted,
                                )
                                Text(
                                    text = step,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.ink,
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                AnchorQuietButton(
                                    label = "Add to plan",
                                    onClick = { onAddStepToPlan(step) },
                                    outlined = false,
                                    tint = tokens.primary,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    },
                                )
                                AnchorQuietButton(
                                    label = "Start 15m prep",
                                    onClick = { onStartFocus(step, 15) },
                                    outlined = true,
                                    tint = tokens.sage,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                if (!item.isCompleted && onPlanWithCaptain != null) {
                    AnchorQuietButton(
                        label = "Plan with Captain",
                        onClick = onPlanWithCaptain,
                        outlined = true,
                        tint = captainLabelColor,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                        },
                    )
                }
            }
        }

        if (item.prepSteps.isNotEmpty()) {
            if (reducedMotion) {
                if (isExpanded) prepContent()
            } else {
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    prepContent()
                }
            }
        } else if (!item.isCompleted && onPlanWithCaptain != null) {
            Box(modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)) {
                AnchorQuietButton(
                    label = "Plan with Captain",
                    onClick = onPlanWithCaptain,
                    outlined = false,
                    tint = captainLabelColor,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                    },
                )
            }
        }

        if (showDivider) {
            AnchorHairline()
        }
    }
}

@Composable
private fun PasteSyllabusDialog(
    onDismiss: () -> Unit,
    onParse: (String) -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    var text by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(AnchorSpacing.radiusSheet),
            color = tokens.surface,
            contentColor = tokens.ink,
            border = BorderStroke(1.dp, tokens.rule),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(0.92f).height(480.dp),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Paste course syllabus",
                        style = MaterialTheme.typography.titleLarge,
                        color = tokens.ink,
                    )
                    Text(
                        text = "Paste syllabus text or a schedule table to extract assignments, exams, and prep milestones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.muted,
                    )
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = {
                        Text(
                            text = "Paste syllabus text here (e.g. CS 101 Midterm Oct 24, Final Project Nov 20)…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.muted,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                    colors = syllabusFieldColors(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnchorQuietButton(
                        label = "Cancel",
                        onClick = onDismiss,
                        outlined = false,
                        tint = tokens.muted,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    HarborCtaButton(
                        onClick = { if (text.isNotBlank()) onParse(text) },
                        enabled = text.isNotBlank(),
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Extract deliverables", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun SyllabusAirlockDialog(
    staged: ParsedSyllabus,
    onDismiss: () -> Unit,
    onCommit: (courseCode: String, courseName: String, selectedDeliverables: List<ParsedDeliverable>) -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    var courseCode by remember { mutableStateOf(staged.courseCode) }
    var courseName by remember { mutableStateOf(staged.courseName) }
    var selectedIndices by remember { mutableStateOf(staged.deliverables.indices.toSet()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(AnchorSpacing.radiusSheet),
            color = tokens.surface,
            contentColor = tokens.ink,
            border = BorderStroke(1.dp, tokens.rule),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(0.95f).height(580.dp),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = tokens.captain,
                            modifier = Modifier.size(22.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Syllabus airlock",
                                style = MaterialTheme.typography.titleLarge,
                                color = tokens.ink,
                            )
                            Text(
                                text = "Review extracted dates before adding them to your plan. Uncheck anything you do not want to track.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.muted,
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = tokens.muted,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = courseCode,
                        onValueChange = { courseCode = it },
                        label = { Text("Course code") },
                        modifier = Modifier.width(140.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                        colors = syllabusFieldColors(),
                    )
                    OutlinedTextField(
                        value = courseName,
                        onValueChange = { courseName = it },
                        label = { Text("Course title") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                        colors = syllabusFieldColors(),
                    )
                }

                Text(
                    text = "${selectedIndices.size} of ${staged.deliverables.size} deliverables selected",
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum",
                        ),
                    color = tokens.primary,
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    itemsIndexed(staged.deliverables) { index, item ->
                        val isChecked = index in selectedIndices
                        val subtitle =
                            buildString {
                                append(formatDeliverableType(item.type))
                                if (item.weightPercent > 0) {
                                    append(", ${item.weightPercent}%")
                                }
                            }
                        AnchorListRow(
                            title = item.title,
                            subtitle = subtitle,
                            trailingText = item.dueDateText,
                            minHeight = 44.dp,
                            showDivider = index < staged.deliverables.lastIndex,
                            checked = isChecked,
                            onClick = {
                                selectedIndices = if (isChecked) selectedIndices - index else selectedIndices + index
                            },
                            leading = {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = null,
                                    colors =
                                        CheckboxDefaults.colors(
                                            checkedColor = tokens.primary,
                                            checkmarkColor = tokens.surface,
                                            uncheckedColor = tokens.fieldEdge,
                                        ),
                                )
                            },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnchorQuietButton(
                        label = "Cancel",
                        onClick = onDismiss,
                        outlined = false,
                        tint = tokens.muted,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    HarborCtaButton(
                        onClick = {
                            val selectedItems =
                                staged.deliverables.filterIndexed { index, _ -> index in selectedIndices }
                            onCommit(
                                courseCode.trim(),
                                courseName.trim().ifBlank { courseCode.trim() },
                                selectedItems,
                            )
                        },
                        enabled = courseCode.isNotBlank() && selectedIndices.isNotEmpty(),
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Add ${selectedIndices.size} milestones to plan",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AddManualDeliverableDialog(
    defaultCourseCode: String,
    onDismiss: () -> Unit,
    onAdd: (courseCode: String, title: String, itemType: SyllabusItemType, dueDateText: String, weightPercent: Int) -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    var courseCode by remember { mutableStateOf(defaultCourseCode) }
    var title by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(SyllabusItemType.EXAM) }
    var dueDateText by remember { mutableStateOf("") }
    var weightText by remember { mutableStateOf("15") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(AnchorSpacing.radiusSheet),
            color = tokens.surface,
            contentColor = tokens.ink,
            border = BorderStroke(1.dp, tokens.rule),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Add deliverable",
                        style = MaterialTheme.typography.titleLarge,
                        color = tokens.ink,
                    )
                    Text(
                        text = "Anchor generates backward-chained preparation milestones automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.muted,
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SyllabusItemType.entries.forEach { type ->
                        AnchorChip(
                            label = formatDeliverableType(type),
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = courseCode,
                        onValueChange = { courseCode = it },
                        label = { Text("Course code") },
                        modifier = Modifier.width(130.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                        colors = syllabusFieldColors(),
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Deliverable title") },
                        placeholder = { Text("e.g. Midterm Exam 2", color = tokens.muted) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                        colors = syllabusFieldColors(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = dueDateText,
                        onValueChange = { dueDateText = it },
                        label = { Text("Due date") },
                        placeholder = { Text("e.g. Oct 24 or Week 8", color = tokens.muted) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                        colors = syllabusFieldColors(),
                    )
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it.filter(Char::isDigit).take(3) },
                        label = { Text("Weight %") },
                        modifier = Modifier.width(110.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                        colors = syllabusFieldColors(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnchorQuietButton(
                        label = "Cancel",
                        onClick = onDismiss,
                        outlined = false,
                        tint = tokens.muted,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    HarborCtaButton(
                        onClick = {
                            onAdd(
                                courseCode,
                                title,
                                selectedType,
                                dueDateText,
                                weightText.toIntOrNull()?.coerceIn(0, 100) ?: 0,
                            )
                        },
                        enabled = courseCode.isNotBlank() && title.isNotBlank(),
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add deliverable", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun syllabusFieldColors() =
    LocalAnchorTokens.current.let { tokens ->
        OutlinedTextFieldDefaults.colors(
            focusedBorderColor = tokens.primary,
            unfocusedBorderColor = tokens.fieldEdge,
            focusedTextColor = tokens.ink,
            unfocusedTextColor = tokens.ink,
            focusedContainerColor = tokens.paper,
            unfocusedContainerColor = tokens.paper,
            focusedLabelColor = tokens.primary,
            unfocusedLabelColor = tokens.muted,
            cursorColor = tokens.primary,
        )
    }
