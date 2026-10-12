package com.anchor.adhd.desktop.blocker

import java.util.concurrent.ConcurrentHashMap

enum class ScopeVerdict {
    IN_SCOPE,
    OUT_OF_SCOPE,
    UNKNOWN,
}

data class ScopeEvaluationResult(
    val verdict: ScopeVerdict,
    val category: String,
    val reason: String,
    val source: String, // "HEURISTIC", "LOCAL_AI", "CACHE", "USER"
)

/** Evaluates focus scope against the current task, without a built-in subject policy. */
object DesktopScopeSentinel {
    private val WORKSPACE_KEYWORDS = listOf(
        "canvas", "blackboard", "moodle", "instructure", "google docs", "google drive",
        "google classroom", "overleaf", "zotero", "mendeley", "syllabus", "lecture notes",
    )

    // Thread-safe in-memory cache to guarantee 0ms overhead on repeat tabs
    private val classificationCache = ConcurrentHashMap<String, ScopeEvaluationResult>()
    private val keywordRegexCache = ConcurrentHashMap<String, Regex>()

    fun clearCache() {
        classificationCache.clear()
    }

    private fun putCacheBounded(key: String, value: ScopeEvaluationResult) {
        if (classificationCache.size > 500) {
            classificationCache.clear()
        }
        classificationCache[key] = value
    }

    /**
     * Matches [keyword] against [text] using word boundaries on alphanumeric edges
     * so task words do not match inside unrelated longer words.
     */
    private fun matchesKeyword(text: String, keyword: String): Boolean {
        if (keyword.isBlank() || !text.contains(keyword)) return false
        val regex = keywordRegexCache.getOrPut(keyword) {
            val prefix = if (keyword.first().isLetterOrDigit()) "\\b" else ""
            val suffix = if (keyword.last().isLetterOrDigit()) "\\b" else ""
            Regex("$prefix${Regex.escape(keyword)}$suffix", RegexOption.IGNORE_CASE)
        }
        return regex.containsMatchIn(text)
    }

    /**
     * Lightweight title matching, with task-specific results cached separately.
     */
    fun evaluateHeuristics(windowTitle: String, activeTaskTitle: String = ""): ScopeEvaluationResult {
        val titleLower = windowTitle.lowercase().trim()
        if (titleLower.isBlank()) {
            return ScopeEvaluationResult(ScopeVerdict.IN_SCOPE, "Workspace", "Blank window title", "HEURISTIC")
        }

        val taskKey = activeTaskTitle.trim().lowercase()
        val scopedCacheKey = "$titleLower||$taskKey"

        // Check static/manual cache first, then task-scoped cache
        classificationCache[titleLower]?.let { return it }
        classificationCache[scopedCacheKey]?.let { return it }

        val workspaceMatch = WORKSPACE_KEYWORDS.firstOrNull { matchesKeyword(titleLower, it) }
        if (workspaceMatch != null) {
            return ScopeEvaluationResult(
                ScopeVerdict.IN_SCOPE, "Workspace", "Study workspace: '$workspaceMatch'", "HEURISTIC",
            )
        }

        // Match the current task; unrelated titles need user or local AI classification.
        if (taskKey.isNotBlank()) {
            val taskTokens =
                taskKey
                    .split(Regex("[^a-z0-9]+"))
                    .filter { it.length > 3 }
            if (taskTokens.any { token -> matchesKeyword(titleLower, token) }) {
                val res = ScopeEvaluationResult(
                    verdict = ScopeVerdict.IN_SCOPE,
                    category = "Current Task",
                    reason = "Matches current task title",
                    source = "HEURISTIC",
                )
                putCacheBounded(scopedCacheKey, res)
                return res
            }
        }

        return ScopeEvaluationResult(
            verdict = ScopeVerdict.UNKNOWN,
            category = "Unknown",
            reason = "Needs local AI evaluation",
            source = "HEURISTIC",
        )
    }

    fun recordTaskClassification(windowTitle: String, activeTaskTitle: String, result: ScopeEvaluationResult) {
        putCacheBounded("${windowTitle.lowercase().trim()}||${activeTaskTitle.lowercase().trim()}", result)
    }

    fun recordManualClassification(windowTitle: String, verdict: ScopeVerdict, reason: String = "User marked") {
        putCacheBounded(
            windowTitle.lowercase().trim(),
            ScopeEvaluationResult(
                verdict = verdict,
                category = if (verdict == ScopeVerdict.IN_SCOPE) "User Allowed" else "User Blocked",
                reason = reason,
                source = "USER",
            ),
        )
    }

    fun getCachedVerdict(windowTitle: String): ScopeEvaluationResult? {
        return classificationCache[windowTitle.lowercase().trim()]
    }
}
