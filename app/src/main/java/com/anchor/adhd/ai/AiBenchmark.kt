package com.anchor.adhd.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import kotlin.math.roundToInt

data class AiBenchmarkResult(
    val modelLabel: String,
    val modelPath: String,
    val loadMs: Long,
    val tokensGenerated: Int,
    val generateMs: Long,
    val tokensPerSecond: Float,
    val gpuLayersRequested: Int,
    val gpuLayersLoaded: Int,
    val contextSize: Int,
    val peakNativeHeapMb: Int,
    val availableRamMb: Int,
    val success: Boolean,
    val error: String? = null
) {
    val summary: String
        get() = if (!success) {
            error ?: "Benchmark failed"
        } else {
            buildString {
                appendLine("Model: $modelLabel")
                appendLine("Load: ${loadMs}ms · Generate: ${generateMs}ms")
                appendLine("Tokens: $tokensGenerated (${"%.1f".format(tokensPerSecond)} tok/s)")
                appendLine("GPU layers: $gpuLayersLoaded (requested $gpuLayersRequested)")
                appendLine("Context: $contextSize · Native heap peak: ${peakNativeHeapMb}MB")
                appendLine("RAM available before load: ${availableRamMb}MB")
            }.trim()
        }
}

class AiBenchmarkRunner(
    private val context: Context,
    private val engine: LocalAiEngine
) {
    suspend fun run(modelPath: String, modelLabel: String): AiBenchmarkResult {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        val availMb = (mem.availMem / (1024 * 1024)).toInt()

        val heapBefore = (Debug.getNativeHeapAllocatedSize() / (1024 * 1024)).toInt()
        var heapPeak = heapBefore

        fun sampleHeap() {
            val now = (Debug.getNativeHeapAllocatedSize() / (1024 * 1024)).toInt()
            if (now > heapPeak) heapPeak = now
        }

        return try {
            engine.withInferenceLock {
                val loadMs = kotlin.system.measureTimeMillis {
                    engine.loadForBenchmark(modelPath)
                    sampleHeap()
                }
                val gpuLoaded = LlamaBridge.loadedGpuLayers()
                val bench = LlamaBridge.benchmark(
                    prompt = "Output JSON only: {\"steps\":[\"Test step\"],\"next_action\":\"Test step\",\"minutes_estimate\":[5]}",
                    maxTokens = 64
                )
                sampleHeap()
                engine.unloadForBenchmark()

                val tps = if (bench.generateMs > 0) {
                    bench.tokensGenerated * 1000f / bench.generateMs
                } else {
                    0f
                }

                AiBenchmarkResult(
                    modelLabel = modelLabel,
                    modelPath = modelPath,
                    loadMs = loadMs,
                    tokensGenerated = bench.tokensGenerated,
                    generateMs = bench.generateMs,
                    tokensPerSecond = (tps * 10).roundToInt() / 10f,
                    gpuLayersRequested = engine.gpuLayersForLoad(),
                    gpuLayersLoaded = gpuLoaded,
                    contextSize = LocalAiEngine.MOBILE_CONTEXT_SIZE,
                    peakNativeHeapMb = heapPeak,
                    availableRamMb = availMb,
                    success = bench.tokensGenerated > 0
                )
            }
        } catch (e: Exception) {
            engine.runCatching { engine.unloadForBenchmark() }
            AiBenchmarkResult(
                modelLabel = modelLabel,
                modelPath = modelPath,
                loadMs = 0,
                tokensGenerated = 0,
                generateMs = 0,
                tokensPerSecond = 0f,
                gpuLayersRequested = engine.gpuLayersForLoad(),
                gpuLayersLoaded = 0,
                contextSize = LocalAiEngine.MOBILE_CONTEXT_SIZE,
                peakNativeHeapMb = heapPeak,
                availableRamMb = availMb,
                success = false,
                error = e.message
            )
        }
    }
}
