package com.anchor.adhd.ui.copy

/**
 * User-facing strings. Factual first; short permission lines OK, no coach-speak.
 */
object AppCopy {
    fun focusSessionsToday(count: Int): String =
        when (count) {
            0 -> "0 today · goal is 1 session"
            1 -> "1 session today · goal met"
            else -> "$count sessions today"
        }

    const val FOCUS_DAILY_GOAL = "One focus block counts for the day."

    fun replanQueue(count: Int): String =
        "$count missed block${if (count == 1) "" else "s"} · Plan → Replan"

    const val REPLAN_SECTION = "Missed blocks to reschedule"
    const val REPLAN_EMPTY = "Queue empty."
    const val SCHEDULE_EMPTY = "No scheduled blocks. Add in Plan or AI breakdown."
    const val INBOX_EMPTY = "No tasks here."
    const val ROUTINES_EMPTY = "No routines yet."

    const val CHECK_IN_SECTION = "Daily check-in"
    const val CHECK_IN_ACTIVATION_HINT = "Activation for focused work · drives task order"
    const val CHECK_IN_TAGS_HINT = "State tags if applicable (optional)"
    const val CHECK_IN_PATTERNS_EMPTY = "No check-ins this week yet."

    const val ENERGY_SECTION = "Energy"
    const val ENERGY_HINT = "Used for task ordering suggestions"

    const val NOW_HINT = "One action at a time"

    const val FOCUS_END_EARLY = "End early · still logged"
    const val FOCUS_SESSION_LENGTH = "Session length"
    const val FOCUS_SHORT_SESSION = "10 min focus · 3 min break"

    const val GROW_SESSION_NOTE = "Ended-early sessions still log."
    const val STRATEGY_SECTION = "Strategies"
    const val STRATEGY_HINT = "In-the-moment tools · tap to read"
    const val STRATEGY_TODAY = "Today's strategy"
    const val STRATEGY_TODAY_HINT = "New each day"

    const val QUICK_CAPTURE_HINT = "Dump it here — goes to inbox"

    const val CBT_SECTION = "Strategies"
    const val CBT_HINT = "Tap to read"

    const val AI_MODE_HINT = "Review output before adding to your plan"
    const val AI_STUB = "Preparing local AI · check Settings"
    const val AI_ON_DEVICE = "Desktop model · ready offline"
    const val AI_STUB_FILE_ONLY = "Model file only · native runtime unavailable"
    const val AI_NO_NATIVE = "On-device AI unavailable in this build"
    const val AI_RUNNING = "Running…"

    const val DIALOG_CLOSE = "Close"

    const val CALENDAR_OCCUPANCY = "Timed events from this phone fill gaps in Plan. All-day events are ignored."
    const val CALENDAR_DENIED = "Calendar access is off — lectures will not block holes."
    const val CALENDAR_ALLOWED = "Using visible calendars for today's timed events."
}
