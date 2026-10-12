package com.anchor.adhd.desktop.db

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import com.anchor.adhd.desktop.DesktopDurationLimits

data class DesktopThoughtCaptureState(
    val draft: String = "",
    val isSaving: Boolean = false,
    val feedback: String? = null,
    val isError: Boolean = false,
)

internal data class DesktopThoughtRequest(val key: String, val text: String, val durationMinutes: Int = 5)

/** App-owned draft and frozen submission survive navigation and failed acknowledgements. */
internal class DesktopThoughtCapture(
    private val scope: CoroutineScope,
    private val allowed: () -> Boolean = { true },
    private val actionLabel: String = "Capture",
    private val draftLabel: String = "thought",
    private val durationMinutes: () -> Int = { 5 },
    private val onSavingChanged: (Boolean) -> Unit = {},
    private val onSaved: () -> Unit = {},
    private val save: suspend (DesktopThoughtRequest) -> Unit,
) {
    private val _state = MutableStateFlow(DesktopThoughtCaptureState())
    val state = _state.asStateFlow()
    val isSaving: Boolean get() = _state.value.isSaving
    private var request: DesktopThoughtRequest? = null
    private var activeId: Long? = null
    private var nextId = 0L

    @Synchronized fun edit(text: String) {
        if (isSaving || text == _state.value.draft) return
        request = null
        _state.value = DesktopThoughtCaptureState(draft = text)
    }

    @Synchronized fun submit(): Boolean {
        if (activeId != null || _state.value.draft.isBlank()) return false
        if (!scope.isActive || !runCatching(allowed).getOrDefault(false)) {
            _state.value = _state.value.copy(
                feedback = "$actionLabel is unavailable right now. Your $draftLabel is still here; try again when the current operation finishes.",
                isError = true,
            )
            return false
        }
        val submitted = request ?: DesktopThoughtRequest(
            UUID.randomUUID().toString(), _state.value.draft.trim(),
            durationMinutes().coerceIn(DesktopDurationLimits.MIN_MINUTES, DesktopDurationLimits.MAX_MINUTES),
        ).also { request = it }
        val id = ++nextId
        activeId = id
        _state.value = _state.value.copy(isSaving = true, feedback = null, isError = false)
        onSavingChanged(true)
        val job = scope.launch {
            // Once saving starts, finish the database acknowledgement even if its owner is cancelled.
            // Retrying an ambiguous database result uses the same request identity.
            withContext(NonCancellable) {
                try {
                    save(submitted)
                } catch (_: Exception) {
                    finish(id, false)
                    return@withContext
                }
                finish(id, true)
                // Returning to work is a separate effect; a navigation failure cannot undo a saved thought.
                runCatching(onSaved)
            }
        }
        job.invokeOnCompletion { finish(id, false) }
        return true
    }

    @Synchronized private fun finish(id: Long, success: Boolean) {
        if (activeId != id) return
        activeId = null
        if (success) {
            request = null
            _state.value = DesktopThoughtCaptureState(feedback = "Added to Milestones.")
        } else {
            _state.value = _state.value.copy(
                isSaving = false,
                feedback = "Couldn't save your $draftLabel. It is still here; try $actionLabel again.",
                isError = true,
            )
        }
        onSavingChanged(false)
    }
}
