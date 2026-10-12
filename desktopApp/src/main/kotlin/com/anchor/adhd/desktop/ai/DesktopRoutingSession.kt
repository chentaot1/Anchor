package com.anchor.adhd.desktop.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

internal data class DesktopRoutingState(
    val draft: String = "",
    val result: SmartRouteResult? = null,
    val resultId: Long = 0,
    val isProcessing: Boolean = false,
    val feedback: String? = null,
    val actionLabel: String? = null,
    val isActionError: Boolean = false,
) {
    val isActing: Boolean get() = actionLabel != null
}

/** App-owned routing state; only the current request can publish or finish enrichment. */
internal class DesktopRoutingSession(
    private val scope: CoroutineScope,
    private val quick: (String) -> SmartRouteResult = DesktopSmartRouter::routeQuick,
    private val enrich: suspend (String) -> SmartRouteResult = DesktopSmartRouter::routeAsync,
) {
    private val _state = MutableStateFlow(DesktopRoutingState())
    val state = _state.asStateFlow()
    private var generation = 0L
    private var activeJob: Job? = null
    private var saveKey: String? = null
    private var activeActionId: Long? = null
    val isActing: Boolean get() = _state.value.isActing

    @Synchronized fun edit(text: String) {
        _state.value = _state.value.copy(draft = text)
    }

    @Synchronized fun submit(): Boolean {
        if (isActing) return false
        val text = _state.value.draft.trim()
        if (text.isEmpty()) return false
        if (!scope.isActive) {
            _state.value = _state.value.copy(feedback = "Routing is unavailable right now. Your text is still here.")
            return false
        }
        val immediate = try {
            quick(text)
        } catch (_: Exception) {
            _state.value = _state.value.copy(feedback = "Couldn't route this text. It is still here; try again.")
            return false
        }
        invalidate()
        saveKey = UUID.randomUUID().toString()
        val id = generation
        val needsEnrichment = immediate is SmartRouteResult.TaskBreakdown || immediate is SmartRouteResult.BrainDump
        _state.value = DesktopRoutingState(result = immediate, resultId = id, isProcessing = needsEnrichment)
        if (needsEnrichment) {
            val job = scope.launch {
                try {
                    publish(id, enrich(text))
                } catch (_: CancellationException) {
                    // The immediate result remains usable when the owner is cancelled.
                } catch (_: Exception) {
                    reportFailure(id)
                } finally {
                    finish(id)
                }
            }
            activeJob = job
            // Also handles cancellation before the coroutine body ever starts.
            job.invokeOnCompletion { finish(id) }
        }
        return true
    }

    @Synchronized fun dismissResult() {
        if (isActing) return
        invalidate()
        saveKey = null
        _state.value = _state.value.copy(result = null, isProcessing = false, feedback = null, isActionError = false)
    }

    @Synchronized fun clear() {
        if (isActing) return
        invalidate()
        saveKey = null
        _state.value = DesktopRoutingState()
    }

    /** Consume the displayed request once, before callbacks can navigate or recompose the UI. */
    @Synchronized fun takeResult(id: Long): Boolean {
        if (isActing || _state.value.result == null || _state.value.resultId != id) return false
        dismissResult()
        return true
    }

    /** Retain the selected preview until its asynchronous action acknowledges success. */
    @Synchronized fun performAction(
        id: Long,
        label: String,
        allowed: () -> Boolean = { true },
        successMessage: String? = null,
        action: suspend (String) -> Unit,
    ): Boolean {
        if (isActing || _state.value.result == null || _state.value.resultId != id) return false
        if (!scope.isActive || !runCatching(allowed).getOrDefault(false)) {
            _state.value = _state.value.copy(feedback = "This action is unavailable right now. Your result is still here; try again shortly.", isActionError = true)
            return false
        }
        val key = checkNotNull(saveKey)
        invalidate() // Freeze the displayed result; later enrichment cannot change a retry's payload.
        val token = generation
        activeActionId = token
        _state.value = _state.value.copy(isProcessing = false, actionLabel = label, feedback = null, isActionError = false)
        val job = scope.launch {
            withContext(NonCancellable) {
                try {
                    action(key)
                    finishAction(token, true, successMessage)
                } catch (_: Exception) {
                    finishAction(token, false, null)
                }
            }
        }
        job.invokeOnCompletion { finishAction(token, false, null) }
        return true
    }

    @Synchronized private fun finishAction(token: Long, success: Boolean, message: String?) {
        if (activeActionId != token) return
        activeActionId = null
        if (success) saveKey = null
        _state.value = _state.value.copy(
            result = if (success) null else _state.value.result,
            actionLabel = null,
            feedback = if (success) message else "Couldn't finish this action. Your result is still here; try again.",
            isActionError = !success,
        )
    }

    private fun invalidate() {
        generation++
        val old = activeJob
        activeJob = null
        old?.cancel()
    }

    @Synchronized private fun publish(id: Long, result: SmartRouteResult) {
        if (generation != id) return
        _state.value = _state.value.copy(result = result, feedback = null)
    }

    @Synchronized private fun reportFailure(id: Long) {
        if (generation != id) return
        _state.value = _state.value.copy(feedback = "Extra guidance is unavailable. You can use this result or route again.")
    }

    @Synchronized private fun finish(id: Long) {
        if (generation != id) return
        activeJob = null
        _state.value = _state.value.copy(isProcessing = false)
    }
}
