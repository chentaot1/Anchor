package com.anchor.adhd.desktop.ai

import com.anchor.adhd.desktop.db.DesktopMilestoneStepSave
import com.anchor.adhd.desktop.db.DesktopTask
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

internal data class DesktopMilestonePreview(val key: String, val sourceId: Long, val sourceTitle: String, val steps: List<String>)
internal data class DesktopMilestoneStepsState(
    val expandedTaskId: Long? = null,
    val previews: Map<Long, DesktopMilestonePreview> = emptyMap(),
    val loadingTaskId: Long? = null,
    val isSaving: Boolean = false,
    val feedback: String? = null,
    val isError: Boolean = false,
)

/** Application-owned previews retain their save identities across navigation and failed acknowledgements. */
internal class DesktopMilestoneSteps(
    private val scope: CoroutineScope,
    private val allowed: () -> Boolean = { true },
    private val quick: (String) -> List<String> = { DesktopAiEngine.generateMicroSteps(it) },
    private val canRefine: () -> Boolean = DesktopAiEngine::isModelReady,
    private val enrich: suspend (String) -> List<String> = { DesktopAiEngine.generateMicroStepsAsync(it) },
    private val onStart: (DesktopTask) -> Unit = {},
    private val onSavingChanged: (Boolean) -> Unit = {},
    private val save: suspend (DesktopMilestoneStepSave) -> DesktopTask?,
) {
    private val _state = MutableStateFlow(DesktopMilestoneStepsState())
    val state = _state.asStateFlow()
    val isSaving: Boolean get() = _state.value.isSaving
    private var generation = 0L
    private var refinement: Job? = null
    private var actionId: Long? = null
    private var nextActionId = 0L

    @Synchronized fun toggle(task: DesktopTask) {
        if (isSaving || !scope.isActive) return
        invalidate()
        if (_state.value.expandedTaskId == task.id) {
            _state.value = _state.value.copy(expandedTaskId = null, loadingTaskId = null)
            return
        }
        val cached = _state.value.previews[task.id]?.takeIf { it.sourceTitle == task.title }
        val preview = cached ?: try {
            DesktopMilestonePreview(UUID.randomUUID().toString(), task.id, task.title, clean(quick(task.title)))
        } catch (_: Exception) {
            _state.value = _state.value.copy(loadingTaskId = null, feedback = "Couldn't prepare the steps. Try breaking down the task again.", isError = true)
            return
        }
        val refine = cached == null && runCatching(canRefine).getOrDefault(false)
        _state.value = _state.value.copy(expandedTaskId = task.id, previews = _state.value.previews + (task.id to preview),
            loadingTaskId = if (refine) task.id else null, feedback = null, isError = false)
        if (refine) {
            val token = generation
            refinement = scope.launch {
                try { publish(token, preview, clean(enrich(task.title))) }
                catch (_: CancellationException) { }
                catch (_: Exception) { refinementFailed(token) }
            }
        }
    }

    @Synchronized fun savePreview(key: String, startFirst: Boolean): Boolean {
        val preview = _state.value.previews[_state.value.expandedTaskId] ?: return false
        if (isSaving || preview.key != key) return false
        if (!scope.isActive || !runCatching(allowed).getOrDefault(false)) {
            _state.value = _state.value.copy(feedback = "Wait for the current operation to finish, then try saving these steps again.", isError = true)
            return false
        }
        val request = DesktopMilestoneStepSave(preview.key, preview.sourceId, preview.sourceTitle, preview.steps.toList(), startFirst)
        invalidate()
        val id = ++nextActionId
        actionId = id
        _state.value = _state.value.copy(isSaving = true, loadingTaskId = null, feedback = null, isError = false)
        onSavingChanged(true)
        val job = scope.launch {
            withContext(NonCancellable) {
                try {
                    val selected = save(request)
                    if (startFirst) requireNotNull(selected) { "The saved first step is unavailable" }
                    // Finish an in-flight write after exit without opening a new timer in a cancelled owner.
                    val started = startFirst && scope.isActive
                    if (started) onStart(selected!!)
                    finish(id, true, startFirst, started)
                } catch (_: Exception) { finish(id, false, startFirst) }
            }
        }
        job.invokeOnCompletion { finish(id, false, startFirst) }
        return true
    }

    private fun invalidate() { generation++; refinement?.cancel(); refinement = null }

    @Synchronized private fun publish(token: Long, previous: DesktopMilestonePreview, steps: List<String>) {
        if (generation != token || isSaving) return
        val preview = if (steps == previous.steps) previous else previous.copy(key = UUID.randomUUID().toString(), steps = steps)
        _state.value = _state.value.copy(previews = _state.value.previews + (preview.sourceId to preview), loadingTaskId = null)
    }

    @Synchronized private fun refinementFailed(token: Long) {
        if (generation != token || isSaving) return
        _state.value = _state.value.copy(loadingTaskId = null, feedback = "The initial steps are ready; refinement couldn't finish.", isError = false)
    }

    @Synchronized private fun finish(id: Long, success: Boolean, startFirst: Boolean, started: Boolean = false) {
        if (actionId != id) return
        actionId = null
        _state.value = if (success) _state.value.copy(isSaving = false, expandedTaskId = null,
            feedback = if (started) "First step saved and started." else if (startFirst) "First step saved to Milestones." else "Steps added to Milestones.", isError = false)
        else _state.value.copy(isSaving = false, feedback = "Couldn't save these steps. Your preview is still here; try again.", isError = true)
        onSavingChanged(false)
    }

    private fun clean(steps: List<String>): List<String> = steps.map(String::trim).filter(String::isNotEmpty).also { require(it.isNotEmpty()) }
}
