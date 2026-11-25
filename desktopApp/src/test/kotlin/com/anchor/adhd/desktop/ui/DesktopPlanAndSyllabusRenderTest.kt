package com.anchor.adhd.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import com.anchor.adhd.desktop.ai.DesktopMilestonePreview
import com.anchor.adhd.desktop.ai.DesktopMilestoneStepsState
import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import com.anchor.adhd.desktop.db.DesktopSyllabusItem
import com.anchor.adhd.desktop.db.DesktopTaskDeletion
import com.anchor.adhd.desktop.db.DesktopThoughtCaptureState
import com.anchor.adhd.desktop.db.SyllabusItemType
import com.anchor.adhd.desktop.theme.AnchorTheme
import com.anchor.adhd.ui.theme.LocalAnchorTokens
import com.anchor.adhd.ui.theme.MorningHarborTokens
import com.anchor.adhd.ui.theme.NightHarborTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DesktopPlanAndSyllabusRenderTest {
    @Test
    fun formattingAndNextMajorDeliverableHelpersCoverEdgeCases() {
        assertEquals("Light", formatTaskDifficulty("EASY"))
        assertEquals("Steady", formatTaskDifficulty("MEDIUM"))
        assertEquals("Deep", formatTaskDifficulty("HARD"))
        assertEquals("Deep", formatTaskDifficulty("deep"))
        assertEquals("Steady", formatTaskDifficulty("   "))
        assertEquals("Custom", formatTaskDifficulty("CUSTOM"))

        assertEquals("Exam", formatDeliverableType(SyllabusItemType.EXAM))
        assertEquals("Project", formatDeliverableType(SyllabusItemType.PROJECT))
        assertEquals("Homework", formatDeliverableType(SyllabusItemType.HOMEWORK))
        assertEquals("Reading", formatDeliverableType(SyllabusItemType.READING))

        val completedExam =
            DesktopSyllabusItem(
                id = 1L,
                courseCode = "CS 101",
                title = "Midterm 1",
                itemType = SyllabusItemType.EXAM,
                dueDateText = "Oct 10",
                dueDateMillis = 1_000L,
                isCompleted = true,
            )
        val homework =
            DesktopSyllabusItem(
                id = 2L,
                courseCode = "CS 101",
                title = "Problem Set 4",
                itemType = SyllabusItemType.HOMEWORK,
                dueDateText = "Oct 14",
                dueDateMillis = 2_000L,
            )
        val laterProject =
            DesktopSyllabusItem(
                id = 3L,
                courseCode = "PSYC 204",
                title = "Lab Report",
                itemType = SyllabusItemType.PROJECT,
                dueDateText = "Nov 12",
                dueDateMillis = 5_000L,
            )
        val soonerExam =
            DesktopSyllabusItem(
                id = 4L,
                courseCode = "CS 101",
                title = "Midterm 2",
                itemType = SyllabusItemType.EXAM,
                dueDateText = "Oct 24",
                dueDateMillis = 3_000L,
            )
        val undatedProject =
            DesktopSyllabusItem(
                id = 5L,
                courseCode = "HIST 110",
                title = "Final Paper",
                itemType = SyllabusItemType.PROJECT,
                dueDateText = "TBD",
                dueDateMillis = 0L,
            )

        assertEquals(
            soonerExam,
            pickNextMajorDeliverable(listOf(completedExam, homework, laterProject, soonerExam, undatedProject)),
        )
        assertNull(pickNextMajorDeliverable(listOf(completedExam, homework)))
    }

    @Test
    fun planScreenRendersLightAndDarkAtWideAndCompactSizesWithCaptainWellAndZeroMotion() {
        withPopulatedPlanDb { db ->
            val lightWide = renderPlan(db = db, width = 1200, height = 800, darkTheme = false, expandFirstTask = true)
            val darkWide = renderPlan(db = db, width = 1200, height = 800, darkTheme = true, expandFirstTask = true)
            val lightCompact = renderPlan(db = db, width = 640, height = 450, darkTheme = false, expandFirstTask = false)
            val darkCompact = renderPlan(db = db, width = 640, height = 450, darkTheme = true, expandFirstTask = false)

            assertTrue(lightWide.png.isNotEmpty())
            assertTrue(darkWide.png.isNotEmpty())
            assertTrue(lightCompact.png.isNotEmpty())
            assertTrue(darkCompact.png.isNotEmpty())

            assertFalse(
                "Morning Light and Night Dark wide Plan renders must be distinct",
                lightWide.png.contentEquals(darkWide.png),
            )
            assertFalse(
                "Morning Light and Night Dark compact Plan renders must be distinct",
                lightCompact.png.contentEquals(darkCompact.png),
            )

            assertTrue("Light wide Plan should render Morning Harbor paper canvas", lightWide.hasPaperColor)
            assertTrue("Dark wide Plan should render Night Harbor paper canvas", darkWide.hasPaperColor)
            assertTrue("Light compact Plan should render Morning Harbor paper canvas", lightCompact.hasPaperColor)
            assertTrue("Dark compact Plan should render Night Harbor paper canvas", darkCompact.hasPaperColor)

            assertTrue("Light wide Plan should render Captain micro-step well", lightWide.hasCaptainSoftColor)
            assertTrue("Dark wide Plan should render Captain micro-step well", darkWide.hasCaptainSoftColor)

            assertArrayEquals(
                "Light Plan screen must not run decorative infinite animation",
                lightWide.png,
                lightWide.laterFramePng,
            )
            assertArrayEquals(
                "Dark Plan screen must not run decorative infinite animation",
                darkWide.png,
                darkWide.laterFramePng,
            )

            val dumpDir = System.getenv("ANCHOR_DUMP_PLAN_SYLLABUS_SNAPSHOTS")
            if (!dumpDir.isNullOrBlank()) {
                val dir = File(dumpDir).apply { mkdirs() }
                File(dir, "plan-light-1200x800.png").writeBytes(lightWide.png)
                File(dir, "plan-dark-1200x800.png").writeBytes(darkWide.png)
                File(dir, "plan-light-640x450.png").writeBytes(lightCompact.png)
                File(dir, "plan-dark-640x450.png").writeBytes(darkCompact.png)
            }
        }
    }

    @Test
    fun syllabusScreenRendersLightAndDarkAtWideAndCompactSizesWithFocalPlateAndZeroMotion() {
        withPopulatedSyllabusDb { db ->
            val lightWide = renderSyllabus(db = db, width = 1200, height = 800, darkTheme = false)
            val darkWide = renderSyllabus(db = db, width = 1200, height = 800, darkTheme = true)
            val lightCompact = renderSyllabus(db = db, width = 640, height = 450, darkTheme = false)
            val darkCompact = renderSyllabus(db = db, width = 640, height = 450, darkTheme = true)

            assertTrue(lightWide.png.isNotEmpty())
            assertTrue(darkWide.png.isNotEmpty())
            assertTrue(lightCompact.png.isNotEmpty())
            assertTrue(darkCompact.png.isNotEmpty())

            assertFalse(
                "Morning Light and Night Dark wide Syllabus renders must be distinct",
                lightWide.png.contentEquals(darkWide.png),
            )
            assertFalse(
                "Morning Light and Night Dark compact Syllabus renders must be distinct",
                lightCompact.png.contentEquals(darkCompact.png),
            )

            assertTrue("Light wide Syllabus should render Morning Harbor paper canvas", lightWide.hasPaperColor)
            assertTrue("Dark wide Syllabus should render Night Harbor paper canvas", darkWide.hasPaperColor)
            assertTrue("Light compact Syllabus should render Morning Harbor paper canvas", lightCompact.hasPaperColor)
            assertTrue("Dark compact Syllabus should render Night Harbor paper canvas", darkCompact.hasPaperColor)

            assertTrue("Light wide Syllabus should render focal plate surface", lightWide.hasSurfacePlateColor)
            assertTrue("Dark wide Syllabus should render focal plate surface", darkWide.hasSurfacePlateColor)
            assertTrue("Light wide Syllabus should render beacon Start focus CTA", lightWide.hasBeaconColor)
            assertTrue("Dark wide Syllabus should render beacon Start focus CTA", darkWide.hasBeaconColor)

            assertArrayEquals(
                "Light Syllabus screen must not run decorative infinite animation",
                lightWide.png,
                lightWide.laterFramePng,
            )
            assertArrayEquals(
                "Dark Syllabus screen must not run decorative infinite animation",
                darkWide.png,
                darkWide.laterFramePng,
            )

            val dumpDir = System.getenv("ANCHOR_DUMP_PLAN_SYLLABUS_SNAPSHOTS")
            if (!dumpDir.isNullOrBlank()) {
                val dir = File(dumpDir).apply { mkdirs() }
                File(dir, "syllabus-light-1200x800.png").writeBytes(lightWide.png)
                File(dir, "syllabus-dark-1200x800.png").writeBytes(darkWide.png)
                File(dir, "syllabus-light-640x450.png").writeBytes(lightCompact.png)
                File(dir, "syllabus-dark-640x450.png").writeBytes(darkCompact.png)
            }
        }
    }

    @Test
    fun emptySyllabusRendersCalmEmptyStateInLightAndDark() {
        withEmptyDb { db ->
            val lightEmpty = renderSyllabus(db = db, width = 1200, height = 800, darkTheme = false)
            val darkEmpty = renderSyllabus(db = db, width = 1200, height = 800, darkTheme = true)

            assertTrue(lightEmpty.png.isNotEmpty())
            assertTrue(darkEmpty.png.isNotEmpty())
            assertTrue(lightEmpty.hasPaperColor)
            assertTrue(darkEmpty.hasPaperColor)
            assertFalse(lightEmpty.png.contentEquals(darkEmpty.png))
            assertArrayEquals(lightEmpty.png, lightEmpty.laterFramePng)
            assertArrayEquals(darkEmpty.png, darkEmpty.laterFramePng)
        }
    }

    private data class RenderedPlan(
        val png: ByteArray,
        val laterFramePng: ByteArray,
        val hasPaperColor: Boolean,
        val hasCaptainSoftColor: Boolean,
    )

    private data class RenderedSyllabus(
        val png: ByteArray,
        val laterFramePng: ByteArray,
        val hasPaperColor: Boolean,
        val hasSurfacePlateColor: Boolean,
        val hasBeaconColor: Boolean,
    )

    private fun renderPlan(
        db: AnchorDesktopDatabase,
        width: Int,
        height: Int,
        darkTheme: Boolean,
        expandFirstTask: Boolean,
    ): RenderedPlan {
        val firstTask = db.tasks.value.firstOrNull()
        val nanoState =
            if (expandFirstTask && firstTask != null) {
                DesktopMilestoneStepsState(
                    expandedTaskId = firstTask.id,
                    previews =
                        mapOf(
                            firstTask.id to
                                DesktopMilestonePreview(
                                    key = "preview-1",
                                    sourceId = firstTask.id,
                                    sourceTitle = firstTask.title,
                                    steps =
                                        listOf(
                                            "Open the rubric and highlight 3 required sections",
                                            "Write a rough 2-sentence thesis placeholder",
                                            "Drop 3 supporting citations as bullet points",
                                        ),
                                ),
                        ),
                )
            } else {
                DesktopMilestoneStepsState()
            }

        val scene =
            ImageComposeScene(
                width = width,
                height = height,
                density = Density(1f),
            ) {
                AnchorTheme(darkTheme = darkTheme, reducedMotion = false) {
                    PlanRenderHarness(db = db, nanoState = nanoState)
                }
            }
        return try {
            scene.render(0L)
            scene.render(16_000_000L)
            val frame1 = scene.render(32_000_000L)
            scene.render(2_032_000_000L)
            val frame2 = scene.render(2_048_000_000L)
            val pixels = frame1.toComposeImageBitmap().toPixelMap()
            val expectedTokens = if (darkTheme) NightHarborTokens else MorningHarborTokens
            RenderedPlan(
                png = frame1.encodeToData(EncodedImageFormat.PNG)!!.bytes,
                laterFramePng = frame2.encodeToData(EncodedImageFormat.PNG)!!.bytes,
                hasPaperColor = containsColor(pixels, width, height, expectedTokens.paper, step = 4),
                hasCaptainSoftColor = containsColor(pixels, width, height, expectedTokens.captainSoft, step = 2),
            )
        } finally {
            scene.close()
        }
    }

    private fun renderSyllabus(
        db: AnchorDesktopDatabase,
        width: Int,
        height: Int,
        darkTheme: Boolean,
    ): RenderedSyllabus {
        val scene =
            ImageComposeScene(
                width = width,
                height = height,
                density = Density(1f),
            ) {
                AnchorTheme(darkTheme = darkTheme, reducedMotion = false) {
                    SyllabusRenderHarness(db = db)
                }
            }
        return try {
            scene.render(0L)
            scene.render(16_000_000L)
            val frame1 = scene.render(32_000_000L)
            scene.render(2_032_000_000L)
            val frame2 = scene.render(2_048_000_000L)
            val pixels = frame1.toComposeImageBitmap().toPixelMap()
            val expectedTokens = if (darkTheme) NightHarborTokens else MorningHarborTokens
            RenderedSyllabus(
                png = frame1.encodeToData(EncodedImageFormat.PNG)!!.bytes,
                laterFramePng = frame2.encodeToData(EncodedImageFormat.PNG)!!.bytes,
                hasPaperColor = containsColor(pixels, width, height, expectedTokens.paper, step = 4),
                hasSurfacePlateColor = containsColor(pixels, width, height, expectedTokens.surface, step = 2),
                hasBeaconColor = containsColor(pixels, width, height, expectedTokens.beacon, step = 2),
            )
        } finally {
            scene.close()
        }
    }

    private fun withPopulatedPlanDb(block: (AnchorDesktopDatabase) -> Unit) {
        val dbFile = File.createTempFile("anchor_plan_render_", ".db")
        try {
            val db = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), customDbFile = dbFile)
            runBlocking {
                db.initialize()
                db.importDataJson(
                    """
                    {
                      "tasks": [
                        {"title": "Draft cognitive psych lab introduction", "durationMinutes": 25, "difficulty": "HARD", "isNextAction": true},
                        {"title": "Review statistics chapter 6 practice set", "durationMinutes": 20, "difficulty": "MEDIUM"},
                        {"title": "Email TA about section swap", "durationMinutes": 10, "difficulty": "EASY", "isCompleted": true}
                      ]
                    }
                    """.trimIndent(),
                    replaceExisting = true,
                )
            }
            block(db)
        } finally {
            dbFile.delete()
            File(dbFile.absolutePath + "-wal").delete()
            File(dbFile.absolutePath + "-shm").delete()
        }
    }

    private fun withPopulatedSyllabusDb(block: (AnchorDesktopDatabase) -> Unit) {
        val dbFile = File.createTempFile("anchor_syllabus_render_", ".db")
        try {
            val db = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), customDbFile = dbFile)
            runBlocking {
                db.initialize()
                db.insertCourse("CS 101", "Data Structures & Algorithms")
                db.insertCourse("PSYC 204", "Cognitive Psychology")
                db.insertSyllabusItem(
                    courseCode = "CS 101",
                    title = "Midterm Exam 2: Trees & Graphs",
                    itemType = SyllabusItemType.EXAM,
                    dueDateText = "Oct 24",
                    dueDateMillis = 1_792_800_000_000L,
                    weightPercent = 25,
                    prepSteps =
                        listOf(
                            "Review BFS and DFS traversal templates",
                            "Solve 3 practice tree-balancing problems",
                            "Build a 1-page complexity cheat sheet",
                        ),
                )
                db.insertSyllabusItem(
                    courseCode = "PSYC 204",
                    title = "Working Memory Lab Report",
                    itemType = SyllabusItemType.PROJECT,
                    dueDateText = "Nov 4",
                    dueDateMillis = 1_793_750_400_000L,
                    weightPercent = 20,
                    prepSteps =
                        listOf(
                            "Clean reaction-time CSV table",
                            "Draft methods and results paragraphs",
                        ),
                )
                db.insertSyllabusItem(
                    courseCode = "CS 101",
                    title = "Problem Set 5: Shortest Paths",
                    itemType = SyllabusItemType.HOMEWORK,
                    dueDateText = "Oct 19",
                    dueDateMillis = 1_792_368_000_000L,
                    weightPercent = 10,
                    prepSteps = listOf("Read prompt and trace Dijkstra example"),
                )
                db.insertSyllabusItem(
                    courseCode = "PSYC 204",
                    title = "Chapter 6 Attention Reading",
                    itemType = SyllabusItemType.READING,
                    dueDateText = "Oct 15",
                    dueDateMillis = 1_792_022_400_000L,
                    weightPercent = 0,
                    prepSteps = emptyList(),
                )
                val readingItem = db.syllabusItems.value.first { it.itemType == SyllabusItemType.READING }
                db.toggleSyllabusItemCompleted(readingItem.id)
            }
            block(db)
        } finally {
            dbFile.delete()
            File(dbFile.absolutePath + "-wal").delete()
            File(dbFile.absolutePath + "-shm").delete()
        }
    }

    private fun withEmptyDb(block: (AnchorDesktopDatabase) -> Unit) {
        val dbFile = File.createTempFile("anchor_empty_render_", ".db")
        try {
            val db = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), customDbFile = dbFile)
            runBlocking { db.initialize() }
            block(db)
        } finally {
            dbFile.delete()
            File(dbFile.absolutePath + "-wal").delete()
            File(dbFile.absolutePath + "-shm").delete()
        }
    }

    private fun containsColor(
        pixels: PixelMap,
        width: Int,
        height: Int,
        target: Color,
        step: Int = 4,
    ): Boolean {
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                if (pixels[x, y] == target) return true
            }
        }
        return false
    }
}

@Composable
private fun PlanRenderHarness(
    db: AnchorDesktopDatabase,
    nanoState: DesktopMilestoneStepsState,
) {
    val tokens = LocalAnchorTokens.current
    val deletionHistory = remember { mutableStateListOf<DesktopTaskDeletion>() }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(tokens.paper),
    ) {
        val compact = isCompactSidebarWidth(maxWidth)
        Row(modifier = Modifier.fillMaxSize()) {
            DesktopNavigationSidebar(
                currentScreen = DesktopScreen.PLAN,
                onScreenSelected = {},
                isFocusActive = false,
                remainingSeconds = 25 * 60,
                onOpenAirlock = {},
                planPendingCount = 2,
                compact = compact,
            )
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                DesktopPlanScreen(
                    db = db,
                    captureState = DesktopThoughtCaptureState(),
                    onDraftChange = {},
                    onAddMilestone = {},
                    deletionHistory = deletionHistory,
                    onTaskChange = { _, _ -> },
                    isAppActionBusy = false,
                    nanoState = nanoState,
                    onToggleNanoTask = {},
                    onSaveNanoSteps = { _, _ -> },
                    onSelectTaskForFocus = {},
                    onBreakdownTask = {},
                )
            }
        }
    }
}

@Composable
private fun SyllabusRenderHarness(db: AnchorDesktopDatabase) {
    val tokens = LocalAnchorTokens.current

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(tokens.paper),
    ) {
        val compact = isCompactSidebarWidth(maxWidth)
        Row(modifier = Modifier.fillMaxSize()) {
            DesktopNavigationSidebar(
                currentScreen = DesktopScreen.SYLLABUS,
                onScreenSelected = {},
                isFocusActive = false,
                remainingSeconds = 25 * 60,
                onOpenAirlock = {},
                planPendingCount = 2,
                compact = compact,
            )
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                DesktopSyllabusScreen(
                    db = db,
                    onStartFocusWithTask = { _, _ -> },
                    onPlanWithAi = {},
                )
            }
        }
    }
}
