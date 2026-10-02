package com.anchor.adhd.domain

data class AirlockParseResult(
    val primaryTask: String,
    val secondaryTasks: List<String>,
    val defaultSomaticStarter: String
)

/**
 * Tier-1 Deterministic Executive Sequencer (< 5ms).
 *
 * Separates the primary focus task from secondary items using regex splitting,
 * archives secondary items, and creates an immediate physical micro-step (somatic starter)
 * without loading any machine learning models into memory.
 */
object AirlockHeuristic {
    private val DELIMITER_REGEX = Regex(
        "[,;\\n]+|\\b(?:and then|then|also|after that|and)\\b|(?<=\\S)\\s+(?=\\d+[\\.\\)])",
        RegexOption.IGNORE_CASE
    )

    private val ACTION_VERB_REGEX = Regex(
        "^\\s*(open|write|read|email|send|call|clean|draft|buy|check|review|study|submit|print|finish|start|fix|wash|pack|code|edit)\\b",
        RegexOption.IGNORE_CASE
    )

    fun parse(rawText: String): AirlockParseResult {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            return AirlockParseResult(
                primaryTask = "",
                secondaryTasks = emptyList(),
                defaultSomaticStarter = ""
            )
        }

        val rawItems = trimmed
            .split(DELIMITER_REGEX)
            .map { it.trim().replace(Regex("""^\d+[\.\)]\s*"""), "").trim() }
            .filter { it.isNotBlank() }

        val items = if (rawItems.isEmpty()) listOf(trimmed) else rawItems
        val primary = items.first()
        val secondaries = items.drop(1)

        val starter = generateDefaultSomaticStarter(primary)

        return AirlockParseResult(
            primaryTask = primary,
            secondaryTasks = secondaries,
            defaultSomaticStarter = starter
        )
    }

    fun generateDefaultSomaticStarter(primaryTask: String): String {
        val clean = primaryTask.trim()
        if (clean.isBlank()) return "Take a deep breath and begin."
        val match = ACTION_VERB_REGEX.find(clean)
        return if (match != null) {
            "Just ${clean.replaceFirstChar { it.lowercase() }}"
        } else {
            "Just open materials for \"$clean\""
        }
    }

    fun extractCleanSomaticStarter(raw: String, fallback: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return fallback

        val nextActionMatch = Regex("\"next_action\"\\s*:\\s*\"([^\"]+)\"").find(trimmed)
        val candidate = if (nextActionMatch != null) {
            nextActionMatch.groupValues[1]
        } else {
            val firstStepMatch = Regex("\"steps\"\\s*:\\s*\\[\\s*\"([^\"]+)\"").find(trimmed)
            if (firstStepMatch != null) {
                firstStepMatch.groupValues[1]
            } else if (trimmed.startsWith("{") || trimmed.contains("{")) {
                return fallback
            } else {
                trimmed
            }
        }

        val cleaned = candidate
            .replace(Regex("^(First physical step:|First step:|Step 1:|Step:)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^Just\\s+", RegexOption.IGNORE_CASE), "")
            .trim('"', ' ', '\n', '\r', '.', '{', '}', '[', ']')

        return if (cleaned.isNotBlank() && !cleaned.contains("{") && !cleaned.contains("[")) {
            "Just $cleaned"
        } else {
            fallback
        }
    }
}
