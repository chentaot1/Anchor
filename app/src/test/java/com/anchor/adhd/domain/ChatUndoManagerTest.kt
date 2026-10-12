package com.anchor.adhd.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anchor.adhd.data.db.AnchorDatabase
import com.anchor.adhd.data.model.InboxState
import com.anchor.adhd.data.repository.PlanRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChatUndoManagerTest {
    private val now = 1_000_000_000L

    @Test
    fun canUndo_trueAfterPush() {
        val m = ChatUndoManager()
        m.push(ChatUndoOp.CreatedTasks(listOf(1L)), now)
        assertTrue(m.canUndo(now + 1_000))
    }

    @Test
    fun canUndo_falseAfterWindowExpires() {
        val m = ChatUndoManager(windowMs = 10_000)
        m.push(ChatUndoOp.CreatedTasks(listOf(1L)), now)
        assertFalse(m.canUndo(now + 20_000))
    }

    @Test
    fun pop_returnsMostRecentInFifo() {
        val m = ChatUndoManager()
        m.push(ChatUndoOp.InboxMoved(1L, InboxState.TODAY), now)
        m.push(ChatUndoOp.CreatedTasks(listOf(2L)), now)
        val op = m.pop(now)
        assertTrue(op is ChatUndoOp.CreatedTasks)
    }

    @Test
    fun pop_returnsNull_whenEmpty() {
        val m = ChatUndoManager()
        assertNull(m.pop(now))
    }

    @Test
    fun dumpAt2358_stillUndoableAt0005() {
        val m = ChatUndoManager()
        val at2358 = now
        val at0005 = now + 7 * 60_000L
        m.push(ChatUndoOp.CreatedTasks(listOf(1L)), at2358, ChatUndoSource.PANIC)
        assertTrue(m.canUndo(at0005))
        assertEquals(1, m.snapshot(at0005).size)
    }

    @Test
    fun olderThan12h_isPruned() {
        val m = ChatUndoManager()
        m.push(ChatUndoOp.CreatedTasks(listOf(1L)), now)
        assertFalse(m.canUndo(now + ChatUndoManager.TWELVE_HOURS + 1))
        assertTrue(m.snapshot(now + ChatUndoManager.TWELVE_HOURS + 1).isEmpty())
    }

    @Test
    fun completionChangedUndo_restoresAllTasksWhenWasCompletedOrNotCompleted() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = PlanRepository(
                taskDao = db.taskDao(),
                routineDao = db.routineDao(),
                calendarDao = db.calendarEventDao(),
                assignmentDao = db.assignmentDao(),
                replanDao = db.replanDao(),
                db = db
            )
            val ids = repo.captureToInbox(listOf("Task A", "Task B", "Task C"))
            ids.forEach { repo.completeTaskCascade(it) }
            assertTrue(ids.all { repo.getTask(it)?.isCompleted == true })

            // Undo marking them complete -> all 3 become incomplete
            ChatUndoApplier.undo(ChatUndoOp.CompletionChanged(ids, wasCompleted = false), repo)
            assertTrue(ids.all { repo.getTask(it)?.isCompleted == false })

            // Undo un-completing them -> all 3 become complete again
            ChatUndoApplier.undo(ChatUndoOp.CompletionChanged(ids, wasCompleted = true), repo)
            assertTrue(ids.all { repo.getTask(it)?.isCompleted == true })
        } finally {
            db.close()
        }
    }
}
