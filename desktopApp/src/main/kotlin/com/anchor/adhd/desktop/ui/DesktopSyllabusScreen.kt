package com.anchor.adhd.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.anchor.adhd.desktop.ai.DesktopSyllabusParser
import com.anchor.adhd.desktop.ai.ParsedDeliverable
import com.anchor.adhd.desktop.ai.ParsedSyllabus
import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import com.anchor.adhd.desktop.db.DesktopSyllabusItem
import com.anchor.adhd.desktop.db.SyllabusItemType
import com.anchor.adhd.desktop.theme.AnchorColors
import com.anchor.adhd.desktop.theme.AnchorSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Academic Harbor & Syllabus Ingestion Screen for Windows 11.
 *
 * Solves ADHD "Syllabus Blindness" and "Now vs Not Now" time blindness by:
 * 1. Extracting exams, term papers, and assignments from PDFs or pasted text.
 * 2. Generating backward-chained "Lead-In Prep" milestones so students never panic the night before.
 * 3. 1-click launching into Anchor's Tide Focus Canvas with distraction blocking armed.
 */
@Composable
fun DesktopSyllabusScreen(
    db: AnchorDesktopDatabase,
    onStartFocusWithTask: (taskTitle: String, durationMinutes: Int) -> Unit,
    modifier: Modifier = Modifier,
    onPlanWithAi: ((DesktopSyllabusItem) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val courses by db.courses.collectAsState()
    val syllabusItems by db.syllabusItems.collectAsState()

    var selectedCourseCode by remember { mutableStateOf<String?>(null) } // null = All
    var showPasteDialog by remember { mutableStateOf(false) }
    var showAirlockDialog by remember { mutableStateOf(false) }
    var stagedSyllabus by remember { mutableStateOf<ParsedSyllabus?>(null) }
    var isParsingFile by remember { mutableStateOf(false) }
    var expandedItemId by remember { mutableStateOf<Long?>(null) }

    // Filter items
    val displayedItems =
        remember(syllabusItems, selectedCourseCode) {
            if (selectedCourseCode == null) syllabusItems else syllabusItems.filter { it.courseCode == selectedCourseCode }
        }

    val nextMajorDeliverable =
        remember(syllabusItems) {
            syllabusItems.firstOrNull {
                !it.isCompleted && (it.itemType == SyllabusItemType.EXAM || it.itemType == SyllabusItemType.PROJECT)
            }
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AnchorColors.HarborPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = AnchorColors.HarborPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Academic Harbor & Syllabus Hub",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Upload your syllabus — Anchor extracts dates & builds low-friction prep milestones",
                    style = MaterialTheme.typography.bodySmall,
                    color = AnchorColors.HarborPrimary.copy(alpha = 0.8f),
                )
            }

            // Ingestion Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Upload File Button (.pdf, .txt, .md)
                Button(
                    onClick = {
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
                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = AnchorColors.HarborPrimary,
                            contentColor = Color(0xFF002A4A),
                        ),
                ) {
                    if (isParsingFile) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF002A4A), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reading...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload Syllabus", fontWeight = FontWeight.Bold)
                    }
                }

                // Paste Text Button
                OutlinedButton(
                    onClick = { showPasteDialog = true },
                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Paste Syllabus", color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Next Big Deliverable Radar Card
        if (nextMajorDeliverable != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                color = Color(0xFF1B2338),
                border = BorderStroke(1.dp, AnchorColors.HarborAi.copy(alpha = 0.35f)),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier =
                                Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE57373).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFFF8A80),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                    color = Color(0xFFE57373).copy(alpha = 0.2f),
                                ) {
                                    Text(
                                        text = "HORIZON RADAR",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF8A80),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = nextMajorDeliverable.courseCode,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.7f),
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "${nextMajorDeliverable.title} — Due: ${nextMajorDeliverable.dueDateText}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            val firstStep = nextMajorDeliverable.prepSteps.firstOrNull() ?: "Review notes"
                            Text(
                                text = "Lead Step: $firstStep",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.75f),
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val starter = nextMajorDeliverable.prepSteps.firstOrNull() ?: nextMajorDeliverable.title
                            onStartFocusWithTask(starter, 25)
                        },
                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = AnchorColors.HarborPrimary,
                                contentColor = Color(0xFF002A4A),
                            ),
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start 25m Lead Prep", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Course Selector Filter Bar
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item {
                CourseFilterChip(
                    label = "All Courses (${syllabusItems.size})",
                    isSelected = selectedCourseCode == null,
                    onClick = { selectedCourseCode = null },
                )
            }
            items(courses) { course ->
                val count = syllabusItems.count { it.courseCode == course.code }
                CourseFilterChip(
                    label = "${course.code} ($count)",
                    isSelected = selectedCourseCode == course.code,
                    onClick = { selectedCourseCode = course.code },
                    onDelete = {
                        scope.launch {
                            db.deleteCourse(course.code)
                            if (selectedCourseCode == course.code) selectedCourseCode = null
                        }
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Deliverables List
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            shape = RoundedCornerShape(AnchorSpacing.radiusCard),
            color = AnchorColors.HarborDock.copy(alpha = 0.75f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        ) {
            if (displayedItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No syllabus deliverables yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Upload a syllabus PDF or paste course text to automatically extract deadlines.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.4f),
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(displayedItems, key = { it.id }) { item ->
                        SyllabusItemCard(
                            item = item,
                            isExpanded = expandedItemId == item.id,
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
                            onPlanWithAi = onPlanWithAi?.let { plan -> { plan(item) } },
                        )
                    }
                }
            }
        }
    }

    // Modal 1: Paste Syllabus Text Dialog
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

    // Modal 2: Syllabus Airlock Review Dialog (Zero-Overwhelm Staging)
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

/**
 * Filter Chip for Course Selection
 */
@Composable
private fun CourseFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
        color = if (isSelected) AnchorColors.HarborPrimary else Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, if (isSelected) AnchorColors.HarborPrimary else Color.White.copy(alpha = 0.15f)),
        modifier = Modifier.clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color(0xFF002A4A) else Color.White,
            )
            if (onDelete != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete Course",
                    tint = if (isSelected) Color(0xFF002A4A).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.4f),
                    modifier =
                        Modifier
                            .size(14.dp)
                            .clickable { onDelete() },
                )
            }
        }
    }
}

/**
 * Syllabus Deliverable Card with Backward-Chained Prep Accordion
 */
@Composable
private fun SyllabusItemCard(
    item: DesktopSyllabusItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    onStartFocus: (taskTitle: String, durationMinutes: Int) -> Unit,
    onPlanWithAi: (() -> Unit)? = null,
) {
    val (typeColor, typeBg) =
        when (item.itemType) {
            SyllabusItemType.EXAM -> Pair(Color(0xFFFF8A80), Color(0xFFE57373).copy(alpha = 0.18f))
            SyllabusItemType.PROJECT -> Pair(Color(0xFFBA68C8), Color(0xFF9C27B0).copy(alpha = 0.18f))
            SyllabusItemType.HOMEWORK -> Pair(Color(0xFF4FC3F7), Color(0xFF0288D1).copy(alpha = 0.18f))
            SyllabusItemType.READING -> Pair(Color(0xFF81C784), Color(0xFF388E3C).copy(alpha = 0.18f))
        }

    Card(
        shape = RoundedCornerShape(AnchorSpacing.radiusCard),
        colors =
            CardDefaults.cardColors(
                containerColor = if (item.isCompleted) Color(0xFF141926).copy(alpha = 0.5f) else Color(0xFF1A2234),
            ),
        border = BorderStroke(1.dp, if (item.isCompleted) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier =
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable { onToggleComplete() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (item.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Toggle Complete",
                            tint = if (item.isCompleted) AnchorColors.HarborGrowth else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Type Badge
                            Surface(shape = RoundedCornerShape(AnchorSpacing.radiusPill), color = typeBg) {
                                Text(
                                    text = item.itemType.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = typeColor,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Course Tag
                            Surface(
                                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                color = Color.White.copy(alpha = 0.08f),
                            ) {
                                Text(
                                    text = item.courseCode,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }

                            if (item.weightPercent > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                    color = Color.White.copy(alpha = 0.08f),
                                ) {
                                    Text(
                                        text = "${item.weightPercent}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (item.isCompleted) Color.White.copy(alpha = 0.4f) else Color.White,
                            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Due Date Badge
                    Surface(
                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                        color = Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .widthIn(min = 66.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = AnchorColors.HarborPrimary,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = item.dueDateText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }

                    // Start Direct Focus Button (Optical baseline & height aligned with Date badge)
                    Surface(
                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                        color = AnchorColors.HarborPrimary.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, AnchorColors.HarborPrimary.copy(alpha = 0.4f)),
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(AnchorSpacing.radiusPill))
                                .clickable { onStartFocus(item.title, 25) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = AnchorColors.HarborPrimary,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Focus",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AnchorColors.HarborPrimary,
                            )
                        }
                    }

                    if (item.prepSteps.isNotEmpty()) {
                        Box(
                            modifier =
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable { onToggleExpand() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Expand lead steps",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    Box(
                        modifier =
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable { onDelete() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            if (!item.isCompleted && onPlanWithAi != null) {
                OutlinedButton(onClick = onPlanWithAi, modifier = Modifier.padding(top = 8.dp, start = 38.dp)) {
                    Text("Plan with AI", color = AnchorColors.HarborPrimary)
                }
            }

            // Expandable Backward-Chained Prep Steps Accordion
            AnimatedVisibility(
                visible = isExpanded && item.prepSteps.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, start = 38.dp, end = 8.dp),
                ) {
                    Text(
                        text = "🌱 BACKWARD-CHAINED PREP STEPS (No-Panic Milestones):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AnchorColors.HarborGrowth,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    item.prepSteps.forEachIndexed { index, step ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${index + 1}.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.5f),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = step,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                color = AnchorColors.HarborGrowth.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, AnchorColors.HarborGrowth.copy(alpha = 0.45f)),
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(AnchorSpacing.radiusPill))
                                        .clickable { onStartFocus(step, 15) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = AnchorColors.HarborFoliage,
                                        modifier = Modifier.size(11.dp),
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Start Prep",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AnchorColors.HarborFoliage,
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

/**
 * Paste Syllabus Text Modal Dialog
 */
@Composable
private fun PasteSyllabusDialog(
    onDismiss: () -> Unit,
    onParse: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(AnchorSpacing.radiusCard),
            color = Color(0xFF1B2338),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            modifier =
                Modifier
                    .fillMaxWidth(0.9f)
                    .height(480.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "📋 Paste Course Syllabus / Schedule",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Paste your syllabus text or schedule table. Anchor will extract dates, assignments, and exams.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = {
                        Text(
                            "Paste syllabus text here (e.g. CS 101 Midterm Oct 24, Final Project Nov 20, Weekly PS)...",
                            color = Color.White.copy(alpha = 0.4f),
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AnchorColors.HarborPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { if (text.isNotBlank()) onParse(text) },
                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                        colors = ButtonDefaults.buttonColors(containerColor = AnchorColors.HarborPrimary, contentColor = Color(0xFF002A4A)),
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Extract Deliverables", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Syllabus Airlock Review Dialog (Zero-Overwhelm Staging)
 */
@Composable
private fun SyllabusAirlockDialog(
    staged: ParsedSyllabus,
    onDismiss: () -> Unit,
    onCommit: (courseCode: String, courseName: String, selectedDeliverables: List<ParsedDeliverable>) -> Unit,
) {
    var courseCode by remember { mutableStateOf(staged.courseCode) }
    var courseName by remember { mutableStateOf(staged.courseName) }
    var selectedIndices by remember { mutableStateOf(staged.deliverables.indices.toSet()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(AnchorSpacing.radiusCard),
            color = Color(0xFF161E30),
            border = BorderStroke(1.dp, AnchorColors.HarborAi.copy(alpha = 0.4f)),
            modifier =
                Modifier
                    .fillMaxWidth(0.95f)
                    .height(580.dp),
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "🛡 Syllabus Airlock: Staged Deliverables",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = "Review extracted dates before adding them to your plan. Uncheck anything you don't want to track.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.6f))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Editable Course Code & Name
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = courseCode,
                        onValueChange = { courseCode = it },
                        label = { Text("Course Code", color = Color.White.copy(alpha = 0.6f)) },
                        modifier = Modifier.width(140.dp),
                        singleLine = true,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AnchorColors.HarborPrimary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            ),
                    )
                    OutlinedTextField(
                        value = courseName,
                        onValueChange = { courseName = it },
                        label = { Text("Course Title", color = Color.White.copy(alpha = 0.6f)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AnchorColors.HarborPrimary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            ),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "${selectedIndices.size} of ${staged.deliverables.size} Deliverables Selected",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AnchorColors.HarborPrimary,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Staged Items List
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(staged.deliverables.size) { index ->
                        val item = staged.deliverables[index]
                        val isChecked = index in selectedIndices

                        Surface(
                            shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                            color = if (isChecked) Color(0xFF1E2840) else Color(0xFF141A28),
                            border =
                                BorderStroke(
                                    1.dp,
                                    if (isChecked) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                ),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedIndices = if (isChecked) selectedIndices - index else selectedIndices + index
                                    },
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            selectedIndices = if (checked) selectedIndices + index else selectedIndices - index
                                        },
                                        colors =
                                            CheckboxDefaults.colors(
                                                checkedColor = AnchorColors.HarborPrimary,
                                                checkmarkColor = Color(0xFF002A4A),
                                            ),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                                color = Color.White.copy(alpha = 0.1f),
                                            ) {
                                                Text(
                                                    text = item.type.name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                )
                                            }
                                            if (item.weightPercent > 0) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "(${item.weightPercent}%)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White.copy(alpha = 0.6f),
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                    color = AnchorColors.HarborPrimary.copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        text = item.dueDateText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AnchorColors.HarborPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val selectedItems = staged.deliverables.filterIndexed { index, _ -> index in selectedIndices }
                            onCommit(courseCode, courseName, selectedItems)
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
                        Text("Anchor ${selectedIndices.size} Milestones to Plan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
