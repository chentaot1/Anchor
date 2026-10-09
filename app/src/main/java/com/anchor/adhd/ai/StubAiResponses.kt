package com.anchor.adhd.ai

import com.anchor.adhd.data.model.AiJobType
import com.anchor.adhd.data.model.BreakdownGranularity
import com.anchor.adhd.domain.BreakdownClamp
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object StubAiResponses {
    private val json = Json { ignoreUnknownKeys = true }

    fun generate(
        type: AiJobType,
        userPrompt: String,
        granularity: BreakdownGranularity = BreakdownGranularity.NORMAL
    ): String = when (type) {
        AiJobType.BREAKDOWN -> breakdown(userPrompt, granularity)
        AiJobType.BRAINDUMP -> brainDump(userPrompt)
        AiJobType.TRIAGE -> triage(userPrompt)
        AiJobType.REPLAN -> replan(userPrompt)
        AiJobType.WEEKLY -> weekly(userPrompt)
        AiJobType.IF_THEN -> ifThen(userPrompt)
    }

    fun breakdown(
        prompt: String,
        granularity: BreakdownGranularity = BreakdownGranularity.NORMAL
    ): String {
        val rawFirst = prompt.lines().firstOrNull()?.trim() ?: "task"
        val title = rawFirst
            .removePrefix("Task:")
            .removePrefix("Break down this task:")
            .substringBefore(". Notes:")
            .trim()
            .take(80)
            .ifBlank { "task" }

        val steps = BreakdownClamp.generateDraftSteps(title, granularity)
        val minutes = steps.mapIndexed { idx, _ -> if (idx == 0) 2 else 10 }
        val nextAction = steps.first()
        val ifThen = "If I feel resistance, then I only commit to the first 2-minute step: $nextAction."

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
