package com.anchor.adhd.desktop

/** Windows task and timer controls share the same supported duration range. */
internal object DesktopDurationLimits {
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 240
    const val MIN_SECONDS = MIN_MINUTES * 60
    const val MAX_SECONDS = MAX_MINUTES * 60
}
