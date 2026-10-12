package com.anchor.adhd.desktop

import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import com.anchor.adhd.desktop.db.DesktopThoughtCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.sql.DriverManager
import java.util.UUID

class DesktopThoughtCaptureDatabaseTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var file: File
    private lateinit var db: AnchorDesktopDatabase

    @Before fun initialize() = runBlocking {
        file = temporary.newFile("capture.db")
        db = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), customDbFile = file)
        db.initialize()
    }

    @Test fun milestoneDurationSurvivesRollbackAndAmbiguousCommitRetry() = runBlocking {
        val initial = db.tasks.value.toList()
        val key = UUID.randomUUID().toString()
        sql("CREATE TRIGGER reject_milestone BEFORE INSERT ON task_captures BEGIN SELECT RAISE(ABORT, 'Milestone acknowledgement fixture'); END;")
        try {
            db.captureThought(key, "A thirteen minute milestone", 13)
            fail("Expected transaction failure")
        } catch (_: java.sql.SQLException) { }
        assertEquals(initial, db.tasks.value)
        assertEquals(initial.size, count("tasks"))
        assertEquals(0, count("task_captures"))
        sql("DROP TRIGGER reject_milestone;")
        db.captureThought(key, "A thirteen minute milestone", 13)
        val saved = db.tasks.value.single { it.title == "A thirteen minute milestone" }
        assertEquals(13, saved.durationMinutes)
        val reopened = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), customDbFile = file)
        reopened.initialize()
        reopened.captureThought(key, "A thirteen minute milestone", 27)
        assertEquals(initial.size + 1, count("tasks"))
        assertEquals(1, count("task_captures"))
        assertEquals(saved, reopened.tasks.value.single { it.id == saved.id })
    }

    @Test fun manualCaptureAcceptsWindowsBoundsAndRejectsInvalidDurationsWithoutWrites() = runBlocking {
        val initial = count("tasks")
        for (minutes in listOf(0, 241, Int.MAX_VALUE)) {
            try {
                db.captureThought(UUID.randomUUID().toString(), "Invalid duration", minutes)
                fail("Expected invalid capture duration")
            } catch (_: IllegalArgumentException) { }
        }
        assertEquals(initial, count("tasks"))
        assertEquals(0, count("task_captures"))
        db.captureThought(UUID.randomUUID().toString(), "One minute", 1)
        db.captureThought(UUID.randomUUID().toString(), "Four hours", 240)
        assertEquals(1, db.tasks.value.single { it.title == "One minute" }.durationMinutes)
        assertEquals(240, db.tasks.value.single { it.title == "Four hours" }.durationMinutes)
    }

    @Test fun retryingAfterALostCommitAcknowledgementCreatesOneTask() = runBlocking {
        val initial = db.tasks.value.size
        var attempts = 0
        val capture = DesktopThoughtCapture(this) {
            db.captureThought(it.key, it.text)
            if (++attempts == 1) throw IOException("Commit succeeded but acknowledgement was lost")
        }
        capture.edit("  Remember the outline  ")
        assertTrue(capture.submit())
        // Wait for the actual IO-backed save, without depending on a test dispatcher.
        kotlinx.coroutines.withTimeout(5000) { while (capture.isSaving) kotlinx.coroutines.yield() }
        assertTrue(capture.state.value.isError)
        assertEquals("  Remember the outline  ", capture.state.value.draft)
        assertEquals(initial + 1, db.tasks.value.size)
        assertTrue(capture.submit())
        kotlinx.coroutines.withTimeout(5000) { while (capture.isSaving) kotlinx.coroutines.yield() }
        assertEquals(initial + 1, db.tasks.value.size)
        val task = db.tasks.value.single { it.title == "Remember the outline" }
        assertEquals(5, task.durationMinutes)
        assertEquals(1, count("task_captures"))
        assertEquals("", capture.state.value.draft)
        assertFalse(capture.state.value.isError)
    }

    @Test fun deliberateIdenticalThoughtsHaveSeparateIdentitiesAndPersistAcrossReopen() = runBlocking {
        val first = UUID.randomUUID().toString()
        val second = UUID.randomUUID().toString()
        db.captureThought(first, "Same thought")
        db.captureThought(second, "Same thought")
        val reopened = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), customDbFile = file)
        reopened.initialize()
        reopened.captureThought(first, "Same thought")
        assertEquals(2, reopened.tasks.value.count { it.title == "Same thought" })
        assertEquals(2, count("task_captures"))
    }

    @Test fun failureBetweenTaskAndAcknowledgementRollsBackBothAndRetryWorks() = runBlocking {
        val initial = db.tasks.value.toList()
        sql("CREATE TRIGGER reject_capture BEFORE INSERT ON task_captures BEGIN SELECT RAISE(ABORT, 'Disk write fixture'); END;")
        val key = UUID.randomUUID().toString()
        try {
            db.captureThought(key, "Keep this draft")
            fail("Expected transaction failure")
        } catch (_: java.sql.SQLException) { }
        assertEquals(initial, db.tasks.value)
        assertEquals(initial.size, count("tasks"))
        assertEquals(0, count("task_captures"))
        sql("DROP TRIGGER reject_capture;")
        db.captureThought(key, "Keep this draft")
        assertEquals(initial.size + 1, db.tasks.value.size)
        assertEquals(1, count("task_captures"))
    }

    @Test fun identitiesCannotBeReusedWithDifferentTextOrInvalidInput() = runBlocking {
        val key = UUID.randomUUID().toString()
        db.captureThought(key, "Original")
        for ((identity, text) in listOf(key to "Different", UUID.randomUUID().toString() to "  ", "bad-key" to "Original")) {
            try {
                db.captureThought(identity, text)
                fail("Expected validation failure")
            } catch (_: IllegalArgumentException) {
            } catch (_: IllegalStateException) { }
        }
        assertEquals(1, db.tasks.value.count { it.title == "Original" })
        assertEquals(0, db.tasks.value.count { it.title == "Different" })
        assertEquals(1, count("task_captures"))
    }

    @Test fun deletingOrReplacingTasksCleansPrivateAcknowledgements() = runBlocking {
        db.captureThought(UUID.randomUUID().toString(), "Delete this thought")
        db.deleteTask(db.tasks.value.single { it.title == "Delete this thought" }.id)
        assertEquals(0, count("task_captures"))
        db.captureThought(UUID.randomUUID().toString(), "Replace this thought")
        val backup = db.exportDataJson()
        assertFalse(backup.contains("task_captures"))
        assertFalse(backup.contains("captureKey"))
        db.importDataJson(backup, replaceExisting = true)
        assertEquals(0, count("task_captures"))
        assertEquals(1, db.tasks.value.count { it.title == "Replace this thought" })
        db.captureThought(UUID.randomUUID().toString(), "Reset this thought")
        db.clearAllTasks()
        assertEquals(0, count("task_captures"))
    }

    private fun sql(statement: String) {
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().use { it.execute(statement) }
        }
    }

    private fun count(table: String): Int = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM $table;").use { result -> result.next(); result.getInt(1) }
        }
    }
}
