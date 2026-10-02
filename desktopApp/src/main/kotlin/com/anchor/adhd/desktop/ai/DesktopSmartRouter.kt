package com.anchor.adhd.desktop.ai

import com.anchor.adhd.domain.AirlockHeuristic

/**
 * Result of the Universal Smart AI Router.
 */
sealed class SmartRouteResult {
    abstract val headline: String
    abstract val detail: String

    data class TimerAction(
        val durationMinutes: Int,
        val taskTitle: String = "Deep Focus",
        override val headline: String = "⏱ Focus Timer Configured",
        override val detail: String = "Set to $durationMinutes minutes",
    ) : SmartRouteResult()

    data class BlockerAction(
        val target: String,
        val enable: Boolean,
        override val headline: String = if (enable) "🛡 Distraction Shield Activated" else "🛡 Shield Rule Updated",
        override val detail: String = if (enable) "Blocking $target" else "Unblocked $target",
    ) : SmartRouteResult()

    data class GroundingReset(
        val recommendedTask: String,
        val suggestedMinutes: Int = 3,
        override val headline: String = "🌱 Compassionate Reset",
        override val detail: String = "Take a breath. No guilt. Let's do 1 tiny micro-win to restore momentum.",
    ) : SmartRouteResult()

    data class TaskBreakdown(
        val taskTitle: String,
        val microSteps: List<String>,
        val step1Starter: String,
        val suggestedMinutes: Int = 15,
        override val headline: String = "🪄 Task Decomposed",
        override val detail: String = "Ready with 2-minute starter: \"$step1Starter\"",
    ) : SmartRouteResult()

    data class BrainDump(
        val tasks: List<String>,
        val primaryTask: String,
        override val headline: String = "📋 Brain Dump Triaged",
        override val detail: String = "Extracted ${tasks.size} actionable items into your inbox.",
    ) : SmartRouteResult()

    data class QuickNote(
        val noteTitle: String,
        override val headline: String = "💡 Impulse Captured",
        override val detail: String = "Saved safely to your task inbox.",
    ) : SmartRouteResult()

    data class ResetDataAction(
        val resetType: ResetType,
        override val headline: String = "🔄 Clean Slate & Fresh Start",
        override val detail: String = "Reset options to clear backlogs and eliminate ADHD guilt.",
    ) : SmartRouteResult()

    data class SyllabusAction(
        val initialQuery: String = "",
        override val headline: String = "🎓 Academic Harbor & Syllabus Hub",
        override val detail: String = "Open the Syllabus Hub to upload course documents and extract deadlines with lead-time prep.",
    ) : SmartRouteResult()
}

/**
 * Types of safe data resets for ADHD users.
 */
enum class ResetType {
    CLEAR_TASKS,
    RESET_STREAK,
    FACTORY_RESET,
}

/**
 * Universal Smart AI Router for Anchor ADHD.
 * Automatically classifies messy, natural-language ADHD user inputs into actionable system intents
 * in < 3ms using deterministic heuristics, backed by on-device Claude-Opus-Fable5 (Q8_0) for nuanced expansions.
 */
object DesktopSmartRouter {
    private val OVERWHELM_KEYWORDS =
        setOf(
            "overwhelmed",
            "behind",
            "ruined",
            "hate myself",
            "wasted",
            "dread",
            "freeze",
            "stuck",
            "can't focus",
            "paralyzed",
            "rabbit hole",
            "scrolled",
            "procrastinating",
            "panic",
            "exhausted",
            "spiral",
            "mess",
            "screwed",
            "distracted",
        )

    private val BLOCKER_REGEX =
        Regex(
            """\b(block|unblock|shield|ban|restrict|allow)\s+([a-zA-Z0-9_\-\.]+)\b""",
            RegexOption.IGNORE_CASE,
        )

    /**
     * Robust, flexible timer intent parser covering all common ADHD timer phrasings:
     * - "timer 25", "timer 15m", "25m timer", "25 min timer", "set timer 30m"
     * - "25m", "15m", "50m", "3m", "5m", "10m", "45m", "60m"
     * - "25 min", "15 minutes", "1 hour", "1 hr", "2 hours"
     * - "start timer", "timer", "pomodoro", "start a timer", "focus timer"
     * - "spark", "3m spark"
     * - "write essay for 25m", "study 45 mins", "clean room 15m"
     */
    private fun parseTimerIntent(input: String): SmartRouteResult.TimerAction? {
        val trimmed = input.trim()
        val lower = trimmed.lowercase()

        // 1. Keyword-only timer request (no number specified) -> default to 25m Pomodoro
        val isGenericTimerKeyword =
            lower == "timer" ||
                lower == "pomodoro" ||
                lower == "start timer" ||
                lower == "start a timer" ||
                lower == "set timer" ||
                lower == "focus timer" ||
                lower == "start focus" ||
                lower == "begin timer" ||
                lower == "start pomodoro"
        if (isGenericTimerKeyword) {
            return SmartRouteResult.TimerAction(
                durationMinutes = 25,
                taskTitle = "Deep Focus",
                headline = "⏱ Starting 25-Minute Focus",
                detail = "Standard 25-minute Pomodoro timer ready. Click to anchor.",
            )
        }

        // 2. Spark keywords -> 3-minute starter
        if (lower == "spark" || lower == "3m spark" || lower == "quick spark" || lower == "micro timer") {
            return SmartRouteResult.TimerAction(
                durationMinutes = 3,
                taskTitle = "Activation Spark",
                headline = "⚡ 3-Minute Activation Spark",
                detail = "Frictionless 3-minute starter to break initiation paralysis.",
            )
        }

        // 3. Hour patterns: "1 hour", "1h", "2 hours", "1 hr timer"
        val hourRegex = Regex("""\b(?:(\d{1,2})\s*(?:h|hr|hrs|hour|hours))\b""", RegexOption.IGNORE_CASE)
        val hourMatch = hourRegex.find(trimmed)
        if (hourMatch != null && (lower.contains("timer") || lower.contains("focus") || trimmed.length <= 15)) {
            val hours = hourMatch.groupValues[1].toIntOrNull() ?: 1
            val mins = (hours * 60).coerceIn(1, 180)
            return SmartRouteResult.TimerAction(
                durationMinutes = mins,
                taskTitle = "Deep Focus",
                headline = "⏱ Starting $mins-Minute Focus ($hours hr)",
                detail = "Setting anchor timer to $hours hour${if (hours > 1) "s" else ""}.",
            )
        }

        // 4. Standalone minute patterns: "25m", "15m", "3m", "50m", "25 min", "15 minutes"
        val standaloneRegex = Regex("""^\s*(\d{1,3})\s*(?:m|min|mins|minute|minutes)\s*$""", RegexOption.IGNORE_CASE)
        val standaloneMatch = standaloneRegex.find(trimmed)
        if (standaloneMatch != null) {
            val mins = standaloneMatch.groupValues[1].toIntOrNull()?.coerceIn(1, 180) ?: 25
            val headline = if (mins == 3) "⚡ 3-Minute Activation Spark" else "⏱ Starting $mins-Minute Focus"
            val detail =
                if (mins ==
                    3
                ) {
                    "Low-friction starter to break initiation paralysis."
                } else {
                    "Setting anchor timer to $mins minutes and activating shield."
                }
            return SmartRouteResult.TimerAction(
                durationMinutes = mins,
                taskTitle = if (mins == 3) "Activation Spark" else "Deep Focus",
                headline = headline,
                detail = detail,
            )
        }

        // 5. Number before timer keyword: "25m timer", "25 min timer", "15 minute sprint", "50m focus", "5 min break"
        val numberBeforeRegex =
            Regex(
                """\b(\d{1,3})\s*(?:m|min|mins|minute|minutes)\s*(?:timer|focus|session|sprint|pomodoro|work|break)\b""",
                RegexOption.IGNORE_CASE,
            )
        val numBeforeMatch = numberBeforeRegex.find(trimmed)
        if (numBeforeMatch != null) {
            val mins = numBeforeMatch.groupValues[1].toIntOrNull()?.coerceIn(1, 180) ?: 25
            val headline = if (mins == 3) "⚡ 3-Minute Activation Spark" else "⏱ Starting $mins-Minute Focus"
            val detail =
                if (mins ==
                    3
                ) {
                    "Low-friction starter to break initiation paralysis."
                } else {
                    "Setting anchor timer to $mins minutes and activating shield."
                }
            return SmartRouteResult.TimerAction(
                durationMinutes = mins,
                taskTitle = if (mins == 3) "Activation Spark" else "Deep Focus",
                headline = headline,
                detail = detail,
            )
        }

        // 6. Keyword before number: "timer 25", "timer 25m", "set timer 30", "set a timer for 15m", "focus 50 min"
        val keywordBeforeRegex =
            Regex(
                """\b(?:timer|focus|set|start|sprint|pomodoro|work)\s*(?:a\s*)?(?:timer\s*)?(?:to|for)?\s*(\d{1,3})\s*(?:m|min|mins|minute|minutes)?\b""",
                RegexOption.IGNORE_CASE,
            )
        val kwBeforeMatch = keywordBeforeRegex.find(trimmed)
        if (kwBeforeMatch != null) {
            val mins = kwBeforeMatch.groupValues[1].toIntOrNull()?.coerceIn(1, 180) ?: 25
            val headline = if (mins == 3) "⚡ 3-Minute Activation Spark" else "⏱ Starting $mins-Minute Focus"
            val detail =
                if (mins ==
                    3
                ) {
                    "Low-friction starter to break initiation paralysis."
                } else {
                    "Setting anchor timer to $mins minutes and activating shield."
                }
            return SmartRouteResult.TimerAction(
                durationMinutes = mins,
                taskTitle = if (mins == 3) "Activation Spark" else "Deep Focus",
                headline = headline,
                detail = detail,
            )
        }

        // 7. Task with embedded timer: "write essay for 25m", "code for 45 min", "study biology 30m"
        val taskTimerRegex =
            Regex(
                """^(.+?)\s+(?:for\s+)?(\d{1,3})\s*(?:m|min|mins|minute|minutes)\s*(?:timer)?$""",
                RegexOption.IGNORE_CASE,
            )
        val taskTimerMatch = taskTimerRegex.find(trimmed)
        if (taskTimerMatch != null) {
            val rawTask = taskTimerMatch.groupValues[1].trim()
            val mins = taskTimerMatch.groupValues[2].toIntOrNull()?.coerceIn(1, 180) ?: 25
            val cleanTask =
                rawTask
                    .removePrefix("timer")
                    .removePrefix("focus")
                    .removePrefix("start")
                    .trim()
            if (cleanTask.isNotBlank() && cleanTask.length >= 3) {
                return SmartRouteResult.TimerAction(
                    durationMinutes = mins,
                    taskTitle = cleanTask.replaceFirstChar { it.uppercase() },
                    headline = "⏱ $mins-Minute Focus on \"$cleanTask\"",
                    detail = "Anchor ready for $cleanTask with $mins-minute timer and distraction shield.",
                )
            }
        }

        return null
    }

    /**
     * Instant (<2ms) deterministic route classification.
     */
    fun routeQuick(input: String): SmartRouteResult {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return SmartRouteResult.QuickNote("Quick task")
        }

        val lower = trimmed.lowercase()

        // Explicit decomposition wins over keywords or list-like details inside one task.
        val breakdownRequest = Regex(
            """^(?:(?:please|can you|could you)\s+)?(?:break\s*down|decompose|split\s+into\s+steps)(?:\s+(?:this\s+task|this|the\s+task))?\s*[:\-]?\s+(.+)$""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).matchEntire(trimmed)
        if (breakdownRequest != null) return taskBreakdown(breakdownRequest.groupValues[1].trim())

        // 1. Timer / Spark Commands (e.g. "timer 25", "25m timer", "25m", "start timer", "write essay for 25m")
        val timerResult = parseTimerIntent(trimmed)
        if (timerResult != null) {
            return timerResult
        }

        // 2. Blocker Commands (e.g. "block discord", "unblock steam", "block youtube")
        val blockerMatch = BLOCKER_REGEX.find(trimmed)
        if (blockerMatch != null) {
            val action = blockerMatch.groupValues[1].lowercase()
            val target = blockerMatch.groupValues[2]
            val isBlock = action == "block" || action == "shield" || action == "ban" || action == "restrict"
            return SmartRouteResult.BlockerAction(
                target = target,
                enable = isBlock,
                headline = if (isBlock) "🛡 Distraction Shield Activated" else "🛡 Shield Rule Updated",
                detail = if (isBlock) "Blocking $target across your session." else "Removed $target from blocklist.",
            )
        }

        // 3. Clean Slate & Reset Commands (e.g. "reset data", "clear tasks", "fresh start", "reset streak")
        val isReset =
            lower.contains("reset data") ||
                lower == "reset" ||
                lower.contains("clear task") ||
                lower.contains("clear backlog") ||
                lower.contains("clean slate") ||
                lower.contains("fresh start") ||
                lower.contains("reset streak") ||
                lower.contains("factory reset") ||
                lower.contains("wipe data")
        if (isReset) {
            val resetType =
                when {
                    lower.contains("streak") || lower.contains("history") -> ResetType.RESET_STREAK
                    lower.contains(
                        "factory",
                    ) ||
                        lower.contains("all") ||
                        lower.contains("wipe") ||
                        lower.contains("database") -> ResetType.FACTORY_RESET
                    else -> ResetType.CLEAR_TASKS
                }
            val (hl, dt) =
                when (resetType) {
                    ResetType.CLEAR_TASKS ->
                        Pair(
                            "🧹 Clear Task Backlog",
                            "Ready to clear lingering tasks and re-anchor with 1 single clean milestone without guilt.",
                        )
                    ResetType.RESET_STREAK ->
                        Pair(
                            "🌱 Reset Streak & History",
                            "Start fresh at Day 1 to eliminate the demotivation of a broken streak.",
                        )
                    ResetType.FACTORY_RESET ->
                        Pair(
                            "⚡ Factory Reset Anchor",
                            "Wipe all tasks, focus sessions, and statistics back to default factory state.",
                        )
                }
            return SmartRouteResult.ResetDataAction(
                resetType = resetType,
                headline = hl,
                detail = dt,
            )
        }

        // 4. Academic Harbor & Syllabus Hub (e.g. "syllabus", "import syllabus", "course schedule", "upload syllabus")
        val isSyllabus =
            lower.contains("syllabus") ||
                lower == "courses" ||
                lower == "course" ||
                lower.contains("import syllabus") ||
                lower.contains("upload syllabus") ||
                lower.contains("course deadline") ||
                lower.contains("homework schedule")
        if (isSyllabus) {
            return SmartRouteResult.SyllabusAction(
                initialQuery = trimmed,
                headline = "🎓 Academic Harbor & Syllabus Hub",
                detail = "Extract exams, projects, and weekly deliverables with backward-chained prep milestones.",
            )
        }

        // 5. Emotional Overwhelm / Derailed Day Reset (e.g. "I've been scrolling reddit for 3 hours and ruined my day")
        val isOverwhelmed = OVERWHELM_KEYWORDS.any { lower.contains(it) }
        if (isOverwhelmed) {
            return SmartRouteResult.GroundingReset(
                recommendedTask = "Take 3 deep breaths and clear your desk",
                suggestedMinutes = 3,
                headline = "🌱 Compassionate Momentum Reset",
                detail = "The morning might be gone, but right now is wide open. Let's do 1 tiny 3-minute starter win.",
            )
        }

        // 4. Multi-Task Brain Dump (multiple commas, bullet points, or 'and then')
        val hasMultipleTasks =
            trimmed.contains("\n") ||
                trimmed.split(",").size >= 3 ||
                trimmed.contains(" and then ", ignoreCase = true) ||
                Regex("""^\d+[\.\)]\s+""", RegexOption.MULTILINE).findAll(trimmed).count() >= 2

        if (hasMultipleTasks) {
            val parsed = AirlockHeuristic.parse(trimmed)
            val allTasks = listOf(parsed.primaryTask) + parsed.secondaryTasks
            return SmartRouteResult.BrainDump(
                tasks = allTasks.filter { it.isNotBlank() },
                primaryTask = parsed.primaryTask.ifBlank { "Deep Work" },
                headline = "📋 Brain Dump Triaged",
                detail = "Split into ${allTasks.size} discrete items. Top priority set as primary.",
            )
        }

        // 5. Actionable Task Breakdown (Project or complex task)
        // If it starts with action verbs or is a clear task title
        return taskBreakdown(trimmed)
    }

    private fun taskBreakdown(taskTitle: String): SmartRouteResult.TaskBreakdown {
        val microSteps = DesktopAiEngine.generateMicroSteps(taskTitle)
        val step1 = microSteps.first()
        return SmartRouteResult.TaskBreakdown(
            taskTitle = taskTitle,
            microSteps = microSteps,
            step1Starter = step1,
            suggestedMinutes = 15,
            headline = "🪄 Smart Task Decomposition",
            detail = "Step 1 (<2m): $step1",
        )
    }

    /**
     * Asynchronous AI Enrichment: If Claude-Opus-Fable5 is active, uses LLM to provide deeper micro-steps or tailored advice.
     */
    suspend fun routeAsync(input: String): SmartRouteResult {
        val quick = routeQuick(input)
        if (!DesktopAiEngine.isModelReady()) return quick

        return when (quick) {
            is SmartRouteResult.TaskBreakdown -> {
                val aiSteps = DesktopAiEngine.generateMicroStepsAsync(quick.taskTitle)
                if (aiSteps.isNotEmpty()) {
                    quick.copy(
                        microSteps = aiSteps,
                        step1Starter = aiSteps.firstOrNull() ?: quick.step1Starter,
                        detail = "Step 1 (<2m): ${aiSteps.first()}",
                    )
                } else {
                    quick
                }
            }
            is SmartRouteResult.BrainDump -> {
                val aiTasks = DesktopAiEngine.parseBrainDumpAsync(input)
                if (aiTasks.isNotEmpty()) {
                    quick.copy(
                        tasks = aiTasks,
                        primaryTask = aiTasks.firstOrNull() ?: quick.primaryTask,
                    )
                } else {
                    quick
                }
            }
            else -> quick
        }
    }
}
