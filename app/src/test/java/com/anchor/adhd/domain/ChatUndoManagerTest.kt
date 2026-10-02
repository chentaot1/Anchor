package com.anchor.adhd.domain

import com.anchor.adhd.data.model.InboxState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
