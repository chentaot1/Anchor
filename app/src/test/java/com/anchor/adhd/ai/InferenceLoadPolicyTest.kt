package com.anchor.adhd.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceLoadPolicyTest {
    @Test
    fun keepLoaded_doesNotUnload() {
        assertTrue(InferenceLoadPolicy.keepResidentAfterGenerate(true))
        assertFalse(InferenceLoadPolicy.keepResidentAfterGenerate(false))
    }

    @Test
    fun contextCandidates_dropsTo1536() {
        assertEquals(listOf(2048, 1536), InferenceLoadPolicy.contextCandidates(2048, 1536))
        assertEquals(listOf(1536), InferenceLoadPolicy.contextCandidates(1536, 1536))
    }

    @Test
    fun effectiveContextSize_capsAtWhatFit() {
        assertEquals(1536, InferenceLoadPolicy.effectiveContextSize(2048, 1536))
        assertEquals(2048, InferenceLoadPolicy.effectiveContextSize(2048, null))
    }

    @Test
    fun needsReload_whenContextChanges() {
        assertTrue(
            InferenceLoadPolicy.needsReload(
                loaded = true,
                loadedPath = "/m.gguf",
                path = "/m.gguf",
                loadedContextSize = 1536,
                contextSize = 2048,
                loadedGpuLayers = 28,
                gpuLayers = 28,
                bridgeLoaded = true
            )
        )
        assertFalse(
            InferenceLoadPolicy.needsReload(
                loaded = true,
                loadedPath = "/m.gguf",
                path = "/m.gguf",
                loadedContextSize = 1536,
                contextSize = 1536,
                loadedGpuLayers = 28,
                gpuLayers = 28,
                bridgeLoaded = true
            )
        )
    }

    @Test
    fun needsReload_whenGpuLayersChange() {
        assertTrue(
            InferenceLoadPolicy.needsReload(
                loaded = true,
                loadedPath = "/m.gguf",
                path = "/m.gguf",
                loadedContextSize = 2048,
                contextSize = 2048,
                loadedGpuLayers = 28,
                gpuLayers = 0,
                bridgeLoaded = true
            )
        )
    }

    @Test
    fun filamentStaysOffWhileAiSurfaceOpen() {
        assertFalse(GpuRamOccupancy.filamentAllowedAfterUnload(aiSurfaceOpen = true))
        assertTrue(GpuRamOccupancy.filamentAllowedAfterUnload(aiSurfaceOpen = false))
    }
}
