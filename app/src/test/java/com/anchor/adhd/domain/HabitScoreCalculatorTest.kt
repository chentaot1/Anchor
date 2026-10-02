package com.anchor.adhd.domain

import com.anchor.adhd.data.model.HabitCompletionEntity
import com.anchor.adhd.data.model.HabitEntity
import com.anchor.adhd.data.model.HabitScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HabitScoreCalculatorTest {
    private val zone = ZoneId.of("America/New_York")
    private val today = LocalDate.of(2026, 8, 2) // Sunday

    private fun habit(
        scheduleType: HabitScheduleType = HabitScheduleType.DAILY,
        scheduleDays: String = "",
        targetPerWeek: Int = 7,
        gracePerWeek: Int = 1
    ) = HabitEntity(
        id = 1,
        name = "Test",
        scheduleType = scheduleType,
        scheduleDays = scheduleDays,
        targetPerWeek = targetPerWeek,
        gracePerWeek = gracePerWeek,
        createdAtMillis = 0
    )

    private fun completion(day: LocalDate) = HabitCompletionEntity(
        habitId = 1,
        dayMillis = day.atStartOfDay(zone).toInstant().toEpochMilli(),
        completed = true
    )

    @Test
    fun perfectDailyHabit_scores100() {
        val completions = (0 until 30).map { completion(today.minusDays(it.toLong())) }
        val result = HabitScoreCalculator.score(habit(), completions, today, zone)
        assertEquals(100, result.score)
        assertEquals(30, result.expected)
        assertEquals(30, result.completed)
    }

    @Test
    fun oneMissedDay_withGrace_doesNotZeroScore() {
        val completions = (1 until 30).map { completion(today.minusDays(it.toLong())) }
        val result = HabitScoreCalculator.score(habit(gracePerWeek = 1), completions, today, zone)
        assertTrue(result.score >= 96)
        assertEquals(1, result.graceUsed)
    }

    @Test
    fun weekdays_onlyCountsWeekdays() {
        val monday = LocalDate.of(2026, 7, 28) // Monday
        val result = HabitScoreCalculator.expectedSlots(
            habit(scheduleType = HabitScheduleType.WEEKDAYS),
            monday,
            monday.plusDays(6)
        )
        assertEquals(5, result)
    }

    @Test
    fun xPerWeek_usesTargetNotDays() {
        val monday = LocalDate.of(2026, 7, 28)
        val result = HabitScoreCalculator.expectedSlots(
            habit(scheduleType = HabitScheduleType.X_PER_WEEK, targetPerWeek = 3),
            monday,
            monday.plusDays(6)
        )
        assertEquals(3, result)
    }

    @Test
    fun weekProgress_countsUpToToday() {
        val monday = LocalDate.of(2026, 7, 27) // Monday
        val wednesday = monday.plusDays(2)
        val completions = listOf(completion(monday), completion(wednesday))
        val progress = HabitScoreCalculator.weekProgress(habit(), completions, wednesday, zone)
        assertEquals(2, progress.completed)
        assertEquals(3, progress.expected)
    }

    @Test
    fun emptyCompletions_scoresWithGrace() {
        val result = HabitScoreCalculator.score(habit(gracePerWeek = 2), emptyList(), today, zone)
        assertTrue(result.score > 0)
        assertEquals(0, result.completed)
    }

    @Test
    fun specificDays_parsesSchedule() {
        val monWedFri = habit(
            scheduleType = HabitScheduleType.SPECIFIC_DAYS,
            scheduleDays = "1,3,5"
        )
        assertTrue(HabitScoreCalculator.isScheduledOn(monWedFri, LocalDate.of(2026, 7, 27))) // Monday
        assertTrue(!HabitScoreCalculator.isScheduledOn(monWedFri, LocalDate.of(2026, 7, 28))) // Tuesday
    }
}
