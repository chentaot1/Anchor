package com.anchor.adhd.desktop.ai

import com.anchor.adhd.desktop.db.DesktopMilestoneStepSave
import com.anchor.adhd.desktop.db.DesktopTask
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopMilestoneStepsTest {
    private fun task(id: Long = 1, title: String = "Write the outline") = DesktopTask(id = id, title = title)
    private fun DesktopMilestoneSteps.preview() = state.value.previews[state.value.expandedTaskId]!!

    @Test fun failedSaveRetainsPreviewAndRetryIdentityAcrossCollapseAndReopen() = runTest {
        val requests = mutableListOf<DesktopMilestoneStepSave>()
        val session = DesktopMilestoneSteps(this, quick = { listOf("First", "Second") }, canRefine = { false }) {
            requests += it
            if (requests.size == 1) throw IOException("No acknowledgement")
            null
        }
        session.toggle(task())
        val preview = session.preview()
        session.savePreview(preview.key, false)
        advanceUntilIdle()
        assertTrue(session.state.value.isError)
        session.toggle(task()); session.toggle(task())
        assertEquals(preview, session.preview())
        session.savePreview(preview.key, false)
        advanceUntilIdle()
        assertEquals(requests[0], requests[1])
        assertNull(session.state.value.expandedTaskId)
    }

    @Test fun savingReservesImmediatelyFreezesPayloadAndAcknowledgesBeforeStartingExactTask() = runTest {
        val release = CompletableDeferred<Unit>()
        val requests = mutableListOf<DesktopMilestoneStepSave>()
        val started = mutableListOf<DesktopTask>()
        val busy = mutableListOf<Boolean>()
        val selected = task(91, "First").copy(durationMinutes = 5)
        val session = DesktopMilestoneSteps(this, quick = { listOf("First", "Second", "Fifth") }, canRefine = { false },
            onStart = { started += it }, onSavingChanged = { busy += it }) { requests += it; release.await(); selected }
        session.toggle(task())
        val preview = session.preview()
        assertTrue(session.savePreview(preview.key, true))
        assertTrue(session.isSaving)
        assertEquals(listOf(true), busy)
        assertFalse(session.savePreview(preview.key, false))
        session.toggle(task(2, "Other task"))
        assertEquals(preview, session.preview())
        runCurrent()
        assertTrue(started.isEmpty())
        assertEquals(preview.steps, requests.single().steps)
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(selected), started)
        assertEquals(listOf(true, false), busy)
    }

    @Test fun staleNoncooperativeRefinementCannotReplaceANewTaskOrAFrozenSave() = runTest {
        val first = CompletableDeferred<List<String>>()
        val second = CompletableDeferred<List<String>>()
        val saved = mutableListOf<DesktopMilestoneStepSave>()
        val session = DesktopMilestoneSteps(this, quick = { listOf("Initial $it") }, canRefine = { true },
            enrich = { title -> withContext(NonCancellable) { if (title == "First") first.await() else second.await() } }) { saved += it; null }
        session.toggle(task(1, "First")); runCurrent()
        session.toggle(task(2, "Second")); runCurrent()
        val preview = session.preview()
        first.complete(listOf("Stale first result")); runCurrent()
        assertEquals(preview, session.preview())
        session.savePreview(preview.key, false)
        second.complete(listOf("Late replacement"))
        advanceUntilIdle()
        assertEquals(preview.steps, saved.single().steps)
        assertEquals(preview, session.state.value.previews[2L])
        assertNull(session.state.value.loadingTaskId)
    }

    @Test fun changedRefinementRejectsOldControlAndChangedSourceCreatesNewIdentity() = runTest {
        val result = CompletableDeferred<List<String>>()
        var writes = 0
        val session = DesktopMilestoneSteps(this, quick = { listOf("Initial") }, canRefine = { true }, enrich = { result.await() }) { writes++; null }
        session.toggle(task())
        val old = session.preview()
        result.complete(listOf("Refined", "Another")); advanceUntilIdle()
        assertNotEquals(old.key, session.preview().key)
        assertFalse(session.savePreview(old.key, false))
        assertEquals(0, writes)
        session.toggle(task(1, "Renamed source"))
        // The displayed same-ID task is first collapsed; reopening compares its latest title.
        session.toggle(task(1, "Renamed source"))
        assertEquals("Renamed source", session.preview().sourceTitle)
        assertNotEquals(old.key, session.preview().key)
        advanceUntilIdle()
    }

    @Test fun cancellationBeforeLaunchRetainsPreviewAndAfterLaunchFinishesSave() = runTest {
        val before = SupervisorJob()
        var writes = 0
        val session = DesktopMilestoneSteps(CoroutineScope(before + StandardTestDispatcher(testScheduler)), quick = { listOf("First") }, canRefine = { false }) { writes++; null }
        session.toggle(task()); session.savePreview(session.preview().key, false)
        before.cancel(); advanceUntilIdle()
        assertEquals(0, writes)
        assertFalse(session.isSaving)
        assertTrue(session.state.value.isError)
        val after = SupervisorJob()
        val release = CompletableDeferred<Unit>()
        val pending = DesktopMilestoneSteps(CoroutineScope(after + StandardTestDispatcher(testScheduler)), quick = { listOf("First") }, canRefine = { false }) { release.await(); writes++; null }
        pending.toggle(task()); pending.savePreview(pending.preview().key, false); runCurrent()
        after.cancel(); release.complete(Unit); advanceUntilIdle()
        assertEquals(1, writes)
        assertFalse(pending.isSaving)
        assertNull(pending.state.value.expandedTaskId)
        assertTrue(after.isCompleted)
    }

    @Test fun blockedActionsAndFailedGenerationKeepExistingPreviewWithoutWriting() = runTest {
        var allowed = false
        var writes = 0
        val session = DesktopMilestoneSteps(this, allowed = { allowed }, quick = { if (it == "Broken") throw IOException() else listOf("First") }, canRefine = { false }) { writes++; null }
        session.toggle(task()); val preview = session.preview()
        assertFalse(session.savePreview(preview.key, false))
        session.toggle(task(2, "Broken"))
        assertEquals(preview, session.preview())
        assertNull(session.state.value.loadingTaskId)
        allowed = true
        assertTrue(session.savePreview(preview.key, false))
        advanceUntilIdle()
        assertEquals(1, writes)
    }

    @Test fun cancellingDuringFirstStepSaveFinishesAcknowledgementWithoutStartingAPhantomTimer() = runTest {
        val owner = SupervisorJob()
        val release = CompletableDeferred<Unit>()
        var starts = 0
        var writes = 0
        val session = DesktopMilestoneSteps(CoroutineScope(owner + StandardTestDispatcher(testScheduler)), quick = { listOf("First") }, canRefine = { false },
            onStart = { starts++ }) { release.await(); writes++; task(91, "First") }
        session.toggle(task()); session.savePreview(session.preview().key, true); runCurrent()
        owner.cancel(); release.complete(Unit); advanceUntilIdle()
        assertEquals(1, writes)
        assertEquals(0, starts)
        assertFalse(session.state.value.isError)
        assertEquals("First step saved to Milestones.", session.state.value.feedback)
        assertTrue(owner.isCompleted)
    }
}
