package com.anchor.adhd.desktop.ai

import com.anchor.adhd.desktop.db.DesktopAirlockSave
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.CoroutineContext

class DesktopAirlockSessionTest {
    @Test fun previewHasNoWritesAndSavingIncludesAllStepsAndSecondaryThoughts() {
        val saved = mutableListOf<DesktopAirlockSave>()
        val session = DesktopAirlockSession(CoroutineScope(Dispatchers.Unconfined),
            quick = { _, _ -> listOf(" Open notebook ", "Write first line", "Open notebook") },
            canRefine = { false }, save = { saved += it })
        session.open()
        session.edit("Write essay; Send email")
        session.generate(DesktopAirlockMode.STEPS)
        assertTrue(saved.isEmpty())
        val preview = session.state.value.preview!!
        assertEquals(listOf("Send email"), preview.secondaryTasks)
        assertTrue(session.savePreview(preview.key, 2))
        assertEquals(listOf("Open notebook", "Write first line", "Open notebook", "Send email"), saved.single().tasks.map { it.title })
        assertEquals(listOf(5, 5, 5, 15), saved.single().tasks.map { it.durationMinutes })
        assertEquals(2, saved.single().anchorIndex)
        assertFalse(session.state.value.isOpen)
        assertEquals("", session.state.value.draft)
    }

    @Test fun failedAcknowledgementRetainsPreviewAndReusesTheSameFrozenRequest() {
        val requests = mutableListOf<DesktopAirlockSave>()
        val session = DesktopAirlockSession(CoroutineScope(Dispatchers.Unconfined),
            quick = { _, _ -> listOf("Read first page") }, canRefine = { false },
            save = { requests += it; if (requests.size == 1) throw IOException("Commit acknowledgement lost") })
        session.open(); session.edit("Read textbook")
        session.generate(DesktopAirlockMode.STEPS)
        val before = session.state.value
        session.savePreview(before.preview!!.key, 0)
        assertTrue(session.state.value.isOpen)
        assertTrue(session.state.value.isError)
        assertEquals(before.draft, session.state.value.draft)
        assertEquals(before.preview, session.state.value.preview)
        session.dismiss(); session.open()
        assertEquals(before.preview, session.state.value.preview)
        session.savePreview(before.preview.key, 0)
        assertEquals(requests.first(), requests.last())
        assertFalse(session.state.value.isSaving)
        assertFalse(session.state.value.isOpen)
    }

    @Test fun savingOlderPreviewPreservesSeparatelyEditedDraft() {
        val session = DesktopAirlockSession(CoroutineScope(Dispatchers.Unconfined),
            quick = { _, _ -> listOf("First task") }, canRefine = { false }, save = {})
        session.open(); session.edit("Original brain dump")
        session.generate(DesktopAirlockMode.TASKS)
        val preview = session.state.value.preview!!
        session.edit("Next brain dump")
        session.savePreview(preview.key)
        session.open()
        assertEquals("Next brain dump", session.state.value.draft)
        assertNull(session.state.value.preview)
    }

    @Test fun staleNonCooperativeRefinementCannotReplaceANewerPreviewOrClearItsBusyFlag() {
        val first = CompletableDeferred<List<String>>()
        val second = CompletableDeferred<List<String>>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val session = DesktopAirlockSession(scope,
            quick = { _, text -> listOf("Quick $text") }, canRefine = { true },
            enrich = { _, text -> withContext(NonCancellable) { if (text == "First") first.await() else second.await() } }, save = {})
        session.edit("First"); session.generate(DesktopAirlockMode.TASKS)
        session.edit("Second"); session.generate(DesktopAirlockMode.TASKS)
        val current = session.state.value.preview
        first.complete(listOf("Late first result"))
        assertEquals(current, session.state.value.preview)
        assertTrue(session.state.value.isRefining)
        second.complete(listOf("Refined second result"))
        assertEquals(listOf("Refined second result"), session.state.value.preview!!.items)
        assertFalse(session.state.value.isRefining)
        scope.cancel()
    }

    @Test fun changedRefinedContentInvalidatesOldControlsAndSaveFreezesAgainstLaterAi() {
        val result = CompletableDeferred<List<String>>()
        val requests = mutableListOf<DesktopAirlockSave>()
        val session = DesktopAirlockSession(CoroutineScope(Dispatchers.Unconfined),
            quick = { _, _ -> listOf("Quick task") }, canRefine = { true },
            enrich = { _, _ -> result.await() }, save = { requests += it })
        session.edit("Input"); session.generate(DesktopAirlockMode.TASKS)
        val oldKey = session.state.value.preview!!.key
        result.complete(listOf("Refined task"))
        val current = session.state.value.preview!!
        assertNotEquals(oldKey, current.key)
        assertFalse(session.savePreview(oldKey, 0))
        assertFalse(session.savePreview(current.key, 1))
        assertTrue(requests.isEmpty())
        session.savePreview(current.key, 0)
        assertEquals("Refined task", requests.single().tasks.single().title)
    }

    @Test fun actionReservationBlocksDuplicateSaveEditingDismissAndGenerationBeforeLaunch() {
        val dispatcher = QueuedDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        var calls = 0
        val session = DesktopAirlockSession(scope, quick = { _, _ -> listOf("Fixture") },
            canRefine = { false }, save = { calls++; throw IOException("Fixture failure") })
        session.open(); session.edit("Draft"); session.generate(DesktopAirlockMode.TASKS)
        val preview = session.state.value.preview!!
        assertTrue(session.savePreview(preview.key))
        assertFalse(session.savePreview(preview.key, 0))
        session.edit("Lost edit"); session.dismiss()
        assertFalse(session.generate(DesktopAirlockMode.STEPS))
        assertTrue(session.state.value.isOpen)
        assertEquals("Draft", session.state.value.draft)
        dispatcher.drain()
        assertEquals(1, calls)
        assertFalse(session.isSaving)
        assertTrue(session.state.value.isError)
        scope.cancel()
    }

    @Test fun cancellationBeforeLaunchReleasesReservationWithoutAWrite() {
        val dispatcher = QueuedDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        var writes = 0
        val session = DesktopAirlockSession(scope, quick = { _, _ -> listOf("Fixture") },
            canRefine = { false }, save = { writes++ })
        session.open(); session.edit("Draft"); session.generate(DesktopAirlockMode.TASKS)
        session.savePreview(session.state.value.preview!!.key)
        scope.cancel(); dispatcher.drain()
        assertEquals(0, writes)
        assertFalse(session.isSaving)
        assertTrue(session.state.value.isOpen)
        assertEquals("Draft", session.state.value.draft)
    }

    @Test fun cancellationAfterStartFinishesAcknowledgementAndKeepsNextDraft() {
        val gate = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var writes = 0
        val session = DesktopAirlockSession(scope, quick = { _, _ -> listOf("Fixture") },
            canRefine = { false }, save = { gate.await(); writes++ })
        session.open(); session.edit("Original"); session.generate(DesktopAirlockMode.TASKS)
        val preview = session.state.value.preview!!
        session.edit("Next")
        session.savePreview(preview.key)
        scope.cancel(); gate.complete(Unit)
        assertEquals(1, writes)
        assertFalse(session.state.value.isOpen)
        assertEquals("Next", session.state.value.draft)
    }

    @Test fun blockedAndClosedScopeActionsKeepAllUserContent() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val session = DesktopAirlockSession(scope, allowed = { false }, quick = { _, _ -> listOf("Fixture") },
            canRefine = { false }, save = { fail("Must not save") })
        session.open(); session.edit("Draft"); session.generate(DesktopAirlockMode.TASKS)
        val preview = session.state.value.preview!!
        assertFalse(session.savePreview(preview.key))
        assertEquals(preview, session.state.value.preview)
        scope.cancel()
        assertFalse(session.generate(DesktopAirlockMode.TASKS))
        assertEquals("Draft", session.state.value.draft)
        assertTrue(session.state.value.isOpen)
    }

    @Test fun choosingWhileNonCooperativeAiRunsFreezesTheRetryPayload() {
        val result = CompletableDeferred<List<String>>()
        val requests = mutableListOf<DesktopAirlockSave>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val session = DesktopAirlockSession(scope, quick = { _, _ -> listOf("Visible task") }, canRefine = { true },
            enrich = { _, _ -> withContext(NonCancellable) { result.await() } },
            save = { requests += it; throw IOException("Retry fixture") })
        session.open(); session.edit("Input"); session.generate(DesktopAirlockMode.TASKS)
        val preview = session.state.value.preview!!
        session.savePreview(preview.key)
        result.complete(listOf("Late replacement"))
        assertEquals(preview, session.state.value.preview)
        assertTrue(session.state.value.isError)
        session.savePreview(preview.key)
        assertEquals(requests.first(), requests.last())
        scope.cancel()
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val work = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { work.addLast(block) }
        fun drain() { while (work.isNotEmpty()) work.removeFirst().run() }
    }
}
