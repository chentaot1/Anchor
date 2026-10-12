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
class DesktopRoutingSessionTest {
    private fun task(text: String) = SmartRouteResult.TaskBreakdown(text, listOf("Open $text"), "Open $text")

    @Test fun previewTextDoesNotClaimAPendingNoteWasSavedAndCountsFollowEnrichment() {
        val note = DesktopSmartRouter.routeQuick("note: Remember the outline") as SmartRouteResult.QuickNote
        assertTrue(note.detail.contains("choose Save to Inbox"))
        assertTrue(note.detail.startsWith("Remember the outline\n"))
        val dump = SmartRouteResult.BrainDump(listOf("One"), "One")
        val enriched = dump.copy(tasks = listOf("One", "Two", "Three"))
        assertTrue(enriched.detail.contains("3 actionable items"))
        assertTrue(enriched.detail.contains("save them"))
    }

    @Test fun oldCancellationCannotFinishTheNewerRequest() = runTest {
        val release = CompletableDeferred<Unit>()
        val router = DesktopRoutingSession(this, ::task) { text ->
            if (text == "Old") awaitCancellation()
            release.await()
            task("Enriched $text")
        }
        router.edit("Old")
        router.submit()
        runCurrent()
        router.edit("New")
        router.submit()
        runCurrent()
        assertTrue(router.state.value.isProcessing)
        assertEquals(task("New"), router.state.value.result)
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(task("Enriched New"), router.state.value.result)
        assertFalse(router.state.value.isProcessing)
    }

    @Test fun cancellationIgnoringOldWorkCannotOverwriteTheNewResultOrDraft() = runTest {
        val oldRelease = CompletableDeferred<Unit>()
        val newRelease = CompletableDeferred<Unit>()
        val router = DesktopRoutingSession(this, ::task) { text ->
            withContext(NonCancellable) {
                (if (text == "Old") oldRelease else newRelease).await()
                task("Enriched $text")
            }
        }
        router.edit("Old")
        router.submit()
        runCurrent()
        router.edit("New")
        router.submit()
        runCurrent()
        router.edit("Next draft")
        newRelease.complete(Unit)
        runCurrent()
        val newest = router.state.value
        oldRelease.complete(Unit)
        advanceUntilIdle()
        assertEquals(newest, router.state.value)
        assertEquals("Next draft", newest.draft)
        assertEquals(task("Enriched New"), newest.result)
        assertFalse(newest.isProcessing)
    }

    @Test fun deterministicReplacementCancelsEnrichmentAndStaysVisible() = runTest {
        val release = CompletableDeferred<Unit>()
        val timer = SmartRouteResult.TimerAction(25)
        val router = DesktopRoutingSession(this, { if (it == "25m") timer else task(it) }) {
            withContext(NonCancellable) { release.await(); task("Stale") }
        }
        router.edit("Old")
        router.submit()
        runCurrent()
        router.edit("25m")
        router.submit()
        assertEquals(timer, router.state.value.result)
        assertFalse(router.state.value.isProcessing)
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(timer, router.state.value.result)
    }

    @Test fun dismissPreservesTheNextDraftAndLateWorkCannotResurrectTheResult() = runTest {
        val release = CompletableDeferred<Unit>()
        val router = DesktopRoutingSession(this, ::task) {
            withContext(NonCancellable) { release.await(); task("Stale") }
        }
        router.edit("First")
        router.submit()
        runCurrent()
        router.edit("Keep this next thought")
        router.dismissResult()
        assertEquals("Keep this next thought", router.state.value.draft)
        assertNull(router.state.value.result)
        assertFalse(router.state.value.isProcessing)
        release.complete(Unit)
        advanceUntilIdle()
        assertNull(router.state.value.result)
        assertEquals("Keep this next thought", router.state.value.draft)
    }

    @Test fun clearExplicitlyRemovesDraftResultAndLateFailure() = runTest {
        val release = CompletableDeferred<Unit>()
        val router = DesktopRoutingSession(this, ::task) {
            withContext(NonCancellable) { release.await(); throw IOException("Old failure") }
        }
        router.edit("First")
        router.submit()
        runCurrent()
        router.edit("Next")
        router.clear()
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(DesktopRoutingState(), router.state.value)
    }

    @Test fun resultActionsConsumeOnceAndCannotActOnANewerRequest() = runTest {
        val router = DesktopRoutingSession(this, ::task) { task("Enriched $it") }
        router.edit("First")
        router.submit()
        val firstId = router.state.value.resultId
        advanceUntilIdle()
        router.edit("Second")
        router.submit()
        val secondId = router.state.value.resultId
        assertFalse(router.takeResult(firstId))
        assertEquals(task("Second"), router.state.value.result)
        router.edit("Next draft")
        assertTrue(router.takeResult(secondId))
        assertFalse(router.takeResult(secondId))
        advanceUntilIdle()
        assertEquals("Next draft", router.state.value.draft)
        assertNull(router.state.value.result)
        assertFalse(router.state.value.isProcessing)
    }

    @Test fun resultActionCancelsPendingWorkBeforeNavigationCallbacks() = runTest {
        val release = CompletableDeferred<Unit>()
        val router = DesktopRoutingSession(this, ::task) {
            withContext(NonCancellable) { release.await(); task("Stale") }
        }
        router.edit("First")
        router.submit()
        runCurrent()
        assertTrue(router.takeResult(router.state.value.resultId))
        assertNull(router.state.value.result)
        assertFalse(router.state.value.isProcessing)
        release.complete(Unit)
        advanceUntilIdle()
        assertNull(router.state.value.result)
    }

    @Test fun enrichmentFailureKeepsTheImmediateResultUsableAndTheNewDraftIntact() = runTest {
        val release = CompletableDeferred<Unit>()
        val router = DesktopRoutingSession(this, ::task) { release.await(); throw IOException("Inference failed") }
        router.edit("First")
        router.submit()
        runCurrent()
        router.edit("Next")
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(task("First"), router.state.value.result)
        assertEquals("Next", router.state.value.draft)
        assertFalse(router.state.value.isProcessing)
        assertTrue(router.state.value.feedback!!.contains("use this result"))
    }

    @Test fun cancelledBeforeLaunchReleasesProcessingAndRejectsFurtherSubmission() = runTest {
        val owner = SupervisorJob()
        var calls = 0
        val router = DesktopRoutingSession(CoroutineScope(owner + StandardTestDispatcher(testScheduler)), ::task) {
            calls++; task(it)
        }
        router.edit("First")
        router.submit()
        owner.cancel()
        advanceUntilIdle()
        assertEquals(0, calls)
        assertFalse(router.state.value.isProcessing)
        assertEquals(task("First"), router.state.value.result)
        router.edit("Keep me")
        assertFalse(router.submit())
        assertEquals("Keep me", router.state.value.draft)
        assertEquals(task("First"), router.state.value.result)
    }

    @Test fun failedQuickRoutingRetainsTheInputWithoutStartingEnrichment() = runTest {
        var calls = 0
        val router = DesktopRoutingSession(this, { throw IOException() }) { calls++; task(it) }
        router.edit("Keep me")
        assertFalse(router.submit())
        assertEquals("Keep me", router.state.value.draft)
        assertNotNull(router.state.value.feedback)
        assertFalse(router.state.value.isProcessing)
        assertEquals(0, calls)
    }

    @Test fun blankSubmissionDoesNotCancelTheCurrentRequest() = runTest {
        val release = CompletableDeferred<Unit>()
        val router = DesktopRoutingSession(this, ::task) { release.await(); task("Enriched") }
        router.edit("First")
        router.submit()
        runCurrent()
        router.edit("  ")
        assertFalse(router.submit())
        assertTrue(router.state.value.isProcessing)
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals("  ", router.state.value.draft)
        assertEquals(task("Enriched"), router.state.value.result)
    }
}
