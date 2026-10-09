package com.anchor.adhd.domain

import com.anchor.adhd.data.model.HabitCompletionEntity
import com.anchor.adhd.data.model.HabitEntity
import com.anchor.adhd.data.model.HabitScheduleType
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class HabitScoreResult(
    val score: Int,
    val expected: Int,
    val completed: Int,
    val graceUsed: Int
)

data class HabitHeatmapDay(
    val dayMillis: Long,
    val scheduled: Boolean,
    val completed: Boolean,
    val graceDay: Boolean
)

data class HabitWeekProgress(
    val completed: Int,
    val expected: Int
)

object HabitScoreCalculator {
    const val SCORE_WINDOW_DAYS = 30
    const val HEATMAP_WEEKS = 12

    fun score(
        habit: HabitEntity,
        completions: List<HabitCompletionEntity>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): HabitScoreResult {
        val rawStart = today.minusDays(SCORE_WINDOW_DAYS.toLong() - 1)
        val habitCreatedDate = java.time.Instant.ofEpochMilli(habit.createdAtMillis).atZone(zone).toLocalDate()
        val windowStart = maxOf(rawStart, habitCreatedDate)
        val expected = expectedSlots(habit, windowStart, today)
        val completed = countCompleted(completions, windowStart, today, zone)
        val missed = (expected - completed).coerceAtLeast(0)
        val weeksInWindow = weeksInRange(windowStart, today)
        val graceBudget = habit.gracePerWeek * weeksInWindow
        val graceUsed = minOf(missed, graceBudget)
        val adjusted = (completed + graceUsed).coerceAtMost(expected)
        val score = if (expected == 0) 100 else (100 * adjusted / expected).coerceIn(0, 100)
        return HabitScoreResult(score, expected, completed, graceUsed)
    }

    fun weekProgress(
        habit: HabitEntity,
        completions: List<HabitCompletionEntity>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): HabitWeekProgress {
        val weekStart = today.with(java.time.DayOfWeek.MONDAY)
        val weekEnd = weekStart.plusDays(6)
        val expected = expectedSlots(habit, weekStart, weekEnd)
        val completed = countCompleted(completions, weekStart, weekEnd, zone)
        return HabitWeekProgress(completed, expected)
    }

    fun heatmap(
        habit: HabitEntity,
        completions: List<HabitCompletionEntity>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<HabitHeatmapDay> {
        val start = today.minusWeeks(HEATMAP_WEEKS.toLong() - 1).with(java.time.DayOfWeek.MONDAY)
        val habitCreatedDate = java.time.Instant.ofEpochMilli(habit.createdAtMillis).atZone(zone).toLocalDate()
        val completionMap = completions
            .filter { it.completed }
            .associateBy { it.dayMillis }
        val days = mutableListOf<HabitHeatmapDay>()
        var date = start
        val end = today.plusDays(1)
        while (date.isBefore(end)) {
            val dayMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val scheduled = !date.isBefore(habitCreatedDate) && isScheduledOn(habit, date)
            val completed = completionMap[dayMillis]?.completed == true
            days += HabitHeatmapDay(dayMillis, scheduled, completed, graceDay = false)
            date = date.plusDays(1)
        }
        return applyGraceDays(habit, days, today, zone)
    }

    fun isScheduledOn(habit: HabitEntity, date: LocalDate): Boolean {
        return when (habit.scheduleType) {
            HabitScheduleType.DAILY -> true
            HabitScheduleType.WEEKDAYS -> date.dayOfWeek.value in 1..5
            HabitScheduleType.SPECIFIC_DAYS -> {
                val days = parseScheduleDays(habit.scheduleDays)
                days.isEmpty() || date.dayOfWeek.value in days
            }
            HabitScheduleType.X_PER_WEEK -> true
        }
    }

    fun expectedSlots(habit: HabitEntity, start: LocalDate, endInclusive: LocalDate): Int {
        if (endInclusive.isBefore(start)) return 0
        return when (habit.scheduleType) {
            HabitScheduleType.X_PER_WEEK -> {
                val weeks = weeksInRange(start, endInclusive)
                habit.targetPerWeek.coerceAtLeast(1) * weeks
            }
            else -> {
                var count = 0
                var date = start
                while (!date.isAfter(endInclusive)) {
                    if (isScheduledOn(habit, date)) count++
                    date = date.plusDays(1)
                }
                count
            }
        }
    }

    internal fun parseScheduleDays(scheduleDays: String): Set<Int> =
        scheduleDays.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .toSet()

    private fun countCompleted(
        completions: List<HabitCompletionEntity>,
        start: LocalDate,
        endInclusive: LocalDate,
        zone: ZoneId
    ): Int {
        val startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return completions.count { it.completed && it.dayMillis in startMillis until endMillis }
    }

    private fun weeksInRange(start: LocalDate, endInclusive: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(start, endInclusive) + 1
        return ((days + 6) / 7).toInt().coerceAtLeast(1)
    }

    private fun applyGraceDays(
        habit: HabitEntity,
        days: List<HabitHeatmapDay>,
        today: LocalDate,
        zone: ZoneId
    ): List<HabitHeatmapDay> {
        if (habit.gracePerWeek <= 0) return days
        val mutable = days.toMutableList()
        var weekStart = today.with(java.time.DayOfWeek.MONDAY)
        repeat(HEATMAP_WEEKS) {
            val weekEnd = weekStart.plusDays(6)
            val weekIndices = mutable.indices.filter { idx ->
                val date = java.time.Instant.ofEpochMilli(mutable[idx].dayMillis).atZone(zone).toLocalDate()
                !date.isBefore(weekStart) && !date.isAfter(weekEnd)
            }
            val missed = weekIndices.count { idx ->
                val day = mutable[idx]
                day.scheduled && !day.completed
            }
            var graceLeft = minOf(missed, habit.gracePerWeek)
            weekIndices.asReversed().forEach { idx ->
                if (graceLeft <= 0) return@forEach
                val day = mutable[idx]
                if (day.scheduled && !day.completed) {
                    mutable[idx] = day.copy(graceDay = true)
                    graceLeft--
                }
            }
            weekStart = weekStart.minusWeeks(1)
        }
        return mutable
    }
}
