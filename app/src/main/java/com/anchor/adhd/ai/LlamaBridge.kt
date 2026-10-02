package com.anchor.adhd.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

data class NativeBenchmarkResult(
    val tokensGenerated: Int,
    val generateMs: Long
)

/**
 * JNI bridge to llama.cpp. Falls back to [StubLlamaBridge] if native lib is missing.
 */
interface LlamaRuntime {
    fun load(modelPath: String, contextSize: Int, gpuLayers: Int): Boolean
    fun unload()
    fun isLoaded(): Boolean
    fun loadedGpuLayers(): Int
    fun generate(prompt: String, maxTokens: Int, grammar: String): Flow<String>
    fun abortGenerate()
    fun benchmark(prompt: String, maxTokens: Int): NativeBenchmarkResult
}

object LlamaBridge {
    private val nativeRuntime: LlamaRuntime? = runCatching { NativeLlamaBridge() }.getOrNull()

    val isNativeAvailable: Boolean
        get() = nativeRuntime != null

    private val runtime: LlamaRuntime = nativeRuntime ?: StubLlamaBridge()

    fun load(modelPath: String, contextSize: Int, gpuLayers: Int = 0): Boolean =
        runtime.load(modelPath, contextSize, gpuLayers)

    fun unload() = runtime.unload()
    fun isLoaded(): Boolean = runtime.isLoaded()
    fun loadedGpuLayers(): Int = runtime.loadedGpuLayers()

    fun generate(prompt: String, maxTokens: Int, grammar: String = ""): Flow<String> =
        runtime.generate(prompt, maxTokens, grammar)

    fun abortGenerate() = runtime.abortGenerate()

    fun benchmark(prompt: String, maxTokens: Int): NativeBenchmarkResult =
        runtime.benchmark(prompt, maxTokens)
}

/** Rule-based fallback when native lib is unavailable (emulator / failed link). */
class StubLlamaBridge : LlamaRuntime {
    private var loaded = false

    override fun load(modelPath: String, contextSize: Int, gpuLayers: Int): Boolean {
        loaded = modelPath.isNotBlank()
        return loaded
    }

    override fun unload() {
        loaded = false
    }

    override fun isLoaded(): Boolean = loaded

    override fun loadedGpuLayers(): Int = 0

    override fun abortGenerate() = Unit

    override fun generate(prompt: String, maxTokens: Int, grammar: String): Flow<String> = flow {
        if (!loaded) error("Model not loaded")
        val json = """
            {"steps":["Open the materials","Do the smallest first step","Work for one focus block"],"next_action":"Open the materials","minutes_estimate":[5,15,25],"if_then":"If I sit at my desk, then I open the materials."}
        """.trimIndent()
        emit(json)
    }

    override fun benchmark(prompt: String, maxTokens: Int): NativeBenchmarkResult =
        NativeBenchmarkResult(tokensGenerated = 32, generateMs = 1200)
}

class NativeLlamaBridge : LlamaRuntime {
    init {
        System.loadLibrary("anchor_llama")
    }

    fun interface TokenCallback {
        fun onToken(token: String)
    }

    external fun nativeLoad(modelPath: String, contextSize: Int, gpuLayers: Int): Boolean
    external fun nativeUnload()
    external fun nativeIsLoaded(): Boolean
    external fun nativeLoadedGpuLayers(): Int
    external fun nativeAbort()
    external fun nativeGenerate(prompt: String, maxTokens: Int, grammar: String, temperature: Float, repeatPenalty: Float, callback: TokenCallback)
    external fun nativeBenchmark(prompt: String, maxTokens: Int): LongArray

    override fun load(modelPath: String, contextSize: Int, gpuLayers: Int): Boolean =
        nativeLoad(modelPath, contextSize, gpuLayers)

    override fun unload() = nativeUnload()
    override fun isLoaded(): Boolean = nativeIsLoaded()
    override fun loadedGpuLayers(): Int = nativeLoadedGpuLayers()
    override fun abortGenerate() = nativeAbort()

    override fun generate(prompt: String, maxTokens: Int, grammar: String): Flow<String> = callbackFlow {
        val job = launch(Dispatchers.IO) {
            try {
                val structured = grammar.isNotBlank()
                nativeGenerate(prompt, maxTokens, grammar,
                    if (structured) DesktopInferenceProfile.JSON_TEMPERATURE else DesktopInferenceProfile.CHAT_TEMPERATURE,
                    if (structured) DesktopInferenceProfile.JSON_REPEAT_PENALTY else DesktopInferenceProfile.CHAT_REPEAT_PENALTY,
                    TokenCallback { token ->
                    trySend(token)
                })
                close()
            } catch (t: Throwable) {
                close(t)
            }
        }
        awaitClose {
            nativeAbort()
            job.cancel()
        }
    }.buffer(Channel.UNLIMITED)

    override fun benchmark(prompt: String, maxTokens: Int): NativeBenchmarkResult {
        val arr = nativeBenchmark(prompt, maxTokens)
        return NativeBenchmarkResult(
            tokensGenerated = arr.getOrNull(0)?.toInt() ?: -1,
            generateMs = arr.getOrNull(1) ?: 0L
        )
    }
}
