package com.anchor.adhd.desktop

import com.anchor.adhd.desktop.db.DesktopDataOperations
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopDataOperationsTest {
    @Test fun reservationBlocksASecondOperationBeforeTheFirstCoroutineStarts() = runTest {
        val operations = DesktopDataOperations(this)
        val release = CompletableDeferred<Unit>()
        var writes = 0
        assertTrue(operations.start("Restoring backup") { writes++; release.await(); "Restored" })
        assertTrue(operations.isBusy)
        assertEquals(0, writes)
        assertFalse(operations.start("Resetting data") { writes++; "Wrong reset" })
        runCurrent()
        assertEquals(1, writes)
        release.complete(Unit)
        advanceUntilIdle()
        assertFalse(operations.isBusy)
        assertEquals("Restored", operations.state.value.feedback)
        assertFalse(operations.state.value.isError)
    }

    @Test fun aFreshPreconditionRejectsReplacementWithoutRunningIt() = runTest {
        val operations = DesktopDataOperations(this)
        var allowed = false
        var calls = 0
        assertFalse(operations.start("Restore", allowed = { allowed }, deniedMessage = "Finish the paused timer") { calls++; "Restored" })
        assertEquals(0, calls)
        assertEquals("Finish the paused timer", operations.state.value.feedback)
        assertTrue(operations.state.value.isError)
        allowed = true
        assertTrue(operations.start("Restore", allowed = { allowed }) { calls++; "Restored" })
        advanceUntilIdle()
        assertEquals(1, calls)
    }

    @Test fun failedOperationsReportTheErrorAndAllowAnotherOperation() = runTest {
        val operations = DesktopDataOperations(this)
        operations.start("Exporting backup") { throw IOException("Disk full") }
        advanceUntilIdle()
        assertFalse(operations.isBusy)
        assertTrue(operations.state.value.feedback!!.contains("Disk full"))
        assertTrue(operations.state.value.isError)
        assertTrue(operations.start("Exporting backup") { "Exported" })
        advanceUntilIdle()
        assertEquals("Exported", operations.state.value.feedback)
        assertFalse(operations.state.value.isError)
    }

    @Test fun cancellationReleasesTheReservationAndReportsAnInterruptedOperation() = runTest {
        val job = SupervisorJob()
        val owner = CoroutineScope(job + StandardTestDispatcher(testScheduler))
        val operations = DesktopDataOperations(owner)
        operations.start("Restore") { awaitCancellation() }
        runCurrent()
        job.cancel()
        advanceUntilIdle()
        assertFalse(operations.isBusy)
        assertTrue(operations.state.value.isError)
        assertTrue(operations.state.value.feedback!!.contains("interrupted"))
    }

    @Test fun aScopeCancelledBeforeLaunchCannotLeaveAStuckBusyFlag() = runTest {
        val job = SupervisorJob().apply { cancel() }
        val operations = DesktopDataOperations(CoroutineScope(job + StandardTestDispatcher(testScheduler)))
        var called = false
        operations.start("Restore") { called = true; "Restored" }
        advanceUntilIdle()
        assertFalse(called)
        assertFalse(operations.isBusy)
        assertTrue(operations.state.value.isError)
    }

    @Test fun feedbackPersistsAfterOperationCompletionUntilExplicitlyDismissed() = runTest {
        val operations = DesktopDataOperations(this)
        operations.start("Reset") { "Recovery backup: C:\\Anchor\\backup.json" }
        advanceUntilIdle()
        // Reading state again is the same contract used by a newly composed screen.
        assertEquals("Recovery backup: C:\\Anchor\\backup.json", operations.state.value.feedback)
        operations.dismissFeedback()
        assertNull(operations.state.value.feedback)
        assertFalse(operations.state.value.isBusy)
    }

    @Test fun successfulPreviewCanCompleteWithoutCreatingAnExtraResultDialog() = runTest {
        val operations = DesktopDataOperations(this)
        operations.start("Reading backup") { null }
        advanceUntilIdle()
        assertFalse(operations.isBusy)
        assertNull(operations.state.value.feedback)
        assertFalse(operations.state.value.isError)
    }
}
