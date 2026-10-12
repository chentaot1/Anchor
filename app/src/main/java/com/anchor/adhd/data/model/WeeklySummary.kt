package com.anchor.adhd.data.model

data class WeeklySummary(
    val focusSessions: Int,
    val focusMinutes: Int,
    val tasksCompleted: Int,
    val checkInsLogged: Int,
    val replanQueueSize: Int,
    val streakDays: Int,
    val avgEstimateErrorPercent: Int = 0,
    val estimateSampleCount: Int = 0
)

data class ActivityLogItem(
    val timestampMillis: Long,
    val label: String,
    val detail: String
)
