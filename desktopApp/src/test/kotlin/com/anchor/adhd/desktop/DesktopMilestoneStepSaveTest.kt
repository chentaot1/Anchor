package com.anchor.adhd.desktop

import com.anchor.adhd.desktop.db.*
import com.anchor.adhd.desktop.ai.DesktopMilestoneSteps
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.sql.DriverManager
import java.util.UUID
import java.io.IOException

class DesktopMilestoneStepSaveTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var file: File
    private lateinit var db: AnchorDesktopDatabase
    private lateinit var source: DesktopTask
    @Before fun initialize() = runBlocking {
        file = temporary.newFile("milestones.db")
        db = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), customDbFile = file)
        db.initialize()
        source = db.tasks.value.first()
    }
    private fun request(start: Boolean = false, steps: List<String> = listOf("First fixture", "Second fixture", "Fifth fixture")) =
        DesktopMilestoneStepSave(UUID.randomUUID().toString(), source.id, source.title, steps, start)

    @Test fun failedLaterStepRollsBackEntireListAndAcknowledgements() = runBlocking {
        val initial = db.tasks.value.toList()
        sql("CREATE TRIGGER reject_step BEFORE INSERT ON tasks WHEN NEW.title='Second fixture' BEGIN SELECT RAISE(ABORT,'Step failure'); END")
        val request = request()
        expectFailure { db.saveMilestoneSteps(request) }
        assertEquals(initial, db.tasks.value)
        assertEquals(initial.size, count("tasks"))
        assertEquals(0, count("task_captures"))
        sql("DROP TRIGGER reject_step")
        assertNull(db.saveMilestoneSteps(request))
        assertEquals(initial.size + 3, count("tasks"))
        assertEquals(3, count("task_captures"))
        assertTrue(db.tasks.value.filter { it.title.endsWith("fixture") }.all { it.durationMinutes == 5 })
    }

    @Test fun startFirstSelectsTheCapturedOccurrenceAndAlternateActionsReuseIt() = runBlocking {
        db.insertTask("Same fixture", 90)
        val old = db.tasks.value.single { it.title == "Same fixture" }
        val initial = db.tasks.value.size
        val request = request(true, listOf("Same fixture", "Same fixture", "Other fixture"))
        val selected = db.saveMilestoneSteps(request)!!
        assertNotEquals(old.id, selected.id)
        assertEquals(5, selected.durationMinutes)
        assertTrue(selected.isNextAction)
        assertEquals(initial + 1, count("tasks"))
        assertEquals(1, count("task_captures"))
        // A lost acknowledgement and later Add All share the first step's exact identity.
        assertEquals(selected.id, db.saveMilestoneSteps(request)!!.id)
        db.saveMilestoneSteps(request.copy(startFirst = false))
        assertEquals(initial + 3, count("tasks"))
        assertEquals(3, count("task_captures"))
        assertEquals(selected.id, db.tasks.value.single { it.isNextAction }.id)
        assertEquals(3, db.tasks.value.count { it.title == "Same fixture" })
        assertEquals(90, db.tasks.value.single { it.id == old.id }.durationMinutes)
    }

    @Test fun failedNowSelectionRollsBackFirstStepAndExistingNowTask() = runBlocking {
        db.makeTaskNow(source.id)
        val initial = db.tasks.value.toList()
        sql("CREATE TRIGGER reject_step_now BEFORE UPDATE ON tasks WHEN NEW.title='First fixture' AND NEW.isNextAction=1 BEGIN SELECT RAISE(ABORT,'NOW failure'); END")
        expectFailure { db.saveMilestoneSteps(request(true)) }
        assertEquals(initial, db.tasks.value)
        assertEquals(initial.size, count("tasks"))
        assertEquals(0, count("task_captures"))
    }

    @Test fun deletedCompletedOrRenamedSourcesCannotSaveStalePreviews() = runBlocking {
        val request = request()
        db.toggleTaskCompleted(source.id)
        expectFailure { db.saveMilestoneSteps(request) }
        db.toggleTaskCompleted(source.id)
        sql("UPDATE tasks SET title='Changed source' WHERE id=${source.id}")
        expectFailure { db.saveMilestoneSteps(request) }
        db.deleteTask(source.id)
        expectFailure { db.saveMilestoneSteps(request) }
        assertEquals(0, count("task_captures"))
        assertFalse(db.tasks.value.any { it.title.endsWith("fixture") })
    }

    @Test fun controllerRetriesAnAmbiguousCommittedBatchWithoutDuplicateSteps() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var attempts = 0
        val initial = db.tasks.value.size
        val controller = DesktopMilestoneSteps(owner, quick = { listOf("First fixture", "Second fixture") }, canRefine = { false }) {
            val selected = db.saveMilestoneSteps(it)
            if (++attempts == 1) throw IOException("Acknowledgement lost after commit")
            selected
        }
        controller.toggle(source)
        val key = controller.state.value.previews[source.id]!!.key
        controller.savePreview(key, false)
        withTimeout(5000) { while (controller.isSaving) yield() }
        assertTrue(controller.state.value.isError)
        val saved = db.tasks.value.filter { it.title.endsWith("fixture") }.map { it.id }.toSet()
        assertEquals(initial + 2, count("tasks"))
        controller.savePreview(key, false)
        withTimeout(5000) { while (controller.isSaving) yield() }
        assertFalse(controller.state.value.isError)
        assertEquals(saved, db.tasks.value.filter { it.title.endsWith("fixture") }.map { it.id }.toSet())
        assertEquals(initial + 2, count("tasks"))
        assertEquals(2, count("task_captures"))
        owner.cancel()
    }

    private suspend fun expectFailure(action: suspend () -> Unit) {
        try { action(); fail("Expected rejected transaction") } catch (_: java.sql.SQLException) { } catch (_: IllegalStateException) { }
    }
    private fun sql(statement: String) = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { conn -> conn.createStatement().use { it.execute(statement) } }
    private fun count(table: String): Int = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { conn ->
        conn.createStatement().use { stmt -> stmt.executeQuery("SELECT COUNT(*) FROM $table").use { it.next(); it.getInt(1) } }
    }
}
