package com.anchor.adhd.data.model

import kotlinx.serialization.Serializable

data class NowTask(
    val task: TaskEntity?,
    val source: NowSource,
    val routine: RoutineEntity? = null
)

enum class NowSource {
    SCHEDULED_NOW,
    INBOX,
    EMPTY
}

@Serializable
data class PostFocusSummary(
    val sessionId: Long,
    val taskId: Long?,
    val taskTitle: String?,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val completed: Boolean,
    val blockedAttempts: Int = 0
)

data class EstimateStats(
    val avgErrorPercent: Int,
    val sampleCount: Int
)
