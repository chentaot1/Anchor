package com.anchor.adhd.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anchor.adhd.data.db.AnchorDatabase
import com.anchor.adhd.data.model.AssignmentEntity
import com.anchor.adhd.data.model.BlockRuleEntity
import com.anchor.adhd.data.model.BlockRuleType
import com.anchor.adhd.data.model.CalendarEventEntity
import com.anchor.adhd.data.model.CheckInEntity
import com.anchor.adhd.data.model.CompanionStateEntity
import com.anchor.adhd.data.model.EnergyLevel
import com.anchor.adhd.data.model.FocusEndTag
import com.anchor.adhd.data.model.FocusGardenEntity
import com.anchor.adhd.data.model.FocusSessionEntity
import com.anchor.adhd.data.model.FunLinkEntity
import com.anchor.adhd.data.model.HabitAutoSource
import com.anchor.adhd.data.model.HabitCompletionEntity
import com.anchor.adhd.data.model.HabitEntity
import com.anchor.adhd.data.model.HabitScheduleType
import com.anchor.adhd.data.model.InboxState
import com.anchor.adhd.data.model.ReplanItemEntity
import com.anchor.adhd.data.model.RoutineEntity
import com.anchor.adhd.data.model.RoutineStepEntity
import com.anchor.adhd.data.model.TaskDifficulty
import com.anchor.adhd.data.model.TaskEntity
import com.anchor.adhd.data.model.TemptationBundleEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackupManagerTest {
    private lateinit var context: Context
    private lateinit var sourceDb: AnchorDatabase
    private lateinit var targetDb: AnchorDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sourceDb = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        targetDb = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        sourceDb.close()
        targetDb.close()
    }

    @Test
    fun exportAndImportV2_preservesAllEntitiesLosslessly() = runBlocking {
        val task = TaskEntity(
            id = 10L,
            title = "Write biology lab report",
            notes = "Include methods and results",
            inboxState = InboxState.WAITING,
            scheduledStartMillis = 1_720_000_000_000L,
            durationMinutes = 45,
            difficulty = TaskDifficulty.DEEP,
            isNextAction = true,
            isCompleted = true,
            parentTaskId = 5L,
            routineId = 30L,
            aiGenerated = true,
            estimatedMinutes = 50,
            actualMinutes = 35,
            dueAtMillis = 1_720_500_000_000L,
            ifThen = "If I open the lab notebook, then I write the methods header",
            createdAtMillis = 1_719_990_000_000L,
            completedAtMillis = 1_720_002_100_000L,
            sortOrder = 3
        )
        val assignment = AssignmentEntity(
            id = 20L,
            title = "Bio Lab 3",
            course = "BIO 101",
            dueAtMillis = 1_720_500_000_000L,
            notes = "Submit PDF on Canvas",
            isCompleted = false
        )
        val calendarEvent = CalendarEventEntity(
            id = "google:evt-123",
            title = "Study Group",
            startMillis = 1_720_100_000_000L,
            endMillis = 1_720_103_600_000L,
            source = "google",
            isAllDay = false
        )
        val routine = RoutineEntity(
            id = 30L,
            name = "Morning Launch",
            cue = "After making coffee",
            ifThen = "If I sit at my desk, then I open Anchor",
            isEnabled = true
        )
        val routineStep = RoutineStepEntity(
            id = 31L,
            routineId = 30L,
            title = "Drink a glass of water",
            durationMinutes = 2,
            sortOrder = 0
        )
        val habit = HabitEntity(
            id = 40L,
            name = "Daily Walk",
            scheduleType = HabitScheduleType.SPECIFIC_DAYS,
            scheduleDays = "1,3,5",
            targetPerWeek = 3,
            gracePerWeek = 1,
            autoSource = HabitAutoSource.STEPS,
            autoThreshold = 6000,
            sortOrder = 2,
            isArchived = false,
            createdAtMillis = 1_719_000_000_000L
        )
        val habitCompletion = HabitCompletionEntity(
            id = 41L,
            habitId = 40L,
            dayMillis = 1_720_000_000_000L,
            completed = true,
            autoCompleted = true
        )
        val session = FocusSessionEntity(
            id = 50L,
            taskId = 10L,
            startedAtMillis = 1_720_000_000_000L,
            endedAtMillis = 1_720_001_500_000L,
            plannedMinutes = 25,
            actualMinutes = 22,
            completed = true,
            locked = true,
            endTag = FocusEndTag.STUCK,
            blockedAttempts = 2,
            topBlockedPackage = "com.google.android.youtube"
        )
        val garden = FocusGardenEntity(
            id = 55L,
            sessionId = 50L,
            treeType = "oak",
            plantedAtMillis = 1_720_001_500_000L
        )
        val companion = CompanionStateEntity(
            id = 1,
            name = "Pip",
            energy = 85,
            maxEnergy = 100,
            pauseUntilMillis = 1_721_000_000_000L,
            streakDays = 6,
            lastActiveDayMillis = 1_720_000_000_000L,
            stage = 3
        )
        val checkIn = CheckInEntity(
            id = 60L,
            energyLevel = EnergyLevel.LOW,
            tags = "OVERWHELMED,FOGGY",
            recordedAtMillis = 1_720_005_000_000L
        )
        val blockRule = BlockRuleEntity(
            id = 70L,
            packageName = "com.google.android.youtube",
            ruleType = BlockRuleType.SCHEDULED,
            startHour = 9,
            startMinute = 30,
            endHour = 17,
            endMinute = 0,
            enabled = true
        )
        val bundle = TemptationBundleEntity(
            id = 80L,
            name = "Podcast + Dishes",
            rewardPackageName = "com.spotify.music",
            unlockMinutes = 20,
            enabled = true,
            unlockUntilMillis = 1_720_050_000_000L
        )
        val replanItem = ReplanItemEntity(
            id = 90L,
            taskId = 10L,
            missedOnDayMillis = 1_719_950_000_000L,
            resolved = false
        )
        val funLink = FunLinkEntity(
            id = 100L,
            name = "Chess Puzzle",
            url = "https://lichess.org/training",
            emoji = "♟️",
            sortOrder = 2,
            enabled = true
        )

        sourceDb.taskDao().insertAll(listOf(task))
        sourceDb.assignmentDao().insertAll(listOf(assignment))
        sourceDb.calendarEventDao().upsertAll(listOf(calendarEvent))
        sourceDb.routineDao().insertAllRoutines(listOf(routine))
        sourceDb.routineDao().insertSteps(listOf(routineStep))
        sourceDb.habitDao().insertAll(listOf(habit))
        sourceDb.habitDao().insertAllCompletions(listOf(habitCompletion))
        sourceDb.focusSessionDao().insertAll(listOf(session))
        sourceDb.companionDao().insertAllGarden(listOf(garden))
        sourceDb.companionDao().upsert(companion)
        sourceDb.checkInDao().insertAll(listOf(checkIn))
        sourceDb.blockRuleDao().insertAll(listOf(blockRule))
        sourceDb.temptationBundleDao().insertAll(listOf(bundle))
        sourceDb.replanDao().insertAll(listOf(replanItem))
        sourceDb.funLinkDao().insertAll(listOf(funLink))

        val exporter = BackupManager(context, sourceDb)
        val json = exporter.exportJsonString()

        val importer = BackupManager(context, targetDb)
        val summary = importer.importFromJson(json, replaceExisting = true)

        assertEquals(2, summary.version)
        assertEquals(1, summary.tasksImported)
        assertEquals(1, summary.assignmentsImported)
        assertEquals(1, summary.calendarEventsImported)
        assertEquals(1, summary.routinesImported)
        assertEquals(1, summary.routineStepsImported)
        assertEquals(1, summary.habitsImported)
        assertEquals(1, summary.habitCompletionsImported)
        assertEquals(1, summary.focusSessionsImported)
        assertEquals(1, summary.focusGardenImported)
        assertTrue(summary.companionRestored)
        assertEquals(1, summary.checkInsImported)
        assertEquals(1, summary.blockRulesImported)
        assertEquals(1, summary.temptationBundlesImported)
        assertEquals(1, summary.replanItemsImported)
        assertEquals(1, summary.funLinksImported)

        assertEquals(listOf(task), targetDb.taskDao().getAll())
        assertEquals(listOf(assignment), targetDb.assignmentDao().getAll())
        assertEquals(listOf(calendarEvent), targetDb.calendarEventDao().getAll())
        assertEquals(listOf(routine), targetDb.routineDao().getAll())
        assertEquals(listOf(routineStep), targetDb.routineDao().getAllSteps())
        assertEquals(listOf(habit), targetDb.habitDao().getAll())
        assertEquals(listOf(habitCompletion), targetDb.habitDao().getAllCompletions())
        assertEquals(listOf(session), targetDb.focusSessionDao().getAll())
        assertEquals(listOf(garden), targetDb.companionDao().getAllGarden())
        assertEquals(companion, targetDb.companionDao().getState())
        assertEquals(listOf(checkIn), targetDb.checkInDao().getAll())
        assertEquals(listOf(blockRule), targetDb.blockRuleDao().getAll())
        assertEquals(listOf(bundle), targetDb.temptationBundleDao().getAll())
        assertEquals(listOf(replanItem), targetDb.replanDao().getAll())
        assertEquals(listOf(funLink), targetDb.funLinkDao().getAll())
    }

    @Test
    fun importFromJson_supportsLegacyV1Payload() = runBlocking {
        val v1Json = """
            {
              "version": 1,
              "exportedAtMillis": 1720000000000,
              "tasks": [
                {
                  "id": 7,
                  "title": "Legacy task",
                  "inboxState": "SOMEDAY",
                  "scheduledStartMillis": null,
                  "durationMinutes": 30,
                  "difficulty": "DEEP",
                  "isCompleted": false
                }
              ],
              "assignments": [
                {
                  "id": 8,
                  "title": "Legacy assignment",
                  "course": "MATH 201",
                  "dueAtMillis": 1720500000000,
                  "isCompleted": true
                }
              ],
              "sessions": [
                {
                  "id": 9,
                  "startedAtMillis": 1720000000000,
                  "plannedMinutes": 25,
                  "completed": true
                }
              ],
              "checkIns": [
                {
                  "id": 10,
                  "timestampMillis": 1720000500000,
                  "energy": "LOW",
                  "tags": "FOGGY"
                }
              ]
            }
        """.trimIndent()

        val importer = BackupManager(context, targetDb)
        val summary = importer.importFromJson(v1Json, replaceExisting = true)

        assertEquals(1, summary.version)
        assertEquals(1, summary.tasksImported)
        assertEquals(1, summary.assignmentsImported)
        assertEquals(1, summary.focusSessionsImported)
        assertEquals(1, summary.checkInsImported)

        val importedTask = targetDb.taskDao().getById(7L)!!
        assertEquals("Legacy task", importedTask.title)
        assertEquals(InboxState.SOMEDAY, importedTask.inboxState)
        assertEquals(TaskDifficulty.DEEP, importedTask.difficulty)
        assertEquals(30, importedTask.durationMinutes)
    }

    @Test
    fun importFromJson_supportsWindowsDesktopBackupPayload() = runBlocking {
        val desktopJson = """
            {
              "version": 1,
              "tasks": [
                {
                  "id": 101,
                  "title": "Finish algorithm proof",
                  "notes": "Check induction base case",
                  "isInbox": false,
                  "estimatedMinutes": 45,
                  "difficulty": "HARD",
                  "dueDateMillis": 1720600000000,
                  "scheduledBlockStartMillis": 1720510000000,
                  "isCompleted": false
                },
                {
                  "id": 102,
                  "title": "Reply to TA email",
                  "isInbox": true,
                  "estimatedMinutes": 10,
                  "difficulty": "EASY",
                  "isCompleted": false
                }
              ],
              "courses": [
                {
                  "id": 5,
                  "code": "CS 301",
                  "name": "Algorithms"
                }
              ],
              "syllabus_items": [
                {
                  "id": 201,
                  "courseId": 5,
                  "title": "Problem Set 4",
                  "dueDateMillis": 1720700000000,
                  "prepSteps": ["Review lecture notes", "Draft solutions"],
                  "isCompleted": false
                }
              ],
              "focus_sessions": [
                {
                  "id": 301,
                  "taskId": 101,
                  "startedAtMillis": 1720510000000,
                  "endedAtMillis": 1720511800000,
                  "durationSeconds": 1800,
                  "actualSeconds": 1500,
                  "completed": true
                }
              ],
              "harbor_stats": {
                "streakDays": 4,
                "focusGardenCount": 3,
                "lastActiveDateMillis": 1720500000000,
                "graceEndMillis": 1720900000000
              },
              "fun_links": [
                {
                  "id": 401,
                  "title": "Lofi Girl",
                  "url": "https://youtube.com",
                  "emoji": "🎧"
                }
              ]
            }
        """.trimIndent()

        val importer = BackupManager(context, targetDb)
        val summary = importer.importFromJson(desktopJson, replaceExisting = true)

        assertEquals(2, summary.tasksImported)
        assertEquals(1, summary.assignmentsImported)
        assertEquals(1, summary.focusSessionsImported)
        assertTrue(summary.companionRestored)
        assertEquals(1, summary.funLinksImported)

        val hardTask = targetDb.taskDao().getById(101L)!!
        assertEquals(TaskDifficulty.DEEP, hardTask.difficulty)
        assertEquals(45, hardTask.durationMinutes)
        assertEquals(1_720_600_000_000L, hardTask.dueAtMillis)
        assertEquals(1_720_510_000_000L, hardTask.scheduledStartMillis)
        assertEquals(InboxState.TODAY, hardTask.inboxState)

        val easyTask = targetDb.taskDao().getById(102L)!!
        assertEquals(TaskDifficulty.LIGHT, easyTask.difficulty)
        assertEquals(InboxState.TODAY, easyTask.inboxState)

        val assignment = targetDb.assignmentDao().getAll().single()
        assertEquals("Problem Set 4", assignment.title)
        assertEquals("CS 301", assignment.course)
        assertEquals(1_720_700_000_000L, assignment.dueAtMillis)
        assertTrue(assignment.notes.contains("Review lecture notes"))

        val session = targetDb.focusSessionDao().getAll().single()
        assertEquals(30, session.plannedMinutes)
        assertEquals(25, session.actualMinutes)

        val companion = targetDb.companionDao().getState()!!
        assertEquals(4, companion.streakDays)
        assertEquals(75, companion.energy)
        assertEquals(1_720_500_000_000L, companion.lastActiveDayMillis)
        assertEquals(1_720_900_000_000L, companion.pauseUntilMillis)

        val funLink = targetDb.funLinkDao().getAll().single()
        assertEquals("Lofi Girl", funLink.name)
    }

    @Test
    fun importFromJson_supportsActualDesktopExportFieldNames() = runBlocking {
        val json = """
            {
              "version": 1,
              "tasks": [{"id": 42, "title": "Write proof", "durationMinutes": 45, "difficulty": "HARD"}],
              "courses": [{"code": "CS 301", "name": "Algorithms"}],
              "syllabus_items": [{"id": 7, "courseCode": "CS 301", "title": "Exam", "dueDateMillis": 1792022400000}],
              "focus_sessions": [{"id": 9, "taskTitle": "Write proof", "durationSeconds": 2700, "actualSeconds": 2100, "completed": true}],
              "harbor_stats": {"totalSessions": 3, "totalMinutes": 90, "streakDays": 7, "last_focus_logical_date": "2026-10-08"},
              "fun_links": [{"name": "Hidden", "url": "https://example.com", "enabled": false}]
            }
        """.trimIndent()
        BackupManager(context, targetDb).importFromJson(json, replaceExisting = true)
        assertEquals("CS 301", targetDb.assignmentDao().getAll().single().course)
        val session = targetDb.focusSessionDao().getAll().single()
        assertEquals(42L, session.taskId)
        assertEquals(45, session.plannedMinutes)
        assertEquals(35, session.actualMinutes)
        val companion = targetDb.companionDao().getState()!!
        assertEquals(7, companion.streakDays)
        assertEquals(75, companion.energy)
        assertEquals(java.time.LocalDate.of(2026, 10, 8).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), companion.lastActiveDayMillis)
        assertFalse(targetDb.funLinkDao().getAll().single().enabled)
    }
}
