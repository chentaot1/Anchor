package com.anchor.adhd.desktop.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

sealed interface LlamaServerStatus {
    data object Idle : LlamaServerStatus

    data object Starting : LlamaServerStatus

    data class Ready(
        val port: Int,
        val modelName: String,
        val binaryPath: String,
    ) : LlamaServerStatus

    data class Error(
        val message: String,
    ) : LlamaServerStatus

    data object Disabled : LlamaServerStatus
}

data class CompletionResult(
    val content: String,
    val predictedPerSecond: Float,
    val promptPerSecond: Float,
    val predictedTokens: Int,
    val durationMillis: Long,
)

/**
 * Manages the local on-device llama-server background process for MiniCPM5-1B-Claude-Opus-Fable5-Thinking-Q8_0
 * (with automatic fallbacks to Q5_K_M and Q4_K_M).
 * Communicates over localhost HTTP (/completion and /health).
 */
class LlamaServerClient(
    private val scope: CoroutineScope,
) {
    private val _status = MutableStateFlow<LlamaServerStatus>(LlamaServerStatus.Idle)
    val status: StateFlow<LlamaServerStatus> = _status.asStateFlow()

    private var process: Process? = null
    private var activePort: Int = 58321

    private val httpClient: HttpClient by lazy {
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build()
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    init {
        // Register shutdown hook to guarantee process cleanup
        Runtime.getRuntime().addShutdownHook(
            Thread {
                stopProcessInternal()
            },
        )
    }

    fun resolveBinary(preferredPath: String? = null): File? {
        if (!preferredPath.isNullOrBlank()) {
            val f = File(preferredPath)
            if (f.exists() && f.isFile) return f
        }

        val prop = System.getProperty("anchor.llama.path")
        if (!prop.isNullOrBlank()) {
            val f = File(prop)
            if (f.exists() && f.isFile) return f
        }

        val envDir = System.getenv("ANCHOR_LLAMA_DIR")
        if (!envDir.isNullOrBlank()) {
            val f = File(envDir, "llama-server.exe")
            if (f.exists() && f.isFile) return f
        }

        val userHome = System.getProperty("user.home")
        val downloadsDir = File(userHome, "Downloads")
        val appData = System.getenv("APPDATA") ?: "$userHome/AppData/Roaming"
        val appDataBinary = File(appData, "Anchor/bin/llama/llama-server.exe")
        if (appDataBinary.exists() && appDataBinary.isFile) return appDataBinary

        val candidates =
            listOf(
                File(appData, "Anchor/bin/llama/llama-server.exe"),
                File("bin/llama/llama-server.exe"),
                File("desktopApp/bin/llama/llama-server.exe"),
                File("../bin/llama/llama-server.exe"),
                File("../../bin/llama/llama-server.exe"),
                File("../../../bin/llama/llama-server.exe"),
                File("../../../../bin/llama/llama-server.exe"),
                File("../../../../../bin/llama/llama-server.exe"),
                File(downloadsDir, "anchor-adhd/desktopApp/bin/llama/llama-server.exe"),
            )

        return candidates.firstOrNull { it.exists() && it.isFile }
    }

    fun resolveModel(preferredPath: String? = null): File? {
        if (!preferredPath.isNullOrBlank()) {
            val f = File(preferredPath)
            if (f.exists() && f.isFile) return f
        }

        val prop = System.getProperty("anchor.model.path")
        if (!prop.isNullOrBlank()) {
            val f = File(prop)
            if (f.exists() && f.isFile) return f
        }

        val envModel = System.getenv("ANCHOR_MODEL_PATH")
        if (!envModel.isNullOrBlank()) {
            val f = File(envModel)
            if (f.exists() && f.isFile) return f
        }

        val userHome = System.getProperty("user.home")
        val downloadsDir = File(userHome, "Downloads")
        val appData = System.getenv("APPDATA") ?: "$userHome/AppData/Roaming"
        val candidates =
            listOf(
                File(downloadsDir, "MiniCPM5-1B-Claude-Opus-Fable5-Thinking-Q8_0.gguf"),
                File(downloadsDir, "MiniCPM5-1B-Claude-Opus-Fable5-Thinking-Q5_K_M.gguf"),
                File(downloadsDir, "MiniCPM5-1B-Claude-Opus-Fable5-Thinking-Q4_K_M.gguf"),
                File(downloadsDir, "MiniCPM5-1B-Q5_K_M.gguf"),
                File(downloadsDir, "MiniCPM5-1B-Q4_K_M.gguf"),
                File(downloadsDir, "Ling-3.0-tiny-Q5_K_M.gguf"),
                File(appData, "Anchor/models/MiniCPM5-1B-Claude-Opus-Fable5-Thinking-Q8_0.gguf"),
                File(appData, "Anchor/models/MiniCPM5-1B-Claude-Opus-Fable5-Thinking-Q5_K_M.gguf"),
                File(appData, "Anchor/models/MiniCPM5-1B-Claude-Opus-Fable5-Thinking-Q4_K_M.gguf"),
                File(appData, "Anchor/models/MiniCPM5-1B-Q5_K_M.gguf"),
                File(appData, "Anchor/models/Ling-3.0-tiny-Q5_K_M.gguf"),
            )

        return candidates.firstOrNull { it.exists() && it.isFile }
    }

    fun start(
        modelPath: String? = null,
        binaryPath: String? = null,
        port: Int = 58321,
        threads: Int = 8,
        contextLength: Int = 2048,
    ) {
        if (_status.value is LlamaServerStatus.Starting || _status.value is LlamaServerStatus.Ready) {
            return
        }

        activePort = port
        _status.value = LlamaServerStatus.Starting

        scope.launch(Dispatchers.IO) {
            val binary = resolveBinary(binaryPath)
            if (binary == null) {
                _status.value = LlamaServerStatus.Error("llama-server binary not found. Copied to %APPDATA%\\Anchor\\bin\\llama\\")
                return@launch
            }

            val model = resolveModel(modelPath)
            if (model == null) {
                _status.value = LlamaServerStatus.Error("Model file not found: MiniCPM5-1B-Claude-Opus-Fable5-Thinking-Q8_0.gguf")
                return@launch
            }

            // Check if an existing instance is already running on this port
            if (checkHealth(port)) {
                val loadedModel = getLoadedModelId(port)
                if (loadedModel != null && (loadedModel.contains(model.name, ignoreCase = true) || File(loadedModel).name.equals(model.name, ignoreCase = true))) {
                    _status.value = LlamaServerStatus.Ready(port, model.name, "http://127.0.0.1:$port")
                    return@launch
                } else if (loadedModel != null) {
                    // Different model is currently running on this port; stop it
                    stopProcessInternal()
                    delay(500)
                } else {
                    _status.value = LlamaServerStatus.Ready(port, model.name, "http://127.0.0.1:$port")
                    return@launch
                }
            }

            try {
                // Optimize thread count to match physical cores rather than hyperthreads (prevents L1/L2 cache thrashing)
                val effectiveThreads = if (threads in 1..4) threads else (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
                val useFlashAttention = !model.name.contains("Ling", ignoreCase = true)
                val command =
                    buildList {
                        add(binary.absolutePath)
                        add("-m")
                        add(model.absolutePath)
                        add("-c")
                        add(contextLength.toString()) // Constrain KV cache to 2048 tokens (~70MB KV cache vs 350MB default)
                        add("-np")
                        add("1") // Single slot: prevents allocating 4 slots, saving RAM on budget devices
                        add("-ctk")
                        add("q8_0") // Quantized K cache (50% memory reduction)
                        add("-ctv")
                        add("q8_0") // Quantized V cache (50% memory reduction)
                        if (useFlashAttention) {
                            add("-fa")
                            add("on") // Flash Attention for dense models
                        }
                        add("-t")
                        add(effectiveThreads.toString())
                        add("-tb")
                        add(effectiveThreads.toString())
                        add("--port")
                        add(port.toString())
                        add("--host")
                        add("127.0.0.1")
                        add("-ngl")
                        add("0") // CPU AVX2: 2x faster than integrated Intel UHD 620
                        add("-sps")
                        add("0.5") // Slot prompt similarity: reuses KV cache for identical system prompts
                        add("--reasoning")
                        add("off") // Disables internal monologue token capture for MiniCPM 5
                        add("--reasoning-budget")
                        add("0")
                        add("--cont-batching") // Dynamic continuous batching for lower interactive latency
                        add("--log-disable")
                    }

                val processBuilder = ProcessBuilder(command)
                processBuilder.directory(binary.parentFile)
                processBuilder.redirectErrorStream(true)
                val proc = processBuilder.start()
                process = proc

                // Poll health endpoint until ready (up to 45 seconds)
                val startTime = System.currentTimeMillis()
                var ready = false

                while (isActive && System.currentTimeMillis() - startTime < 45_000L) {
                    if (!proc.isAlive) {
                        val exitCode = proc.exitValue()
                        _status.value = LlamaServerStatus.Error("llama-server terminated unexpectedly (exit code $exitCode)")
                        return@launch
                    }

                    if (checkHealth(port)) {
                        ready = true
                        break
                    }
                    delay(500)
                }

                if (ready) {
                    _status.value = LlamaServerStatus.Ready(port, model.name, binary.absolutePath)
                } else {
                    stopProcessInternal()
                    _status.value = LlamaServerStatus.Error("Timed out waiting for llama-server to respond on port $port")
                }
            } catch (e: Exception) {
                _status.value = LlamaServerStatus.Error("Failed to start llama-server: ${e.message}")
            }
        }
    }

    fun stop() {
        stopProcessInternal()
        _status.value = LlamaServerStatus.Idle
    }

    private fun stopProcessInternal() {
        val p = process ?: return
        process = null
        try {
            if (p.isAlive) {
                p.destroy()
                p.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (p.isAlive) {
                    p.destroyForcibly()
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun checkHealth(port: Int): Boolean =
        try {
            val request =
                HttpRequest
                    .newBuilder()
                    .uri(URI.create("http://127.0.0.1:$port/health"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200
        } catch (_: Exception) {
            false
        }

    private fun getLoadedModelId(port: Int): String? =
        try {
            val request =
                HttpRequest
                    .newBuilder()
                    .uri(URI.create("http://127.0.0.1:$port/v1/models"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
            if (response.statusCode() == 200) {
                val root = json.parseToJsonElement(response.body()).jsonObject
                val data = root["data"]?.jsonArray
                data?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }

    suspend fun complete(
        prompt: String,
        grammar: String? = null,
        nPredict: Int = 384,
        temperature: Float = 0.2f,
        stopTokens: List<String> = listOf("<|role_end|>", "<role>", "<|im_end|>", "<|endoftext|>", "</s>"),
    ): CompletionResult? =
        withContext(Dispatchers.IO) {
            val currentStatus = _status.value
            val port = if (currentStatus is LlamaServerStatus.Ready) currentStatus.port else activePort

            val stopsJson = stopTokens.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }
            val escapedPrompt =
                prompt
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")

            val grammarJson =
                if (!grammar.isNullOrBlank()) {
                    val escapedGrammar =
                        grammar
                            .replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\n", "\\n")
                            .replace("\r", "\\r")
                            .replace("\t", "\\t")
                    ",\n    \"grammar\": \"$escapedGrammar\""
                } else {
                    ""
                }

            val payload =
                """
                {
                    "prompt": "$escapedPrompt",
                    "n_predict": $nPredict,
                    "temperature": $temperature,
                    "repeat_penalty": 1.15,
                    "cache_prompt": false,
                    "slot_id": -1,
                    "stop": [$stopsJson]$grammarJson
                }
                """.trimIndent()

            val startTime = System.currentTimeMillis()
            try {
                val request =
                    HttpRequest
                        .newBuilder()
                        .uri(URI.create("http://127.0.0.1:$port/completion"))
                        .timeout(Duration.ofSeconds(60))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofString(payload, Charsets.UTF_8))
                        .build()

                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
                if (response.statusCode() != 200) {
                    return@withContext null
                }

                val durationMillis = System.currentTimeMillis() - startTime
                val body = response.body()

                val root = json.parseToJsonElement(body).jsonObject
                val content = root["content"]?.jsonPrimitive?.content ?: ""
                val timings = root["timings"]?.jsonObject

                val predictedPerSec = timings?.get("predicted_per_second")?.jsonPrimitive?.floatOrNull ?: 0f
                val promptPerSec = timings?.get("prompt_per_second")?.jsonPrimitive?.floatOrNull ?: 0f
                val predictedN = timings?.get("predicted_n")?.jsonPrimitive?.intOrNull ?: content.split("\\s+".toRegex()).size

                CompletionResult(
                    content = content,
                    predictedPerSecond = predictedPerSec,
                    promptPerSecond = promptPerSec,
                    predictedTokens = predictedN,
                    durationMillis = durationMillis,
                )
            } catch (_: Exception) {
                null
            }
        }

    suspend fun chatCompletion(
        messages: List<Pair<String, String>>, // role to content
        maxTokens: Int = 256,
        temperature: Float = 0.4f,
    ): CompletionResult? =
        withContext(Dispatchers.IO) {
            val currentStatus = _status.value
            val port = if (currentStatus is LlamaServerStatus.Ready) currentStatus.port else activePort

            val messagesJson =
                messages.joinToString(",") { (role, content) ->
                    val escapedContent =
                        content
                            .replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\n", "\\n")
                            .replace("\r", "\\r")
                            .replace("\t", "\\t")
                    """{"role":"$role","content":"$escapedContent"}"""
                }

            val payload =
                """
                {
                    "messages": [$messagesJson],
                    "max_tokens": $maxTokens,
                    "temperature": $temperature,
                    "stream": false
                }
                """.trimIndent()

            val startTime = System.currentTimeMillis()
            try {
                val request =
                    HttpRequest
                        .newBuilder()
                        .uri(URI.create("http://127.0.0.1:$port/v1/chat/completions"))
                        .timeout(Duration.ofSeconds(60))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofString(payload, Charsets.UTF_8))
                        .build()

                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
                if (response.statusCode() != 200) {
                    return@withContext null
                }

                val durationMillis = System.currentTimeMillis() - startTime
                val body = response.body()

                val root = json.parseToJsonElement(body).jsonObject
                val choices = root["choices"]?.jsonArray
                val firstChoice = choices?.firstOrNull()?.jsonObject
                val messageObj = firstChoice?.get("message")?.jsonObject
                val content = messageObj?.get("content")?.jsonPrimitive?.content ?: ""

                val timings = root["timings"]?.jsonObject
                val predictedPerSec = timings?.get("predicted_per_second")?.jsonPrimitive?.floatOrNull ?: 0f
                val promptPerSec = timings?.get("prompt_per_second")?.jsonPrimitive?.floatOrNull ?: 0f
                val predictedN = timings?.get("predicted_n")?.jsonPrimitive?.intOrNull ?: content.split("\\s+".toRegex()).size

                CompletionResult(
                    content = content,
                    predictedPerSecond = predictedPerSec,
                    promptPerSecond = promptPerSec,
                    predictedTokens = predictedN,
                    durationMillis = durationMillis,
                )
            } catch (_: Exception) {
                null
            }
        }
}
