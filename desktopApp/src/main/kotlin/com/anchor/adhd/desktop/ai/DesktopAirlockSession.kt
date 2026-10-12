package com.anchor.adhd.desktop.ai

import com.anchor.adhd.desktop.db.DesktopAirlockSave
import com.anchor.adhd.desktop.db.DesktopAirlockTask
import com.anchor.adhd.domain.AirlockHeuristic
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

internal enum class DesktopAirlockMode { STEPS, TASKS }

internal data class DesktopAirlockPreview(
    val key: String,
    val input: String,
    val mode: DesktopAirlockMode,
    val items: List<String>,
    val secondaryTasks: List<String>,
)

internal data class DesktopAirlockState(
    val isOpen: Boolean = false,
    val draft: String = "",
    val preview: DesktopAirlockPreview? = null,
    val isRefining: Boolean = false,
    val isSaving: Boolean = false,
    val feedback: String? = null,
    val isError: Boolean = false,
)

/** App-owned draft and preview; generation and persistence each acknowledge their own request. */
internal class DesktopAirlockSession(
    private val scope: CoroutineScope,
    private val allowed: () -> Boolean = { true },
    private val quick: (DesktopAirlockMode, String) -> List<String> = { mode, text ->
        if (mode == DesktopAirlockMode.STEPS) DesktopAiEngine.generateMicroSteps(text)
        else DesktopAiEngine.parseBrainDump(text)
    },
    private val canRefine: () -> Boolean = DesktopAiEngine::isModelReady,
    private val enrich: suspend (DesktopAirlockMode, String) -> List<String> = { mode, text ->
        if (mode == DesktopAirlockMode.STEPS) DesktopAiEngine.generateMicroStepsAsync(text)
        else DesktopAiEngine.parseBrainDumpAsync(text)
    },
    private val save: suspend (DesktopAirlockSave) -> Unit,
) {
    private val _state = MutableStateFlow(DesktopAirlockState())
    val state = _state.asStateFlow()
    val isSaving: Boolean get() = _state.value.isSaving
    private var generation = 0L
    private var refining: Job? = null
    private var actionId: Long? = null

    @Synchronized fun open() { _state.value = _state.value.copy(isOpen = true) }

    @Synchronized fun dismiss() {
        if (isSaving) return
        invalidate()
        _state.value = _state.value.copy(isOpen = false, isRefining = false)
    }

    @Synchronized fun edit(text: String) {
        if (isSaving || text == _state.value.draft) return
        invalidate()
        _state.value = _state.value.copy(draft = text, isRefining = false, feedback = null, isError = false)
    }

    @Synchronized fun generate(mode: DesktopAirlockMode): Boolean {
        if (isSaving || _state.value.draft.isBlank()) return false
        if (!scope.isActive) return unavailable()
        val input = _state.value.draft.trim()
        val parsed = AirlockHeuristic.parse(input)
        val target = if (mode == DesktopAirlockMode.STEPS) parsed.primaryTask.ifBlank { input } else input
        val items = try { clean(quick(mode, target)).also { require(it.isNotEmpty()) } }
        catch (_: Exception) {
            _state.value = _state.value.copy(feedback = "Couldn't prepare this preview. Your text is still here; try again.", isError = true)
            return false
        }
        invalidate()
        val token = generation
        val shouldRefine = runCatching(canRefine).getOrDefault(false)
        _state.value = _state.value.copy(
            preview = DesktopAirlockPreview(UUID.randomUUID().toString(), input, mode, items,
                if (mode == DesktopAirlockMode.STEPS) parsed.secondaryTasks else emptyList()),
            isRefining = shouldRefine, feedback = null, isError = false,
        )
        if (shouldRefine) {
            val job = scope.launch {
                try { publish(token, clean(enrich(mode, target))) }
                catch (_: CancellationException) { }
                catch (_: Exception) { refinementFailed(token) }
                finally { finishRefinement(token) }
            }
            refining = job
            job.invokeOnCompletion { finishRefinement(token) }
        }
        return true
    }

    /** A key and index from the displayed preview prevent stale controls from saving newer content. */
    @Synchronized fun savePreview(key: String, anchorIndex: Int? = null): Boolean {
        val preview = _state.value.preview ?: return false
        if (isSaving || preview.key != key || (anchorIndex != null && anchorIndex !in preview.items.indices)) return false
        if (!scope.isActive || !runCatching(allowed).getOrDefault(false)) return unavailable()
        val tasks = preview.items.map { DesktopAirlockTask(it, if (preview.mode == DesktopAirlockMode.STEPS) 5 else 15) } +
            preview.secondaryTasks.map { DesktopAirlockTask(it, 15) }
        val request = DesktopAirlockSave(preview.key, tasks, anchorIndex)
        invalidate() // Freeze the visible payload and its retry identity before persistence starts.
        val token = generation
        actionId = token
        _state.value = _state.value.copy(isRefining = false, isSaving = true, feedback = null, isError = false)
        val job = scope.launch {
            withContext(NonCancellable) {
                try { save(request); finishSave(token, preview.input, true) }
                catch (_: Exception) { finishSave(token, preview.input, false) }
            }
        }
        job.invokeOnCompletion { finishSave(token, preview.input, false) }
        return true
    }

    private fun unavailable(): Boolean {
        _state.value = _state.value.copy(feedback = "This action is unavailable right now. Your text and preview are still here; try again shortly.", isError = true)
        return false
    }

    private fun invalidate() {
        generation++
        val old = refining
        refining = null
        old?.cancel()
    }

    @Synchronized private fun publish(token: Long, items: List<String>) {
        if (token != generation || items.isEmpty()) return
        _state.value = _state.value.copy(preview = _state.value.preview?.copy(key = UUID.randomUUID().toString(), items = items))
    }

    @Synchronized private fun refinementFailed(token: Long) {
        if (token != generation) return
        _state.value = _state.value.copy(feedback = "Extra guidance is unavailable. You can use this preview or generate again.")
    }

    @Synchronized private fun finishRefinement(token: Long) {
        if (token != generation) return
        refining = null
        _state.value = _state.value.copy(isRefining = false)
    }

    @Synchronized private fun finishSave(token: Long, input: String, success: Boolean) {
        if (actionId != token) return
        actionId = null
        _state.value = if (success) DesktopAirlockState(
            draft = _state.value.draft.takeUnless { it.trim() == input }.orEmpty(),
            feedback = "Added to Milestones.",
        ) else _state.value.copy(isSaving = false, isError = true,
            feedback = "Couldn't save these tasks. Your text and preview are still here; try again.")
    }

    private fun clean(items: List<String>) = items.map(String::trim).filter(String::isNotEmpty)
}
