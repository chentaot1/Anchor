package com.anchor.adhd.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anchor.adhd.data.model.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TaskDaoTest {
    private lateinit var db: AnchorDatabase
    private lateinit var taskDao: TaskDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        taskDao = db.taskDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun getMissedScheduledBefore_includesSameDayOverdueBlock() = runBlocking {
        val dayStart = 86_400_000L
        val dayEnd = dayStart + 86_400_000L
        val now = dayStart + 10 * 60 * 60_000L
        val overdueStart = dayStart + 8 * 60 * 60_000L

        taskDao.insert(
            TaskEntity(
                title = "Missed morning block",
                scheduledStartMillis = overdueStart,
                durationMinutes = 30
            )
        )

        val missed = taskDao.getMissedScheduledBefore(now, dayEnd)

        assertEquals(1, missed.size)
        assertEquals("Missed morning block", missed.first().title)
    }

    @Test
    fun getMissedScheduledBefore_excludesFutureBlock() = runBlocking {
        val dayStart = 86_400_000L
        val dayEnd = dayStart + 86_400_000L
        val now = dayStart + 10 * 60 * 60_000L

        taskDao.insert(
            TaskEntity(
                title = "Later today",
                scheduledStartMillis = now + 60 * 60_000L,
                durationMinutes = 30
            )
        )

        val missed = taskDao.getMissedScheduledBefore(now, dayEnd)

        assertTrue(missed.isEmpty())
    }

    @Test
    fun observeScheduledForDay_onlyReturnsTodayBlocks() = runBlocking {
        val dayStart = 86_400_000L
        val dayEnd = dayStart + 86_400_000L

        taskDao.insert(
            TaskEntity(
                title = "Today",
                scheduledStartMillis = dayStart + 60 * 60_000L,
                durationMinutes = 20
            )
        )
        taskDao.insert(
            TaskEntity(
                title = "Tomorrow",
                scheduledStartMillis = dayEnd + 60 * 60_000L,
                durationMinutes = 20
            )
        )

        val today = taskDao.observeScheduledForDay(dayStart, dayEnd).first()

        assertEquals(1, today.size)
        assertEquals("Today", today.first().title)
    }
}
