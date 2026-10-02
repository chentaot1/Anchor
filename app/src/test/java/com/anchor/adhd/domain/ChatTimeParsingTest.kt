package com.anchor.adhd.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTimeParsingTest {

    @Test
    fun splitCapture_splitsCommasAndNumbered() {
        val parts = splitCapture("1) buy milk, 2) call mom, 3) write")
        assertEquals(listOf("buy milk", "call mom", "write"), parts)
    }

    @Test
    fun splitCapture_handlesAnd() {
        assertEquals(listOf("a", "b"), splitCapture("a and b"))
    }

    @Test
    fun parseBrainDumpWithoutModel_splitsCommasAndLines() {
        assertEquals(
            listOf("buy milk", "call mom", "finish lab"),
            parseBrainDumpWithoutModel("buy milk, call mom\nfinish lab"),
        )
    }

    @Test
    fun parseBrainDumpWithoutModel_singleLineStillOneTask() {
        assertEquals(listOf("review notes for chem"), parseBrainDumpWithoutModel("review notes for chem"))
    }

    @Test
    fun scanTime_inMinutes_setsFuture() {
        val now = 1_000_000_000L
        val result = scanTime("in 30 min", now)
        assertNotNull(result)
        assertEquals(now + 30 * 60_000L, result!!)
    }

    @Test
    fun scanTime_inHours_setsFuture() {
        val now = 1_000_000_000L
        val result = scanTime("in 2 hours", now)
        assertEquals(now + 2 * 3_600_000L, result!!)
    }

    @Test
    fun scanTime_noon_returnsNontnnull() {
        assertNotNull(scanTime("at 12pm", 1_000_000_000L))
    }

    @Test
    fun scanTime_bareHourWithPreposition_returnsTime() {
        // "at 3" (bare hour, no colon / am-pm) now parses as 3:00.
        assertNotNull(scanTime("at 3", 1_000_000_000L))
    }

    @Test
    fun scanTime_pastTime_rollsForwardToTomorrow() {
        // Freeze "now" to 23:00, then ask for 13:00 — must roll to tomorrow, not produce a past time.
        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.LocalDate.now(zone).atTime(23, 0).atZone(zone).toInstant().toEpochMilli()
        val result = scanTime("at 1pm", now)
        assertNotNull(result)
        val target = java.time.Instant.ofEpochMilli(result!!).atZone(zone)
        assertTrue(target.toLocalDate().isAfter(java.time.LocalDate.now(zone)) || target.hour == 13)
    }

    @Test
    fun scanTime_doesNotMisparseNumberInItemTitle() {
        // "buy 3 shirts" has a plain number but no at/by or am/pm → not a time.
        assertNull(scanTime("buy 3 shirts", 1_000_000_000L))
    }

    @Test
    fun scanTime_noTime_returnsNull() {
        assertNull(scanTime("no time mentioned", 1_000_000_000L))
    }

    @Test
    fun scanRemind_stripsTime_fromTitle() {
        val (title, at) = scanRemind("remind me to submit the form at 5pm", 1_000_000_000L)
        assertEquals("submit the form", title)
        assertNotNull(at)
    }

    @Test
    fun snoozeTarget_inMinutes_usesExplicitTime() {
        val now = 1_000_000_000L
        assertEquals(now + 20 * 60_000L, snoozeTargetMillis("snooze in 20 min", now))
    }

    @Test
    fun snoozeTarget_default_plusThirty() {
        val now = 1_000_000_000L
        assertEquals(now + 30 * 60_000L, snoozeTargetMillis("snooze that", now))
    }

    @Test
    fun snoozeTarget_night_landsOnEightPm() {
        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.LocalDate.now(zone).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val result = snoozeTargetMillis("push to night", now)
        val target = java.time.Instant.ofEpochMilli(result).atZone(zone)
        assertEquals(20, target.hour)
        assertEquals(0, target.minute)
    }
    @Test
    fun targetDateFor_tomorrow_isTheNextCalendarDate() {
        val zone = java.time.ZoneId.systemDefault()
        val base = java.time.LocalDate.of(2026, 9, 9)
        val now = base.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        assertEquals(base.plusDays(1), targetDateFor("tomorrow", now, zone))
        assertEquals(base.plusDays(2), targetDateFor("day after tomorrow", now, zone))
    }

    @Test
    fun targetDateFor_explicitDateAndWeekday_resolveFromBaseDate() {
        val zone = java.time.ZoneId.systemDefault()
        val base = java.time.LocalDate.of(2026, 9, 9) // Wednesday
        val now = base.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        assertEquals(java.time.LocalDate.of(2026, 9, 12), targetDateFor("on 9/12", now, zone))
        assertEquals(java.time.LocalDate.of(2026, 9, 14), targetDateFor("next monday", now, zone))
    }

    @Test
    fun scheduleTargetMillis_dateOnly_defaultsToNineOnTargetDate() {
        val zone = java.time.ZoneId.systemDefault()
        val base = java.time.LocalDate.of(2026, 9, 9)
        val now = base.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val result = scheduleTargetMillis("finish lab tomorrow", now)

        assertNotNull(result)
        val target = java.time.Instant.ofEpochMilli(result!!).atZone(zone)
        assertEquals(base.plusDays(1), target.toLocalDate())
        assertEquals(9, target.hour)
    }

    @Test
    fun scanRemind_tomorrow_stripsDateFromTitle() {
        val zone = java.time.ZoneId.systemDefault()
        val base = java.time.LocalDate.of(2026, 9, 9)
        val now = base.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val (title, at) = scanRemind("remind me to call mom tomorrow", now)

        assertEquals("call mom", title)
        assertNotNull(at)
        assertEquals(base.plusDays(1), java.time.Instant.ofEpochMilli(at!!).atZone(zone).toLocalDate())
    }

}
