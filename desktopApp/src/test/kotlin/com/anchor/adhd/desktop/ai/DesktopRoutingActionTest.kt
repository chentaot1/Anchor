package com.anchor.adhd.desktop.ai

import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopRoutingActionTest {
    private fun note(text: String) = SmartRouteResult.QuickNote(text)

    @Test fun actionReservesImmediatelyAndKeepsPreviewAndNextDraftUntilSuccess() = runTest {
        val release = CompletableDeferred<Unit>()
        val router = DesktopRoutingSession(this, ::note)
        router.edit("First")
        router.submit()
        val id = router.state.value.resultId
        var writes = 0
        assertTrue(router.performAction(id, "Saving tasks", successMessage = "Saved") { writes++; release.await() })
        assertTrue(router.isActing)
        assertEquals(note("First"), router.state.value.result)
        assertFalse(router.performAction(id, "Wrong action") { writes++ })
        router.edit("Next draft")
        assertFalse(router.submit())
        assertFalse(router.takeResult(id))
        router.dismissResult()
        router.clear()
        assertEquals(note("First"), router.state.value.result)
        assertEquals("Next draft", router.state.value.draft)
        assertEquals(0, writes)
        runCurrent()
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, writes)
        assertFalse(router.isActing)
        assertNull(router.state.value.result)
        assertEquals("Next draft", router.state.value.draft)
        assertEquals("Saved", router.state.value.feedback)
        assertFalse(router.state.value.isActionError)
    }

    @Test fun failureRetainsTheSelectedResultAndStableIdentityAcrossRetries() = runTest {
        val router = DesktopRoutingSession(this, ::note)
        val keys = mutableListOf<String>()
        router.edit("First")
        router.submit()
        val id = router.state.value.resultId
        router.performAction(id, "Saving tasks") { keys += it; throw IOException("Lost acknowledgement") }
        advanceUntilIdle()
        assertFalse(router.isActing)
        assertEquals(note("First"), router.state.value.result)
        assertTrue(router.state.value.isActionError)
        router.performAction(id, "Saving tasks") { keys += it }
        advanceUntilIdle()
        assertEquals(keys[0], keys[1])
        assertNull(router.state.value.result)
        router.edit("First")
        router.submit()
        router.performAction(router.state.value.resultId, "Saving tasks") { keys += it }
        advanceUntilIdle()
        assertNotEquals(keys[0], keys[2])
    }

    @Test fun lateEnrichmentCannotChangeTheSelectedActionOrItsProcessingState() = runTest {
        val enrichment = CompletableDeferred<Unit>()
        val action = CompletableDeferred<Unit>()
        fun task(text: String) = SmartRouteResult.TaskBreakdown(text, listOf(text), text)
        val router = DesktopRoutingSession(this, ::task) {
            withContext(NonCancellable) { enrichment.await(); task("Stale") }
        }
        router.edit("Selected")
        router.submit()
        runCurrent()
        router.performAction(router.state.value.resultId, "Saving tasks") { action.await() }
        runCurrent()
        enrichment.complete(Unit)
        runCurrent()
        assertEquals(task("Selected"), router.state.value.result)
        assertTrue(router.isActing)
        assertFalse(router.state.value.isProcessing)
        action.complete(Unit)
        advanceUntilIdle()
        assertNull(router.state.value.result)
    }

    @Test fun freshGuardAndStaleRequestRejectionsNeverRunCallbacks() = runTest {
        var allowed = false
        var writes = 0
        val router = DesktopRoutingSession(this, ::note)
        router.edit("Old")
        router.submit()
        val oldId = router.state.value.resultId
        router.edit("Current")
        router.submit()
        val id = router.state.value.resultId
        assertFalse(router.performAction(oldId, "Save") { writes++ })
        assertFalse(router.performAction(id, "Save", allowed = { allowed }) { writes++ })
        assertEquals(note("Current"), router.state.value.result)
        assertTrue(router.state.value.isActionError)
        allowed = true
        assertTrue(router.performAction(id, "Save", allowed = { allowed }) { writes++ })
        advanceUntilIdle()
        assertEquals(1, writes)
    }

    @Test fun cancellationBeforeActionLaunchKeepsTheResultAndReleasesBusy() = runTest {
        val owner = SupervisorJob()
        val router = DesktopRoutingSession(CoroutineScope(owner + StandardTestDispatcher(testScheduler)), ::note)
        var writes = 0
        router.edit("Keep this")
        router.submit()
        router.performAction(router.state.value.resultId, "Save") { writes++ }
        owner.cancel()
        advanceUntilIdle()
        assertEquals(0, writes)
        assertFalse(router.isActing)
        assertEquals(note("Keep this"), router.state.value.result)
        assertTrue(router.state.value.isActionError)
        assertFalse(router.performAction(router.state.value.resultId, "Save") { writes++ })
    }

    @Test fun cancellationAfterSavingStartsFinishesAcknowledgement() = runTest {
        val owner = SupervisorJob()
        val release = CompletableDeferred<Unit>()
        val router = DesktopRoutingSession(CoroutineScope(owner + StandardTestDispatcher(testScheduler)), ::note)
        var writes = 0
        router.edit("Finish this")
        router.submit()
        router.performAction(router.state.value.resultId, "Save") { release.await(); writes++ }
        runCurrent()
        owner.cancel()
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, writes)
        assertNull(router.state.value.result)
        assertFalse(router.isActing)
        assertFalse(router.state.value.isActionError)
        assertTrue(owner.isCompleted)
    }

    @Test fun dismissingFailedActionClearsItsFeedbackButKeepsTheNextDraft() = runTest {
        val router = DesktopRoutingSession(this, ::note)
        router.edit("First")
        router.submit()
        router.performAction(router.state.value.resultId, "Save") { throw IOException() }
        advanceUntilIdle()
        router.edit("Next")
        router.dismissResult()
        assertNull(router.state.value.result)
        assertNull(router.state.value.feedback)
        assertFalse(router.state.value.isActionError)
        assertEquals("Next", router.state.value.draft)
    }
}
