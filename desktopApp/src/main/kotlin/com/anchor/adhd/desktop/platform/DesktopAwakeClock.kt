package com.anchor.adhd.desktop.platform

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.ptr.LongByReference
import com.sun.jna.win32.StdCallLibrary

/** Elapsed time while Windows is awake; sleep and hibernation cannot earn focus credit. */
internal object DesktopAwakeClock {
    private interface WindowsAwakeTime : StdCallLibrary {
        fun QueryUnbiasedInterruptTimePrecise(time: LongByReference)
    }

    // Keep one clock source for the lifetime of the process. Switching origins mid-session
    // would turn the difference between clock epochs into elapsed focus time.
    private val reader: () -> Long by lazy {
        if (Platform.isWindows()) {
            // Use the Windows 10+ API contract: the precise entry point is not exported
            // directly by Kernel32 on every supported Windows installation.
            val nativeClock = Native.load("api-ms-win-core-realtime-l1-1-1.dll", WindowsAwakeTime::class.java)
            val windowsReader: () -> Long = {
                val time = LongByReference()
                nativeClock.QueryUnbiasedInterruptTimePrecise(time)
                time.value * 100L // Windows reports 100-nanosecond units.
            }
            windowsReader
        } else {
            System::nanoTime
        }
    }

    fun nanoTime(): Long = reader()
}
