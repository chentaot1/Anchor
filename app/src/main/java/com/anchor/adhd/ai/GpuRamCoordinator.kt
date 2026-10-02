package com.anchor.adhd.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One RAM owner at a time: Filament Engine vs llama GPU.
 * AI surface open → filamentAllowed=false. Unload llama never re-enables Filament
 * while that surface is still open. Close → keep the local model warm 2 minutes, then Filament.
 */
class GpuRamCoordinator(
    private val aiEngine: LocalAiEngine,
    private val scope: CoroutineScope,
) {
    private val _filamentAllowed = MutableStateFlow(true)
    val filamentAllowed: StateFlow<Boolean> = _filamentAllowed.asStateFlow()

    private var warmJob: Job? = null
    @Volatile
    private var aiSurfaceOpen: Boolean = false

    fun onAiSurfaceOpened() {
        warmJob?.cancel()
        aiSurfaceOpen = true
        _filamentAllowed.value = false
    }

    fun onAiSurfaceClosed() {
        if (!aiSurfaceOpen) return
        aiSurfaceOpen = false
        warmJob?.cancel()
        warmJob = scope.launch {
            delay(WARM_KEEP_MS)
            aiEngine.ensureUnloaded()
            if (!aiSurfaceOpen) _filamentAllowed.value = true
        }
    }

    suspend fun unloadNowAndWait() {
        warmJob?.cancel()
        aiEngine.ensureUnloaded()
        _filamentAllowed.value = GpuRamOccupancy.filamentAllowedAfterUnload(aiSurfaceOpen)
    }

    fun unloadNow() {
        scope.launch { unloadNowAndWait() }
    }

    companion object {
        const val WARM_KEEP_MS = 2 * 60 * 1000L
    }
}

internal object GpuRamOccupancy {
    fun filamentAllowedAfterUnload(aiSurfaceOpen: Boolean): Boolean = !aiSurfaceOpen
}

internal object InferenceLoadPolicy {
    fun keepResidentAfterGenerate(keepLoaded: Boolean): Boolean = keepLoaded

    fun contextCandidates(requested: Int, mobile: Int = LocalAiEngine.MOBILE_CONTEXT_SIZE): List<Int> =
        if (requested > mobile) listOf(requested, mobile).distinct() else listOf(requested)

    fun effectiveContextSize(requested: Int, maxThatFits: Int?): Int =
        if (maxThatFits == null) requested else minOf(requested, maxThatFits)

    fun effectiveGpuLayers(requested: Int, maxThatFit: Int?): Int =
        if (maxThatFit == null) requested else minOf(requested, maxThatFit)

    fun needsReload(
        loaded: Boolean,
        loadedPath: String?,
        path: String,
        loadedContextSize: Int,
        contextSize: Int,
        loadedGpuLayers: Int,
        gpuLayers: Int,
        bridgeLoaded: Boolean,
    ): Boolean = !loaded || !bridgeLoaded || loadedPath != path ||
        loadedContextSize != contextSize || loadedGpuLayers != gpuLayers
}
