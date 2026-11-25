package com.anchor.adhd.ui.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusProgressTest {
    @Test
    fun returnsZeroWhenIdle() {
        assertEquals(
            0f,
            computeFocusProgress(
                active = false,
                isWorkPhase = true,
                remainingSeconds = 600,
                totalWorkSeconds = 1200,
                breakMinutes = 5,
            ),
            0.0001f,
        )
    }

    @Test
    fun computesWorkPhaseProgressFromTotalWorkSeconds() {
        assertEquals(
            0.25f,
            computeFocusProgress(
                active = true,
                isWorkPhase = true,
                remainingSeconds = 900,
                totalWorkSeconds = 1200,
                breakMinutes = 5,
            ),
            0.0001f,
        )
    }

    @Test
    fun computesBreakPhaseProgressFromBreakMinutesRatherThanWorkSeconds() {
        assertEquals(
            0.5f,
            computeFocusProgress(
                active = true,
                isWorkPhase = false,
                remainingSeconds = 150,
                totalWorkSeconds = 1500,
                breakMinutes = 5,
            ),
            0.0001f,
        )
    }

    @Test
    fun guardsZeroDurationsAndClampsOutOfRangeRemainingSeconds() {
        assertEquals(
            1f,
            computeFocusProgress(
                active = true,
                isWorkPhase = false,
                remainingSeconds = 0,
                totalWorkSeconds = 0,
                breakMinutes = 0,
            ),
            0.0001f,
        )
        assertEquals(
            0f,
            computeFocusProgress(
                active = true,
                isWorkPhase = true,
                remainingSeconds = 2000,
                totalWorkSeconds = 1200,
                breakMinutes = 5,
            ),
            0.0001f,
        )
        assertEquals(
            1f,
            computeFocusProgress(
                active = true,
                isWorkPhase = true,
                remainingSeconds = -10,
                totalWorkSeconds = 1200,
                breakMinutes = 5,
            ),
            0.0001f,
        )
    }

    @Test
    fun firstStepShowsNowTaskNotesOnlyWhenIdleOrMatchingActiveTask() {
        assertEquals(
            "Open lab rubric",
            resolveFocusFirstStep(
                active = false,
                isWorkPhase = true,
                timerTaskId = null,
                nowTaskId = 42L,
                nowTaskNotes = "  \nOpen lab rubric\nSecond line",
            ),
        )
        assertEquals(
            "Open lab rubric",
            resolveFocusFirstStep(
                active = true,
                isWorkPhase = true,
                timerTaskId = 42L,
                nowTaskId = 42L,
                nowTaskNotes = "Open lab rubric",
            ),
        )
        assertNull(
            resolveFocusFirstStep(
                active = true,
                isWorkPhase = true,
                timerTaskId = 99L,
                nowTaskId = 42L,
                nowTaskNotes = "Unrelated now task notes",
            ),
        )
        assertNull(
            resolveFocusFirstStep(
                active = true,
                isWorkPhase = true,
                timerTaskId = null,
                nowTaskId = 42L,
                nowTaskNotes = "Unrelated now task notes",
            ),
        )
        assertNull(
            resolveFocusFirstStep(
                active = true,
                isWorkPhase = false,
                timerTaskId = 42L,
                nowTaskId = 42L,
                nowTaskNotes = "Open lab rubric",
            ),
        )
    }
}
