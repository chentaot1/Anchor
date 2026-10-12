@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.anchor.adhd.data.repository

import com.anchor.adhd.data.db.HabitDao
import com.anchor.adhd.data.model.HabitAutoSource
import com.anchor.adhd.data.model.HabitCompletionEntity
import com.anchor.adhd.data.model.HabitEntity
import com.anchor.adhd.data.model.HabitScheduleType
import com.anchor.adhd.domain.HabitHeatmapDay
import com.anchor.adhd.domain.HabitScoreCalculator
import com.anchor.adhd.domain.HabitScoreResult
import com.anchor.adhd.domain.HabitWeekProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

data class HabitProgress(
    val habit: HabitEntity,
    val completedToday: Boolean,
    val weekProgress: HabitWeekProgress,
    val score: HabitScoreResult,
    val heatmap: List<HabitHeatmapDay>
)

class HabitRepository(
    private val habitDao: HabitDao
) {
    fun observeActiveHabits(): Flow<List<HabitEntity>> = habitDao.observeActive()

    fun observeHabitsWithProgress(
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Flow<List<HabitProgress>> {
        val heatmapStart = today
            .minusWeeks(HabitScoreCalculator.HEATMAP_WEEKS.toLong() - 1)
            .with(java.time.DayOfWeek.MONDAY)
        val fromMillis = heatmapStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val todayMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()

        return habitDao.observeActive().flatMapLatest { habits ->
            if (habits.isEmpty()) return@flatMapLatest flowOf(emptyList())
            val ids = habits.map { it.id }
            habitDao.observeCompletionsForHabits(ids, fromMillis, toMillis).map { allCompletions ->
                val byHabit = allCompletions.groupBy { it.habitId }
                habits.map { habit ->
                    val completions = byHabit[habit.id].orEmpty()
                    val completedToday = completions.any { it.dayMillis == todayMillis && it.completed }
                    HabitProgress(
                        habit = habit,
                        completedToday = completedToday,
                        weekProgress = HabitScoreCalculator.weekProgress(habit, completions, today, zone),
                        score = HabitScoreCalculator.score(habit, completions, today, zone),
                        heatmap = HabitScoreCalculator.heatmap(habit, completions, today, zone)
                    )
                }
            }
        }
    }

    suspend fun create(
        name: String,
        scheduleType: HabitScheduleType,
        scheduleDays: String = "",
        targetPerWeek: Int = 7,
        gracePerWeek: Int = 1,
        autoSource: HabitAutoSource = HabitAutoSource.NONE,
        autoThreshold: Int = 0
    ): Result<Long> {
        if (name.isBlank()) return Result.failure(IllegalArgumentException("Name required"))
        if (habitDao.countActive() >= MAX_ACTIVE_HABITS) {
            return Result.failure(HabitLimitException(MAX_ACTIVE_HABITS))
        }
        val sortOrder = habitDao.countActive()
        val id = habitDao.insert(
            HabitEntity(
                name = name.trim(),
                scheduleType = scheduleType,
                scheduleDays = scheduleDays,
                targetPerWeek = targetPerWeek.coerceAtLeast(1),
                gracePerWeek = gracePerWeek.coerceAtLeast(0),
                autoSource = autoSource,
                autoThreshold = autoThreshold,
                sortOrder = sortOrder
            )
        )
        return Result.success(id)
    }

    suspend fun update(habit: HabitEntity) {
        habitDao.update(habit)
    }

    suspend fun archive(id: Long) {
        habitDao.archive(id)
    }

    suspend fun delete(id: Long) {
        habitDao.deleteCompletionsForHabit(id)
        habitDao.deleteById(id)
    }

    suspend fun toggleCompletion(
        habitId: Long,
        dayMillis: Long = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    ): Boolean {
        val existing = habitDao.getCompletion(habitId, dayMillis)
        return if (existing?.completed == true) {
            habitDao.deleteCompletion(habitId, dayMillis)
            false
        } else {
            habitDao.upsertCompletion(
                HabitCompletionEntity(
                    habitId = habitId,
                    dayMillis = dayMillis,
                    completed = true,
                    autoCompleted = false
                )
            )
            true
        }
    }

    suspend fun getById(id: Long): HabitEntity? = habitDao.getById(id)

    suspend fun syncAutoCompletions(steps: Long, exerciseMinutes: Int) {
        val today = LocalDate.now()
        val todayMillis = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        habitDao.observeActive().first().forEach { habit ->
            if (habit.isArchived) return@forEach
            if (!com.anchor.adhd.domain.HabitScoreCalculator.isScheduledOn(habit, today)) return@forEach
            val threshold = habit.autoThreshold.coerceAtLeast(1)
            val met = when (habit.autoSource) {
                com.anchor.adhd.data.model.HabitAutoSource.STEPS -> steps >= threshold
                com.anchor.adhd.data.model.HabitAutoSource.EXERCISE -> exerciseMinutes >= threshold
                com.anchor.adhd.data.model.HabitAutoSource.NONE -> false
            }
            if (!met) return@forEach
            val existing = habitDao.getCompletion(habit.id, todayMillis)
            if (existing?.completed == true) return@forEach
            habitDao.upsertCompletion(
                HabitCompletionEntity(
                    habitId = habit.id,
                    dayMillis = todayMillis,
                    completed = true,
                    autoCompleted = true
                )
            )
        }
    }

    suspend fun activeCount(): Int = habitDao.countActive()

    companion object {
        const val MAX_ACTIVE_HABITS = 8
    }
}

class HabitLimitException(val limit: Int) : Exception("Maximum $limit active habits")
