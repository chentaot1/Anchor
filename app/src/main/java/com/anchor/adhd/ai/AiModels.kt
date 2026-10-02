package com.anchor.adhd.ai

import kotlinx.serialization.Serializable

@Serializable
data class AiBreakdownResult(
    val steps: List<String>,
    val next_action: String,
    val minutes_estimate: List<Int> = emptyList(),
    val if_then: String? = null
)

@Serializable
data class AiBrainDumpResult(
    val tasks: List<String>,
    val notes: String? = null
)

@Serializable
data class AiTriageResult(
    val ordered_task_titles: List<String>,
    val rationale: String? = null
)

@Serializable
data class AiReplanResult(
    val recommended_titles: List<String>,
    val defer_titles: List<String> = emptyList(),
    val message: String? = null
)
