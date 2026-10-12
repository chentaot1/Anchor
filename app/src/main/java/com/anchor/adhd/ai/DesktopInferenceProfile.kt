package com.anchor.adhd.ai

/** Verified against the running desktop llama-server on 2026-09-30. */
object DesktopInferenceProfile {
    const val CONTEXT_SIZE = 2048
    const val THREADS = 4
    const val GPU_LAYERS = 0
    const val MAX_TOKENS = 256
    const val JSON_TEMPERATURE = 0.2f
    const val CHAT_TEMPERATURE = 0.4f
    const val TOP_K = 40
    const val TOP_P = 0.95f
    const val MIN_P = 0.05f
    const val REPEAT_LAST_N = 64
    const val JSON_REPEAT_PENALTY = 1.15f
    const val CHAT_REPEAT_PENALTY = 1.0f
    const val MODEL_SHA256 = "8125095ae223278e728adb4148a8a466cec920929c1de5a5f5ebcb479ec31a2c"
    const val MODEL_BYTES = 1_153_529_792L

    fun prompt(system: String, user: String, history: List<Pair<String, String>> = emptyList()): String = buildString {
        append("<|im_start|>system\n${system.trim()}<|im_end|>\n")
        history.filter { it.first == "user" || it.first == "assistant" }.forEach { (role, content) ->
            append("<|im_start|>$role\n${content.trim()}<|im_end|>\n")
        }
        append("<|im_start|>user\n${user.trim()}<|im_end|>\n")
        append("<|im_start|>assistant\n<think>\n\n</think>\n\n")
    }
}
