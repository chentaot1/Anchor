package com.anchor.adhd.data.model

data class CheckInPatterns(
    val checkInsThisWeek: Int,
    val tagCounts: Map<CheckInTag, Int>
)
