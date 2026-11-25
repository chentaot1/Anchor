package com.anchor.adhd.ui.components

import com.anchor.adhd.ui.focus.formatFocusCountdown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnchorTideVisualsTest {
    @Test
    fun tideBarFractionsScaleRelativeToPeakWithMinimumFloor() {
        val days = listOf(
            WeeklyTideDay("M", 0, false),
            WeeklyTideDay("T", 30, false),
            WeeklyTideDay("W", 60, true),
        )
        val fractions = computeTideBarFractions(days, minScaleMinutes = 60)
        assertEquals(0f, fractions[0])
        assertEquals(0.5f, fractions[1], 0.001f)
        assertEquals(1.0f, fractions[2], 0.001f)
    }

    @Test
    fun tideBarFractionsScaleAboveFloorAndHandleEmptyOrNegativeInputs() {
        assertTrue(computeTideBarFractions(emptyList()).isEmpty())

        val days = listOf(
            WeeklyTideDay("M", -10, false),
            WeeklyTideDay("T", 60, false),
            WeeklyTideDay("W", 120, true),
        )
        val fractions = computeTideBarFractions(days, minScaleMinutes = 60)
        assertEquals(0f, fractions[0], 0.001f)
        assertEquals(0.5f, fractions[1], 0.001f)
        assertEquals(1.0f, fractions[2], 0.001f)

        val allZero = listOf(WeeklyTideDay("M", 0, true))
        assertEquals(listOf(0f), computeTideBarFractions(allZero, minScaleMinutes = 0))
    }

    @Test
    fun formatTideDurationHandlesMinutesAndHours() {
        assertEquals("0m", formatTideDuration(0))
        assertEquals("45m", formatTideDuration(45))
        assertEquals("4h 10m", formatTideDuration(250))
        assertTrue(formatTideDuration(120) == "2h")
        assertEquals("0m", formatTideDuration(-15))
    }

    @Test
    fun computeTodayTideFractionClampsAndGuardsZeroGoal() {
        assertEquals(0f, computeTodayTideFraction(todayMinutes = 0, goalMinutes = 60), 0.001f)
        assertEquals(0.5f, computeTodayTideFraction(todayMinutes = 30, goalMinutes = 60), 0.001f)
        assertEquals(1f, computeTodayTideFraction(todayMinutes = 90, goalMinutes = 60), 0.001f)
        assertEquals(0f, computeTodayTideFraction(todayMinutes = -5, goalMinutes = 60), 0.001f)
        assertEquals(1f, computeTodayTideFraction(todayMinutes = 10, goalMinutes = 0), 0.001f)
    }

    @Test
    fun formatFocusCountdownFormatsZeroPaddedMinutesAndSeconds() {
        assertEquals("00:00", formatFocusCountdown(0L))
        assertEquals("00:00", formatFocusCountdown(-12L))
        assertEquals("01:05", formatFocusCountdown(65L))
        assertEquals("25:00", formatFocusCountdown(1500L))
        assertEquals("90:09", formatFocusCountdown(5409L))
    }
}
