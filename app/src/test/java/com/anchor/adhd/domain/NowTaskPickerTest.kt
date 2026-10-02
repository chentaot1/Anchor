package com.anchor.adhd.domain

import com.anchor.adhd.data.model.EnergyLevel
import com.anchor.adhd.data.model.NowSource
import com.anchor.adhd.data.model.NowTask
import com.anchor.adhd.data.model.RoutineEntity
import com.anchor.adhd.data.model.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NowTaskPickerTest {
    private val morningRoutine = RoutineEntity(id = 1, name = "Morning launch", cue = "wake up")
    private val studyRoutine = RoutineEntity(id = 2, name = "Study start", cue = "desk")

    @Test
    fun pickOverdueScheduledBeforeFutureBlock() {
        val now = 1_000_000L
        val overdue = task(1, "Late essay", start = now - 90 * 60_000L, duration = 30)
        val future = task(2, "Later lab", start = now + 60 * 60_000L, duration = 45)

        val result = NowTaskPicker.pick(
            scheduled = listOf(future, overdue),
            inboxToday = emptyList(),
            energy = EnergyLevel.OK,
            routines = emptyList(),
            nowMillis = now
        )

        assertEquals(overdue.id, result.task?.id)
        assertEquals(NowSource.SCHEDULED_NOW, result.source)
    }

    @Test
    fun pickActiveBlockDuringWindow() {
        val now = 1_000_000L
        val active = task(1, "Reading", start = now - 5 * 60_000L, duration = 30)

        val result = NowTaskPicker.pick(
            scheduled = listOf(active),
            inboxToday = emptyList(),
            energy = EnergyLevel.OK,
            routines = emptyList(),
            nowMillis = now
        )

        assertEquals(active.id, result.task?.id)
        assertEquals(NowSource.SCHEDULED_NOW, result.source)
    }

    @Test
    fun pickInboxWhenNothingScheduled() {
        val inboxTask = TaskEntity(id = 5, title = "Email professor")

        val result = NowTaskPicker.pick(
            scheduled = emptyList(),
            inboxToday = listOf(inboxTask),
            energy = EnergyLevel.OK,
            routines = emptyList(),
            nowMillis = 1_000_000L
        )

        assertEquals(inboxTask.id, result.task?.id)
        assertEquals(NowSource.INBOX, result.source)
    }

    @Test
    fun suggestMorningRoutineEarlyWhenScheduleEmpty() {
        val todayStart = 86_400_000L

        val result = NowTaskPicker.pick(
            scheduled = emptyList(),
            inboxToday = emptyList(),
            energy = EnergyLevel.OK,
            routines = listOf(morningRoutine, studyRoutine),
            nowMillis = todayStart + 60 * 60_000L
        )

        assertNull(result.task)
        assertEquals(morningRoutine.id, result.routine?.id)
        assertEquals(NowSource.EMPTY, result.source)
    }

    @Test
    fun laterTodayExcludesCurrentAndPastBlocks() {
        val now = 1_000_000L
        val current = task(1, "Now", start = now - 5 * 60_000L, duration = 30)
        val future = task(2, "Later", start = now + 60 * 60_000L, duration = 20)
        val past = task(3, "Past", start = now - 2 * 60 * 60_000L, duration = 20)
        val nowTask = NowTask(current, NowSource.SCHEDULED_NOW)

        val later = NowTaskPicker.laterToday(
            scheduled = listOf(current, future, past),
            nowTask = nowTask,
            nowMillis = now
        )

        assertEquals(listOf(future.id), later.map { it.id })
    }

    private fun task(id: Long, title: String, start: Long, duration: Int) =
        TaskEntity(
            id = id,
            title = title,
            scheduledStartMillis = start,
            durationMinutes = duration
        )
}
