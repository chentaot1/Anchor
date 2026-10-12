package com.anchor.adhd.desktop.db

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class DesktopDataOperationState(
    val runningLabel: String? = null,
    val feedback: String? = null,
    val isError: Boolean = false,
) {
    val isBusy: Boolean get() = runningLabel != null
}

/** Reserves before launching, and keeps operation state alive across screen navigation. */
internal class DesktopDataOperations(private val scope: CoroutineScope) {
    private var nextId = 0L
    private var activeId: Long? = null
    private val _state = MutableStateFlow(DesktopDataOperationState())
    val state = _state.asStateFlow()
    val isBusy: Boolean get() = _state.value.isBusy

    @Synchronized
    fun start(
        label: String,
        allowed: () -> Boolean = { true },
        deniedMessage: String = "Finish your timer and save pending work before replacing data.",
        action: suspend () -> String?,
    ): Boolean {
        if (activeId != null) return false
        if (!runCatching(allowed).getOrDefault(false)) {
            _state.value = DesktopDataOperationState(feedback = deniedMessage, isError = true)
            return false
        }
        val id = ++nextId
        activeId = id
        _state.value = DesktopDataOperationState(runningLabel = label)
        val job = scope.launch {
            try {
                finish(id, action(), false)
            } catch (cancelled: CancellationException) {
                finish(id, "Operation interrupted. Check your current data before retrying.", true)
                throw cancelled
            } catch (error: Exception) {
                finish(id, "$label could not finish: ${error.message ?: "Unknown error"}", true)
            }
        }
        // A cancelled scope can reject the launch before its body/finally ever runs.
        job.invokeOnCompletion {
            finish(id, "Operation did not finish. Check your current data before retrying.", true)
        }
        return true
    }

    @Synchronized
    private fun finish(id: Long, feedback: String?, isError: Boolean) {
        if (activeId != id) return
        activeId = null
        _state.value = DesktopDataOperationState(feedback = feedback, isError = isError)
    }

    @Synchronized fun dismissFeedback() {
        _state.value = _state.value.copy(feedback = null, isError = false)
    }
}
