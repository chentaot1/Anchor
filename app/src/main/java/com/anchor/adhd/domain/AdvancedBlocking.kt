package com.anchor.adhd.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

@Serializable
enum class ProtectionMode { FOCUS, ALWAYS, QUOTA, TRACK_ONLY }

@Serializable
data class AppProtection(
    val packageName: String,
    val label: String,
    val mode: ProtectionMode = ProtectionMode.FOCUS,
    val dailyMinutes: Int = 30,
    val quotaGroup: String = "",
    val lockedUntilMillis: Long = 0
)

@Serializable
data class BlockingState(
    val apps: List<AppProtection> = emptyList(),
    val standingShield: Boolean = false,
    val curfewEnabled: Boolean = false,
    val curfewStartMinute: Int = 22 * 60,
    val curfewEndMinute: Int = 7 * 60,
    val studyEnabled: Boolean = false,
    val studyStartMinute: Int = 9 * 60,
    val studyEndMinute: Int = 17 * 60,
    val lockdownUntilMillis: Long = 0,
    val day: String = "",
    val usageSeconds: Map<String, Long> = emptyMap(),
    val focusMinutes: Int = 0,
    val bankedMinutes: Int = 0,
    val creditedSessions: Set<Long> = emptySet(),
    val leisureUntilMillis: Long = 0
)

data class ProtectionDecision(val reason: String, val leisureAllowed: Boolean = false)

/** Desktop-style protection rules, independent of Android services and storage. */
object AdvancedBlocking {
    fun logicalDay(now: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDateTime().minusHours(4).toLocalDate().toString()

    fun nextReset(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        java.time.LocalDate.parse(logicalDay(now, zone)).plusDays(1)
            .atTime(4, 0).atZone(zone).toInstant().toEpochMilli()

    fun rollover(state: BlockingState, now: Long): BlockingState =
        if (state.day == logicalDay(now)) state else state.copy(
            day = logicalDay(now), usageSeconds = emptyMap(), focusMinutes = 0,
            bankedMinutes = 0, creditedSessions = emptySet(), leisureUntilMillis = 0
        )

    fun inWindow(minute: Int, start: Int, end: Int): Boolean = when {
        start == end -> true
        start < end -> minute in start until end
        else -> minute >= start || minute < end
    }

    fun usage(state: BlockingState, app: AppProtection): Long =
        if (app.quotaGroup.isBlank()) state.usageSeconds[app.packageName] ?: 0 else
            state.usageSeconds[groupKey(app.quotaGroup)] ?: 0

    fun groupKey(group: String) = "group:${group.trim().lowercase(java.util.Locale.ROOT)}"

    fun effectiveDailyMinutes(state: BlockingState, app: AppProtection): Int =
        if (app.quotaGroup.isBlank()) app.dailyMinutes.coerceIn(0, 1440) else
            state.apps.filter { it.quotaGroup.isNotBlank() && groupKey(it.quotaGroup) == groupKey(app.quotaGroup) }
                .minOfOrNull { it.dailyMinutes.coerceIn(0, 1440) } ?: app.dailyMinutes.coerceIn(0, 1440)

    fun leisureSpendMinutes(state: BlockingState, minutes: Int, now: Long): Int {
        val remaining = (nextReset(now) - maxOf(now, state.leisureUntilMillis)).coerceAtLeast(0L)
        return minOf(minutes.coerceAtLeast(0), ((remaining + 59_999L) / 60_000L).toInt())
    }

    fun spendLeisure(state: BlockingState, minutes: Int, now: Long, focus: Boolean = false): BlockingState {
        val charged = leisureSpendMinutes(state, minutes, now)
        if (minutes !in listOf(15, 30) || charged == 0 || state.bankedMinutes < charged ||
            leisureRestriction(state, focus, now) != null) return state
        return state.copy(
            bankedMinutes = state.bankedMinutes - charged,
            leisureUntilMillis = minOf(maxOf(now, state.leisureUntilMillis) + minutes * 60_000L, nextReset(now))
        )
    }

    fun recordUsage(state: BlockingState, pkg: String, seconds: Long): BlockingState {
        val app = state.apps.find { it.packageName == pkg } ?: return state
        val elapsed = seconds.coerceIn(0, 5)
        var usage = state.usageSeconds + (pkg to ((state.usageSeconds[pkg] ?: 0) + elapsed))
        if (app.quotaGroup.isNotBlank()) {
            val key = groupKey(app.quotaGroup)
            usage = usage + (key to ((usage[key] ?: 0) + elapsed))
        }
        return state.copy(usageSeconds = usage)
    }

    fun decide(state: BlockingState, pkg: String, focus: Boolean, now: Long): ProtectionDecision? {
        if (pkg in WhitelistBasics.essentialPackages) return null
        val app = state.apps.find { it.packageName == pkg } ?: return null
        if (app.mode == ProtectionMode.TRACK_ONLY) return null
        leisureRestriction(state, focus, now)?.let { return ProtectionDecision(it) }
        return when {
            app.mode == ProtectionMode.QUOTA && usage(state, app) >= effectiveDailyMinutes(state, app) * 60L ->
                ProtectionDecision("Daily allowance reached (${effectiveDailyMinutes(state, app)} minutes)")
            state.leisureUntilMillis > now -> null
            state.standingShield -> ProtectionDecision("Standing shield", leisureAllowed = true)
            app.mode == ProtectionMode.ALWAYS -> ProtectionDecision("Always protected", leisureAllowed = true)
            else -> null
        }
    }

    fun leisureRestriction(state: BlockingState, focus: Boolean, now: Long): String? {
        val local = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        val minute = local.hour * 60 + local.minute
        return when {
            focus -> "Protected during focus"
            state.lockdownUntilMillis > now -> "Daily lockdown until 4 AM"
            state.studyEnabled && local.dayOfWeek.value <= 5 &&
                inWindow(minute, state.studyStartMinute, state.studyEndMinute) ->
                "Protected during your weekday study window"
            state.curfewEnabled && inWindow(minute, state.curfewStartMinute, state.curfewEndMinute) ->
                "Night curfew"
            else -> null
        }
    }

    fun earnedBlocks(minutes: Int): Int {
        var remaining = minutes.coerceAtLeast(0)
        var tier = 0
        while (remaining >= tierMinutes(tier)) {
            remaining -= tierMinutes(tier)
            tier++
        }
        return tier
    }

    private fun tierMinutes(tier: Int) = when (tier) { 0 -> 60; 1 -> 75; 2 -> 90; else -> 120 }

    fun credit(state: BlockingState, sessionId: Long, minutes: Int): BlockingState {
        if (sessionId in state.creditedSessions || minutes <= 0) return state
        val total = state.focusMinutes + minutes.coerceAtMost(1440)
        val newlyEarned = (earnedBlocks(total) - earnedBlocks(state.focusMinutes)) * 30
        return state.copy(
            focusMinutes = total,
            bankedMinutes = (state.bankedMinutes + newlyEarned).coerceAtMost(60),
            creditedSessions = state.creditedSessions + sessionId
        )
    }

    fun nextRewardIn(state: BlockingState): Int {
        var remaining = state.focusMinutes
        var tier = 0
        while (remaining >= tierMinutes(tier)) { remaining -= tierMinutes(tier); tier++ }
        return tierMinutes(tier) - remaining
    }

    fun syncQuotaGroupAllowances(
        apps: Map<String, AppProtection>,
        updated: AppProtection
    ): Map<String, AppProtection> {
        val normalizedMinutes = updated.dailyMinutes.coerceIn(0, 1440)
        val normalizedGroup = updated.quotaGroup.trim()
        val normalizedUpdated = updated.copy(dailyMinutes = normalizedMinutes, quotaGroup = normalizedGroup)
        val withUpdated = apps + (normalizedUpdated.packageName to normalizedUpdated)
        if (normalizedGroup.isBlank()) return withUpdated
        val targetKey = groupKey(normalizedGroup)
        return withUpdated.mapValues { (_, app) ->
            if (app.quotaGroup.isNotBlank() && groupKey(app.quotaGroup) == targetKey) {
                app.copy(dailyMinutes = normalizedMinutes)
            } else {
                app
            }
        }
    }

    fun syncQuotaGroupAllowances(
        apps: List<AppProtection>,
        updated: AppProtection
    ): List<AppProtection> =
        syncQuotaGroupAllowances(apps.associateBy { it.packageName }, updated).values.toList()

    fun formatLockRemaining(lockedUntilMillis: Long, nowMillis: Long): String {
        val remainingMs = (lockedUntilMillis - nowMillis).coerceAtLeast(0L)
        if (remainingMs == 0L) return "0m left"
        val totalMinutes = ((remainingMs + 59_999L) / 60_000L).coerceAtLeast(1L)
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        val days = hours / 24L
        if (days > 0L) return if (hours % 24L > 0L) "${days}d ${hours % 24L}h left" else "${days}d left"
        return when {
            hours > 0L && minutes > 0L -> "${hours}h ${minutes}m left"
            hours > 0L -> "${hours}h left"
            else -> "${minutes}m left"
        }
    }
}
