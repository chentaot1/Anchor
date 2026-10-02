package com.anchor.adhd.ai

import android.content.Context
import com.anchor.adhd.data.prefs.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * On-device inference via llama.cpp JNI.
 * Uses the desktop's verified MiniCPM5 Q8_0 profile with reasoning disabled.
 */
class LocalAiEngine(
    private val context: Context,
    private val preferences: UserPreferences
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val inferenceMutex = Mutex()
    private var loaded = false
    private var loadedPath: String? = null
    private var loadedContextSize: Int = 0
    private var loadedGpuLayers: Int = -1
    private var maxContextThatFits: Int? = null
    private var maxGpuLayersThatFit: Int? = null
    private var lastUserGpuLayers: Int = -1

    val isModelReady: Boolean
        get() = preferences.isModelDownloaded()

    /** Serialize all inference and explicit unload paths (AI jobs, focus start). */
    suspend fun <T> withInferenceLock(block: suspend () -> T): T = inferenceMutex.withLock { block() }

    suspend fun ensureUnloaded() = withInferenceLock {
        unloadLocked()
    }

    suspend fun gpuLayersForLoad(): Int {
        if (!preferences.isGpuOffloadEnabled()) return 0
        return preferences.getGpuLayerCount().coerceIn(0, 99)
    }

    suspend fun generateJson(
        systemPrompt: String,
        userPrompt: String,
        grammar: String,
        maxTokens: Int = DEFAULT_MAX_TOKENS,
        keepLoaded: Boolean = true,
        contextSize: Int = CHAT_CONTEXT_SIZE
    ): String = withInferenceLock {
        ensureLoadedLocked(contextSize)
        try {
            val builder = StringBuilder()
            val fullPrompt = buildPrompt(systemPrompt, userPrompt)
            LlamaBridge.generate(fullPrompt, maxTokens, grammar).collect { builder.append(it) }
            extractJson(builder.toString().trim())
        } finally {
            if (!InferenceLoadPolicy.keepResidentAfterGenerate(keepLoaded)) {
                unloadLocked()
            }
        }
    }

    fun generate(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int = DEFAULT_MAX_TOKENS,
        grammar: String = "",
        keepLoaded: Boolean = true,
        contextSize: Int = CHAT_CONTEXT_SIZE,
        history: List<Pair<String, String>> = emptyList()
    ): Flow<String> = flow {
        withInferenceLock {
            ensureLoadedLocked(contextSize)
            try {
                val fullPrompt = DesktopInferenceProfile.prompt(systemPrompt, userPrompt, history)
                LlamaBridge.generate(fullPrompt, maxTokens, grammar).collect { token ->
                    emit(token)
                }
            } finally {
                if (!InferenceLoadPolicy.keepResidentAfterGenerate(keepLoaded)) {
                    unloadLocked()
                }
            }
        }
    }

    suspend fun loadForBenchmark(path: String) {
        if (!loadWithFallbacks(path, MOBILE_CONTEXT_SIZE)) {
            throw IllegalStateException(
                "Unable to load the desktop model. Close other apps to free memory and retry."
            )
        }
        loaded = true
        loadedPath = path
        loadedContextSize = MOBILE_CONTEXT_SIZE
        loadedGpuLayers = LlamaBridge.loadedGpuLayers()
    }

    suspend fun unloadForBenchmark() {
        unloadLocked()
    }

    private suspend fun ensureLoadedLocked(contextSize: Int = MOBILE_CONTEXT_SIZE) {
        if (!preferences.isModelDownloaded()) {
            throw IllegalStateException("Desktop model is being installed or is unavailable — check Settings")
        }
        val path = preferences.getActiveModelPath()
        if (loadedPath != null && loadedPath != path) {
            maxContextThatFits = null
        }
        val requestedGpu = gpuLayersForLoad()
        if (requestedGpu != lastUserGpuLayers) {
            maxGpuLayersThatFit = null
            lastUserGpuLayers = requestedGpu
        }
        val ctx = InferenceLoadPolicy.effectiveContextSize(contextSize, maxContextThatFits)
        val gpu = InferenceLoadPolicy.effectiveGpuLayers(requestedGpu, maxGpuLayersThatFit)
        if (!InferenceLoadPolicy.needsReload(
                loaded, loadedPath, path, loadedContextSize, ctx, loadedGpuLayers, gpu, LlamaBridge.isLoaded()
            )
        ) {
            return
        }
        unloadLocked()
        val sizes = listOf(DesktopInferenceProfile.CONTEXT_SIZE)
        for (size in sizes) {
            if (loadWithFallbacks(path, size, requestedGpu)) {
                loaded = true
                loadedPath = path
                loadedContextSize = size
                loadedGpuLayers = LlamaBridge.loadedGpuLayers()
                if (size < contextSize) maxContextThatFits = size
                if (loadedGpuLayers < requestedGpu) maxGpuLayersThatFit = loadedGpuLayers
                return
            }
        }
        throw IllegalStateException(
            "Failed to load the desktop model with its 2048-token CPU profile. Free memory and retry."
        )
    }

    private suspend fun loadWithFallbacks(
        primaryPath: String,
        contextSize: Int = MOBILE_CONTEXT_SIZE,
        requestedGpu: Int = -1,
    ): Boolean {
        val gpuLayers = if (requestedGpu >= 0) {
            InferenceLoadPolicy.effectiveGpuLayers(requestedGpu, maxGpuLayersThatFit)
        } else {
            gpuLayersForLoad()
        }
        val candidates = buildList {
            add(primaryPath)
        }.distinct()

        for (path in candidates) {
            if (tryLoad(path, contextSize, gpuLayers)) return true
            if (gpuLayers > 0 && tryLoad(path, contextSize, 0)) return true
        }
        return false
    }

    private fun tryLoad(path: String, contextSize: Int, gpuLayers: Int): Boolean {
        if (!java.io.File(path).exists()) return false
        return LlamaBridge.load(path, contextSize, gpuLayers)
    }

    private fun modelFile(filename: String) =
        context.filesDir.resolve("models/$filename")

    private fun unloadLocked() {
        if (loaded || LlamaBridge.isLoaded()) {
            LlamaBridge.unload()
        }
        loaded = false
        loadedPath = null
        loadedContextSize = 0
        loadedGpuLayers = -1
    }

    private fun buildPrompt(system: String, user: String): String {
        return DesktopInferenceProfile.prompt(system, user)
    }

    private fun extractJson(raw: String): String {
        val stripped = stripThinkingBlocks(raw).trim()
        val start = stripped.indexOf('{')
        val end = stripped.lastIndexOf('}')
        if (start >= 0 && end > start) return stripped.substring(start, end + 1)
        val arrStart = stripped.indexOf('[')
        val arrEnd = stripped.lastIndexOf(']')
        if (arrStart >= 0 && arrEnd > arrStart) return stripped.substring(arrStart, arrEnd + 1)
        return stripped
    }

    private fun stripThinkingBlocks(text: String): String {
        var result = text
        val open = "<" + "think" + ">"
        val close = "</" + "think" + ">"
        while (true) {
            val start = result.indexOf(open)
            if (start < 0) break
            val end = result.indexOf(close, start + open.length)
            if (end < 0) break
            result = result.removeRange(start, end + close.length)
        }
        return result
    }

    companion object {
        /** Keep both task and chat contexts identical to the active desktop profile. */
        const val MOBILE_CONTEXT_SIZE = DesktopInferenceProfile.CONTEXT_SIZE
        const val CHAT_CONTEXT_SIZE = DesktopInferenceProfile.CONTEXT_SIZE

        const val DEFAULT_MAX_TOKENS = DesktopInferenceProfile.MAX_TOKENS
        const val DEFAULT_GPU_LAYERS = DesktopInferenceProfile.GPU_LAYERS

        const val BREAKDOWN_SYSTEM = """Break a task into concrete, low-friction micro-steps for someone with ADHD. Keep it practical and grounding.
Rules: Step 1 MUST be a 2-minute frictionless physical starter (e.g. "Open document and type title", "Put dishes in sink", "Open IDE and write failing test").
Provide 3-5 domain-tailored steps (writing, coding, chores, study, admin). Avoid generic placeholders.
Each step under 20 words; exactly one next_action matching step 1.
Output ONLY valid JSON format:
{"steps": ["Step 1", "Step 2", "Step 3"], "next_action": "Step 1", "minutes_estimate": [2, 10, 15]}"""

        const val BRAINDUMP_SYSTEM = """Turn a chaotic brain dump into a clean, actionable list of distinct tasks. Keep each title brief.
Max 8 tasks. Remove filler words and anxiety spirals. Output ONLY valid JSON with a "tasks" array of strings."""

        const val TRIAGE_SYSTEM = """Order tasks for someone with ADHD: quick wins first, then medium, defer the heavy ones.
Output ONLY valid JSON with an "ordered_task_titles" array of strings."""

        const val REPLAN_SYSTEM = """Help someone get back on track after a derailed day. Recommend 1-2 tasks for today, defer the rest, kindly.
Output ONLY valid JSON with "recommended_titles" and "defer_titles" arrays of strings."""

        const val WEEKLY_SYSTEM = """
            Create a gentle weekly plan for a student with ADHD. Break it into steps with times.
            Output breakdown JSON with steps, next_action, minutes_estimate. JSON only.
        """

        const val IF_THEN_SYSTEM = """
            Write one simple implementation intention (if-then) for a routine. Output JSON with an if_then field. JSON only.
        """

        const val CHAT_TURN_SYSTEM = """
            You are Anchor, a personal ADHD planner for a student. Short steps. No coach-speak.
            Classify the request into exactly one intent and a short query string.
            Output ONLY JSON {"intent":"<verb>","query":"..."}.
            Verbs: capture_tasks, whats_today, remind, mark_done, move_someday, breakdown, brain_dump, schedule, triage, replan, start_focus, snooze, pick_one, chat, clarify.
        """

        const val CHAT_RESPONSE_SYSTEM = """You are Anchor, a compassionate personal ADHD executive coach. Speak directly to the user in a warm, gentle, grounding voice (2-4 sentences max).
Validate their feeling or situation in one sentence, then give them exactly ONE tiny, frictionless 2-minute physical starter step to bypass task paralysis.
Never output meta-instructions, guidelines, system rules, thinking tags, or third-person analysis. Speak directly to the person as "you"."""


    }
}
