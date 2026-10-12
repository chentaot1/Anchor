package com.anchor.adhd.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AdvancedBlockingTest {
    private val now = LocalDateTime.of(2026, 9, 30, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val app = AppProtection("social.app", "Social", ProtectionMode.ALWAYS)

    @Test fun resetIsAtFourAndPreservesProtection() {
        val before = LocalDateTime.of(2026, 9, 30, 3, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val reset = AdvancedBlocking.nextReset(before)
        assertEquals("2026-09-29", AdvancedBlocking.logicalDay(before))
        assertEquals("2026-09-30", AdvancedBlocking.logicalDay(reset))
        val state = BlockingState(apps = listOf(app), day = "2026-09-29", bankedMinutes = 30, usageSeconds = mapOf(app.packageName to 600), focusMinutes = 60, leisureUntilMillis = reset + 60_000)
        val next = AdvancedBlocking.rollover(state, reset)
        assertEquals(state.apps, next.apps)
        assertEquals(0, next.bankedMinutes)
        assertTrue(next.usageSeconds.isEmpty())
        assertEquals(0L, next.leisureUntilMillis)
    }

    @Test fun sameDayDoesNotEraseUsage() {
        val state = BlockingState(day = AdvancedBlocking.logicalDay(now), usageSeconds = mapOf(app.packageName to 60))
        assertEquals(state, AdvancedBlocking.rollover(state, now))
    }

    @Test fun daylightSavingStillResetsAtLocalFour() {
        val zone = ZoneId.of("America/New_York")
        listOf(LocalDateTime.of(2026, 3, 8, 3, 59), LocalDateTime.of(2026, 11, 1, 3, 59)).forEach { local ->
            val before = local.atZone(zone).toInstant().toEpochMilli()
            assertEquals(local.toLocalDate().minusDays(1).toString(), AdvancedBlocking.logicalDay(before, zone))
            val reset = AdvancedBlocking.nextReset(before, zone)
            assertEquals(4, java.time.Instant.ofEpochMilli(reset).atZone(zone).hour)
            assertEquals(local.toLocalDate().toString(), AdvancedBlocking.logicalDay(reset, zone))
        }
    }

    @Test fun allowanceBlocksAtExactLimit() {
        val quota = app.copy(mode = ProtectionMode.QUOTA, dailyMinutes = 1)
        val state = BlockingState(apps = listOf(quota), usageSeconds = mapOf(app.packageName to 59))
        assertNull(AdvancedBlocking.decide(state, app.packageName, false, now))
        val exhausted = AdvancedBlocking.recordUsage(state, app.packageName, 1)
        assertNotNull(AdvancedBlocking.decide(exhausted, app.packageName, false, now))
    }

    @Test fun groupsShareUsageAndRemovingMemberCannotRestoreAllowance() {
        val first = app.copy(mode = ProtectionMode.QUOTA, quotaGroup = "Social", dailyMinutes = 1)
        val second = first.copy(packageName = "second.app", quotaGroup = "social")
        var state = BlockingState(apps = listOf(first, second))
        repeat(6) { state = AdvancedBlocking.recordUsage(state, first.packageName, 5) }
        repeat(6) { state = AdvancedBlocking.recordUsage(state, second.packageName, 5) }
        assertEquals(60L, AdvancedBlocking.usage(state, second))
        assertNotNull(AdvancedBlocking.decide(state, second.packageName, false, now))
        state = state.copy(apps = listOf(second))
        assertEquals(60L, AdvancedBlocking.usage(state, second))
    }

    @Test fun trackOnlyAndEssentialsStayUsableDuringLockdown() {
        val track = app.copy(mode = ProtectionMode.TRACK_ONLY)
        val essential = app.copy(packageName = "com.samsung.android.dialer")
        val state = BlockingState(apps = listOf(track, essential), lockdownUntilMillis = now + 1000)
        assertNull(AdvancedBlocking.decide(state, track.packageName, true, now))
        assertNull(AdvancedBlocking.decide(state, essential.packageName, true, now))
    }

    @Test fun leisureCannotBypassFocusLockdownOrQuota() {
        val state = BlockingState(apps = listOf(app), leisureUntilMillis = now + 1000)
        assertNull(AdvancedBlocking.decide(state, app.packageName, false, now))
        assertFalse(AdvancedBlocking.decide(state, app.packageName, true, now)!!.leisureAllowed)
        assertFalse(AdvancedBlocking.decide(state.copy(lockdownUntilMillis = now + 1000), app.packageName, false, now)!!.leisureAllowed)
        assertNotNull(AdvancedBlocking.decide(state.copy(apps = listOf(app.copy(mode = ProtectionMode.QUOTA, dailyMinutes = 0))), app.packageName, false, now))
    }

    @Test fun standingShieldAppliesOutsideFocus() {
        val state = BlockingState(apps = listOf(app.copy(mode = ProtectionMode.FOCUS)))
        assertNull(AdvancedBlocking.decide(state, app.packageName, false, now))
        assertTrue(AdvancedBlocking.decide(state.copy(standingShield = true), app.packageName, false, now)!!.leisureAllowed)
    }

    @Test fun overnightAndEqualTimeWindows() {
        assertTrue(AdvancedBlocking.inWindow(23 * 60, 22 * 60, 7 * 60))
        assertTrue(AdvancedBlocking.inWindow(6 * 60, 22 * 60, 7 * 60))
        assertFalse(AdvancedBlocking.inWindow(7 * 60, 22 * 60, 7 * 60))
        assertFalse(AdvancedBlocking.inWindow(12 * 60, 22 * 60, 7 * 60))
        assertTrue(AdvancedBlocking.inWindow(12 * 60, 9 * 60, 9 * 60))
    }

    @Test fun studyWindowBlocksWeekdaysAndCannotBeBoughtOut() {
        val state = BlockingState(apps = listOf(app), studyEnabled = true, leisureUntilMillis = now + 10_000)
        assertEquals("Protected during your weekday study window", AdvancedBlocking.decide(state, app.packageName, false, now)!!.reason)
        val saturday = LocalDateTime.of(2026, 10, 3, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertNull(AdvancedBlocking.decide(state.copy(leisureUntilMillis = saturday + 10_000), app.packageName, false, saturday))
    }

    @Test fun curfewCannotBeBoughtOut() {
        val state = BlockingState(apps = listOf(app), curfewEnabled = true, curfewStartMinute = 0, curfewEndMinute = 0, leisureUntilMillis = now + 1000)
        assertEquals("Night curfew", AdvancedBlocking.decide(state, app.packageName, false, now)!!.reason)
    }

    @Test fun rewardTiersAndDuplicateSessionProtection() {
        var state = AdvancedBlocking.credit(BlockingState(), 1, 60)
        assertEquals(30, state.bankedMinutes)
        assertEquals(state, AdvancedBlocking.credit(state, 1, 60))
        assertEquals(75, AdvancedBlocking.nextRewardIn(state))
        state = AdvancedBlocking.credit(state, 2, 75)
        assertEquals(60, state.bankedMinutes)
        assertEquals(90, AdvancedBlocking.nextRewardIn(state))
        state = AdvancedBlocking.credit(state, 3, 90)
        assertEquals(60, state.bankedMinutes)
        assertEquals(120, AdvancedBlocking.nextRewardIn(state))
    }

    @Test fun overflowingBankDoesNotReappearAfterSpending() {
        val capped = AdvancedBlocking.credit(BlockingState(), 1, 225)
        val spent = capped.copy(bankedMinutes = 30)
        assertEquals(30, AdvancedBlocking.credit(spent, 2, 1).bankedMinutes)
    }

    @Test fun syncQuotaGroupAllowancesSynchronizesMatchingGroupCaseInsensitively() {
        val first = AppProtection("com.instagram", "Instagram", ProtectionMode.QUOTA, dailyMinutes = 30, quotaGroup = "Social")
        val second = AppProtection("com.tiktok", "TikTok", ProtectionMode.QUOTA, dailyMinutes = 30, quotaGroup = " social ")
        val other = AppProtection("com.reddit", "Reddit", ProtectionMode.QUOTA, dailyMinutes = 20, quotaGroup = "Forums")
        val updatedFirst = first.copy(dailyMinutes = 45)
        val synced = AdvancedBlocking.syncQuotaGroupAllowances(listOf(first, second, other), updatedFirst)
        assertEquals(45, synced.first { it.packageName == "com.instagram" }.dailyMinutes)
        assertEquals(45, synced.first { it.packageName == "com.tiktok" }.dailyMinutes)
        assertEquals(20, synced.first { it.packageName == "com.reddit" }.dailyMinutes)
    }

    @Test fun formatLockRemainingFormatsDaysHoursAndMinutes() {
        assertEquals("0m left", AdvancedBlocking.formatLockRemaining(now - 1000L, now))
        assertEquals("45m left", AdvancedBlocking.formatLockRemaining(now + 45 * 60_000L, now))
        assertEquals("2h 15m left", AdvancedBlocking.formatLockRemaining(now + (2 * 60 + 15) * 60_000L, now))
        assertEquals("3d left", AdvancedBlocking.formatLockRemaining(now + 3 * 24 * 60 * 60_000L, now))
        assertEquals("3d 4h left", AdvancedBlocking.formatLockRemaining(now + (3 * 24 + 4) * 60 * 60_000L, now))
    }

    @Test fun inconsistentGroupAllowancesUseSameLimitForEveryMember() {
        val first = app.copy(mode = ProtectionMode.QUOTA, quotaGroup = " Social ", dailyMinutes = 45)
        val second = first.copy(packageName = "other.app", quotaGroup = "social", dailyMinutes = 20)
        val state = BlockingState(apps = listOf(first, second), usageSeconds = mapOf("group:social" to 1200L))
        for (member in state.apps) {
            assertEquals(20, AdvancedBlocking.effectiveDailyMinutes(state, member))
            assertNotNull(AdvancedBlocking.decide(state, member.packageName, false, now))
        }
    }

    @Test fun leisureExtendsActiveWindowAndChargesOnlyTimeBeforeReset() {
        val state = BlockingState(bankedMinutes = 60, leisureUntilMillis = now + 15 * 60_000L)
        val extended = AdvancedBlocking.spendLeisure(state, 30, now)
        assertEquals(now + 45 * 60_000L, extended.leisureUntilMillis)
        assertEquals(30, extended.bankedMinutes)
        val nearReset = AdvancedBlocking.nextReset(now) - 90_000L
        val capped = AdvancedBlocking.spendLeisure(BlockingState(bankedMinutes = 2), 15, nearReset)
        assertEquals(AdvancedBlocking.nextReset(nearReset), capped.leisureUntilMillis)
        assertEquals(0, capped.bankedMinutes)
        val fullyExtended = capped.copy(bankedMinutes = 30)
        assertEquals(fullyExtended, AdvancedBlocking.spendLeisure(fullyExtended, 15, nearReset))
        val lastSecond = AdvancedBlocking.nextReset(now) - 1000L
        assertEquals(1, AdvancedBlocking.leisureSpendMinutes(BlockingState(), 30, lastSecond))
    }
}
