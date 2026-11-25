package com.anchor.adhd.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.anchor.adhd.desktop.ai.DesktopMilestonePreview
import com.anchor.adhd.desktop.ai.DesktopMilestoneStepsState
import com.anchor.adhd.desktop.ai.DesktopRoutingSession
import com.anchor.adhd.desktop.blocker.DesktopProcessMonitor
import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import com.anchor.adhd.desktop.db.DesktopDataOperations
import com.anchor.adhd.desktop.db.DesktopHarborState
import com.anchor.adhd.desktop.db.DesktopTask
import com.anchor.adhd.desktop.db.DesktopTaskDeletion
import com.anchor.adhd.desktop.db.DesktopThoughtCaptureState
import com.anchor.adhd.desktop.db.SyllabusItemType
import com.anchor.adhd.desktop.prefs.DesktopUserPreferences
import com.anchor.adhd.desktop.theme.AnchorTheme
import com.anchor.adhd.desktop.ui.DesktopAiScreen
import com.anchor.adhd.desktop.ui.DesktopBlockerScreen
import com.anchor.adhd.desktop.ui.DesktopFocusScreen
import com.anchor.adhd.desktop.ui.DesktopHomeDeck
import com.anchor.adhd.desktop.ui.DesktopNavigationSidebar
import com.anchor.adhd.desktop.ui.DesktopPlanScreen
import com.anchor.adhd.desktop.ui.DesktopScreen
import com.anchor.adhd.desktop.ui.DesktopSettingsScreen
import com.anchor.adhd.desktop.ui.DesktopSyllabusScreen
import com.anchor.adhd.desktop.ui.isCompactSidebarWidth
import com.anchor.adhd.ui.components.WeeklyTideDay
import com.anchor.adhd.ui.theme.LocalAnchorTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private enum class SnapshotTarget(
    val slug: String,
    val screen: DesktopScreen,
) {
    HOME("home", DesktopScreen.GROVE),
    PLAN("plan", DesktopScreen.PLAN),
    FOCUS("focus", DesktopScreen.FOCUS),
    BLOCKER("blocker", DesktopScreen.BLOCKER),
    SYLLABUS("syllabus", DesktopScreen.SYLLABUS),
    CAPTAIN("captain", DesktopScreen.AI),
    SETTINGS("settings", DesktopScreen.SETTINGS),
}

private class SnapshotFixtures : AutoCloseable {
    private val dbFile = File.createTempFile("anchor_redesign_snapshot_", ".db")
    private val prefsFile = File.createTempFile("anchor_redesign_prefs_", ".preferences_pb")
    val scope = CoroutineScope(Dispatchers.Unconfined)
    val db = AnchorDesktopDatabase(scope, customDbFile = dbFile)
    private val prefsStore =
        PreferenceDataStoreFactory.createWithPath(
            scope = scope,
            produceFile = { prefsFile.absolutePath.toPath() },
        )
    val prefs = DesktopUserPreferences(prefsStore)
    val processMonitor = DesktopProcessMonitor(scope, database = db) {}
    val dataOperations = DesktopDataOperations(scope)

    val sampleTasks: List<DesktopTask>
    val weeklyTideDays: List<WeeklyTideDay>
    val planNanoState: DesktopMilestoneStepsState

    init {
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
                    ),
            )
            db.insertSyllabusItem(
                courseCode = "PSYC 204",
                title = "Working Memory Lab Report",
                itemType = SyllabusItemType.PROJECT,
                dueDateText = "Nov 4",
                dueDateMillis = 1_793_750_400_000L,
                weightPercent = 20,
                prepSteps = listOf("Clean reaction-time CSV table", "Draft methods paragraph"),
            )
            db.insertBlockRule("discord.exe", "APP", scheduleMode = "FOCUS_ONLY", dailyAllowanceMinutes = 75)
            db.insertBlockRule("steam.exe", "APP", scheduleMode = "ALWAYS_24_7", dailyAllowanceMinutes = -1)
            db.insertBlockRule("youtube.com", "WEB", scheduleMode = "LEISURE_QUOTA", dailyAllowanceMinutes = 60)
            db.insertBlockRule("reddit.com", "WEB", scheduleMode = "FOCUS_ONLY", dailyAllowanceMinutes = 30)
        }
        sampleTasks = db.tasks.value
        weeklyTideDays =
            listOf(
                WeeklyTideDay("T", 25, false),
                WeeklyTideDay("F", 50, false),
                WeeklyTideDay("S", 15, false),
                WeeklyTideDay("S", 0, false),
                WeeklyTideDay("M", 75, false),
                WeeklyTideDay("T", 40, false),
                WeeklyTideDay("W", 45, true),
            )
        val expandedTask = sampleTasks.firstOrNull { it.isNextAction }
        planNanoState =
            if (expandedTask != null) {
                DesktopMilestoneStepsState(
                    expandedTaskId = expandedTask.id,
                    previews =
                        mapOf(
                            expandedTask.id to
                                DesktopMilestonePreview(
                                    key = "snapshot-preview",
                                    sourceId = expandedTask.id,
                                    sourceTitle = expandedTask.title,
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
    }

    override fun close() {
        dbFile.delete()
        File(dbFile.absolutePath + "-wal").delete()
        File(dbFile.absolutePath + "-shm").delete()
        prefsFile.delete()
    }
}

class DesktopRedesignSnapshotTest {
    @Test
    fun allSevenScreensRenderMorningAndNightHarborSnapshotsInNavigationShell() {
        val outputDir = redesignSnapshotOutputDir()
        outputDir.mkdirs()

        SnapshotFixtures().use { fixtures ->
            val captures = linkedMapOf<String, ByteArray>()
            for (target in SnapshotTarget.entries) {
                for (darkTheme in listOf(false, true)) {
                    val themeSlug = if (darkTheme) "dark" else "light"
                    for ((width, height) in SNAPSHOT_VIEWPORTS) {
                        val fileName = "${target.slug}-$themeSlug-${width}x$height.png"
                        val png =
                            renderScreenPng(
                                target = target,
                                fixtures = fixtures,
                                width = width,
                                height = height,
                                darkTheme = darkTheme,
                            )
                        val file = File(outputDir, fileName)
                        file.writeBytes(png)
                        captures[fileName] = png
                        assertTrue("Expected snapshot file to exist: ${file.absolutePath}", file.isFile)
                        assertTrue(
                            "$fileName should exceed 5 KB (got ${png.size} bytes)",
                            png.size > MIN_SNAPSHOT_BYTES,
                        )
                    }
                }
            }

            for (target in SnapshotTarget.entries) {
                for ((width, height) in SNAPSHOT_VIEWPORTS) {
                    val light = captures["${target.slug}-light-${width}x$height.png"]!!
                    val dark = captures["${target.slug}-dark-${width}x$height.png"]!!
                    assertFalse(
                        "${target.slug} at ${width}x$height must differ between Morning Harbor Light and Night Harbor Dark",
                        light.contentEquals(dark),
                    )
                }
            }
        }
    }

    private fun renderScreenPng(
        target: SnapshotTarget,
        fixtures: SnapshotFixtures,
        width: Int,
        height: Int,
        darkTheme: Boolean,
    ): ByteArray {
        val scene =
            ImageComposeScene(
                width = width,
                height = height,
                density = Density(1f),
            ) {
                AnchorTheme(darkTheme = darkTheme, reducedMotion = true) {
                    RedesignSnapshotShell(target = target, fixtures = fixtures)
                }
            }
        return try {
            val t = 0L
            scene.render(t)
            val frame = scene.render(t + 16_000_000L)
            frame.encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("Failed to encode PNG for ${target.slug} (${width}x$height, dark=$darkTheme)")
        } finally {
            scene.close()
        }
    }

    private fun redesignSnapshotOutputDir(): File {
        var current: File? = File(System.getProperty("user.dir")).canonicalFile
        while (current != null) {
            if (File(current, "settings.gradle.kts").isFile && File(current, "DESIGN.md").isFile) {
                return File(current, "desktopApp/build/redesign-snapshots")
            }
            current = current.parentFile
        }
        error("Unable to resolve Anchor repository root for redesign-snapshots output")
    }

    companion object {
        private const val MIN_SNAPSHOT_BYTES = 5 * 1024
        private val SNAPSHOT_VIEWPORTS = listOf(1200 to 800, 640 to 450)
    }
}

@Composable
private fun RedesignSnapshotShell(
    target: SnapshotTarget,
    fixtures: SnapshotFixtures,
) {
    val tokens = LocalAnchorTokens.current
    val scope = rememberCoroutineScope()
    val routingSession = remember { DesktopRoutingSession(scope) }
    val deletionHistory = remember { mutableStateListOf<DesktopTaskDeletion>() }
    val primaryTask = fixtures.sampleTasks.firstOrNull { it.isNextAction }
    val isFocusTarget = target.screen == DesktopScreen.FOCUS
    val remainingSeconds = if (isFocusTarget) 18 * 60 + 24 else 25 * 60

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(tokens.paper),
    ) {
        val compact = isCompactSidebarWidth(maxWidth)
        Row(modifier = Modifier.fillMaxSize()) {
            DesktopNavigationSidebar(
                currentScreen = target.screen,
                onScreenSelected = {},
                isFocusActive = isFocusTarget,
                remainingSeconds = remainingSeconds,
                onOpenAirlock = {},
                planPendingCount = fixtures.sampleTasks.count { !it.isCompleted },
                compact = compact,
            )
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                when (target.screen) {
                    DesktopScreen.GROVE ->
                        DesktopHomeDeck(
                            routingSession = routingSession,
                            canRunRouterAction = { true },
                            currentTask = primaryTask,
                            tasks = fixtures.sampleTasks,
                            funLinks = emptyList(),
                            isFocusActive = false,
                            remainingSeconds = remainingSeconds,
                            durationMinutes = 25,
                            todayFocusMinutes = 45,
                            deflectedCount = 6,
                            streakDays = 4,
                            onStartFocus = {},
                            onOpenDurationPicker = {},
                            onLaunchMomentumPreset = {},
                            thoughtCaptureState = DesktopThoughtCaptureState(),
                            onThoughtDraftChange = {},
                            onCaptureThought = {},
                            onMakeTaskNow = {},
                            onToggleTaskCompleted = {},
                            onNavigateToPlan = {},
                            harborState = DesktopHarborState(totalSessions = 14, totalMinutes = 340, streakDays = 4),
                            weeklyFocusDays = fixtures.weeklyTideDays,
                            weeklySessionCount = 7,
                            todaySessionCount = 2,
                            onBreakDownWithCaptain = {},
                            onOpenCaptain = {},
                            hourOfDay = 9,
                        )
                    DesktopScreen.PLAN ->
                        DesktopPlanScreen(
                            db = fixtures.db,
                            captureState = DesktopThoughtCaptureState(),
                            onDraftChange = {},
                            onAddMilestone = {},
                            deletionHistory = deletionHistory,
                            onTaskChange = { _, _ -> },
                            isAppActionBusy = false,
                            nanoState = fixtures.planNanoState,
                            onToggleNanoTask = {},
                            onSaveNanoSteps = { _, _ -> },
                            onSelectTaskForFocus = {},
                            onBreakdownTask = {},
                        )
                    DesktopScreen.FOCUS ->
                        DesktopFocusScreen(
                            currentTask = primaryTask,
                            isFocusActive = true,
                            remainingSeconds = remainingSeconds,
                            durationMinutes = 25,
                            isFullscreen = false,
                            onToggleFullscreen = {},
                            onToggleFocus = {},
                            onAdjustSeconds = {},
                            onStartSpark = {},
                            onResetTimer = {},
                            onOpenDurationPicker = {},
                            onOpenAirlock = {},
                            isWorkPhase = true,
                            hasOpenFocusSession = true,
                            showSessionCompleteBanner = false,
                            breakDurationMinutes = 5,
                            onMarkTaskCompleted = {},
                            onStartRestorativeBreak = {},
                            onDismissSessionCompleteBanner = {},
                            isFloatingIslandActive = false,
                            onToggleFloatingIsland = {},
                            onBreakDownWithCaptain = {},
                        )
                    DesktopScreen.BLOCKER ->
                        DesktopBlockerScreen(
                            db = fixtures.db,
                            prefs = fixtures.prefs,
                            processMonitor = fixtures.processMonitor,
                            isFocusActive = false,
                            onTestRescueShield = {},
                        )
                    DesktopScreen.SYLLABUS ->
                        DesktopSyllabusScreen(
                            db = fixtures.db,
                            onStartFocusWithTask = { _, _ -> },
                            onPlanWithAi = {},
                        )
                    DesktopScreen.AI ->
                        DesktopAiScreen(
                            db = fixtures.db,
                            initialTask = primaryTask?.title.orEmpty(),
                            onStartFocus = {},
                            onOpenSyllabus = {},
                            initialTool = 0,
                        )
                    DesktopScreen.SETTINGS ->
                        DesktopSettingsScreen(
                            currentStreakDays = fixtures.db.harborState.value.streakDays,
                            db = fixtures.db,
                            prefs = fixtures.prefs,
                            durationMinutes = 25,
                            onDurationChanged = {},
                            backupOperationScope = fixtures.scope,
                            dataOperations = fixtures.dataOperations,
                            canReplaceData = { true },
                        )
                }
            }
        }
    }
}
