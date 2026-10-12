package com.anchor.adhd.domain

import com.anchor.adhd.data.model.HabitCompletionEntity
import com.anchor.adhd.data.model.HabitEntity
import com.anchor.adhd.data.model.HabitScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HabitScoreCalculatorTest {
    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 4, 15) // Wednesday

    private fun dayMillis(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun newlyCreatedHabit_notPenalizedForPreCreationDays() {
        val habit = HabitEntity(
            id = 1L,
            name = "Drink water",
            scheduleType = HabitScheduleType.DAILY,
            gracePerWeek = 0,
            createdAtMillis = dayMillis(today)
        )
        val resultNoCompletions = HabitScoreCalculator.score(habit, emptyList(), today, zone)
        // On day 1 with 1 scheduled day and 0 completions (0 grace), missed = 1 -> 0%
        assertEquals(0, resultNoCompletions.score)

        // When completed today (day 1), score is 100% with one recorded completion (not penalized for 20 days before creation)
        val completion = HabitCompletionEntity(habitId = 1L, dayMillis = dayMillis(today), completed = true)
        val resultCompleted = HabitScoreCalculator.score(habit, listOf(completion), today, zone)
        assertEquals(100, resultCompleted.score)
        assertEquals(1, resultCompleted.completed)
    }

    @Test
    fun dailyHabit_withGraceDays_absorbsMissedDaysWithinGrace() {
        val createdDate = today.minusDays(6) // 7-day history
        val habit = HabitEntity(
            id = 2L,
            name = "Meditate",
            scheduleType = HabitScheduleType.DAILY,
            gracePerWeek = 1,
            createdAtMillis = dayMillis(createdDate)
        )
        // Complete 6 out of 7 days (miss 1 day, covered by gracePerWeek = 1)
        val completions = (0..5).map { offset ->
            HabitCompletionEntity(
                habitId = 2L,
                dayMillis = dayMillis(today.minusDays(offset.toLong())),
                completed = true
            )
        }
        val result = HabitScoreCalculator.score(habit, completions, today, zone)
        assertEquals(100, result.score)
        assertEquals(6, result.completed)
    }

    @Test
    fun specificDaysSchedule_weekProgressUsesFullWeekTargetEvenMidWeek() {
        // today is Wednesday 2026-04-15; week is Mon Apr 13 .. Sun Apr 19
        val habit = HabitEntity(
            id = 3L,
            name = "Gym",
            scheduleType = HabitScheduleType.SPECIFIC_DAYS,
            scheduleDays = "1,3,5",
            gracePerWeek = 0,
            createdAtMillis = dayMillis(today.minusDays(14))
        )
        val monday = LocalDate.of(2026, 4, 13)
        val completions = listOf(
            HabitCompletionEntity(habitId = 3L, dayMillis = dayMillis(monday), completed = true)
        )
        val progress = HabitScoreCalculator.weekProgress(habit, completions, today, zone)
        assertEquals(1, progress.completed)
        assertEquals(3, progress.expected)
    }

    @Test
    fun flexibleWeeklySchedule_calculatesWeekProgressAndScore() {
        val habit = HabitEntity(
            id = 4L,
            name = "Read 20 pages",
            scheduleType = HabitScheduleType.X_PER_WEEK,
            targetPerWeek = 4,
            gracePerWeek = 0,
            createdAtMillis = dayMillis(today.minusDays(6))
        )
        val monday = LocalDate.of(2026, 4, 13)
        val tuesday = LocalDate.of(2026, 4, 14)
        val wednesday = LocalDate.of(2026, 4, 15)
        val completions = listOf(monday, tuesday, wednesday).map { d ->
            HabitCompletionEntity(habitId = 4L, dayMillis = dayMillis(d), completed = true)
        }
        val progress = HabitScoreCalculator.weekProgress(habit, completions, today, zone)
        assertEquals(3, progress.completed)
        assertEquals(4, progress.expected)

        val scoreResult = HabitScoreCalculator.score(habit, completions, today, zone)
        assertEquals(75, scoreResult.score)
    }

    @Test
    fun heatmap_marksDaysBeforeCreationAsUnscheduled() {
        val createdDate = today.minusDays(2)
        val habit = HabitEntity(
            id = 5L,
            name = "Journal",
            scheduleType = HabitScheduleType.DAILY,
            createdAtMillis = dayMillis(createdDate)
        )
        val cells = HabitScoreCalculator.heatmap(habit, emptyList(), today, zone)
        fun dateOf(cell: HabitHeatmapDay) = java.time.Instant.ofEpochMilli(cell.dayMillis).atZone(zone).toLocalDate()
        val preCreationCells = cells.filter { dateOf(it).isBefore(createdDate) }
        val postCreationCells = cells.filter { !dateOf(it).isBefore(createdDate) && !dateOf(it).isAfter(today) }

        assertTrue(preCreationCells.isNotEmpty())
        assertTrue(preCreationCells.all { !it.scheduled })
        assertTrue(postCreationCells.all { it.scheduled })
        val futureCells = cells.filter { dateOf(it).isAfter(today) }
        assertTrue(futureCells.all { !it.completed })
    }
}
