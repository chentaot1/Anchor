package com.anchor.adhd.desktop.db

import java.time.LocalDate

/** Stored streaks describe the latest completed day; the UI shows whether that run is still current. */
internal object DesktopStreak {
    fun currentDays(days: Int, lastDate: String, today: LocalDate): Int {
        if (lastDate.isBlank()) return days.coerceAtLeast(0) // Undated legacy/imported progress.
        val last = runCatching { LocalDate.parse(lastDate) }.getOrNull() ?: return 0
        return if (last == today || last == today.minusDays(1)) days.coerceAtLeast(0) else 0
    }

    fun fromCompletedDays(dates: Set<LocalDate>): Pair<Int, String> {
        val last = dates.maxOrNull() ?: return 0 to ""
        var day = last
        var count = 0
        while (day in dates) {
            count++
            day = day.minusDays(1)
        }
        return count to last.toString()
    }
}
