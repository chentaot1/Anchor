package com.anchor.adhd.desktop.focus

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopCountdownTest {
    private var nowNanos = 0L

    private fun advanceMillis(millis: Long) {
        nowNanos += millis * 1_000_000L
    }

    @Test
    fun delayedUiUpdateUsesAllElapsedTime() {
        val timer = DesktopCountdown(60) { nowNanos }
        timer.resume()
        advanceMillis(17_400)
        assertEquals(43, timer.remainingSeconds)
        advanceMillis(42_600)
        assertEquals(0, timer.remainingSeconds)
        assertEquals(60, timer.elapsedSeconds)
        advanceMillis(60_000)
        assertEquals(0, timer.remainingSeconds)
    }

    @Test
    fun pausedTimeIsExcludedAndFractionalSecondsArePreserved() {
        val timer = DesktopCountdown(60) { nowNanos }
        timer.resume()
        advanceMillis(400)
        timer.pause()
        advanceMillis(120_000)
        assertEquals(60, timer.remainingSeconds)
        timer.resume()
        advanceMillis(600)
        assertEquals(59, timer.remainingSeconds)
        assertEquals(1, timer.elapsedSeconds)
    }

    @Test
    fun repeatedResumeDoesNotLoseElapsedTime() {
        val timer = DesktopCountdown(60) { nowNanos }
        timer.resume()
        advanceMillis(5_000)
        timer.resume()
        advanceMillis(5_000)
        assertEquals(50, timer.remainingSeconds)
    }

    @Test
    fun adjustmentsPreserveTimeAlreadyElapsed() {
        val timer = DesktopCountdown(120) { nowNanos }
        timer.resume()
        advanceMillis(10_400)
        timer.adjustSeconds(60)
        assertEquals(170, timer.remainingSeconds)
        advanceMillis(600)
        assertEquals(169, timer.remainingSeconds)
        assertEquals(11, timer.elapsedSeconds)
        timer.adjustSeconds(-300)
        assertEquals(60, timer.remainingSeconds)
        timer.adjustSeconds(Int.MAX_VALUE)
        assertEquals(240 * 60, timer.remainingSeconds)
    }

    @Test
    fun resetStartsANewPausedSession() {
        val timer = DesktopCountdown(60) { nowNanos }
        timer.resume()
        advanceMillis(60_000)
        timer.reset(300)
        advanceMillis(30_000)
        assertEquals(300, timer.remainingSeconds)
        timer.resume()
        advanceMillis(1_000)
        assertEquals(299, timer.remainingSeconds)
        assertEquals(1, timer.elapsedSeconds)
    }

    @Test
    fun manyLateTicksDoNotAccumulateDrift() {
        val timer = DesktopCountdown(25 * 60) { nowNanos }
        timer.resume()
        repeat(100) {
            advanceMillis(1_017)
            timer.remainingSeconds
        }
        assertEquals(1399, timer.remainingSeconds)
    }

    @Test
    fun changingTotalDurationPreservesElapsedTimeAndPauseState() {
        val timer = DesktopCountdown(1500) { nowNanos }
        timer.resume()
        advanceMillis(300_000)
        timer.pause()
        timer.setTotalSeconds(600)
        assertEquals(300, timer.remainingSeconds)
        assertEquals(300, timer.elapsedSeconds)
        advanceMillis(60_000)
        assertEquals(300, timer.remainingSeconds)
        timer.setTotalSeconds(180)
        assertEquals(0, timer.remainingSeconds)
        assertEquals(300, timer.elapsedSeconds)
    }

    @Test
    fun shorteningATimerDoesNotAwardUnworkedTime() {
        val timer = DesktopCountdown(25 * 60) { nowNanos }
        timer.resume()
        advanceMillis(5 * 60_000)
        timer.adjustSeconds(-19 * 60)
        advanceMillis(60_000)
        assertEquals(0, timer.remainingSeconds)
        assertEquals(6 * 60, timer.elapsedSeconds)
    }

    @Test fun addingFiveMinutesCannotShortenAFourHourSession() {
        val timer = DesktopCountdown(240 * 60) { nowNanos }
        timer.adjustSeconds(300)
        assertEquals(240 * 60, timer.remainingSeconds)
        timer.resume()
        advanceMillis(61_400)
        val before = timer.remainingSeconds
        timer.adjustSeconds(300)
        assertEquals(240 * 60, timer.remainingSeconds)
        org.junit.Assert.assertTrue(timer.remainingSeconds >= before)
        assertEquals(61, timer.elapsedSeconds)
    }

    @Test fun subtractingFiveMinutesFromALongSessionSubtractsOnlyFiveMinutes() {
        val timer = DesktopCountdown(230 * 60) { nowNanos }
        timer.adjustSeconds(-300)
        assertEquals(225 * 60, timer.remainingSeconds)
        timer.adjustSeconds(300)
        assertEquals(230 * 60, timer.remainingSeconds)
    }

    @Test fun subtractingTimeNearCompletionCannotIncreaseTheRemainingTime() {
        val timer = DesktopCountdown(60) { nowNanos }
        timer.resume()
        advanceMillis(30_400)
        val before = timer.snapshot()
        timer.adjustSeconds(-300)
        assertEquals(before, timer.snapshot())
        assertEquals(30, timer.remainingSeconds)
        timer.adjustSeconds(0)
        assertEquals(before, timer.snapshot())
        advanceMillis(29_600)
        assertEquals(0, timer.remainingSeconds)
        assertEquals(60, timer.elapsedSeconds)
    }

    @Test fun adjustmentsPreserveAlreadyLongerRecoveredTimersWithoutReversingDirection() {
        val timer = DesktopCountdown(0) { nowNanos }
        timer.restore(DesktopCountdownState(300 * 60 * 1_000_000_000L, 600_400_000_000L))
        timer.adjustSeconds(300)
        assertEquals(300 * 60, timer.remainingSeconds)
        timer.adjustSeconds(-300)
        assertEquals(295 * 60, timer.remainingSeconds)
        assertEquals(600, timer.elapsedSeconds)
        timer.adjustSeconds(Int.MIN_VALUE)
        assertEquals(60, timer.remainingSeconds)
    }

    @Test fun remainingTargetAccountsForTimeSinceTheLastUiTickAndKeepsRunning() {
        val timer = DesktopCountdown(1500) { nowNanos }
        timer.resume()
        advanceMillis(10_400)
        assertEquals(1490, timer.remainingSeconds)
        advanceMillis(2_300)
        timer.setRemainingSeconds(180)
        assertEquals(DesktopCountdownState(180_000_000_000L, 12_700_000_000L), timer.snapshot())
        advanceMillis(400)
        assertEquals(DesktopCountdownState(179_600_000_000L, 13_100_000_000L), timer.snapshot())
    }

    @Test fun pausedRemainingTargetSurvivesRecoveryAndCreditsAllWorkedTimeOnce() {
        val timer = DesktopCountdown(1500) { nowNanos }
        val ledger = DesktopFocusLedger()
        ledger.begin(com.anchor.adhd.desktop.db.DesktopTask(1, "Original work"), 1500)
        val session = ledger.snapshot()!!
        timer.resume()
        advanceMillis(300_400)
        timer.pause()
        timer.setRemainingSeconds(180)
        advanceMillis(60_000)
        val checkpoint = timer.snapshot()
        assertEquals(DesktopCountdownState(180_000_000_000L, 300_400_000_000L), checkpoint)
        assertEquals(session, ledger.snapshot())

        val recovered = DesktopCountdown(0) { nowNanos }
        recovered.restore(checkpoint)
        val recoveredLedger = DesktopFocusLedger().apply { restore(session) }
        advanceMillis(60_000)
        assertEquals(checkpoint, recovered.snapshot())
        recovered.resume()
        advanceMillis(180_000)
        assertEquals(0, recovered.remainingSeconds)
        val credit = recoveredLedger.finish(recovered.elapsedSeconds, completed = true)!!
        assertEquals("Original work", credit.taskTitle)
        assertEquals(session.saveKey, credit.saveKey)
        assertEquals(1500, credit.plannedSeconds)
        assertEquals(480, credit.actualSeconds)
        org.junit.Assert.assertNull(recoveredLedger.finish(recovered.elapsedSeconds, completed = true))
    }

    @Test fun remainingTargetsUseWindowsBoundsWithoutDiscardingElapsedWork() {
        val timer = DesktopCountdown(60) { nowNanos }
        timer.resume()
        advanceMillis(60_000)
        assertEquals(0, timer.remainingSeconds)
        timer.pause()
        timer.setRemainingSeconds(Int.MIN_VALUE)
        assertEquals(DesktopCountdownState(60_000_000_000L, 60_000_000_000L), timer.snapshot())
        timer.setRemainingSeconds(Int.MAX_VALUE)
        assertEquals(DesktopCountdownState(14_400_000_000_000L, 60_000_000_000L), timer.snapshot())
    }
}
