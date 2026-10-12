package com.anchor.adhd.desktop

import com.anchor.adhd.desktop.ai.DesktopAirlockMode
import com.anchor.adhd.desktop.ai.DesktopAirlockSession
import com.anchor.adhd.desktop.db.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.sql.DriverManager
import java.util.UUID

class DesktopAirlockSaveTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var file: File
    private lateinit var db: AnchorDesktopDatabase

    @Before fun initialize() = runBlocking {
        file = temporary.newFile("airlock.db")
        db = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), customDbFile = file)
        db.initialize()
    }

    @Test fun failedSecondItemRollsBackWholeBatchAndRetrySelectsTheFiveMinuteStep() = runBlocking {
        val initial = db.tasks.value.toList()
        sql("CREATE TRIGGER reject_airlock BEFORE INSERT ON tasks WHEN NEW.title='Second fixture' BEGIN SELECT RAISE(ABORT,'Fixture failure'); END;")
        val request = DesktopAirlockSave(UUID.randomUUID().toString(),
            listOf(DesktopAirlockTask("First fixture", 5), DesktopAirlockTask("Second fixture", 5), DesktopAirlockTask("Other thought", 15)), 0)
        try { db.saveAirlockTasks(request); fail("Expected failure") } catch (_: java.sql.SQLException) { }
        assertEquals(initial, db.tasks.value)
        assertEquals(initial.size, count("tasks"))
        assertEquals(0, count("task_captures"))
        sql("DROP TRIGGER reject_airlock;")
        val selected = db.saveAirlockTasks(request)!!
        assertEquals("First fixture", selected.title)
        assertEquals(5, selected.durationMinutes)
        assertTrue(selected.isNextAction)
        assertEquals(15, db.tasks.value.single { it.title == "Other thought" }.durationMinutes)
        assertEquals(3, count("task_captures"))
    }

    @Test fun failedNowPromotionRollsBackTasksAcknowledgementsAndPreviousSelection() = runBlocking {
        db.makeTaskNow(db.tasks.value.first().id)
        val initial = db.tasks.value.toList()
        sql("CREATE TRIGGER reject_airlock_now BEFORE UPDATE ON tasks WHEN NEW.title='Primary fixture' AND NEW.isNextAction=1 BEGIN SELECT RAISE(ABORT,'NOW failure'); END;")
        val request = DesktopAirlockSave(UUID.randomUUID().toString(), listOf(DesktopAirlockTask("Primary fixture", 5), DesktopAirlockTask("Secondary fixture", 15)), 0)
        try { db.saveAirlockTasks(request); fail("Expected failure") } catch (_: java.sql.SQLException) { }
        assertEquals(initial, db.tasks.value)
        assertEquals(initial.size, count("tasks"))
        assertEquals(0, count("task_captures"))
    }

    @Test fun repeatedIdenticalItemsRemainDistinctAndAlternateActionsReuseExactOccurrences() = runBlocking {
        val initial = db.tasks.value.size
        val request = DesktopAirlockSave(UUID.randomUUID().toString(), listOf(DesktopAirlockTask("Same fixture", 5), DesktopAirlockTask("Same fixture", 5)))
        assertNull(db.saveAirlockTasks(request))
        val second = db.saveAirlockTasks(request.copy(anchorIndex = 1))!!
        val first = db.saveAirlockTasks(request.copy(anchorIndex = 0))!!
        assertNotEquals(first.id, second.id)
        assertEquals(first.id, db.tasks.value.single { it.isNextAction }.id)
        assertEquals(initial + 2, count("tasks"))
        assertEquals(2, count("task_captures"))
        assertEquals(first.id, db.saveAirlockTasks(request.copy(anchorIndex = 0))!!.id)
        assertEquals(initial + 2, count("tasks"))
    }

    @Test fun controllerRetryAfterACommittedSaveDoesNotDuplicateTheBatch() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val initial = db.tasks.value.size
        var attempts = 0
        val session = DesktopAirlockSession(scope, quick = { _, _ -> listOf("Primary fixture", "Secondary fixture") },
            canRefine = { false }, save = { request -> db.saveAirlockTasks(request); if (++attempts == 1) throw IOException("Acknowledgement lost") })
        session.open(); session.edit("Raw fixture"); session.generate(DesktopAirlockMode.STEPS)
        val key = session.state.value.preview!!.key
        session.savePreview(key, 0)
        withTimeout(5000) { while (session.isSaving) yield() }
        assertTrue(session.state.value.isOpen)
        assertEquals(initial + 2, count("tasks"))
        val first = db.tasks.value.single { it.title == "Primary fixture" }
        session.savePreview(key, 0)
        withTimeout(5000) { while (session.isSaving) yield() }
        assertFalse(session.state.value.isOpen)
        assertEquals(initial + 2, count("tasks"))
        assertEquals(first.id, db.tasks.value.single { it.title == "Primary fixture" }.id)
        assertEquals(2, count("task_captures"))
        scope.cancel()
    }

    private fun sql(statement: String) = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { conn ->
        conn.createStatement().use { it.execute(statement) }
    }
    private fun count(table: String): Int = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { conn ->
        conn.createStatement().use { stmt -> stmt.executeQuery("SELECT COUNT(*) FROM $table").use { it.next(); it.getInt(1) } }
    }
}
