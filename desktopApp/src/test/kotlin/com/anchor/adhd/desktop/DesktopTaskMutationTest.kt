package com.anchor.adhd.desktop

import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import com.anchor.adhd.desktop.db.DesktopTaskDeletion
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.sql.DriverManager
import java.util.UUID

class DesktopTaskMutationTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var file: File
    private lateinit var db: AnchorDesktopDatabase

    @Before fun initialize() = runBlocking {
        file = temporary.newFile("task-mutations.db")
        db = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), customDbFile = file)
        db.initialize()
    }

    @Test fun explicitCompletionIsIdempotentUsesFreshMetadataAndRejectsMissingTasks() = runBlocking {
        val target = db.tasks.value.first()
        sql("UPDATE tasks SET title='Fresh title', durationMinutes=37 WHERE id=${target.id}")
        assertNotEquals("Fresh title", db.tasks.value.first { it.id == target.id }.title)
        val saved = db.setTaskCompleted(target.id, true)
        assertEquals("Fresh title", saved.title)
        assertEquals(37, saved.durationMinutes)
        assertTrue(saved.isCompleted)
        assertEquals(saved, db.setTaskCompleted(target.id, true))
        assertEquals(saved.copy(isCompleted = false), db.setTaskCompleted(target.id, false))
        db.deleteTask(target.id)
        assertTrue(runCatching { db.setTaskCompleted(target.id, true) }.isFailure)
    }

    @Test fun rejectedAndIgnoredCompletionNeverPublishSuccess() = runBlocking {
        val target = db.tasks.value.first()
        val original = db.tasks.value.toList()
        for (mode in listOf("ABORT, 'Completion fixture'", "IGNORE")) {
            sql("CREATE TRIGGER reject_complete BEFORE UPDATE ON tasks WHEN NEW.id=${target.id} BEGIN SELECT RAISE($mode); END")
            assertTrue(runCatching { db.setTaskCompleted(target.id, true) }.isFailure)
            assertEquals(original, db.tasks.value)
            assertEquals(0, number("SELECT isCompleted FROM tasks WHERE id=${target.id}"))
            sql("DROP TRIGGER reject_complete")
        }
        assertTrue(db.setTaskCompleted(target.id, true).isCompleted)
    }

    @Test fun partialIgnoredDeletionRollsBackRowsAcknowledgementsAndPublishedList() = runBlocking {
        db.captureThought(UUID.randomUUID().toString(), "First completed fixture")
        db.captureThought(UUID.randomUUID().toString(), "Second completed fixture")
        val targets = db.tasks.value.filter { it.title.endsWith("completed fixture") }
        targets.forEach { db.setTaskCompleted(it.id, true) }
        val original = db.tasks.value.toList()
        sql("CREATE TRIGGER ignore_delete BEFORE DELETE ON tasks WHEN OLD.id=${targets.last().id} BEGIN SELECT RAISE(IGNORE); END")
        assertTrue(runCatching { db.deleteCompletedTasks() }.isFailure)
        assertEquals(original, db.tasks.value)
        assertEquals(original.size, number("SELECT COUNT(*) FROM tasks"))
        assertEquals(2, number("SELECT COUNT(*) FROM task_captures"))
        sql("DROP TRIGGER ignore_delete")
        val deleted = db.deleteCompletedTasks()
        assertEquals(targets.map { it.copy(isCompleted = true) }.toSet(), deleted.tasks.toSet())
        assertEquals(0, number("SELECT COUNT(*) FROM task_captures"))
        assertFalse(db.tasks.value.any { it.id in targets.map { task -> task.id } })
    }

    @Test fun ignoredLaterUndoInsertionRollsBackTheWholeRestoreAndAllowsRetry() = runBlocking {
        val targets = db.tasks.value.take(2)
        val batches = targets.map { db.deleteTask(it.id) }
        val deleted = DesktopTaskDeletion(batches.flatMap { it.tasks }, batches.flatMap { it.captures })
        val remaining = db.tasks.value.toList()
        sql("CREATE TRIGGER ignore_restore BEFORE INSERT ON tasks WHEN NEW.id=${deleted.tasks.last().id} BEGIN SELECT RAISE(IGNORE); END")
        assertTrue(runCatching { db.restoreDeletedTasks(deleted) }.isFailure)
        assertEquals(remaining, db.tasks.value)
        assertEquals(0, number("SELECT COUNT(*) FROM tasks WHERE id IN (${deleted.tasks.joinToString { it.id.toString() }})"))
        sql("DROP TRIGGER ignore_restore")
        db.restoreDeletedTasks(deleted)
        assertEquals(deleted.tasks.toSet(), db.tasks.value.filter { it.id in deleted.tasks.map { task -> task.id } }.toSet())
    }

    private fun sql(statement: String) {
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { conn ->
            conn.createStatement().use { it.execute(statement) }
        }
    }
    private fun number(statement: String): Int = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { conn ->
        conn.createStatement().use { stmt -> stmt.executeQuery(statement).use { it.next(); it.getInt(1) } }
    }
}
