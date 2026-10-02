package com.anchor.adhd.domain

import com.anchor.adhd.data.model.CalendarEventEntity
import com.anchor.adhd.data.model.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class SchedulingSlotsTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 8, 13)
    private val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
    private val shutdown = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun at(hour: Int, minute: Int = 0): Long =
        day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun holeAfterMorningTask_isBeforeLecture_notAfterLecture() {
        val now = at(9, 0)
        val occupancy = SchedulingSlots.occupancyIntervals(
            tasks = listOf(
                TaskEntity(
                    title = "Morning block",
                    scheduledStartMillis = at(9, 0),
                    durationMinutes = 20
                )
            ),
            events = listOf(
                CalendarEventEntity(
                    id = "lecture",
                    title = "Lecture",
                    startMillis = at(14, 0),
                    endMillis = at(16, 0)
                )
            )
        )

        val hole = SchedulingSlots.firstHoleThatFits(
            nowMillis = now,
            durationMinutes = 20,
            dayStartMillis = dayStart,
            shutdownMillis = at(21, 0),
            occupancy = occupancy,
            zone = zone
        )

        assertNotNull(hole)
        assertEquals(at(9, 20), hole)
        assertTrue(hole!! < at(14, 0))
    }

    @Test
    fun allDayBirthday_isNotAWall() {
        val occupancy = SchedulingSlots.occupancyIntervals(
            tasks = emptyList(),
            events = listOf(
                CalendarEventEntity(
                    id = "bday",
                    title = "Birthday",
                    startMillis = dayStart,
                    endMillis = shutdown,
                    isAllDay = true
                )
            )
        )

        val hole = SchedulingSlots.firstHoleThatFits(
            nowMillis = at(9, 0),
            durationMinutes = 20,
            dayStartMillis = dayStart,
            shutdownMillis = at(21, 0),
            occupancy = occupancy,
            zone = zone
        )

        assertEquals(at(9, 0), hole)
    }

    @Test
    fun noGapBeforeShutdown_returnsNull_notPastMidnight() {
        val hole = SchedulingSlots.firstHoleThatFits(
            nowMillis = at(23, 30),
            durationMinutes = 45,
            dayStartMillis = dayStart,
            shutdownMillis = shutdown,
            occupancy = emptyList(),
            zone = zone
        )

        assertNull(hole)
    }

    @Test
    fun emptyMorning_returnsRoundedNow() {
        val hole = SchedulingSlots.firstHoleThatFits(
            nowMillis = at(9, 0),
            durationMinutes = 20,
            dayStartMillis = dayStart,
            shutdownMillis = at(21, 0),
            occupancy = emptyList(),
            zone = zone
        )
        assertEquals(at(9, 0), hole)
    }
}
