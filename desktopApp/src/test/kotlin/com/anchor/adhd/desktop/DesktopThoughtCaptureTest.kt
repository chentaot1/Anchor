package com.anchor.adhd.desktop

import com.anchor.adhd.desktop.db.DesktopThoughtCapture
import com.anchor.adhd.desktop.db.DesktopThoughtRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopThoughtCaptureTest {
    @Test fun milestoneRetryFreezesDurationAndEditingUsesTheNewReadyPlan() = runTest {
        var minutes = 13
        val requests = mutableListOf<DesktopThoughtRequest>()
        val busy = mutableListOf<Boolean>()
        val capture = DesktopThoughtCapture(this, actionLabel = "Add Milestone", draftLabel = "milestone",
            durationMinutes = { minutes }, onSavingChanged = { busy += it }) {
            requests += it
            if (requests.size <= 2) throw IOException("No acknowledgement")
        }
        capture.edit("  Write the next section  ")
        assertTrue(capture.submit())
        assertEquals(listOf(true), busy)
        minutes = 27
        advanceUntilIdle()
        assertEquals(13, requests.single().durationMinutes)
        assertTrue(capture.state.value.feedback!!.contains("milestone"))
        assertEquals(listOf(true, false), busy)
        assertTrue(capture.submit())
        advanceUntilIdle()
        assertEquals(requests[0], requests[1])
        capture.edit("Write the revised section")
        assertTrue(capture.submit())
        advanceUntilIdle()
        assertNotEquals(requests[0].key, requests[2].key)
        assertEquals(27, requests[2].durationMinutes)
        assertEquals("", capture.state.value.draft)
        assertEquals(listOf(true, false, true, false, true, false), busy)
    }

    @Test fun separateEntrySessionsKeepTheirOwnDraftsAndDurationPolicies() = runTest {
        val requests = mutableListOf<DesktopThoughtRequest>()
        val home = DesktopThoughtCapture(this) { requests += it }
        val milestone = DesktopThoughtCapture(this, durationMinutes = { 240 }) { requests += it }
        home.edit("A passing thought")
        milestone.edit("A long milestone")
        milestone.submit()
        advanceUntilIdle()
        assertEquals("A passing thought", home.state.value.draft)
        assertEquals(240, requests.single().durationMinutes)
        home.submit()
        advanceUntilIdle()
        assertEquals(5, requests.last().durationMinutes)
        assertNotEquals(requests.first().key, requests.last().key)
    }

    @Test fun cancellingBeforeLaunchReleasesTheSharedCaptureReservation() = runTest {
        val owner = SupervisorJob()
        val busy = mutableListOf<Boolean>()
        var writes = 0
        val capture = DesktopThoughtCapture(CoroutineScope(owner + StandardTestDispatcher(testScheduler)),
            onSavingChanged = { busy += it }) { writes++ }
        capture.edit("Still available after cancellation")
        capture.submit()
        assertEquals(listOf(true), busy)
        owner.cancel()
        advanceUntilIdle()
        assertEquals(listOf(true, false), busy)
        assertEquals(0, writes)
        assertEquals("Still available after cancellation", capture.state.value.draft)
    }

    @Test fun returnToWorkRunsOnlyAfterSuccessfulAcknowledgementAndOnceOnRetry() = runTest {
        val release = CompletableDeferred<Unit>()
        var attempts = 0
        var returns = 0
        lateinit var capture: DesktopThoughtCapture
        capture = DesktopThoughtCapture(this, actionLabel = "Scribe", onSaved = {
            assertFalse(capture.isSaving)
            assertEquals("", capture.state.value.draft)
            returns++
        }) {
            attempts++
            if (attempts == 1) throw IOException("No acknowledgement")
            release.await()
        }
        capture.edit("Remember this before returning")
        capture.submit()
        advanceUntilIdle()
        assertEquals(0, returns)
        assertEquals("Remember this before returning", capture.state.value.draft)
        assertTrue(capture.state.value.feedback!!.contains("Scribe"))
        capture.submit()
        runCurrent()
        assertEquals(0, returns)
        assertFalse(capture.submit())
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, returns)
        assertEquals(2, attempts)
    }

    @Test fun returnToWorkFailureCannotTurnCommittedCaptureIntoFailedRetry() = runTest {
        var writes = 0
        val capture = DesktopThoughtCapture(this, onSaved = { throw IllegalStateException("Window no longer exists") }) { writes++ }
        capture.edit("Saved even if navigation fails")
        capture.submit()
        advanceUntilIdle()
        assertEquals(1, writes)
        assertEquals("", capture.state.value.draft)
        assertFalse(capture.state.value.isError)
        assertFalse(capture.submit())
        assertEquals(1, writes)
    }

    @Test fun savingReservesImmediatelyAndClearsTheDraftOnlyAfterAcknowledgement() = runTest {
        val release = CompletableDeferred<Unit>()
        val requests = mutableListOf<DesktopThoughtRequest>()
        val capture = DesktopThoughtCapture(this) { requests += it; release.await() }
        capture.edit("  Remember the outline  ")
        assertTrue(capture.submit())
        assertTrue(capture.isSaving)
        assertFalse(capture.submit())
        capture.edit("A different draft")
        assertEquals("  Remember the outline  ", capture.state.value.draft)
        assertTrue(requests.isEmpty())
        runCurrent()
        assertEquals("Remember the outline", requests.single().text)
        assertEquals("  Remember the outline  ", capture.state.value.draft)
        release.complete(Unit)
        advanceUntilIdle()
        assertFalse(capture.isSaving)
        assertEquals("", capture.state.value.draft)
        assertEquals("Added to Milestones.", capture.state.value.feedback)
        assertFalse(capture.state.value.isError)
    }

    @Test fun failedAcknowledgementRetainsTheDraftAndRetriesTheSameIdentity() = runTest {
        val requests = mutableListOf<DesktopThoughtRequest>()
        val capture = DesktopThoughtCapture(this) {
            requests += it
            if (requests.size == 1) throw IOException("Lost acknowledgement")
        }
        capture.edit("  Keep this thought  ")
        capture.submit()
        advanceUntilIdle()
        assertFalse(capture.isSaving)
        assertEquals("  Keep this thought  ", capture.state.value.draft)
        assertTrue(capture.state.value.isError)
        // Recomposition or returning to Home reads the same app-owned state.
        capture.edit(capture.state.value.draft)
        assertTrue(capture.submit())
        advanceUntilIdle()
        assertEquals(requests[0], requests[1])
        assertEquals("", capture.state.value.draft)
        capture.edit("  Keep this thought  ")
        capture.submit()
        advanceUntilIdle()
        assertNotEquals(requests[0].key, requests[2].key)
    }

    @Test fun editingAFailedDraftCreatesANewRequest() = runTest {
        val requests = mutableListOf<DesktopThoughtRequest>()
        val capture = DesktopThoughtCapture(this) { requests += it; throw IOException() }
        capture.edit("First thought")
        capture.submit()
        advanceUntilIdle()
        capture.edit("Revised thought")
        assertNull(capture.state.value.feedback)
        capture.submit()
        advanceUntilIdle()
        assertNotEquals(requests[0].key, requests[1].key)
        assertEquals("Revised thought", requests[1].text)
    }

    @Test fun blankAndBlockedSubmissionsDoNotWriteAndTheGuardIsCheckedAgainOnRetry() = runTest {
        var allowed = false
        var writes = 0
        val capture = DesktopThoughtCapture(this, allowed = { allowed }) { writes++ }
        capture.edit(" \n ")
        assertFalse(capture.submit())
        capture.edit("Keep me")
        assertFalse(capture.submit())
        assertFalse(capture.isSaving)
        assertEquals("Keep me", capture.state.value.draft)
        assertTrue(capture.state.value.isError)
        allowed = true
        assertTrue(capture.submit())
        advanceUntilIdle()
        assertEquals(1, writes)
    }

    @Test fun cancelledOwnerRejectsNewWorkWithoutLosingTheDraft() = runTest {
        val owner = SupervisorJob().apply { cancel() }
        var writes = 0
        val capture = DesktopThoughtCapture(CoroutineScope(owner + StandardTestDispatcher(testScheduler))) { writes++ }
        capture.edit("Still here")
        assertFalse(capture.submit())
        advanceUntilIdle()
        assertEquals(0, writes)
        assertFalse(capture.isSaving)
        assertEquals("Still here", capture.state.value.draft)
    }

    @Test fun cancellingBeforeTheCoroutineStartsReleasesBusyAndKeepsTheDraft() = runTest {
        val owner = SupervisorJob()
        var writes = 0
        val capture = DesktopThoughtCapture(CoroutineScope(owner + StandardTestDispatcher(testScheduler))) { writes++ }
        capture.edit("Still here")
        assertTrue(capture.submit())
        owner.cancel()
        advanceUntilIdle()
        assertEquals(0, writes)
        assertFalse(capture.isSaving)
        assertEquals("Still here", capture.state.value.draft)
        assertTrue(capture.state.value.isError)
    }

    @Test fun cancellingAfterSavingStartsStillFinishesAcknowledgement() = runTest {
        val owner = SupervisorJob()
        val release = CompletableDeferred<Unit>()
        var writes = 0
        val capture = DesktopThoughtCapture(CoroutineScope(owner + StandardTestDispatcher(testScheduler))) {
            release.await()
            writes++
        }
        capture.edit("Finish saving")
        capture.submit()
        runCurrent()
        owner.cancel()
        assertTrue(capture.isSaving)
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, writes)
        assertFalse(capture.isSaving)
        assertEquals("", capture.state.value.draft)
        assertFalse(capture.state.value.isError)
        assertTrue(owner.isCompleted)
    }
}
