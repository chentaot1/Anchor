package com.anchor.adhd.data.repository

import android.content.Context
import com.anchor.adhd.data.db.BlockRuleDao
import com.anchor.adhd.data.db.CompanionDao
import com.anchor.adhd.data.db.FocusSessionDao
import com.anchor.adhd.data.db.TemptationBundleDao
import com.anchor.adhd.data.db.TaskDao
import com.anchor.adhd.data.model.BlockRuleEntity
import com.anchor.adhd.data.model.BlockRuleType
import com.anchor.adhd.data.model.FocusEndTag
import com.anchor.adhd.data.model.FocusGardenEntity
import com.anchor.adhd.data.model.FocusSessionEntity
import com.anchor.adhd.data.model.TemptationBundleEntity
import com.anchor.adhd.data.prefs.UserPreferences
import com.anchor.adhd.service.FocusBlockService
import com.anchor.adhd.service.FocusTimerService
import com.anchor.adhd.domain.PlantCatalog
import com.anchor.adhd.domain.PlantSpecies
import com.anchor.adhd.widget.WidgetUpdater
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

class FocusRepository(
    private val context: Context,
    private val sessionDao: FocusSessionDao,
    private val blockRuleDao: BlockRuleDao,
    private val bundleDao: TemptationBundleDao,
    private val taskDao: TaskDao,
    private val preferences: UserPreferences,
    private val companionDao: CompanionDao,
    private val advancedBlocker: AdvancedBlockerRepository? = null
) {
    fun observeBlockRules(): Flow<List<BlockRuleEntity>> = blockRuleDao.observeAll()
    fun observeRecentSessions(): Flow<List<FocusSessionEntity>> = sessionDao.observeRecent()
    fun observeBundles(): Flow<List<TemptationBundleEntity>> = bundleDao.observeAll()

    fun observeTodaySessionCount(): Flow<Int> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return sessionDao.observeCompletedCountForDay(start, end)
    }

    suspend fun refreshBlockedPackages() {
        val rules = blockRuleDao.observeEnabled().first()
        val bundles = bundleDao.observeAll().first()
        val mode = preferences.blockListModeFlow.first()
        FocusBlockService.blockRules = rules
        FocusBlockService.temptationBundles = bundles
        FocusBlockService.blockListMode = mode
        FocusBlockService.whitelistPackages = if (mode == com.anchor.adhd.data.model.BlockListMode.WHITELIST) {
            rules.filter { it.ruleType == BlockRuleType.SESSION }.map { it.packageName }.toSet()
        } else {
            emptySet()
        }
        FocusBlockService.scheduledShieldActive =
            com.anchor.adhd.service.BlockRuleEvaluator.scheduledShieldActive(rules)
    }

    suspend fun startSession(taskId: Long?, workMinutes: Int, breakMinutes: Int, locked: Boolean = true): Long {
        awaitEndActiveSession(completed = false)
        val taskTitle = taskId?.let { id -> taskDao.getById(id)?.title }
        val sessionId = sessionDao.insert(
            FocusSessionEntity(
                taskId = taskId,
                startedAtMillis = System.currentTimeMillis(),
                plannedMinutes = workMinutes,
                locked = locked
            )
        )
        refreshBlockedPackages()
        val intent = FocusTimerService.startIntent(
            context, sessionId, taskId, taskTitle, workMinutes, breakMinutes, locked
        )
        context.startForegroundService(intent)
        WidgetUpdater.updateAll(context)
        return sessionId
    }

    suspend fun finishSession(sessionId: Long, completed: Boolean, actualMinutes: Int? = null): Boolean {
        val endedAt = System.currentTimeMillis()
        val session = sessionDao.getById(sessionId) ?: return false
        if (session.endedAtMillis != null) return false

        val actual = actualMinutes ?: ((endedAt - session.startedAtMillis) / 60_000L).toInt().coerceAtLeast(1)
        val updated = sessionDao.endSessionIfActive(sessionId, endedAt, completed)
        if (updated == 0) return false

        sessionDao.updateWrapUp(sessionId, actual, null)
        if (completed) {
            // Credit actual work only, never extra time beyond the planned work block.
            val worked = actual.coerceIn(0, session.plannedMinutes.coerceAtLeast(0))
            advancedBlocker?.creditSession(sessionId, worked, session.startedAtMillis + worked * 60_000L)
        }
        sessionDao.snapshotLeaks(
            sessionId,
            FocusBlockService.blockedAttempts,
            FocusBlockService.topBlockedPackage
        )
        FocusBlockService.sessionActive = false
        if (completed && companionDao.countTreesForSession(sessionId) == 0) {
            val minutes = actualMinutes ?: session.plannedMinutes
            if (minutes >= 5) {
                val zone = ZoneId.systemDefault()
                val weekStart = LocalDate.now(zone).minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
                val weekEnd = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val weeklySessions = sessionDao.observeCompletedCountBetween(weekStart, weekEnd).first()
                val species = PlantCatalog.defaultPlantedSpecies(weeklySessions)
                companionDao.insertTree(
                    FocusGardenEntity(sessionId = sessionId, treeType = species.assetName)
                )
                unlockRewardBundles()
                preferences.addFunCredit(1)
            }
        }
        refreshBlockedPackages()
        WidgetUpdater.updateAll(context)
        return true
    }

    suspend fun wrapUpSession(sessionId: Long, endTag: FocusEndTag?, actualMinutes: Int?) {
        sessionDao.updateWrapUp(sessionId, actualMinutes, endTag?.name)
    }

    private suspend fun unlockRewardBundles() {
        val now = System.currentTimeMillis()
        bundleDao.observeAll().first().filter { it.enabled }.forEach { bundle ->
            bundleDao.setUnlockUntil(bundle.id, now + bundle.unlockMinutes * 60_000L)
        }
    }

    suspend fun endActiveSession(completed: Boolean) {
        val active = sessionDao.getActiveSession() ?: return
        context.startService(
            android.content.Intent(context, FocusTimerService::class.java).apply {
                action = FocusTimerService.ACTION_CANCEL
            }
        )
        finishSession(active.id, completed)
    }

    private suspend fun awaitEndActiveSession(completed: Boolean) {
        sessionDao.getActiveSession()?.let { endActiveSession(completed) }
    }

    suspend fun addBlockRule(rule: BlockRuleEntity) {
        blockRuleDao.insert(rule)
        refreshBlockedPackages()
    }

    suspend fun addScheduledShield(
        packageName: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ) {
        blockRuleDao.insert(
            BlockRuleEntity(
                packageName = packageName.trim(),
                ruleType = BlockRuleType.SCHEDULED,
                startHour = startHour,
                startMinute = startMinute,
                endHour = endHour,
                endMinute = endMinute,
                enabled = true
            )
        )
        refreshBlockedPackages()
    }

    suspend fun toggleBlockRule(id: Long, enabled: Boolean) {
        blockRuleDao.setEnabled(id, enabled)
        refreshBlockedPackages()
    }

    suspend fun deleteBlockRule(id: Long) {
        blockRuleDao.delete(id)
        refreshBlockedPackages()
    }

    suspend fun addTemptationBundle(name: String, rewardPackageName: String, unlockMinutes: Int) {
        bundleDao.insert(
            TemptationBundleEntity(
                name = name.trim(),
                rewardPackageName = rewardPackageName.trim(),
                unlockMinutes = unlockMinutes.coerceIn(5, 120)
            )
        )
        refreshBlockedPackages()
    }

    suspend fun toggleBundle(id: Long, enabled: Boolean) {
        val bundle = bundleDao.observeAll().first().find { it.id == id } ?: return
        bundleDao.update(bundle.copy(enabled = enabled))
        refreshBlockedPackages()
    }

    suspend fun deleteBundle(id: Long) {
        bundleDao.delete(id)
        refreshBlockedPackages()
    }
}
