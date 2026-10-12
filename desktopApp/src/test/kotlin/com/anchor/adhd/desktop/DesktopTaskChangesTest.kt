package com.anchor.adhd.desktop

import com.anchor.adhd.desktop.db.DesktopDataOperations
import com.anchor.adhd.desktop.db.DesktopTaskChanges
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.sql.SQLException

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopTaskChangesTest {
    @Test fun taskChangesShareTheReservationAndCheckPendingSavesBeforeWriting() = runTest {
        val operations = DesktopDataOperations(this)
        var allowed = false
        var writes = 0
        val changes = DesktopTaskChanges(operations) { allowed }
        assertFalse(changes.start("Updating milestone") { writes++ })
        assertEquals(0, writes)
        allowed = true
        val release = CompletableDeferred<Unit>()
        assertTrue(changes.start("Deleting milestone") { writes++; release.await() })
        assertTrue(operations.isBusy)
        assertFalse(changes.start("Updating milestone") { writes++ })
        assertFalse(operations.start("Restoring backup") { writes++; null })
        runCurrent()
        assertEquals(1, writes)
        release.complete(Unit)
        advanceUntilIdle()
        assertFalse(operations.isBusy)
    }

    @Test fun aCancelledOwnerBeforeLaunchWritesNothingAndReleasesReservation() = runTest {
        val owner = SupervisorJob()
        val operations = DesktopDataOperations(CoroutineScope(owner + StandardTestDispatcher(testScheduler)))
        val changes = DesktopTaskChanges(operations) { true }
        var writes = 0
        changes.start("Deleting milestone") { writes++ }
        owner.cancel()
        advanceUntilIdle()
        assertEquals(0, writes)
        assertFalse(operations.isBusy)
    }

    @Test fun cancellationDuringDeletionStillFinishesAcknowledgementAndUndoBookkeeping() = runTest {
        val owner = SupervisorJob()
        val operations = DesktopDataOperations(CoroutineScope(owner + StandardTestDispatcher(testScheduler)))
        val changes = DesktopTaskChanges(operations) { true }
        val release = CompletableDeferred<Unit>()
        val history = mutableListOf<String>()
        var committed = false
        changes.start("Deleting milestone") {
            committed = true
            release.await()
            history += "Exact deleted task"
        }
        runCurrent()
        assertTrue(committed)
        owner.cancel()
        assertTrue(operations.isBusy)
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("Exact deleted task"), history)
        assertFalse(operations.isBusy)
        assertTrue(owner.isCompleted)
    }

    @Test fun sqlFailureKeepsPlainApplicationFeedbackAndRetryDoesNotLoseUndo() = runTest {
        val operations = DesktopDataOperations(this)
        val changes = DesktopTaskChanges(operations) { true }
        val history = mutableListOf("Original deletion")
        changes.start("Restoring milestones") {
            throw SQLException("Technical SQLite details")
        }
        advanceUntilIdle()
        val feedback = operations.state.value.feedback!!
        assertTrue(feedback.contains("Check the list and try again"))
        assertFalse(feedback.contains("SQLite"))
        assertEquals(listOf("Original deletion"), history)
        // The global feedback is still available to another screen.
        assertEquals(feedback, operations.state.value.feedback)
        assertTrue(changes.start("Restoring milestones") { history.clear() })
        advanceUntilIdle()
        assertTrue(history.isEmpty())
        assertNull(operations.state.value.feedback)
    }
}
