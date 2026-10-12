package com.anchor.adhd.domain

object ReminderQuietHours {
    fun isQuietHour(hour: Int, quietStart: Int, quietEnd: Int): Boolean {
        return if (quietStart > quietEnd) {
            hour >= quietStart || hour < quietEnd
        } else {
            hour in quietStart until quietEnd
        }
    }
}
