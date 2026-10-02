package com.anchor.adhd.domain

/**
 * Grove vitality (weekly sessions) and shop level helpers.
 * Replaces Pip companion naming; vitality is cosmetic only.
 */
object GroveLevel {
    val vitalityLabels = listOf("Seed", "Sprout", "Growing", "Rooted", "Flourishing")

    fun vitalityStageForWeeklySessions(count: Int): Int = when {
        count <= 0 -> 0
        count <= 2 -> 1
        count <= 5 -> 2
        count <= 9 -> 3
        else -> 4
    }

    fun vitalityLabel(stage: Int): String =
        vitalityLabels.getOrElse(stage.coerceIn(0, vitalityLabels.lastIndex)) { vitalityLabels.first() }

    /** Vitality fill 0f..1f from weekly focus sessions (Finch-style bar). */
    fun vitalityProgress(weeklyFocusSessions: Int): Float = when {
        weeklyFocusSessions <= 0 -> 0.12f
        weeklyFocusSessions <= 2 -> 0.35f
        weeklyFocusSessions <= 5 -> 0.55f
        weeklyFocusSessions <= 9 -> 0.78f
        else -> 1f
    }
}
