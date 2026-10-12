package com.anchor.adhd.desktop.focus

import com.anchor.adhd.desktop.db.DesktopTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopFocusLedgerTest {
    @Test
    fun switchingTasksPreservesOriginalCreditAndNewSessionAttribution() {
        var now = 0L
        val timer = DesktopCountdown(1500) { now }
        val ledger = DesktopFocusLedger()
        val original = DesktopTask(1, "Draft essay", durationMinutes = 25)
        ledger.begin(original, 1500)
        timer.resume()
        now += 600_000_000_000L
        // Reordering, deleting, or completing the planner entry must not change this session.
        val next = original.copy(id = 2, title = "Read chapter", durationMinutes = 10)
        val partial = ledger.finish(timer.elapsedSeconds, completed = false)!!
        assertEquals("Draft essay", partial.taskTitle)
        assertEquals(600, partial.actualSeconds)
        assertEquals(1500, partial.plannedSeconds)
        assertFalse(partial.completed)
        timer.reset(600)
        ledger.begin(next, 600)
        timer.resume()
        now += 600_000_000_000L
        val completed = ledger.finish(timer.elapsedSeconds, completed = true)!!
        assertEquals("Read chapter", completed.taskTitle)
        assertEquals(600, completed.actualSeconds)
        assertTrue(completed.completed)
    }

    @Test
    fun sessionCannotBeCreditedTwice() {
        val ledger = DesktopFocusLedger()
        ledger.begin(DesktopTask(1, "Essay"), 1500)
        assertEquals(1500, ledger.finish(1500, completed = true)!!.actualSeconds)
        assertNull(ledger.finish(1500, completed = true))
        assertNull(ledger.finish(1500, completed = false))
        assertFalse(ledger.isOpen)
    }

    @Test
    fun shortAbandonedSessionClosesWithoutCredit() {
        val ledger = DesktopFocusLedger()
        ledger.begin(null, 1500)
        assertNull(ledger.finish(299, completed = false))
        assertFalse(ledger.isOpen)
        ledger.begin(null, 300)
        assertEquals("Deep Work", ledger.finish(300, completed = false)!!.taskTitle)
    }

    @Test
    fun shortenedCompletedTimerKeepsPlannedAndActualTimeSeparate() {
        val ledger = DesktopFocusLedger()
        ledger.begin(DesktopTask(1, "Essay"), 1500)
        val credit = ledger.finish(360, completed = true)!!
        assertEquals(1500, credit.plannedSeconds)
        assertEquals(360, credit.actualSeconds)
    }

    @Test(expected = IllegalStateException::class)
    fun replacingAnOpenSessionRequiresClosingItFirst() {
        val ledger = DesktopFocusLedger()
        ledger.begin(null, 1500)
        ledger.begin(null, 600)
    }

    @Test
    fun explicitSessionDurationChangePreservesTaskAttribution() {
        val ledger = DesktopFocusLedger()
        ledger.begin(DesktopTask(1, "Essay"), 1500)
        ledger.setPlannedSeconds(600)
        val credit = ledger.finish(600, completed = true)!!
        assertEquals("Essay", credit.taskTitle)
        assertEquals(600, credit.plannedSeconds)
        assertEquals(600, credit.actualSeconds)
    }

    @Test
    fun adjustingPausedRemainingTimeKeepsAllWorkedTimeInTheCredit() {
        var now = 0L
        val timer = DesktopCountdown(900) { now }
        val ledger = DesktopFocusLedger()
        ledger.begin(null, 900)
        timer.resume()
        now += 300_400_000_000L
        timer.pause()
        timer.adjustSeconds(60)
        now += 120_000_000_000L
        assertEquals(660, timer.remainingSeconds)
        timer.resume()
        now += 599_600_000_000L
        assertEquals(60, timer.remainingSeconds)
        assertEquals(900, ledger.finish(timer.elapsedSeconds, completed = false)!!.actualSeconds)
    }
}
