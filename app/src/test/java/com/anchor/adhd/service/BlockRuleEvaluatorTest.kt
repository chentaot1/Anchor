package com.anchor.adhd.service

import com.anchor.adhd.data.model.BlockRuleEntity
import com.anchor.adhd.data.model.BlockRuleType
import com.anchor.adhd.data.model.TemptationBundleEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class BlockRuleEvaluatorTest {
    @Test
    fun sessionRule_blocksOnlyWhenSessionActive() {
        val rule = BlockRuleEntity(
            packageName = "com.example.app",
            ruleType = BlockRuleType.SESSION,
            enabled = true
        )

        assertTrue(
            BlockRuleEvaluator.shouldBlock(
                packageName = "com.example.app",
                rules = listOf(rule),
                bundles = emptyList(),
                sessionActive = true
            )
        )
        assertFalse(
            BlockRuleEvaluator.shouldBlock(
                packageName = "com.example.app",
                rules = listOf(rule),
                bundles = emptyList(),
                sessionActive = false
            )
        )
    }

    @Test
    fun scheduledRule_respectsTimeWindow() {
        val rule = BlockRuleEntity(
            packageName = "com.example.app",
            ruleType = BlockRuleType.SCHEDULED,
            startHour = 9,
            startMinute = 0,
            endHour = 17,
            endMinute = 0,
            enabled = true
        )

        assertTrue(BlockRuleEvaluator.isInWindow(LocalTime.of(10, 0), rule))
        assertFalse(BlockRuleEvaluator.isInWindow(LocalTime.of(18, 0), rule))
    }

    @Test
    fun temptationBundle_unlocksRewardAppAfterSession() {
        val bundle = TemptationBundleEntity(
            name = "YouTube",
            rewardPackageName = "com.google.android.youtube",
            unlockMinutes = 15,
            enabled = true,
            unlockUntilMillis = System.currentTimeMillis() + 60_000L
        )

        assertFalse(
            BlockRuleEvaluator.shouldBlock(
                packageName = "com.google.android.youtube",
                rules = emptyList(),
                bundles = listOf(bundle),
                sessionActive = false
            )
        )
        assertTrue(
            BlockRuleEvaluator.shouldBlock(
                packageName = "com.google.android.youtube",
                rules = emptyList(),
                bundles = listOf(bundle),
                sessionActive = true
            )
        )
    }

    @Test
    fun whitelistMode_blocksUnlistedPackagesDuringSession() {
        val allowed = BlockRuleEntity(
            packageName = "com.allowed.app",
            ruleType = BlockRuleType.SESSION,
            enabled = true
        )
        assertTrue(
            BlockRuleEvaluator.shouldBlock(
                packageName = "com.other.app",
                rules = listOf(allowed),
                bundles = emptyList(),
                sessionActive = true,
                blockListMode = com.anchor.adhd.data.model.BlockListMode.WHITELIST,
                whitelistPackages = setOf("com.allowed.app")
            )
        )
        assertFalse(
            BlockRuleEvaluator.shouldBlock(
                packageName = "com.allowed.app",
                rules = listOf(allowed),
                bundles = emptyList(),
                sessionActive = true,
                blockListMode = com.anchor.adhd.data.model.BlockListMode.WHITELIST,
                whitelistPackages = setOf("com.allowed.app")
            )
        )
    }

    @Test
    fun scheduledRule_clampsOutOfRangeHoursAndMinutesAndTreatsEqualEndpointsAsAllDay() {
        val outOfRangeRule = BlockRuleEntity(
            packageName = "com.example.app",
            ruleType = BlockRuleType.SCHEDULED,
            startHour = -3,
            startMinute = -10,
            endHour = 29,
            endMinute = 99,
            enabled = true
        )
        assertTrue(BlockRuleEvaluator.isInWindow(LocalTime.of(0, 0), outOfRangeRule))
        assertTrue(BlockRuleEvaluator.isInWindow(LocalTime.of(23, 58), outOfRangeRule))
        assertFalse(BlockRuleEvaluator.isInWindow(LocalTime.of(23, 59), outOfRangeRule))

        val equalBoundaryRule = BlockRuleEntity(
            packageName = "com.example.app",
            ruleType = BlockRuleType.SCHEDULED,
            startHour = 9,
            startMinute = 0,
            endHour = 9,
            endMinute = 0,
            enabled = true
        )
        assertTrue(BlockRuleEvaluator.isInWindow(LocalTime.of(9, 0), equalBoundaryRule))
        assertTrue(BlockRuleEvaluator.isInWindow(LocalTime.of(21, 30), equalBoundaryRule))
    }
}
