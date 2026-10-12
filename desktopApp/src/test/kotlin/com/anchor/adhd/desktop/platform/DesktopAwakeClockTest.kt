package com.anchor.adhd.desktop.platform

import com.anchor.adhd.desktop.focus.DesktopCountdown
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.ptr.LongByReference
import com.sun.jna.win32.StdCallLibrary
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class DesktopAwakeClockTest {
    private interface InterruptTimeKernel32 : StdCallLibrary {
        fun QueryUnbiasedInterruptTime(time: LongByReference): Boolean
    }

    @Test fun defaultCountdownUsesWindowsAwakeTimeInNanoseconds() {
        assumeTrue(Platform.isWindows())
        // Compare the precise production source with Windows' independent tick-based API.
        val kernel = Native.load("kernel32", InterruptTimeKernel32::class.java)
        val before = LongByReference()
        assertTrue(kernel.QueryUnbiasedInterruptTime(before))
        val precise = DesktopAwakeClock.nanoTime()
        val after = LongByReference()
        assertTrue(kernel.QueryUnbiasedInterruptTime(after))
        val tickTolerance = 100_000_000L
        assertTrue(precise >= before.value * 100L - tickTolerance)
        assertTrue(precise <= after.value * 100L + tickTolerance)

        val countdown = DesktopCountdown(60)
        val beforeResume = DesktopAwakeClock.nanoTime()
        countdown.resume()
        val afterResume = DesktopAwakeClock.nanoTime()
        Thread.sleep(60)
        val beforeSnapshot = DesktopAwakeClock.nanoTime()
        val elapsed = countdown.snapshot().elapsedNanos
        val afterSnapshot = DesktopAwakeClock.nanoTime()
        assertTrue(elapsed > 0)
        assertTrue(elapsed >= beforeSnapshot - afterResume)
        assertTrue(elapsed <= afterSnapshot - beforeResume)
    }
}
