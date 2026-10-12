package com.anchor.adhd.desktop

import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import com.anchor.adhd.desktop.db.DesktopBackupFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DesktopBackupSafetyTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun database() = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), File(folder.root, "anchor.db"))

    @Test
    fun unrelatedOrMalformedBackupsCannotClearExistingData() = runBlocking {
        val db = database()
        db.initialize()
        db.insertTask("Keep my work", 40)
        db.recordFocusSession("Keep history", 1200, 1200, true)
        val tasks = db.tasks.value
        val rules = db.blockRules.value
        val sessions = db.getFocusSessions()
        val harbor = db.harborState.value
        val invalid = listOf(
            "{}", "[]", "{\"unrelated\":true}", "{\"tasks\":{}}", "{\"tasks\":[null]}",
            "{\"tasks\":[{\"title\":\"\"}]}", "{\"tasks\":[{\"title\":true}]}",
            "{\"tasks\":[{\"title\":\"Broken\",\"durationMinutes\":0}]}",
            "{\"tasks\":[{\"title\":\"New\"}],\"focus_sessions\":[{\"actualSeconds\":-1}]}",
            "{\"version\":99,\"tasks\":[]}", "{\"tasks\":[],\"harbor_stats\":{\"totalMinutes\":-3}}",
            "{\"fun_links\":[],\"funLinks\":[{\"url\":\"https://example.com\"}]}",
            """{"tasks":[],"focus_sessions":[{"saveKey":[]}]}""",
            """{"tasks":[],"focus_sessions":[{"saveKey":"ca87575f-a247-4e2e-90ac-d766e6af75ed"},{"saveKey":"ca87575f-a247-4e2e-90ac-d766e6af75ed"}]}""",
        )
        for (json in invalid) {
            assertTrue("Backup should be rejected: $json", runCatching { db.importDataJson(json, replaceExisting = true) }.isFailure)
            assertEquals(tasks, db.tasks.value)
            assertEquals(rules, db.blockRules.value)
            assertEquals(sessions, db.getFocusSessions())
            assertEquals(harbor, db.harborState.value)
        }
    }

    @Test
    fun partialTaskBackupReplacesTasksAndPreservesOtherSections() = runBlocking {
        val db = database()
        db.initialize()
        db.recordFocusSession("Existing history", 600, 600, true)
        val rules = db.blockRules.value
        val links = db.funLinks.value
        val sessions = db.getFocusSessions()
        db.importDataJson("""{"tasks":[{"title":"Restored task","durationMinutes":35}]}""", replaceExisting = true)
        assertEquals("Restored task", db.tasks.value.single().title)
        assertEquals(rules, db.blockRules.value)
        assertEquals(links, db.funLinks.value)
        assertEquals(sessions, db.getFocusSessions())
        db.importDataJson("""{"fun_links":[]} """, replaceExisting = true)
        assertTrue(db.funLinks.value.isEmpty())
        assertEquals("Restored task", db.tasks.value.single().title)
    }

    @Test
    fun androidAssignmentRestoreKeepsWindowsTasksAndProtection() = runBlocking {
        val db = database()
        db.initialize()
        val tasks = db.tasks.value
        val rules = db.blockRules.value
        val json = """{"version":2,"assignments":[{"title":"Essay","course":"ENG 201","dueAtMillis":1792022400000}]}"""
        val preview = db.previewBackup(json)
        assertEquals(1, preview.sectionCounts["Assignments"])
        assertEquals(1, preview.sectionCounts["Courses"])
        assertEquals(2, preview.totalRecords)
        db.importDataJson(json, replaceExisting = true)
        assertEquals(tasks, db.tasks.value)
        assertEquals(rules, db.blockRules.value)
        assertEquals("Essay", db.syllabusItems.value.single().title)
    }

    @Test
    fun restoreWritesAUsableRecoveryBackupBeforeReplacingTasks() = runBlocking {
        val db = database()
        db.initialize()
        db.insertTask("Original work", 40)
        val (count, recovery) = db.restoreBackup("""{"tasks":[{"title":"Imported work"}]}""")
        assertEquals(1, count)
        assertEquals("Imported work", db.tasks.value.single().title)
        assertTrue(recovery.isFile)
        val originalJson = recovery.readText(Charsets.UTF_8)
        assertTrue(originalJson.contains("Original work"))
        db.importDataJson(originalJson, replaceExisting = true)
        assertTrue(db.tasks.value.any { it.title == "Original work" && it.durationMinutes == 40 })
        assertFalse(db.tasks.value.any { it.title == "Imported work" })
    }

    @Test
    fun failedRecoveryBackupPreventsReplacement() = runBlocking {
        val db = AnchorDesktopDatabase(
            CoroutineScope(Dispatchers.Unconfined), File(folder.root, "anchor.db"),
            backupFileWriter = { _, _ -> throw java.io.IOException("Simulated disk full") },
        )
        db.initialize()
        val tasks = db.tasks.value
        val error = runCatching { db.restoreBackup("""{"tasks":[{"title":"Replacement"}]}""") }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("Could not save a recovery backup"))
        assertEquals(tasks, db.tasks.value)
        assertFalse(folder.root.listFiles()!!.any { it.name.startsWith("anchor_auto_backup_restore_") })
    }

    @Test
    fun resetActionsCannotEraseDataWhenTheirRecoveryWriteFails() = runBlocking {
        val db = AnchorDesktopDatabase(
            CoroutineScope(Dispatchers.Unconfined), File(folder.root, "anchor.db"),
            backupFileWriter = { _, _ -> throw java.io.IOException("Simulated disk full") },
        )
        db.initialize()
        db.recordFocusSession("Preserve focus", 600, 600, true)
        val tasks = db.tasks.value
        val sessions = db.getFocusSessions()
        val harbor = db.harborState.value
        assertTrue(runCatching { db.clearAllTasks() }.isFailure)
        assertTrue(runCatching { db.resetFocusHistoryAndStreak() }.isFailure)
        assertTrue(runCatching { db.resetAllData() }.isFailure)
        assertEquals(tasks, db.tasks.value)
        assertEquals(sessions, db.getFocusSessions())
        assertEquals(harbor, db.harborState.value)
    }

    @Test
    fun resetSqlFailureRollsBackDeletedTasksAndHistory() = runBlocking {
        val db = database()
        db.initialize()
        db.insertTask("Keep through rollback", 40)
        db.recordFocusSession("Keep recorded focus", 600, 600, true)
        val originalTasks = db.tasks.value
        val originalSessions = db.getFocusSessions()
        java.sql.DriverManager.getConnection("jdbc:sqlite:${File(folder.root, "anchor.db").absolutePath}").use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("CREATE TRIGGER reject_starter BEFORE INSERT ON tasks BEGIN SELECT RAISE(ABORT, 'Simulated starter failure'); END;")
            }
        }
        assertTrue(runCatching { db.clearAllTasks() }.isFailure)
        assertTrue(runCatching { db.resetAllData() }.isFailure)
        assertEquals(originalSessions, db.getFocusSessions())
        val reopened = database()
        reopened.initialize()
        assertEquals(originalTasks, reopened.tasks.value)
        assertEquals(10, reopened.harborState.value.totalMinutes)
    }

    @Test
    fun secondsBasedAliasBackupsRetainDurationsAndUpdateHistoryTotals() = runBlocking {
        val db = database()
        db.initialize()
        db.recordFocusSession("Old", 1500, 1500, true)
        val json = """{"focusSessions":[{"taskTitle":"Imported","durationSeconds":900,"actualSeconds":600,"completed":true}]}"""
        assertEquals(1, db.previewBackup(json).sectionCounts["Focus sessions"])
        db.importDataJson(json, replaceExisting = true)
        assertEquals(900, db.getFocusSessions().single().durationSeconds)
        assertEquals(600, db.getFocusSessions().single().actualSeconds)
        assertEquals(1, db.harborState.value.totalSessions)
        assertEquals(10, db.harborState.value.totalMinutes)
    }

    @Test
    fun atomicExportReplacesTheWholeFileAndCleansUpTemporaryFiles() {
        val target = File(folder.root, "backup.json")
        target.writeText("old backup")
        val content = "{\"tasks\":[],\"notes\":\"中文 🌿\"}"
        DesktopBackupFiles.write(target, content)
        assertEquals(content, target.readText(Charsets.UTF_8))
        assertFalse(folder.root.listFiles()!!.any { it.name.startsWith(".anchor-backup-") })
        val directoryTarget = folder.newFolder("existing-directory")
        File(directoryTarget, "keep.txt").writeText("keep")
        assertTrue(runCatching { DesktopBackupFiles.write(directoryTarget, content) }.isFailure)
        assertEquals("keep", File(directoryTarget, "keep.txt").readText())
        assertFalse(folder.root.listFiles()!!.any { it.name.startsWith(".anchor-backup-") })
    }
}
