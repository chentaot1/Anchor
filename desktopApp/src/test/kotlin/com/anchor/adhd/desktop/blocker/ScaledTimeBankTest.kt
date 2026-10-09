package com.anchor.adhd.desktop.blocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaledTimeBankTest {
    @Test
    fun tierRequirements_scaleProgressively() {
        assertEquals(60, ScaledTimeBank.getTierRequiredMinutes(0, 60))
        assertEquals(75, ScaledTimeBank.getTierRequiredMinutes(1, 60))
        assertEquals(90, ScaledTimeBank.getTierRequiredMinutes(2, 60))
        assertEquals(120, ScaledTimeBank.getTierRequiredMinutes(3, 60))
        assertEquals(120, ScaledTimeBank.getTierRequiredMinutes(4, 60))
    }

    @Test
    fun calculateStatus_tracksTiersAndCapsBankAtSixtyMinutes() {
        val tier1 = ScaledTimeBank.calculateStatus(focusMinutesToday = 60, spentMinutesToday = 0, baseMinutes = 60)
        assertEquals(30, tier1.totalEarnedMinutesToday)
        assertEquals(30, tier1.availableBankedMinutes)
        assertEquals(2, tier1.currentTier)
        assertEquals(0, tier1.minutesIntoCurrentTier)
        assertEquals(75, tier1.minutesRequiredForNextTier)
        assertFalse(tier1.isCapped)

        // 60 + 75 + 90 = 225m -> 90m earned, capped at 60m available
        val tier3 = ScaledTimeBank.calculateStatus(focusMinutesToday = 225, spentMinutesToday = 0, baseMinutes = 60)
        assertEquals(90, tier3.totalEarnedMinutesToday)
        assertEquals(60, tier3.availableBankedMinutes)
        assertTrue(tier3.isCapped)
    }

    @Test
    fun nonPositiveBaseMinutes_coercesSafelyWithoutInfiniteLoop() {
        val zeroBase = ScaledTimeBank.calculateStatus(focusMinutesToday = 5, spentMinutesToday = 0, baseMinutes = 0)
        assertTrue(zeroBase.totalEarnedMinutesToday > 0)
        assertTrue(zeroBase.minutesRequiredForNextTier >= 1)

        val negativeBase = ScaledTimeBank.calculateStatus(focusMinutesToday = 3, spentMinutesToday = 0, baseMinutes = -30)
        assertTrue(negativeBase.minutesRequiredForNextTier >= 1)
    }
}
