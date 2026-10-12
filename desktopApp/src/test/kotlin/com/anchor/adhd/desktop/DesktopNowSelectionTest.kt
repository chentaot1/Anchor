package com.anchor.adhd.desktop

import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import com.anchor.adhd.desktop.db.DesktopTask
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.sql.DriverManager

class DesktopNowSelectionTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var file: File
    private lateinit var db: AnchorDesktopDatabase
    private lateinit var original: DesktopTask
    private lateinit var target: DesktopTask
    @Before fun initialize() = runBlocking {
        file = temporary.newFile("now.db")
        db = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), customDbFile = file)
        db.initialize()
        original = db.makeTaskNow(db.tasks.value.first().id)
        db.insertTask("Selection fixture", 35)
        target = db.tasks.value.single { it.title == "Selection fixture" }
    }

    @Test fun failedPromotionRollsBackPreviousNowAndOrderingThenRetryAcknowledgesExactTask() = runBlocking {
        val initial = db.tasks.value.toList()
        sql("CREATE TRIGGER reject_now BEFORE UPDATE ON tasks WHEN NEW.id=${target.id} AND NEW.isNextAction=1 BEGIN SELECT RAISE(ABORT,'NOW failure'); END")
        try {
            db.makeTaskNow(target.id)
            fail("Expected selection failure")
        } catch (failure: IllegalStateException) {
            assertEquals("Try selecting this task again.", failure.message)
            // Coroutine stack recovery can add another wrapper around the original cause.
            assertTrue(generateSequence<Throwable>(failure) { it.cause }.take(8).any { it is java.sql.SQLException })
        }
        assertEquals(initial, db.tasks.value)
        assertEquals(listOf(original.id), nowIds())
        assertEquals(initial.map { it.id to it.sortOrder }.sortedBy { it.first }, ordering())
        sql("DROP TRIGGER reject_now")
        val selected = db.makeTaskNow(target.id)
        assertEquals(target.id, selected.id)
        assertEquals(35, selected.durationMinutes)
        assertTrue(selected.isNextAction)
        assertEquals(listOf(target.id), nowIds())
        assertEquals(initial.size, db.tasks.value.size)
    }

    @Test fun missingCompletedAndDeletedTargetsCannotClearExistingNow() = runBlocking {
        expectFailure { db.makeTaskNow(Long.MAX_VALUE) }
        db.toggleTaskCompleted(target.id)
        expectFailure { db.makeTaskNow(target.id) }
        db.deleteTask(target.id)
        expectFailure { db.makeTaskNow(target.id) }
        assertEquals(listOf(original.id), nowIds())
        assertTrue(db.tasks.value.single { it.id == original.id }.isNextAction)
    }

    @Test fun promotionReturnsFreshCommittedMetadataForTheRequestedIdDespiteStalePublishedTasks() = runBlocking {
        db.insertTask("Selection fixture", 90)
        val other = db.tasks.value.single { it.title == "Selection fixture" && it.id != target.id }
        sql("UPDATE tasks SET title='Updated fixture',durationMinutes=45 WHERE id=${target.id}")
        assertEquals(35, db.tasks.value.single { it.id == target.id }.durationMinutes)
        val selected = db.makeTaskNow(target.id)
        assertEquals(target.id, selected.id)
        assertEquals("Updated fixture", selected.title)
        assertEquals(45, selected.durationMinutes)
        assertEquals(selected, db.tasks.value.single { it.id == selected.id })
        assertEquals(90, db.tasks.value.single { it.id == other.id }.durationMinutes)
        assertFalse(db.tasks.value.single { it.id == other.id }.isNextAction)
    }

    @Test fun silentlyIgnoredTargetUpdateCannotAcknowledgeSelectionOrCommitTheClearedNow() = runBlocking {
        val initial = db.tasks.value.toList()
        sql("CREATE TRIGGER ignore_now BEFORE UPDATE ON tasks WHEN NEW.id=${target.id} AND NEW.isNextAction=1 BEGIN SELECT RAISE(IGNORE); END")
        expectFailure { db.setNextActionTask(target.id) }
        assertEquals(initial, db.tasks.value)
        assertEquals(listOf(original.id), nowIds())
        assertEquals(initial.map { it.id to it.sortOrder }.sortedBy { it.first }, ordering())
    }

    @Test fun selectingNowFromAnImportedMinimumSortValueStaysAheadOfEveryOtherRank() = runBlocking {
        db.importDataJson("""{"tasks":[
            {"title":"Earliest imported task","sortOrder":-2147483648},
            {"title":"Later imported task","sortOrder":2147483647},
            {"title":"Select this imported task","durationMinutes":13,"sortOrder":0}
        ]}""", replaceExisting = true)
        val selectedId = db.tasks.value.single { it.title == "Select this imported task" }.id
        val selected = db.makeTaskNow(selectedId)
        assertTrue("NOW selection must remain ahead of the existing sort range",
            db.tasks.value.filter { it.id != selectedId }.all { selected.sortOrder < it.sortOrder })
        assertEquals(selectedId, db.tasks.value.first().id)
        assertEquals(13, selected.durationMinutes)
        assertEquals(listOf(selectedId), nowIds())
    }

    @Test fun rebasingPreservesEveryOtherTaskMetadataAndItsRelativeOrderIncludingTiesAndCompletedTasks() = runBlocking {
        sql("UPDATE tasks SET sortOrder=-2147483648, notes='Keep notes', durationMinutes=17 WHERE id=${original.id}")
        db.insertTask("Tie older", 7)
        val older = db.tasks.value.single { it.title == "Tie older" }
        db.insertTask("Tie newer", 9)
        val newer = db.tasks.value.single { it.title == "Tie newer" }
        db.setTaskCompleted(older.id, true)
        sql("UPDATE tasks SET sortOrder=2147483647 WHERE id=${target.id}")
        db = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), customDbFile = file)
        db.initialize()
        val before = db.tasks.value.associateBy { it.id }
        val baseOrder = before.values.filter { it.id != target.id }
            .sortedWith(compareBy<DesktopTask> { it.sortOrder }.thenByDescending { it.id }).map { it.id }
        db.makeTaskNow(target.id)
        val after = db.tasks.value.associateBy { it.id }
        assertEquals(before.keys, after.keys)
        for ((id, old) in before) {
            assertEquals(old.copy(sortOrder=after.getValue(id).sortOrder, isNextAction=id == target.id), after[id])
        }
        assertEquals(baseOrder, after.values.filter { it.id != target.id }.sortedBy { it.sortOrder }.map { it.id })
        assertTrue(after.getValue(newer.id).sortOrder < after.getValue(older.id).sortOrder)
        assertTrue(after.getValue(older.id).isCompleted)
        // A subsequent selection is ordinary and retains the rebased queue behind it.
        val second = db.makeTaskNow(original.id)
        assertEquals(original.id, db.tasks.value.first().id)
        assertTrue(second.sortOrder < db.tasks.value.filter { it.id != original.id }.minOf { it.sortOrder })
        assertEquals(listOf(original.id), nowIds())
        val reopened = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), customDbFile = file)
        reopened.initialize()
        assertEquals(db.tasks.value, reopened.tasks.value)
    }

    @Test fun rejectedIgnoredOrRewrittenRebaseRollsBackAllRanksAndTheOriginalNowThenRetryWorks() = runBlocking {
        sql("UPDATE tasks SET sortOrder=-2147483648 WHERE id=${original.id}")
        db.insertTask("Later rebase row", 7)
        val later = db.tasks.value.single { it.title == "Later rebase row" }
        db = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), customDbFile = file)
        db.initialize()
        val before = db.tasks.value.toList()
        for (trigger in listOf(
            "BEFORE UPDATE OF sortOrder ON tasks WHEN NEW.id=${later.id} BEGIN SELECT RAISE(ABORT,'Rebase rejected'); END",
            "BEFORE UPDATE OF sortOrder ON tasks WHEN NEW.id=${later.id} BEGIN SELECT RAISE(IGNORE); END",
            "AFTER UPDATE OF sortOrder ON tasks WHEN NEW.id=${later.id} BEGIN UPDATE tasks SET sortOrder=999 WHERE id=NEW.id; END",
        )) {
            sql("CREATE TRIGGER reject_rebase $trigger")
            expectFailure { db.makeTaskNow(target.id) }
            assertEquals(before, db.tasks.value)
            assertEquals(before.map { it.id to it.sortOrder }.sortedBy { it.first }, ordering())
            assertEquals(listOf(original.id), nowIds())
            sql("DROP TRIGGER reject_rebase")
        }
        assertEquals(target.id, db.makeTaskNow(target.id).id)
        assertEquals(listOf(target.id), nowIds())
        assertTrue(db.tasks.value.first().sortOrder < db.tasks.value.drop(1).minOf { it.sortOrder })
    }

    @Test fun aRejectedFinalPromotionRollsBackAnAlreadyRebasedQueue() = runBlocking {
        sql("UPDATE tasks SET sortOrder=-2147483648 WHERE id=${original.id}")
        db = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), customDbFile = file)
        db.initialize()
        val before = db.tasks.value.toList()
        sql("CREATE TRIGGER reject_rebased_now BEFORE UPDATE ON tasks WHEN NEW.id=${target.id} AND NEW.isNextAction=1 BEGIN SELECT RAISE(ABORT,'Final promotion rejected'); END")
        expectFailure { db.makeTaskNow(target.id) }
        assertEquals(before, db.tasks.value)
        assertEquals(before.map { it.id to it.sortOrder }.sortedBy { it.first }, ordering())
        assertEquals(listOf(original.id), nowIds())
        sql("DROP TRIGGER reject_rebased_now")
        assertEquals(target.id, db.makeTaskNow(target.id).id)
        assertEquals(listOf(target.id), nowIds())
    }

    @Test fun anIgnoredPreviousNowClearCannotCommitTwoSelectedTasks() = runBlocking {
        val before = db.tasks.value.toList()
        sql("CREATE TRIGGER ignore_previous_now BEFORE UPDATE ON tasks WHEN OLD.id=${original.id} AND OLD.isNextAction=1 AND NEW.isNextAction=0 BEGIN SELECT RAISE(IGNORE); END")
        expectFailure { db.makeTaskNow(target.id) }
        assertEquals(before, db.tasks.value)
        assertEquals(before.map { it.id to it.sortOrder }.sortedBy { it.first }, ordering())
        assertEquals(listOf(original.id), nowIds())
        sql("DROP TRIGGER ignore_previous_now")
        assertEquals(target.id, db.makeTaskNow(target.id).id)
        assertEquals(listOf(target.id), nowIds())
    }

    private suspend fun expectFailure(action: suspend () -> Unit) {
        try { action(); fail("Expected selection failure") } catch (_: java.sql.SQLException) { } catch (_: IllegalStateException) { }
    }
    private fun sql(statement: String) = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { conn -> conn.createStatement().use { it.execute(statement) } }
    private fun nowIds(): List<Long> = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { conn ->
        conn.createStatement().use { stmt -> stmt.executeQuery("SELECT id FROM tasks WHERE isNextAction=1 ORDER BY id").use { rows -> buildList { while (rows.next()) add(rows.getLong(1)) } } }
    }
    private fun ordering(): List<Pair<Long,Int>> = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { conn ->
        conn.createStatement().use { stmt -> stmt.executeQuery("SELECT id,sortOrder FROM tasks ORDER BY id").use { rows -> buildList { while (rows.next()) add(rows.getLong(1) to rows.getInt(2)) } } }
    }
}
