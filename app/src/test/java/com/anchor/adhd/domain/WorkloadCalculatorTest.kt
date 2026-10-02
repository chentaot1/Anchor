package com.anchor.adhd.domain

import com.anchor.adhd.data.model.CalendarEventEntity
import com.anchor.adhd.data.model.TaskEntity
import com.anchor.adhd.data.model.WorkloadStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkloadCalculatorTest {
    @Test
    fun emptyDay_isOk() {
        val result = WorkloadCalculator.calculate(emptyList(), emptyList(), 360)
        assertEquals(0, result.effectiveLoad)
        assertEquals(WorkloadStatus.OK, result.status)
    }

    @Test
    fun overlappingTasks_countUnionNotSum() {
        val base = 1_000_000L
        val tasks = listOf(
            TaskEntity(id = 1, title = "A", scheduledStartMillis = base, durationMinutes = 60),
            TaskEntity(id = 2, title = "B", scheduledStartMillis = base + 30 * 60_000, durationMinutes = 60)
        )
        val result = WorkloadCalculator.calculate(tasks, emptyList(), 360)
        assertEquals(90, result.effectiveLoad)
        assertEquals(120, result.plannedMinutes)
    }

    @Test
    fun calendarUnion_addsBusyTime() {
        val base = 1_000_000L
        val tasks = listOf(
            TaskEntity(id = 1, title = "A", scheduledStartMillis = base, durationMinutes = 60)
        )
        val events = listOf(
            CalendarEventEntity(
                id = "e1",
                title = "Meeting",
                startMillis = base + 2 * 60 * 60_000,
                endMillis = base + 3 * 60 * 60_000
            )
        )
        val result = WorkloadCalculator.calculate(tasks, events, 360)
        assertEquals(120, result.effectiveLoad)
        assertEquals(60, result.busyMinutes)
    }

    @Test
    fun overCapacity_isOver() {
        val base = 1_000_000L
        val tasks = listOf(
            TaskEntity(id = 1, title = "A", scheduledStartMillis = base, durationMinutes = 400)
        )
        val result = WorkloadCalculator.calculate(tasks, emptyList(), 360)
        assertEquals(WorkloadStatus.OVER, result.status)
    }

    @Test
    fun warningAtEightyPercent() {
        val base = 1_000_000L
        val tasks = listOf(
            TaskEntity(id = 1, title = "A", scheduledStartMillis = base, durationMinutes = 300)
        )
        val result = WorkloadCalculator.calculate(tasks, emptyList(), 360)
        assertEquals(WorkloadStatus.WARNING, result.status)
    }
}
