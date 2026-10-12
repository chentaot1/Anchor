package com.anchor.adhd.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anchor.adhd.calendar.DeviceCalendarOccupancy
import com.anchor.adhd.data.db.AnchorDatabase
import com.anchor.adhd.data.model.CalendarEventEntity
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
class CalendarWindowTest {
    private lateinit var db: AnchorDatabase
    private lateinit var repository: PlanRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PlanRepository(
            taskDao = db.taskDao(),
            routineDao = db.routineDao(),
            calendarDao = db.calendarEventDao(),
            assignmentDao = db.assignmentDao(),
            replanDao = db.replanDao(),
            db = db
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun todaySync_doesNotDeleteTomorrowRows() = runBlocking {
        val today = 86_400_000L
        val tomorrow = today + 86_400_000L
        val dayEnd = tomorrow
        db.calendarEventDao().upsertAll(
            listOf(
                CalendarEventEntity(
                    id = DeviceCalendarOccupancy.id(1, today + 10 * 3_600_000L),
                    title = "Today lecture",
                    startMillis = today + 10 * 3_600_000L,
                    endMillis = today + 11 * 3_600_000L,
                    source = DeviceCalendarOccupancy.SOURCE
                ),
                CalendarEventEntity(
                    id = DeviceCalendarOccupancy.id(1, tomorrow + 10 * 3_600_000L),
                    title = "Tomorrow lecture",
                    startMillis = tomorrow + 10 * 3_600_000L,
                    endMillis = tomorrow + 11 * 3_600_000L,
                    source = DeviceCalendarOccupancy.SOURCE
                )
            )
        )

        repository.replaceDeviceCalendarWindow(
            from = today,
            to = dayEnd,
            events = listOf(
                CalendarEventEntity(
                    id = DeviceCalendarOccupancy.id(2, today + 14 * 3_600_000L),
                    title = "New today",
                    startMillis = today + 14 * 3_600_000L,
                    endMillis = today + 15 * 3_600_000L,
                    source = DeviceCalendarOccupancy.SOURCE
                )
            )
        )

        val all = db.calendarEventDao().getAll()
        assertEquals(2, all.size)
        assertTrue(all.any { it.title == "New today" })
        assertTrue(all.any { it.title == "Tomorrow lecture" })
        assertTrue(all.none { it.title == "Today lecture" })
    }

    @Test
    fun todaySync_doesNotDeleteGoogleRows() = runBlocking {
        val today = 86_400_000L
        db.calendarEventDao().upsertAll(
            listOf(
                CalendarEventEntity(
                    id = "google-1",
                    title = "Google event",
                    startMillis = today + 9 * 3_600_000L,
                    endMillis = today + 10 * 3_600_000L,
                    source = "google"
                )
            )
        )

        repository.replaceDeviceCalendarWindow(today, today + 86_400_000L, emptyList())

        val all = db.calendarEventDao().getAll()
        assertEquals(1, all.size)
        assertEquals("Google event", all.first().title)
    }
}
