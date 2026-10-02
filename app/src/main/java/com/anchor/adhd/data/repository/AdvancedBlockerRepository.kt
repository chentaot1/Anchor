package com.anchor.adhd.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anchor.adhd.domain.AdvancedBlocking
import com.anchor.adhd.domain.AppProtection
import com.anchor.adhd.domain.BlockingState
import com.anchor.adhd.domain.WhitelistBasics
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.blockingStore by preferencesDataStore(name = "advanced_blocking")

class AdvancedBlockerRepository(context: Context) {
    private val store = context.applicationContext.blockingStore
    private val key = stringPreferencesKey("state")
    private val json = Json { ignoreUnknownKeys = true }
    private fun decode(raw: String?): BlockingState =
        raw?.let { json.decodeFromString<BlockingState>(it) } ?: BlockingState()
    val state = store.data.map { AdvancedBlocking.rollover(decode(it[key]), System.currentTimeMillis()) }

    private suspend fun update(transform: (BlockingState, Long) -> BlockingState) {
        store.edit { prefs ->
            val now = System.currentTimeMillis()
            val stored = decode(prefs[key])
            val next = transform(AdvancedBlocking.rollover(stored, now), now)
            if (next != stored) prefs[key] = json.encodeToString(next)
        }
    }

    suspend fun saveApp(app: AppProtection) = update { state, now ->
        val existing = state.apps.find { it.packageName == app.packageName }
        if (focusActive() || state.lockdownUntilMillis > now || (existing?.lockedUntilMillis ?: 0) > now ||
            app.packageName in WhitelistBasics.essentialPackages) state else state.copy(
            apps = state.apps.filterNot { it.packageName == app.packageName } +
                app.copy(dailyMinutes = app.dailyMinutes.coerceIn(0, 1440), quotaGroup = app.quotaGroup.trim())
        )
    }

    suspend fun removeApp(pkg: String) = update { state, now ->
        if (focusActive() || state.lockdownUntilMillis > now || state.apps.any { it.packageName == pkg && it.lockedUntilMillis > now })
            state else state.copy(apps = state.apps.filterNot { it.packageName == pkg })
    }

    suspend fun configure(transform: (BlockingState) -> BlockingState) = update { state, now ->
        if (focusActive() || state.lockdownUntilMillis > now) state else {
            val configured = transform(state)
            // Configuration cannot change usage, bank balances, or existing app locks.
            state.copy(standingShield = configured.standingShield, curfewEnabled = configured.curfewEnabled,
                curfewStartMinute = configured.curfewStartMinute.coerceIn(0, 1439),
                curfewEndMinute = configured.curfewEndMinute.coerceIn(0, 1439),
                studyEnabled = configured.studyEnabled,
                studyStartMinute = configured.studyStartMinute.coerceIn(0, 1439),
                studyEndMinute = configured.studyEndMinute.coerceIn(0, 1439))
        }
    }

    suspend fun lockdown() = update { state, now ->
        state.copy(lockdownUntilMillis = AdvancedBlocking.nextReset(now), leisureUntilMillis = 0)
    }

    suspend fun lockApp(pkg: String) = update { state, now -> state.copy(
        apps = state.apps.map { if (it.packageName == pkg) it.copy(lockedUntilMillis = now + 86_400_000L) else it }
    ) }

    suspend fun tick(pkg: String?, seconds: Long) = update { state, _ ->
        if (pkg == null) state else AdvancedBlocking.recordUsage(state, pkg, seconds)
    }

    suspend fun creditSession(id: Long, minutes: Int, earnedAt: Long = System.currentTimeMillis()) = update { state, now ->
        if (AdvancedBlocking.logicalDay(earnedAt) == AdvancedBlocking.logicalDay(now)) AdvancedBlocking.credit(state, id, minutes) else state
    }

    suspend fun spendLeisure(minutes: Int, focus: Boolean): Boolean {
        var spent = false
        update { state, now ->
            if (AdvancedBlocking.leisureRestriction(state, focus || focusActive(), now) != null || state.leisureUntilMillis > now ||
                minutes !in listOf(15, 30) || state.bankedMinutes < minutes) state else {
                spent = true
                state.copy(bankedMinutes = state.bankedMinutes - minutes,
                    leisureUntilMillis = minOf(now + minutes * 60_000L, AdvancedBlocking.nextReset(now)))
            }
        }
        return spent
    }

    suspend fun clear() { store.edit { it.clear() } }

    private fun focusActive() = com.anchor.adhd.service.FocusTimerState.state.value.phase == com.anchor.adhd.service.FocusTimerState.Phase.WORK
}
