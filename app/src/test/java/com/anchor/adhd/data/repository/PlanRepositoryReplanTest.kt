package com.anchor.adhd.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anchor.adhd.data.db.AnchorDatabase
import com.anchor.adhd.data.model.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlanRepositoryReplanTest {
    private lateinit var db: AnchorDatabase
    private lateinit var repository: PlanRepository
    private val zone = ZoneId.systemDefault()

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
    fun queueReplanForMissedTasks_doesNotDuplicateUnresolvedItems() = runBlocking {
        val day = LocalDate.of(2026, 8, 2)
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val now = dayStart + 10 * 60 * 60_000L

        db.taskDao().insert(
            TaskEntity(
                title = "Overdue block",
                scheduledStartMillis = dayStart + 8 * 60 * 60_000L,
                durationMinutes = 30
            )
        )

        // Simulate "now" by using a task whose end is before our synthetic query window.
        val missed = db.taskDao().getMissedScheduledBefore(now, dayStart + 86_400_000L)
        assertEquals(1, missed.size)

        repository.queueReplanForMissedTasks(day)
        repository.queueReplanForMissedTasks(day)

        val queue = repository.observeReplan().first()
        assertEquals(1, queue.size)
    }

    @Test
    fun scheduleTaskAtNextSlot_usesHoleBeforeLecture() = runBlocking {
        val zone = java.time.ZoneOffset.UTC
        val day = java.time.LocalDate.of(2026, 8, 13)
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val nine = day.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val shutdown = day.atTime(21, 0).atZone(zone).toInstant().toEpochMilli()
        val taskId = db.taskDao().insert(
            TaskEntity(title = "Reschedule me", durationMinutes = 20)
        )
        db.taskDao().insert(
            TaskEntity(
                title = "Morning block",
                scheduledStartMillis = nine,
                durationMinutes = 20
            )
        )
        db.calendarEventDao().upsertAll(
            listOf(
                com.anchor.adhd.data.model.CalendarEventEntity(
                    id = "lecture",
                    title = "Lecture",
                    startMillis = day.atTime(14, 0).atZone(zone).toInstant().toEpochMilli(),
                    endMillis = day.atTime(16, 0).atZone(zone).toInstant().toEpochMilli()
                )
            )
        )

        val start = repository.scheduleTaskAtNextSlot(
            taskId,
            20,
            nowMillis = nine,
            dayStartMillis = dayStart,
            shutdownMillis = shutdown
        )

        val expected = day.atTime(9, 20).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, start)
        assertEquals(expected, db.taskDao().getById(taskId)!!.scheduledStartMillis)
    }

    @Test
    fun scheduleTaskAtNextSlot_noHole_staysUnscheduledToday() = runBlocking {
        val zone = java.time.ZoneOffset.UTC
        val day = java.time.LocalDate.of(2026, 8, 13)
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val now = day.atTime(23, 30).atZone(zone).toInstant().toEpochMilli()
        val shutdown = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val taskId = db.taskDao().insert(
            TaskEntity(title = "Late dump", durationMinutes = 45)
        )

        val start = repository.scheduleTaskAtNextSlot(
            taskId,
            45,
            nowMillis = now,
            dayStartMillis = dayStart,
            shutdownMillis = shutdown
        )

        assertEquals(null, start)
        val task = db.taskDao().getById(taskId)!!
        assertEquals(null, task.scheduledStartMillis)
        assertEquals(com.anchor.adhd.data.model.InboxState.TODAY, task.inboxState)
    }

    @Test
    fun captureToInbox_neverSetsScheduledStart() = runBlocking {
        val ids = repository.captureToInbox(listOf("buy milk", "call mom"))
        assertEquals(2, ids.size)
        ids.forEach { id ->
            val task = db.taskDao().getById(id)!!
            assertEquals(null, task.scheduledStartMillis)
            assertEquals(com.anchor.adhd.data.model.InboxState.TODAY, task.inboxState)
        }
    }

    @Test
    fun breakdownSteps_fillHolesSequentiallyThenInbox() = runBlocking {
        val zone = java.time.ZoneOffset.UTC
        val day = java.time.LocalDate.of(2026, 8, 13)
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val nine = day.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val shutdown = day.atTime(9, 45).atZone(zone).toInstant().toEpochMilli()
        val parent = TaskEntity(title = "Essay", durationMinutes = 60, scheduledStartMillis = 99L)
        val children = listOf(
            TaskEntity(title = "Open doc", durationMinutes = 20, scheduledStartMillis = 99L, sortOrder = 0),
            TaskEntity(title = "Write intro", durationMinutes = 20, scheduledStartMillis = 99L, sortOrder = 1),
            TaskEntity(title = "Cite sources", durationMinutes = 20, scheduledStartMillis = 99L, sortOrder = 2)
        )
        val parentId = repository.insertParentWithChildren(parent, children)
        val insertedParent = db.taskDao().getById(parentId)!!
        assertEquals(null, insertedParent.scheduledStartMillis)

        val childIds = db.taskDao().getChildren(parentId).sortedBy { it.sortOrder }.map { it.id }
        repository.scheduleIntoHoles(childIds, nine, dayStart, shutdown)

        val scheduled = childIds.map { db.taskDao().getById(it)!! }
        assertEquals(day.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(), scheduled[0].scheduledStartMillis)
        assertEquals(day.atTime(9, 20).atZone(zone).toInstant().toEpochMilli(), scheduled[1].scheduledStartMillis)
        assertEquals(null, scheduled[2].scheduledStartMillis)
        assertEquals(com.anchor.adhd.data.model.InboxState.TODAY, scheduled[2].inboxState)
    }

    @Test
    fun insertParentWithChildren_writesAllSevenOrNone() = runBlocking {
        val parent = TaskEntity(title = "Essay")
        val children = (1..7).map { TaskEntity(title = "Open step $it", sortOrder = it) }
        val parentId = repository.insertParentWithChildren(parent, children)
        val stored = db.taskDao().getChildren(parentId)
        assertEquals(7, stored.size)
        stored.forEach { child ->
            assertEquals(parentId, child.parentTaskId)
        }
    }

    @Test
    fun setTaskActualMinutes_replacesInsteadOfAccumulating() = runBlocking {
        val taskId = db.taskDao().insert(TaskEntity(title = "Write", actualMinutes = 10))

        repository.setTaskActualMinutes(taskId, 25)

        val updated = db.taskDao().getById(taskId)!!
        assertEquals(25, updated.actualMinutes)
    }
}
