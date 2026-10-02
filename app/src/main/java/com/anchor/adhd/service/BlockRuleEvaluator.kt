package com.anchor.adhd.service

import com.anchor.adhd.data.model.BlockListMode
import com.anchor.adhd.data.model.BlockRuleEntity
import com.anchor.adhd.data.model.BlockRuleType
import com.anchor.adhd.data.model.TemptationBundleEntity
import com.anchor.adhd.domain.WhitelistBasics
import java.time.LocalTime

object BlockRuleEvaluator {

    fun shouldBlock(
        packageName: String,
        rules: List<BlockRuleEntity>,
        bundles: List<TemptationBundleEntity>,
        sessionActive: Boolean,
        blockListMode: BlockListMode = BlockListMode.BLOCKLIST,
        whitelistPackages: Set<String> = emptySet()
    ): Boolean {
        val now = LocalTime.now()
        val nowMillis = System.currentTimeMillis()

        if (blockListMode == BlockListMode.WHITELIST) {
            val bundle = bundles.find { it.enabled && it.rewardPackageName == packageName }
            if (bundle != null) {
                if (sessionActive) return true
                return nowMillis >= bundle.unlockUntilMillis
            }
            return evaluateWhitelistMode(
                packageName = packageName,
                rules = rules,
                sessionActive = sessionActive,
                blockListMode = blockListMode,
                whitelistPackages = whitelistPackages,
                now = now
            )
        }

        for (rule in rules) {
            if (!rule.enabled || rule.packageName != packageName) continue
            when (rule.ruleType) {
                BlockRuleType.SESSION -> if (sessionActive) return true
                BlockRuleType.SCHEDULED -> if (isInWindow(now, rule)) return true
            }
        }

        val bundle = bundles.find { it.enabled && it.rewardPackageName == packageName } ?: run {
            return evaluateWhitelistMode(
                packageName = packageName,
                rules = rules,
                sessionActive = sessionActive,
                blockListMode = blockListMode,
                whitelistPackages = whitelistPackages,
                now = now
            )
        }
        if (sessionActive) return true
        return nowMillis >= bundle.unlockUntilMillis
    }

    private fun evaluateWhitelistMode(
        packageName: String,
        rules: List<BlockRuleEntity>,
        sessionActive: Boolean,
        blockListMode: BlockListMode,
        whitelistPackages: Set<String>,
        now: LocalTime
    ): Boolean {
        if (blockListMode != BlockListMode.WHITELIST) return false
        val shielding = sessionActive || rules.any {
            it.enabled && it.ruleType == BlockRuleType.SCHEDULED && isInWindow(now, it)
        }
        if (!shielding) return false
        val allowed = whitelistPackages.ifEmpty {
            rules.filter { it.enabled && it.ruleType == BlockRuleType.SESSION }.map { it.packageName }.toSet()
        } + WhitelistBasics.essentialPackages
        return packageName !in allowed
    }

    fun isInWindow(now: LocalTime, rule: BlockRuleEntity): Boolean {
        val startH = rule.startHour ?: return false
        val startM = rule.startMinute ?: 0
        val endH = rule.endHour ?: return false
        val endM = rule.endMinute ?: 0
        val start = LocalTime.of(startH, startM)
        val end = LocalTime.of(endH, endM)
        return if (end.isAfter(start) || end == start) {
            !now.isBefore(start) && now.isBefore(end)
        } else {
            !now.isBefore(start) || now.isBefore(end)
        }
    }

    fun scheduledShieldActive(rules: List<BlockRuleEntity>): Boolean =
        rules.any { it.enabled && it.ruleType == BlockRuleType.SCHEDULED && isInWindow(LocalTime.now(), it) }
}
