package com.anchor.adhd.desktop

import com.anchor.adhd.desktop.ai.DesktopRoutingSession
import com.anchor.adhd.desktop.ai.SmartRouteResult
import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.sql.DriverManager
import java.util.UUID

class DesktopRouterTaskSaveTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var file: File
    private lateinit var db: AnchorDesktopDatabase

    @Before fun initialize() = runBlocking {
        file = temporary.newFile("router.db")
        db = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), customDbFile = file)
        db.initialize()
    }

    @Test fun failureOnSecondTaskRollsBackTheEntireListAndAcknowledgements() = runBlocking {
        val initial = db.tasks.value.toList()
        sql("CREATE TRIGGER reject_second BEFORE INSERT ON tasks WHEN NEW.title = 'Second fixture' BEGIN SELECT RAISE(ABORT, 'Second task failure'); END;")
        val key = UUID.randomUUID().toString()
        val titles = listOf("First fixture", "Second fixture", "Third fixture")
        try {
            db.saveRouterTasks(key, titles)
            fail("Expected list-save failure")
        } catch (_: java.sql.SQLException) { }
        assertEquals(initial, db.tasks.value)
        assertEquals(initial.size, count("tasks"))
        assertEquals(0, count("task_captures"))
        sql("DROP TRIGGER reject_second;")
        db.saveRouterTasks(key, titles)
        titles.forEach { title -> assertEquals(15, db.tasks.value.single { it.title == title }.durationMinutes) }
        assertEquals(initial.size + 3, count("tasks"))
        assertEquals(3, count("task_captures"))
    }

    @Test fun acknowledgementFailureAfterSecondInsertionAlsoRollsBackBothTables() = runBlocking {
        val initial = db.tasks.value.size
        sql("CREATE TRIGGER reject_ack BEFORE INSERT ON task_captures WHEN NEW.title = 'Second fixture' BEGIN SELECT RAISE(ABORT, 'Acknowledgement failure'); END;")
        try {
            db.saveRouterTasks(UUID.randomUUID().toString(), listOf("First fixture", "Second fixture"))
            fail("Expected acknowledgement failure")
        } catch (_: java.sql.SQLException) { }
        assertEquals(initial, count("tasks"))
        assertEquals(initial, db.tasks.value.size)
        assertEquals(0, count("task_captures"))
    }

    @Test fun actionRetryAfterCommitAndFocusPreparationFailureDoesNotDuplicateTasks() = runBlocking {
        val titles = listOf("First fixture", "Second fixture", "Third fixture")
        val router = DesktopRoutingSession(this, { SmartRouteResult.BrainDump(titles, titles.first()) })
        router.edit("Fixture list")
        router.submit()
        val id = router.state.value.resultId
        var attempts = 0
        suspend fun saveAndPrepare(key: String) {
            db.saveRouterTasks(key, titles)
            if (++attempts == 1) throw IOException("Committed save; focus preparation unavailable")
            assertNotNull(db.upsertAndMakeTaskNow(titles.first(), 25))
        }
        router.performAction(id, "Preparing focus", action = ::saveAndPrepare)
        withTimeout(5000) { router.state.first { !it.isActing } }
        assertNotNull(router.state.value.result)
        assertTrue(router.state.value.isActionError)
        titles.forEach { title -> assertEquals(1, db.tasks.value.count { it.title == title }) }
        router.performAction(id, "Preparing focus", action = ::saveAndPrepare)
        withTimeout(5000) { router.state.first { !it.isActing } }
        assertNull(router.state.value.result)
        assertFalse(router.state.value.isActionError)
        titles.forEach { title -> assertEquals(1, db.tasks.value.count { it.title == title }) }
        assertEquals(3, count("task_captures"))
        val primary = db.tasks.value.single { it.title == titles.first() }
        assertEquals(25, primary.durationMinutes)
        assertTrue(primary.isNextAction)
    }

    @Test fun alternateActionsAndReorderingReuseItemsFromOnePreview() = runBlocking {
        val key = UUID.randomUUID().toString()
        db.saveRouterTasks(key, listOf("Second fixture", "Third fixture"))
        db.saveRouterTasks(key, listOf("First fixture", "Second fixture", "Third fixture"))
        db.saveRouterTasks(key, listOf("Third fixture", "Second fixture", "First fixture"))
        listOf("First fixture", "Second fixture", "Third fixture").forEach { title ->
            assertEquals(1, db.tasks.value.count { it.title == title })
        }
        assertEquals(3, count("task_captures"))
    }

    @Test fun duplicateItemsRemainDistinctAndDeliberateNewPreviewCreatesFreshTasks() = runBlocking {
        val key = UUID.randomUUID().toString()
        val titles = listOf("Duplicate fixture", "Duplicate fixture")
        db.saveRouterTasks(key, titles)
        db.saveRouterTasks(key, titles)
        assertEquals(2, db.tasks.value.count { it.title == "Duplicate fixture" })
        db.saveRouterTasks(UUID.randomUUID().toString(), titles)
        assertEquals(4, db.tasks.value.count { it.title == "Duplicate fixture" })
        assertEquals(4, count("task_captures"))
    }

    @Test fun reopenRetainsIdentityAndReplacementCleansPrivateMetadata() = runBlocking {
        val key = UUID.randomUUID().toString()
        val title = "Don't forget \"the outline\" in C:\\work"
        db.saveRouterTasks(key, listOf("  $title  ", " "))
        val reopened = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), customDbFile = file)
        reopened.initialize()
        reopened.saveRouterTasks(key, listOf(title))
        assertEquals(1, reopened.tasks.value.count { it.title == title })
        val backup = reopened.exportDataJson()
        assertFalse(backup.contains("captureKey"))
        reopened.importDataJson(backup, replaceExisting = true)
        assertEquals(0, count("task_captures"))
        assertEquals(1, reopened.tasks.value.count { it.title == title })
    }

    @Test fun invalidIdentityAndEmptyListsCannotWriteTasks() = runBlocking {
        val initial = db.tasks.value.toList()
        try {
            db.saveRouterTasks("invalid", listOf("Wrong fixture"))
            fail("Expected identity rejection")
        } catch (_: IllegalArgumentException) { }
        db.saveRouterTasks(UUID.randomUUID().toString(), listOf(" ", "\n"))
        assertEquals(initial, db.tasks.value)
        assertEquals(initial.size, count("tasks"))
        assertEquals(0, count("task_captures"))
    }

    @Test fun focusPreparationFindsCommittedTasksEvenWhenThePublishedSnapshotIsStale() = runBlocking {
        sql("INSERT INTO tasks (title, durationMinutes, createdAtMillis, sortOrder) VALUES ('Unpublished fixture', 15, 1, 0);")
        assertFalse(db.tasks.value.any { it.title == "Unpublished fixture" })
        val target = db.upsertAndMakeTaskNow("unpublished fixture", 25)!!
        assertEquals("Unpublished fixture", target.title)
        assertEquals(25, target.durationMinutes)
        assertTrue(target.isNextAction)
        assertEquals(1, db.tasks.value.count { it.title.equals("Unpublished fixture", ignoreCase = true) })
    }

    @Test fun failedFocusPromotionRollsBackANewTaskAndPreservesNowSelection() = runBlocking {
        val initial = db.tasks.value.toList()
        sql("CREATE TRIGGER reject_promotion BEFORE UPDATE ON tasks WHEN NEW.title = 'Focus fixture' AND NEW.isNextAction = 1 BEGIN SELECT RAISE(ABORT, 'Focus promotion failure'); END;")
        try {
            db.upsertAndMakeTaskNow("Focus fixture", 25)
            fail("Expected promotion failure")
        } catch (_: java.sql.SQLException) { }
        assertEquals(initial, db.tasks.value)
        assertEquals(initial.size, count("tasks"))
        sql("DROP TRIGGER reject_promotion;")
        assertTrue(db.upsertAndMakeTaskNow("Focus fixture", 25)!!.isNextAction)
        assertEquals(initial.size + 1, count("tasks"))
    }

    @Test fun failedFocusPromotionAlsoRollsBackAnExistingTasksDuration() = runBlocking {
        db.insertTask("Focus fixture", 15)
        val initial = db.tasks.value.toList()
        sql("CREATE TRIGGER reject_promotion BEFORE UPDATE ON tasks WHEN NEW.title = 'Focus fixture' AND NEW.isNextAction = 1 BEGIN SELECT RAISE(ABORT, 'Focus promotion failure'); END;")
        try {
            db.upsertAndMakeTaskNow("Focus fixture", 25)
            fail("Expected promotion failure")
        } catch (_: java.sql.SQLException) { }
        assertEquals(initial, db.tasks.value)
        sql("DROP TRIGGER reject_promotion;")
        assertEquals(15, db.upsertAndMakeTaskNow("Focus fixture", 15)!!.durationMinutes)
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
