package com.anchor.adhd.ai

import com.anchor.adhd.data.model.AiJobType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object StubAiResponses {
    private val json = Json { ignoreUnknownKeys = true }

    fun generate(type: AiJobType, userPrompt: String): String = when (type) {
        AiJobType.BREAKDOWN -> breakdown(userPrompt)
        AiJobType.BRAINDUMP -> brainDump(userPrompt)
        AiJobType.TRIAGE -> triage(userPrompt)
        AiJobType.REPLAN -> replan(userPrompt)
        AiJobType.WEEKLY -> weekly(userPrompt)
        AiJobType.IF_THEN -> ifThen(userPrompt)
    }

    private fun breakdown(prompt: String): String {
        val title = prompt.lines().firstOrNull()?.trim()?.take(80) ?: "task"
        val lower = prompt.lowercase()

        val isWriting = listOf("write", "essay", "report", "draft", "paper", "article", "thesis", "blog", "doc", "paragraph", "script").any { lower.contains(it) }
        val isCoding = listOf("code", "bug", "debug", "fix", "feature", "test", "compile", "refactor", "api", "git", "deploy", "kotlin", "java", "python", "sql", "android", "app").any { lower.contains(it) }
        val isChore = listOf("clean", "room", "dishes", "laundry", "trash", "tidy", "organize", "vacuum", "mop", "sweep", "kitchen", "bathroom", "bed").any { lower.contains(it) }
        val isStudy = listOf("study", "read", "exam", "chapter", "lecture", "homework", "flashcard", "quiz", "math", "revision", "class", "notes").any { lower.contains(it) }
        val isAdmin = listOf("email", "mail", "bill", "pay", "call", "tax", "form", "invoice", "application", "schedule", "dentist", "doctor", "bank", "paperwork").any { lower.contains(it) }

        val (steps, minutes, nextAction, ifThen) = when {
            isWriting -> Quadruple(
                listOf(
                    "Open blank document & type title with 3 rough bullets",
                    "Write first 100 messy, unedited words without backspacing",
                    "Draft main supporting arguments with rough citations",
                    "Proofread and read aloud for rhythm"
                ),
                listOf(3, 10, 20, 10),
                "Open blank document & type title with 3 rough bullets",
                "If I open the document, then I type the title and 3 bullets before touching formatting."
            )
            isCoding -> Quadruple(
                listOf(
                    "Open IDE & write 1 minimal failing test or reproduction log",
                    "Inspect state at breakpoint or print line of failure",
                    "Write minimal 10-line fix and verify test passes",
                    "Run full test suite and clean up debug logs"
                ),
                listOf(5, 5, 15, 10),
                "Open IDE & write 1 minimal failing test or reproduction log",
                "If I open the IDE, then I write the reproduce command before looking at the codebase."
            )
            isChore -> Quadruple(
                listOf(
                    "Set a 5-minute timer & bag all loose floor trash",
                    "Clear all dishes and cups into the sink",
                    "Wipe down a single flat workspace surface",
                    "Take trash bag out to bin"
                ),
                listOf(5, 5, 10, 5),
                "Set a 5-minute timer & bag all loose floor trash",
                "If I stand up, then I immediately pick up one piece of trash."
            )
            isStudy -> Quadruple(
                listOf(
                    "Open material to chapter heading & skim bold summary terms",
                    "Read key section and jot 3 core takeaway notes",
                    "Attempt 2 practice questions without notes",
                    "Self-check answers and bookmark missed concepts"
                ),
                listOf(5, 15, 15, 10),
                "Open material to chapter heading & skim bold summary terms",
                "If I sit at my desk, then I open directly to the chapter heading."
            )
            isAdmin -> Quadruple(
                listOf(
                    "Open browser/client and navigate directly to required form",
                    "Fill out required personal info and top section",
                    "Attach necessary documents or draft 2-sentence response",
                    "Review fields and submit or hit send"
                ),
                listOf(3, 10, 10, 5),
                "Open browser/client and navigate directly to required form",
                "If I open the browser, then I go straight to the form without opening new tabs."
            )
            else -> Quadruple(
                listOf(
                    "Set up workspace and complete the 2-minute physical start: $title",
                    "Work for 10 focused minutes without tab switching",
                    "Review progress and finish primary chunk",
                    "Document status and note clear next checkpoint"
                ),
                listOf(2, 10, 20, 5),
                "Set up workspace and complete the 2-minute physical start: $title",
                "If I feel resistance, then I only commit to the first 2-minute step."
            )
        }

        return json.encodeToString(
            AiBreakdownResult(
                steps = steps,
                next_action = nextAction,
                minutes_estimate = minutes,
                if_then = ifThen
            )
        )
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun brainDump(prompt: String): String {
        // Noise filter: reject emotional complaints and keep actionable tasks
        val noiseWords = setOf("ugh", "tired", "so tired", "hate this", "overwhelmed", "why", "help", "stressed")
        val tasks = prompt.lines()
            .map { it.trim().trimStart('-', '*', '•', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', ')').trim() }
            .filter { line ->
                line.isNotBlank() && line.lowercase() !in noiseWords && line.length >= 3
            }
            .take(8)
            .ifEmpty { listOf("Pick one quick 5-minute task", "Set up desk workspace") }

        return json.encodeToString(AiBrainDumpResult(tasks = tasks, notes = "Organized by domain & actionability (on-device local)"))
    }

    private fun triage(prompt: String): String {
        val titles = prompt.lines().map { it.trim().trimStart('-', '*', '•') }.filter { it.isNotBlank() }.take(8)
        // Group into Quick Wins (< 10m) first, then deeper focus tasks
        val quickKeywords = listOf("call", "email", "reply", "pay", "send", "trash", "wash", "buy", "check")
        val quickWins = titles.filter { t -> quickKeywords.any { t.contains(it, ignoreCase = true) } }
        val deepWork = titles.filterNot { it in quickWins }
        val ordered = (quickWins + deepWork).take(6)

        return json.encodeToString(
            AiTriageResult(
                ordered_task_titles = ordered.ifEmpty { titles },
                rationale = "Ordered for ADHD momentum: quick low-friction wins first to build dopamine, followed by deep focus."
            )
        )
    }

    private fun replan(prompt: String): String {
        val titles = prompt.lines().filter { it.isNotBlank() }.take(5)
        val keep = titles.take(2)
        val defer = titles.drop(2)
        return json.encodeToString(
            AiReplanResult(
                recommended_titles = keep,
                defer_titles = defer,
                message = "Protected your energy: keeping 2 essential anchors for today. Deferring the rest so you don't burn out."
            )
        )
    }

    private fun weekly(prompt: String): String = json.encodeToString(
        AiBreakdownResult(
            steps = listOf(
                "Review last week's focus sessions",
                "Block study times around fixed classes",
                "Pick 3 assignments to finish",
                "Schedule one recovery block"
            ),
            next_action = "Review last week's focus sessions",
            minutes_estimate = listOf(15, 30, 45, 20),
            if_then = "If it's Sunday evening, then I open Anchor weekly plan."
        )
    )

    private fun ifThen(prompt: String): String = json.encodeToString(
        AiBreakdownResult(
            steps = listOf("Practice the if-then once"),
            next_action = "Practice the if-then once",
            minutes_estimate = listOf(2),
            if_then = "If ${prompt.take(40)}, then I do the first 2-minute step."
        )
    )
}
