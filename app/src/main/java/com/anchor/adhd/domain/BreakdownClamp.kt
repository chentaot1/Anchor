package com.anchor.adhd.domain

import com.anchor.adhd.ai.AiBreakdownResult

/**
 * Kotlin clamp after JSON, before Room insert: 2–7 steps, physical first verb,
 * drop blocked-app aliases (YouTube etc.).
 */
object BreakdownClamp {
    private val PHYSICAL = listOf(
        "Open", "Walk", "Write", "Call", "Sit", "Pick", "Read", "Put", "Close", "Set",
        "Plug", "Fill", "Wash", "Fold", "Send", "Email", "Text", "Find", "Grab", "Stand",
        "Move", "Clear", "Lay", "Take", "Check", "Save", "Copy", "Paste", "Print", "Ask",
        "Book", "Pay", "Pack", "Place", "Pull", "Push", "Turn", "Type", "Sketch"
    )

    private val BANNED_FIRST = setOf("start", "try", "begin", "attempt")

    fun clamp(steps: List<String>, blockedPackages: Collection<String>): List<String> {
        val aliases = BlockedAppAliases.aliasesFor(blockedPackages)
        val rewritten = steps.map { rewriteVerb(it.trim()) }
            .filter { it.isNotBlank() }
            .filter { step -> aliases.none { alias -> BlockedAppAliases.stepMentions(step, alias) } }
            .distinctBy { it.lowercase() }
        val limited = rewritten.take(7)
        return when {
            limited.size >= 2 -> limited
            limited.size == 1 -> listOf(limited[0], "Do the smallest next step")
            else -> listOf("Open the materials", "Do the smallest next step")
        }
    }

    fun clampResult(result: AiBreakdownResult, blockedPackages: Collection<String>): AiBreakdownResult {
        val steps = clamp(result.steps, blockedPackages)
        val minutes = steps.mapIndexed { index, _ ->
            result.minutes_estimate.getOrNull(index) ?: 15
        }
        val next = steps.firstOrNull { it.equals(result.next_action, ignoreCase = true) } ?: steps.first()
        return result.copy(steps = steps, minutes_estimate = minutes, next_action = next)
    }

    internal fun rewriteVerb(step: String): String {
        val trimmed = step.trim()
        if (trimmed.isEmpty()) return trimmed
        val first = trimmed.substringBefore(' ')
        val rest = trimmed.substringAfter(' ', missingDelimiterValue = "").trim()
        return when {
            first.lowercase() in BANNED_FIRST && rest.isNotEmpty() -> "Open $rest"
            first.lowercase() in BANNED_FIRST -> "Open the materials"
            PHYSICAL.any { it.equals(first, ignoreCase = true) } ->
                PHYSICAL.first { it.equals(first, ignoreCase = true) } + if (rest.isEmpty()) "" else " $rest"
            else -> trimmed
        }
    }
}

object BlockedAppAliases {
    private val KNOWN = mapOf(
        "com.google.android.youtube" to listOf("youtube"),
        "com.zhiliaoapp.musically" to listOf("tiktok", "tik tok"),
        "com.instagram.android" to listOf("instagram", "insta"),
        "com.reddit.frontpage" to listOf("reddit"),
        "com.discord" to listOf("discord"),
    )
    private val GENERIC_TAILS = setOf("android", "app", "main", "lite", "debug")

    fun aliasesFor(packages: Collection<String>): Set<String> {
        val out = mutableSetOf<String>()
        packages.forEach { pkg ->
            KNOWN[pkg]?.let { out += it }
            val tail = pkg.substringAfterLast('.')
            if (tail.length >= 4 && tail.lowercase() !in GENERIC_TAILS) out += tail
        }
        return out
    }

    fun stepMentions(step: String, alias: String): Boolean {
        if (alias.length < 3) return false
        return Regex("\\b${Regex.escape(alias)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(step)
    }

    fun labelForPackage(pkg: String): String = when {
        pkg.contains("youtube", ignoreCase = true) -> "YouTube"
        pkg.contains("musically") || pkg.contains("tiktok", ignoreCase = true) -> "TikTok"
        pkg.contains("instagram", ignoreCase = true) -> "Instagram"
        pkg.contains("reddit", ignoreCase = true) -> "Reddit"
        pkg.contains("discord", ignoreCase = true) -> "Discord"
        else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}
