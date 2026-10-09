package com.anchor.adhd.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anchor.adhd.data.db.AnchorDatabase
import com.anchor.adhd.data.model.HabitAutoSource
import com.anchor.adhd.data.model.HabitScheduleType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HabitRepositoryTest {
    private lateinit var db: AnchorDatabase
    private lateinit var repository: HabitRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = HabitRepository(db.habitDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun create_rejectsBlankNameAndEnforcesMaxActiveHabits() = runBlocking {
        val blankResult = repository.create("   ", HabitScheduleType.DAILY)
        assertTrue(blankResult.isFailure)

        repeat(HabitRepository.MAX_ACTIVE_HABITS) { idx ->
            val res = repository.create("Habit $idx", HabitScheduleType.DAILY)
            assertTrue(res.isSuccess)
        }
        assertEquals(HabitRepository.MAX_ACTIVE_HABITS, repository.activeCount())

        val overflow = repository.create("Habit Overflow", HabitScheduleType.DAILY)
        assertTrue(overflow.isFailure)
        assertTrue(overflow.exceptionOrNull() is HabitLimitException)
    }

    @Test
    fun toggleCompletion_insertsAndRemovesCompletionForDay() = runBlocking {
        val habitId = repository.create("Stretch", HabitScheduleType.DAILY).getOrThrow()
        val dayMillis = LocalDate.of(2026, 4, 15).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val firstToggle = repository.toggleCompletion(habitId, dayMillis)
        assertTrue(firstToggle)
        assertTrue(db.habitDao().getCompletion(habitId, dayMillis)?.completed == true)

        val secondToggle = repository.toggleCompletion(habitId, dayMillis)
        assertFalse(secondToggle)
        assertNull(db.habitDao().getCompletion(habitId, dayMillis))
    }

    @Test
    fun syncAutoCompletions_marksStepsAndExerciseHabitsWhenThresholdMet() = runBlocking {
        val stepsHabitId = repository.create(
            name = "Walk 5k steps",
            scheduleType = HabitScheduleType.DAILY,
            autoSource = HabitAutoSource.STEPS,
            autoThreshold = 5000
        ).getOrThrow()
        val exerciseHabitId = repository.create(
            name = "30m exercise",
            scheduleType = HabitScheduleType.DAILY,
            autoSource = HabitAutoSource.EXERCISE,
            autoThreshold = 30
        ).getOrThrow()

        val todayMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // Below threshold -> neither completed
        repository.syncAutoCompletions(steps = 3000L, exerciseMinutes = 15)
        assertNull(db.habitDao().getCompletion(stepsHabitId, todayMillis))
        assertNull(db.habitDao().getCompletion(exerciseHabitId, todayMillis))

        // Meet steps threshold only
        repository.syncAutoCompletions(steps = 5200L, exerciseMinutes = 20)
        val stepsComp = db.habitDao().getCompletion(stepsHabitId, todayMillis)
        assertTrue(stepsComp?.completed == true)
        assertTrue(stepsComp?.autoCompleted == true)
        assertNull(db.habitDao().getCompletion(exerciseHabitId, todayMillis))

        // Meet exercise threshold
        repository.syncAutoCompletions(steps = 5200L, exerciseMinutes = 35)
        val exComp = db.habitDao().getCompletion(exerciseHabitId, todayMillis)
        assertTrue(exComp?.completed == true)
        assertTrue(exComp?.autoCompleted == true)
    }

    @Test
    fun archiveAndDelete_manageHabitLifecycleAndCompletions() = runBlocking {
        val id = repository.create("Take meds", HabitScheduleType.DAILY).getOrThrow()
        val todayMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        repository.toggleCompletion(id, todayMillis)

        repository.archive(id)
        assertTrue(repository.observeActiveHabits().first().isEmpty())

        repository.delete(id)
        assertNull(repository.getById(id))
        assertNull(db.habitDao().getCompletion(id, todayMillis))
    }
}
