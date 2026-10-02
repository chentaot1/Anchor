package com.anchor.adhd.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCalendarOccupancyTest {
    @Test
    fun declined_isSkipped() {
        assertFalse(
            DeviceCalendarOccupancy.keep(
                allDay = 0,
                selfAttendeeStatus = DeviceCalendarOccupancy.STATUS_DECLINED,
                calendarVisible = true
            )
        )
    }

    @Test
    fun allDay_isSkipped() {
        assertFalse(
            DeviceCalendarOccupancy.keep(
                allDay = 1,
                selfAttendeeStatus = DeviceCalendarOccupancy.STATUS_NONE,
                calendarVisible = true
            )
        )
    }

    @Test
    fun hiddenCalendar_isSkipped() {
        assertFalse(
            DeviceCalendarOccupancy.keep(
                allDay = 0,
                selfAttendeeStatus = DeviceCalendarOccupancy.STATUS_NONE,
                calendarVisible = false
            )
        )
    }

    @Test
    fun acceptedAndNone_areKept() {
        assertTrue(
            DeviceCalendarOccupancy.keep(
                allDay = 0,
                selfAttendeeStatus = DeviceCalendarOccupancy.STATUS_NONE,
                calendarVisible = true
            )
        )
        assertTrue(
            DeviceCalendarOccupancy.keep(
                allDay = 0,
                selfAttendeeStatus = DeviceCalendarOccupancy.STATUS_ACCEPTED,
                calendarVisible = true
            )
        )
    }

    @Test
    fun id_isEventIdPlusBegin() {
        assertEquals("device:99:1700000000000", DeviceCalendarOccupancy.id(99L, 1_700_000_000_000L))
    }
}
