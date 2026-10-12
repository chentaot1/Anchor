package com.anchor.adhd.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderQuietHoursTest {
    @Test
    fun overnightQuietWindow_blocksLateNightAndEarlyMorning() {
        assertTrue(ReminderQuietHours.isQuietHour(23, quietStart = 22, quietEnd = 7))
        assertTrue(ReminderQuietHours.isQuietHour(3, quietStart = 22, quietEnd = 7))
        assertFalse(ReminderQuietHours.isQuietHour(12, quietStart = 22, quietEnd = 7))
    }

    @Test
    fun sameDayQuietWindow_blocksMiddayOnly() {
        assertTrue(ReminderQuietHours.isQuietHour(13, quietStart = 12, quietEnd = 14))
        assertFalse(ReminderQuietHours.isQuietHour(10, quietStart = 12, quietEnd = 14))
    }
}
