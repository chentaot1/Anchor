package com.anchor.adhd.desktop.blocker

/**
 * Scaled Leisure Economy & Anti-Idling Time-Bank Engine.
 *
 * Implements progressive / tiered scaling for earned leisure blocks (30 mins of unblocked time):
 * - Tier 1: 60 min study -> 30 min leisure (2:1 ratio)
 * - Tier 2: +75 min study -> 30 min leisure (2.5:1 ratio)
 * - Tier 3: +90 min study -> 30 min leisure (3:1 ratio)
 * - Tier 4+: +120 min study -> 30 min leisure (4:1 ratio)
 *
 * Anti-Hoarding Reservoir:
 * - Banked leisure is capped at 60 minutes (2 blocks) at any given moment.
 *   This discourages "just running" the timer all day to accumulate hours of binge time.
 * - Resets on daily rollover at 4:00 AM.
 */
data class TimeBankStatus(
    val totalFocusMinutesToday: Int,
    val totalEarnedMinutesToday: Int,
    val spentMinutesToday: Int,
    val availableBankedMinutes: Int,
    val currentTier: Int,
    val minutesIntoCurrentTier: Int,
    val minutesRequiredForNextTier: Int,
    val nextTierProgress: Float,
) {
    val isCapped: Boolean get() = availableBankedMinutes >= ScaledTimeBank.MAX_BANKED_CAPACITY_MINUTES
}

object ScaledTimeBank {
    const val MAX_BANKED_CAPACITY_MINUTES = 60 // Max 2 blocks (60m) banked at any one time

    fun getTierRequiredMinutes(tierIndex: Int, baseMinutes: Int = 60): Int {
        val safeBase = baseMinutes.coerceAtLeast(1)
        return when (tierIndex) {
            0 -> safeBase                                       // Tier 1: e.g. 60m
            1 -> (safeBase * 1.25).toInt().coerceAtLeast(1)     // Tier 2: e.g. 75m
            2 -> (safeBase * 1.5).toInt().coerceAtLeast(1)      // Tier 3: e.g. 90m
            else -> (safeBase * 2).coerceAtLeast(1)             // Tier 4+: e.g. 120m
        }
    }

    fun calculateStatus(
        focusMinutesToday: Int,
        spentMinutesToday: Int,
        baseMinutes: Int = 60,
    ): TimeBankStatus {
        val safeBase = baseMinutes.coerceAtLeast(1)
        var remainingFocus = focusMinutesToday.coerceAtLeast(0)
        var totalEarned = 0
        var tier = 0

        while (true) {
            val req = getTierRequiredMinutes(tier, safeBase)
            if (remainingFocus >= req) {
                remainingFocus -= req
                totalEarned += 30
                tier++
            } else {
                break
            }
        }

        val nextReq = getTierRequiredMinutes(tier, safeBase)
        val rawAvailable = (totalEarned - spentMinutesToday).coerceAtLeast(0)
        val available = rawAvailable.coerceAtMost(MAX_BANKED_CAPACITY_MINUTES)
        val progress = if (nextReq > 0) (remainingFocus.toFloat() / nextReq).coerceIn(0f, 1f) else 0f

        return TimeBankStatus(
            totalFocusMinutesToday = focusMinutesToday,
            totalEarnedMinutesToday = totalEarned,
            spentMinutesToday = spentMinutesToday,
            availableBankedMinutes = available,
            currentTier = tier + 1,
            minutesIntoCurrentTier = remainingFocus,
            minutesRequiredForNextTier = nextReq,
            nextTierProgress = progress,
        )
    }
}
