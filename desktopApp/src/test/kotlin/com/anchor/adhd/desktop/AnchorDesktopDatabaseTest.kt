package com.anchor.adhd.desktop

import com.anchor.adhd.desktop.blocker.DesktopProcessMonitor
import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDateTime

class AnchorDesktopDatabaseTest {
    @Test
    fun androidBackupImportsAssignmentsMinuteSessionsCompanionAndAllFunLinks() = runBlocking {
        db.initialize()
        val json = """
            {
              "version": 2,
              "tasks": [{"id": 42, "title": "Write proof", "durationMinutes": 45, "difficulty": "DEEP"}],
              "assignments": [
                {"title": "Essay", "course": "ENG 201", "dueAtMillis": 1792022400000, "notes": "Draft outline\nRevise", "isCompleted": true},
                {"title": "Quiz", "course": "ENG 201", "dueAtMillis": 1792108800000}
              ],
              "focusSessions": [
                {"taskId": 42, "plannedMinutes": 45, "actualMinutes": 35, "completed": true, "startedAtMillis": 1000, "endedAtMillis": 2101000},
                {"taskId": null, "plannedMinutes": 10, "actualMinutes": null, "completed": false, "startedAtMillis": 3000, "endedAtMillis": null}
              ],
              "companionState": {"streakDays": 7, "lastActiveDayMillis": 1792022400000},
              "funLinks": [
                {"name": "Puzzle", "url": "https://example.com/puzzle", "emoji": "🧩", "sortOrder": 2, "enabled": true},
                {"name": "Hidden", "url": "https://example.com/hidden", "emoji": "🌙", "sortOrder": 3, "enabled": false}
              ]
            }
        """.trimIndent()
        db.importDataJson(json, replaceExisting = true)
        assertEquals("HARD", db.tasks.value.single().difficulty)
        assertEquals(1, db.courses.value.size)
        assertEquals("ENG 201", db.courses.value.single().code)
        assertEquals(2, db.syllabusItems.value.size)
        val essay = db.syllabusItems.value.first { it.title == "Essay" }
        assertEquals(1792022400000L, essay.dueDateMillis)
        assertEquals(listOf("Draft outline", "Revise"), essay.prepSteps)
        assertTrue(essay.isCompleted)
        val sessions = db.getFocusSessions()
        assertEquals(2, sessions.size)
        val work = sessions.first { it.completed }
        assertEquals("Write proof", work.taskTitle)
        assertEquals(2700, work.durationSeconds)
        assertEquals(2100, work.actualSeconds)
        assertEquals(0, sessions.first { !it.completed }.actualSeconds)
        assertEquals(35, db.harborState.value.totalMinutes)
        assertEquals(7, db.harborState.value.streakDays)
        assertEquals(1, db.funLinks.value.size)
        val exported = db.exportDataJson()
        assertTrue(exported.contains("Hidden"))
        db.importDataJson(exported, replaceExisting = true)
        assertEquals(1, db.funLinks.value.size)
        assertTrue(db.exportDataJson().contains("Hidden"))
    }

    @Test
    fun focusTaskUpsertPromotesExistingTaskAndDoesNotReuseCompletedTask() = runBlocking {
        db.initialize()
        db.insertTask("First task", 10)
        db.insertTask("Selected task", 45)
        val selected = db.tasks.value.first { it.title == "Selected task" }
        val target = db.upsertAndMakeTaskNow("selected task", 35)!!
        assertEquals(selected.id, target.id)
        assertEquals(35, target.durationMinutes)
        assertEquals(target.id, db.tasks.value.first { !it.isCompleted }.id)
        assertTrue(target.isNextAction)
        assertEquals(1, db.tasks.value.count { it.title.equals("selected task", ignoreCase = true) })
        db.toggleTaskCompleted(target.id)
        val fresh = db.upsertAndMakeTaskNow("Selected task", 20)!!
        assertTrue(fresh.id != target.id)
        assertFalse(fresh.isCompleted)
        assertTrue(fresh.isNextAction)
    }

    private lateinit var tempFile: File
    private lateinit var db: AnchorDesktopDatabase

    @Before
    fun setup() {
        tempFile = File.createTempFile("anchor_test_", ".db")
        db = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), customDbFile = tempFile)
    }

    @After
    fun tearDown() {
        tempFile.delete()
        File(tempFile.absolutePath + "-wal").delete()
        File(tempFile.absolutePath + "-shm").delete()
    }

    @Test
    fun testInitializationAndSeedData() =
        runBlocking {
            db.initialize()

            val funLinks = db.funLinks.value
            assertEquals(4, funLinks.size)
            assertTrue(funLinks.any { it.name == "Wordle" })
            assertTrue(funLinks.any { it.name == "Connections" })
            assertTrue(funLinks.any { it.name == "Chess Puzzles" })
            assertTrue(funLinks.any { it.name == "Contexto" })

            val blockRules = db.getBlockRules()
            assertTrue(blockRules.contains("discord.exe"))
            assertTrue(blockRules.contains("spotify.exe"))
            assertFalse(blockRules.contains("steam.exe"))
            assertTrue(blockRules.contains("youtube"))
        }

    @Test
    fun testTaskCrud() =
        runBlocking {
            db.initialize()

            db.insertTask("Complete Windows 11 port", 30)
            var tasks = db.tasks.value
            val task = tasks.find { it.title == "Complete Windows 11 port" }
            assertTrue(task != null)
            assertFalse(task!!.isCompleted)
            assertEquals(30, task.durationMinutes)

            // Toggle completed
            db.toggleTaskCompleted(task.id)
            tasks = db.tasks.value
            val updated = tasks.find { it.id == task.id }
            assertTrue(updated!!.isCompleted)

            // Delete task
            db.deleteTask(task.id)
            tasks = db.tasks.value
            assertFalse(tasks.any { it.id == task.id })
        }

    @Test
    fun testFocusSessionAndHarborProgression() =
        runBlocking {
            db.initialize()

            var harbor = db.harborState.value
            assertFalse(harbor.unlockedBoat)

            // Record 1st session -> unlocks boat
            db.recordFocusSession("Deep Coding", 1500, 1500, true)
            harbor = db.harborState.value
            assertEquals(1, harbor.totalSessions)
            assertTrue(harbor.unlockedBoat)
            assertFalse(harbor.unlockedWake)

            // Record 4 more sessions -> total 5 -> unlocks wake
            repeat(4) {
                db.recordFocusSession("Deep Coding", 1500, 1500, true)
            }
            harbor = db.harborState.value
            assertEquals(5, harbor.totalSessions)
            assertTrue(harbor.unlockedWake)
        }

    @Test
    fun testBlockRuleEditsLocksAndSteamSurviveSubsequentInitialize() =
        runBlocking {
            db.initialize()

            // 1. Toggle discord.exe off and set a 24h lock on youtube
            val initialRules = db.blockRules.value
            val discordRule = initialRules.first { it.target == "discord.exe" }
            val youtubeRule = initialRules.first { it.target == "youtube" }

            db.toggleBlockRule(discordRule.id)
            db.lockBlockRuleFor24Hours(youtubeRule.id)

            // 2. Add a custom rule and a steam.exe rule
            db.insertBlockRule("steam.exe", "APP")
            db.insertBlockRule("reddit.com", "WEB")

            // 3. Re-instantiate database on the same file and call initialize() again
            val restartedDb = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), customDbFile = tempFile)
            restartedDb.initialize()

            val afterRestartRules = restartedDb.blockRules.value
            val discordAfter = afterRestartRules.first { it.target == "discord.exe" }
            val youtubeAfter = afterRestartRules.first { it.target == "youtube" }
            val steamAfter = afterRestartRules.find { it.target == "steam.exe" }
            val redditAfter = afterRestartRules.find { it.target == "reddit.com" }

            assertFalse("discord.exe disabled state should survive initialize()", discordAfter.enabled)
            assertTrue("youtube 24h lock should survive initialize()", youtubeAfter.lockUntilEpoch > System.currentTimeMillis())
            assertNotNull("user-added steam.exe rule should survive subsequent initialize()", steamAfter)
            assertTrue("steam.exe should remain enabled", steamAfter!!.enabled)
            assertNotNull("custom reddit.com rule should survive initialize()", redditAfter)
        }

    @Test
    fun testFocusStreakLogicalDateProgression() =
        runBlocking {
            db.initialize()

            val day1Morning = LocalDateTime.of(2026, 10, 5, 10, 0)
            val day1LateNightBeforeRollover = LocalDateTime.of(2026, 10, 6, 2, 30) // Still logical date 2026-10-05 (before 4 AM)
            val day2Afternoon = LocalDateTime.of(2026, 10, 6, 14, 0) // Logical date 2026-10-06 (consecutive)
            val day3Evening = LocalDateTime.of(2026, 10, 7, 19, 0) // Logical date 2026-10-07 (consecutive)
            val day5AfterGap = LocalDateTime.of(2026, 10, 9, 11, 0) // Logical date 2026-10-09 (1 day missed -> resets to 1)

            // First completed session sets streak to 1
            db.recordFocusSession("Day 1 Session 1", 1500, 1500, true, now = day1Morning)
            assertEquals(1, db.harborState.value.streakDays)
            assertEquals("2026-10-05", db.harborState.value.lastFocusLogicalDate)

            // Another session on the same logical day (even at 2:30 AM next calendar day before 4:00 AM rollover) keeps streak at 1
            db.recordFocusSession("Day 1 Late Night", 1500, 1500, true, now = day1LateNightBeforeRollover)
            assertEquals(1, db.harborState.value.streakDays)
            assertEquals("2026-10-05", db.harborState.value.lastFocusLogicalDate)

            // Consecutive logical day increments streak to 2
            db.recordFocusSession("Day 2 Session", 1500, 1500, true, now = day2Afternoon)
            assertEquals(2, db.harborState.value.streakDays)
            assertEquals("2026-10-06", db.harborState.value.lastFocusLogicalDate)

            // Next consecutive logical day via convenience overload increments streak to 3
            db.recordFocusSession(25, "Day 3 Session", now = day3Evening)
            assertEquals(3, db.harborState.value.streakDays)
            assertEquals("2026-10-07", db.harborState.value.lastFocusLogicalDate)

            // Incomplete session does not advance or reset streak
            db.recordFocusSession("Abandoned Session", 1500, 300, false, now = day5AfterGap)
            assertEquals(3, db.harborState.value.streakDays)

            // Completed session after a gap resets streak to 1
            db.recordFocusSession("Day 5 Session After Gap", 1500, 1500, true, now = day5AfterGap)
            assertEquals(1, db.harborState.value.streakDays)
            assertEquals("2026-10-09", db.harborState.value.lastFocusLogicalDate)
        }

    @Test
    fun testDailyProtectionStatsPersistenceAndRollover() =
        runBlocking {
            db.initialize()

            val day1Time = LocalDateTime.of(2026, 10, 5, 13, 0)
            val day1LateTime = LocalDateTime.of(2026, 10, 6, 3, 15) // Still logical date 2026-10-05
            val day2AfterRollover = LocalDateTime.of(2026, 10, 6, 4, 5) // New logical date 2026-10-06

            val monitor =
                DesktopProcessMonitor(
                    scope = CoroutineScope(Dispatchers.Unconfined),
                    database = db,
                )
            monitor.syncProtectionStatsFromDatabase(day1Time)

            assertEquals(0, monitor.getQuickPassesUsedToday(day1Time))
            assertEquals(0, monitor.deflectedCount.value)

            // Grant 2 quick passes and record 3 deflections on day 1
            assertTrue(monitor.grantQuickPass("discord.exe", durationMillis = 120_000L, now = day1Time))
            assertTrue(monitor.grantQuickPass("spotify.exe", durationMillis = 120_000L, now = day1LateTime))
            monitor.recordDeflection(day1Time)
            monitor.recordDeflection(day1Time)
            monitor.recordDeflection(day1LateTime)

            assertEquals(2, monitor.getQuickPassesUsedToday(day1LateTime))
            assertEquals(3, monitor.deflectedCount.value)

            // Simulate app restart on the same logical day -> counts should reload from SQLite
            val restartedMonitor =
                DesktopProcessMonitor(
                    scope = CoroutineScope(Dispatchers.Unconfined),
                    database = db,
                )
            restartedMonitor.syncProtectionStatsFromDatabase(day1LateTime)
            assertEquals(2, restartedMonitor.getQuickPassesUsedToday(day1LateTime))
            assertEquals(3, restartedMonitor.deflectedCount.value)

            // Cross the 4:00 AM daily boundary -> both quick passes and deflected count reset to 0
            assertEquals(0, restartedMonitor.getQuickPassesUsedToday(day2AfterRollover))
            assertEquals(0, restartedMonitor.deflectedCount.value)

            val day2Stats = db.getDailyProtectionStats(day2AfterRollover)
            assertEquals("2026-10-06", day2Stats.logicalDate)
            assertEquals(0, day2Stats.quickPassesUsed)
            assertEquals(0, day2Stats.deflectedCount)
        }

    @Test
    fun testJsonExportImportRoundTripAndPreResetAutoBackup() =
        runBlocking {
            db.initialize()

            // Populate all supported tables
            db.insertTask("Write Architecture Doc", 45)
            db.insertCourse("CS-101", "Algorithms", "#80CBC4")

            db.insertSyllabusItem(
                courseCode = "CS-101",
                title = "Problem Set 1",
                itemType = com.anchor.adhd.desktop.db.SyllabusItemType.HOMEWORK,
                dueDateText = "2026-10-15",
                dueDateMillis = 1792022400000L,
                weightPercent = 15,
                prepSteps = listOf("Read prompt", "Solve DP"),
            )
            val syllabusItemId = db.syllabusItems.value.first().id
            db.toggleSyllabusItemCompleted(syllabusItemId)

            db.insertClassSchedule("CS-101", "Algorithms", "Lecture", DayOfWeek.MONDAY.value, 600, 690)
            db.insertBlockRule("steam.exe", "APP")
            val discordRule = db.blockRules.value.first { it.target == "discord.exe" }
            db.toggleBlockRule(discordRule.id)

            val sessionTime = LocalDateTime.of(2026, 10, 8, 15, 0)
            db.recordFocusSession("Write Architecture Doc", 2700, 2700, true, now = sessionTime)

            // Export to JSON
            val exportedJson = db.exportDataJson()
            val exportedCourseCount = db.courses.value.size
            val exportedSyllabusCount = db.syllabusItems.value.size
            assertTrue(exportedJson.contains("Write Architecture Doc"))
            assertTrue(exportedJson.contains("Problem Set 1"))
            assertTrue(exportedJson.contains("steam.exe"))

            // Reset all data and verify pre-reset auto-backup was written
            db.resetAllData()
            val autoBackup = db.lastAutoBackupFile
            assertNotNull("resetAllData() should create a pre-reset auto-backup file", autoBackup)
            assertTrue("Pre-reset auto-backup file should exist", autoBackup!!.exists())
            assertTrue("Pre-reset auto-backup should contain exported data", autoBackup.readText().contains("Write Architecture Doc"))
            autoBackup.delete()

            assertFalse(db.tasks.value.any { it.title == "Write Architecture Doc" })
            assertTrue(db.courses.value.isEmpty())
            assertTrue(db.syllabusItems.value.isEmpty())
            assertEquals(0, db.harborState.value.totalSessions)

            // Import into a clean database and verify all entities are restored
            val secondDbFile = File.createTempFile("anchor_restore_test_", ".db")
            try {
                val restoredDb = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), customDbFile = secondDbFile)
                restoredDb.initialize()

                val importedCount = restoredDb.importDataJson(exportedJson, replaceExisting = true)
                assertTrue("importDataJson should import records", importedCount > 0)

                assertTrue(restoredDb.tasks.value.any { it.title == "Write Architecture Doc" && it.durationMinutes == 45 })

                assertEquals(exportedCourseCount, restoredDb.courses.value.size)
                val restoredCourse = restoredDb.courses.value.first { it.code == "CS-101" }
                assertEquals("CS-101", restoredCourse.code)
                assertEquals("Algorithms", restoredCourse.name)

                assertEquals(exportedSyllabusCount, restoredDb.syllabusItems.value.size)
                val restoredSyllabus = restoredDb.syllabusItems.value.first { it.title == "Problem Set 1" }
                assertEquals("Problem Set 1", restoredSyllabus.title)
                assertEquals("CS-101", restoredSyllabus.courseCode)
                assertTrue(restoredSyllabus.isCompleted)

                assertTrue(restoredDb.classSchedules.value.any { it.courseCode == "CS-101" && it.dayOfWeek == DayOfWeek.MONDAY.value })

                val restoredRules = restoredDb.blockRules.value
                assertTrue(restoredRules.any { it.target == "steam.exe" && it.enabled })
                assertFalse(restoredRules.first { it.target == "discord.exe" }.enabled)

                val restoredHarbor = restoredDb.harborState.value
                assertEquals(1, restoredHarbor.totalSessions)
                assertEquals(45, restoredHarbor.totalMinutes)
                assertEquals(1, restoredHarbor.streakDays)
                assertEquals("2026-10-08", restoredHarbor.lastFocusLogicalDate)
                assertTrue(restoredHarbor.unlockedBoat)
            } finally {
                secondDbFile.delete()
                File(secondDbFile.absolutePath + "-wal").delete()
                File(secondDbFile.absolutePath + "-shm").delete()
            }
        }

    @Test
    fun testDeleteCompletedSetNextActionAndUpdateTaskDetails() =
        runBlocking {
            db.initialize()
            db.insertTask("First Task", 20)
            db.insertTask("Second Task", 35)

            val first = db.tasks.value.first { it.title == "First Task" }
            val second = db.tasks.value.first { it.title == "Second Task" }

            // Set Second Task as Next Action -> should sort to top of incomplete tasks
            db.setNextActionTask(second.id)
            assertEquals(second.id, db.tasks.value.first { !it.isCompleted }.id)
            assertTrue(db.tasks.value.first { it.id == second.id }.isNextAction)

            // Update First Task details
            db.updateTaskDetails(first.id, "First Task Edited", 50)
            val updatedFirst = db.tasks.value.first { it.id == first.id }
            assertEquals("First Task Edited", updatedFirst.title)
            assertEquals(50, updatedFirst.durationMinutes)

            // Complete First Task and delete completed tasks
            db.toggleTaskCompleted(first.id)
            assertTrue(db.tasks.value.any { it.isCompleted })
            db.deleteCompletedTasks()
            assertFalse(db.tasks.value.any { it.isCompleted })
            assertFalse(db.tasks.value.any { it.id == first.id })
        }
}
