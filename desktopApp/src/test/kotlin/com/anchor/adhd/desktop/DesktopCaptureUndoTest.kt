package com.anchor.adhd.desktop

import com.anchor.adhd.desktop.db.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.sql.DriverManager
import java.util.UUID

class DesktopCaptureUndoTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var file: File
    private lateinit var db: AnchorDesktopDatabase

    @Before fun initialize() = runBlocking {
        file = temporary.newFile("capture-undo.db")
        db = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), customDbFile = file)
        db.initialize()
    }

    @Test fun undoRestoresTheCaptureIdentitySoAnOriginalRetryReusesTheExactTask() = runBlocking {
        val key = UUID.randomUUID().toString()
        db.captureThought(key, "Undo this captured milestone", 13)
        val saved = db.tasks.value.single { it.title == "Undo this captured milestone" }
        val deleted = db.deleteTask(saved.id)
        assertEquals(0, count("task_captures"))
        db.restoreDeletedTasks(deleted)
        val reopened = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), customDbFile = file)
        reopened.initialize()
        reopened.captureThought(key, "Undo this captured milestone", 27)
        assertEquals(listOf(saved), reopened.tasks.value.filter { it.title == saved.title })
        assertEquals(1, count("task_captures"))
    }

    @Test fun renamedTaskUndoKeepsItsOriginalCaptureTextAndDoesNotStealNow() = runBlocking {
        val key = UUID.randomUUID().toString()
        db.captureThought(key, "Original capture", 13)
        val id = db.tasks.value.single { it.title == "Original capture" }.id
        db.updateTaskDetails(id, "Revised milestone", 37)
        val original = db.makeTaskNow(id)
        val deleted = db.deleteTask(id)
        val newNow = db.makeTaskNow(db.tasks.value.first().id)
        db.restoreDeletedTasks(deleted)
        db.captureThought(key, "Original capture", 13)
        assertEquals(original.copy(isNextAction = false), db.tasks.value.single { it.id == id })
        assertEquals(newNow.id, db.tasks.value.single { it.isNextAction }.id)
        assertFalse(db.tasks.value.any { it.title == "Original capture" })
        assertEquals(1, count("task_captures"))
    }

    @Test fun completedBatchUndoRestoresDistinctIdenticalCaptureOccurrences() = runBlocking {
        val keys = List(2) { UUID.randomUUID().toString() }
        keys.forEachIndexed { index, key -> db.captureThought(key, "Repeated capture", 5 + index * 8) }
        val saved = db.tasks.value.filter { it.title == "Repeated capture" }
        saved.forEach { db.setTaskCompleted(it.id, true) }
        val deletion = db.deleteCompletedTasks()
        assertEquals(2, deletion.captures.size)
        assertEquals(0, count("task_captures"))
        db.restoreDeletedTasks(deletion)
        keys.forEach { db.captureThought(it, "Repeated capture", 90) }
        assertEquals(saved.map { it.copy(isCompleted = true) }.toSet(), db.tasks.value.filter { it.title == "Repeated capture" }.toSet())
        assertEquals(2, count("task_captures"))
        val exported = db.exportDataJson()
        keys.forEach { assertFalse(exported.contains(it)) }
    }

    @Test fun failedLaterCaptureRestoreRollsBackTasksAndEarlierIdentitiesAndRetriesTheSameBatch() = runBlocking {
        val keys = List(2) { UUID.randomUUID().toString() }
        listOf("First undo fixture", "Second undo fixture").forEachIndexed { index, title -> db.captureThought(keys[index], title, 13) }
        val tasks = db.tasks.value.filter { it.title.endsWith("undo fixture") }
        tasks.forEach { db.setTaskCompleted(it.id, true) }
        val deletion = db.deleteCompletedTasks()
        val remaining = db.tasks.value.toList()
        for (mode in listOf("ABORT, 'Undo capture fixture'", "IGNORE")) {
            sql("CREATE TRIGGER reject_undo_capture BEFORE INSERT ON task_captures WHEN NEW.title='Second undo fixture' BEGIN SELECT RAISE($mode); END")
            assertTrue(runCatching { db.restoreDeletedTasks(deletion) }.isFailure)
            assertEquals(remaining, db.tasks.value)
            assertEquals(remaining.size, count("tasks"))
            assertEquals(0, count("task_captures"))
            sql("DROP TRIGGER reject_undo_capture")
        }
        db.restoreDeletedTasks(deletion)
        assertEquals(remaining.size + 2, count("tasks"))
        assertEquals(2, count("task_captures"))
        keys.forEachIndexed { index, key -> db.captureThought(key, listOf("First undo fixture", "Second undo fixture")[index], 90) }
        assertEquals(remaining.size + 2, count("tasks"))
    }

    @Test fun captureKeyReusedAfterDeletionCannotBeOverwrittenByUndo() = runBlocking {
        val key = UUID.randomUUID().toString()
        db.captureThought(key, "Recaptured task", 13)
        val original = db.tasks.value.single { it.title == "Recaptured task" }
        val deleted = db.deleteTask(original.id)
        db.captureThought(key, "Recaptured task", 27)
        val replacement = db.tasks.value.single { it.title == "Recaptured task" }
        assertNotEquals(original.id, replacement.id)
        assertTrue(runCatching { db.restoreDeletedTasks(deleted) }.isFailure)
        assertEquals(listOf(replacement), db.tasks.value.filter { it.title == "Recaptured task" })
        assertEquals(1, count("task_captures"))
        db.captureThought(key, "Recaptured task", 13)
        assertEquals(replacement, db.tasks.value.single { it.title == "Recaptured task" })
    }

    @Test fun identitiesOutsideTheDeletionBatchAreRejectedWithoutRestoringRows() = runBlocking {
        val key = UUID.randomUUID().toString()
        db.captureThought(key, "Scoped undo", 13)
        val target = db.tasks.value.single { it.title == "Scoped undo" }
        val deleted = db.deleteTask(target.id)
        val other = db.tasks.value.first()
        val invalid = deleted.copy(captures = deleted.captures.map { it.copy(taskId = other.id) })
        assertTrue(runCatching { db.restoreDeletedTasks(invalid) }.isFailure)
        assertFalse(db.tasks.value.any { it.id == target.id })
        assertEquals(0, count("task_captures"))
    }

    private fun sql(statement: String) {
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { conn -> conn.createStatement().use { it.execute(statement) } }
    }

    private fun count(table: String): Int = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { conn ->
        conn.createStatement().use { stmt -> stmt.executeQuery("SELECT COUNT(*) FROM $table").use { it.next(); it.getInt(1) } }
    }
}
