package com.anchor.adhd.domain

import com.anchor.adhd.ai.AiBreakdownResult
import com.anchor.adhd.data.model.BreakdownGranularity

/**
 * Kotlin clamp after JSON, before Room insert: enforces granularity step bounds,
 * physical first verb, <=2m starter estimate, placeholder rejection, and drops blocked-app aliases.
 */
object BreakdownClamp {
    private val PHYSICAL = listOf(
        "Open", "Walk", "Write", "Call", "Sit", "Pick", "Read", "Put", "Close", "Set",
        "Plug", "Fill", "Wash", "Fold", "Send", "Email", "Text", "Find", "Grab", "Stand",
        "Move", "Clear", "Lay", "Take", "Check", "Save", "Copy", "Paste", "Print", "Ask",
        "Book", "Pay", "Pack", "Place", "Pull", "Push", "Turn", "Type", "Sketch",
        "Gather", "Jot", "Draft", "Run", "Group", "Skim", "Summarize", "Identify", "Complete"
    )

    private val BANNED_FIRST = setOf("start", "try", "begin", "attempt")
    private val PLACEHOLDER_REGEX = Regex("(?i)^(step\\s*\\d+|<.*>|action)$")

    fun stepRangeFor(granularity: BreakdownGranularity): IntRange = when (granularity) {
        BreakdownGranularity.MILD -> 2..3
        BreakdownGranularity.NORMAL -> 3..5
        BreakdownGranularity.SPICY -> 5..7
    }

    fun generateDraftSteps(
        taskTitle: String,
        granularity: BreakdownGranularity = BreakdownGranularity.NORMAL
    ): List<String> {
        val cleanTitle = taskTitle.trim().ifBlank { "this task" }
        val lower = cleanTitle.lowercase()
        val specificSteps = when {
            lower.contains("write") || lower.contains("essay") || lower.contains("paper") || lower.contains("draft") ->
                listOf(
                    "Open blank document and write title",
                    "Jot 3 bullet points of what you want to say",
                    "Draft one messy paragraph (don't edit)",
                    "Check that paragraph against the writing instructions",
                    "Save the draft and mark the next section to write",
                    "Write two supporting sentences for the next bullet point",
                    "Read the draft aloud once and save your file"
                )
            lower.contains("code") ||
                lower.contains("bug") ||
                lower.contains("feature") ||
                lower.contains("refactor") ||
                lower.contains("programming") ->
                listOf(
                    "Open project and reproduce current behavior",
                    "Find exact file and line to change",
                    "Write a minimal failing test or log",
                    "Implement smallest code change to pass",
                    "Run the relevant test and check the result",
                    "Clean up temporary debug logs or unused imports",
                    "Save changes and note the next test case"
                )
            lower.contains("dish") ->
                listOf(
                    "Gather the dirty dishes beside the sink",
                    "Fill the sink with warm water and dish soap",
                    "Wash one cup, then rinse it",
                    "Place the clean cup on the drying rack",
                    "Wash and rinse the next few dishes the same way",
                    "Wipe down the sink rim with a cloth",
                    "Hang up the dish towel to dry"
                )
            lower.contains("clean") || lower.contains("laundry") || lower.contains("room") ->
                listOf(
                    "Put 5 visible trash items in the bin",
                    "Clear one flat surface completely",
                    "Group similar items into a single pile",
                    "Put away one category of items",
                    "Check that the cleared area is ready to use",
                    "Put loose clothes into the laundry hamper",
                    "Take the trash bag to the bin"
                )
            lower.contains("assignment") || lower.contains("homework") || lower.contains("problem set") ->
                listOf(
                    "Open the instructions for \"$cleanTitle\"",
                    "Find the first unanswered exercise and read what it asks",
                    "Draft an answer to that exercise using your course notes",
                    "Check the answer against the instructions and correct one mistake",
                    "Save your answer and mark the next unanswered exercise",
                    "Complete the next exercise using the same notes",
                    "Verify submission format requirements and save the file"
                )
            lower.contains("study") || lower.contains("read") || lower.contains("chapter") || lower.contains("exam") ->
                listOf(
                    "Open notes and read first heading",
                    "Write down 3 questions you want answered",
                    "Skim key terms and bolded concepts",
                    "Summarize main idea in one sentence",
                    "Check your summary against the notes and mark the next section",
                    "Test yourself on 2 key terms without looking at the page",
                    "Bookmark the exact heading to resume next session"
                )
            lower.contains("email") || lower.contains("message") || lower.contains("reply") ->
                listOf(
                    "Open draft and type recipient name",
                    "Write the core point in 1 sentence",
                    "Add polite greeting and sign-off",
                    "Quick scan and hit send",
                    "Check that the message appears in sent items",
                    "Archive or flag the original thread",
                    "Close the mail tab to clear your workspace"
                )
            else ->
                listOf(
                    "Open the instructions or materials for \"$cleanTitle\"",
                    "Identify the required result and one small piece you can complete now",
                    "Complete that piece using the instructions",
                    "Check the result against the requirements",
                    "Save your progress and write down the next unfinished piece",
                    "Complete one more small section while materials are open",
                    "Put tools in order for the next work block"
                )
        }
        return specificSteps.distinct().take(stepRangeFor(granularity).last)
    }

    fun clamp(
        steps: List<String>,
        blockedPackages: Collection<String>,
        granularity: BreakdownGranularity? = null,
        taskTitle: String = ""
    ): List<String> {
        val range = granularity?.let { stepRangeFor(it) } ?: (2..7)
        val aliases = BlockedAppAliases.aliasesFor(blockedPackages)
        val rewritten = steps
            .map { it.trim() }
            .filter { it.isNotBlank() && !PLACEHOLDER_REGEX.matches(it) }
            .map { rewriteVerb(it) }
            .filter { it.isNotBlank() && !PLACEHOLDER_REGEX.matches(it) }
            .filter { step -> aliases.none { alias -> BlockedAppAliases.stepMentions(step, alias) } }
            .distinctBy { it.lowercase() }

        val filled = if (rewritten.size < range.first) {
            if (granularity == null && taskTitle.isBlank()) {
                val fallback = listOf("Open the materials", "Do the smallest next step")
                (rewritten + fallback).distinctBy { it.lowercase() }.take(range.first)
            } else {
                val draft = generateDraftSteps(taskTitle, granularity ?: BreakdownGranularity.NORMAL)
                    .map { rewriteVerb(it.trim()) }
                    .filter { step -> aliases.none { alias -> BlockedAppAliases.stepMentions(step, alias) } }
                (rewritten + draft).distinctBy { it.lowercase() }
            }
        } else {
            rewritten
        }
        return filled.take(range.last)
    }

    fun clampResult(
        result: AiBreakdownResult,
        blockedPackages: Collection<String>,
        granularity: BreakdownGranularity = BreakdownGranularity.NORMAL,
        taskTitle: String = ""
    ): AiBreakdownResult {
        val steps = clamp(result.steps, blockedPackages, granularity, taskTitle)
        val minutes = steps.mapIndexed { index, _ ->
            val rawMin = result.minutes_estimate.getOrNull(index)
            if (index == 0) {
                if (rawMin != null && rawMin in 1..2) rawMin else 2
            } else {
                if (rawMin != null && rawMin > 0) rawMin else 10
            }
        }
        val next = steps.first()
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
