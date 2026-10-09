package com.anchor.adhd.data.repository

import com.anchor.adhd.data.db.CheckInDao
import com.anchor.adhd.data.db.CompanionDao
import com.anchor.adhd.data.db.FocusSessionDao
import com.anchor.adhd.data.db.ReplanDao
import com.anchor.adhd.data.db.TaskDao
import com.anchor.adhd.data.model.ActivityLogItem
import com.anchor.adhd.data.model.CheckInEntity
import com.anchor.adhd.data.model.CheckInPatterns
import com.anchor.adhd.data.model.CheckInTag
import com.anchor.adhd.data.model.CompanionStateEntity
import com.anchor.adhd.data.model.EnergyLevel
import com.anchor.adhd.data.model.FocusGardenEntity
import com.anchor.adhd.data.model.PlantedTile
import com.anchor.adhd.data.model.WeeklySummary
import com.anchor.adhd.data.model.EstimateStats
import com.anchor.adhd.data.prefs.UserPreferences
import com.anchor.adhd.domain.CheckInCodec
import com.anchor.adhd.domain.PipStages
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class GrowRepository(
    private val checkInDao: CheckInDao,
    private val companionDao: CompanionDao,
    private val sessionDao: FocusSessionDao,
    private val taskDao: TaskDao,
    private val replanDao: ReplanDao,
    private val preferences: UserPreferences
) {
    fun observeCompanion(): Flow<CompanionStateEntity?> = companionDao.observe()
    fun observeTreeCount(): Flow<Int> = companionDao.observeTreeCount()
    fun observeGarden(): Flow<List<FocusGardenEntity>> = companionDao.observeGarden()
    fun observePlantedTiles(): Flow<List<PlantedTile>> = companionDao.observePlantedTiles()
    fun observeCheckIns(): Flow<List<CheckInEntity>> = checkInDao.observeRecent()

    fun observeLatestCheckInSince(sinceMillis: Long): Flow<CheckInEntity?> =
        checkInDao.observeLatestSince(sinceMillis)

    fun observeCheckInPatterns(): Flow<CheckInPatterns> {
        val zone = ZoneId.systemDefault()
        val weekStart = LocalDate.now(zone).minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        return checkInDao.observeRecent().map { checkIns ->
            val thisWeek = checkIns.filter { it.recordedAtMillis >= weekStart }
            val tagCounts = CheckInTag.entries.associateWith { tag ->
                thisWeek.count { CheckInCodec.parseTags(it.tags).contains(tag) }
            }.filterValues { it > 0 }
            CheckInPatterns(checkInsThisWeek = thisWeek.size, tagCounts = tagCounts)
        }
    }

    fun observeWeeklySummary(): Flow<WeeklySummary> {
        val zone = ZoneId.systemDefault()
        val weekStart = LocalDate.now(zone).minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val weekEnd = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return combine(
            combine(
                sessionDao.observeCompletedCountBetween(weekStart, weekEnd),
                sessionDao.observeCompletedMinutesBetween(weekStart, weekEnd),
                taskDao.observeCompletedCountBetween(weekStart, weekEnd)
            ) { sessions, minutes, tasks -> Triple(sessions, minutes, tasks) },
            combine(
                checkInDao.observeRecent().map { checkIns -> checkIns.count { it.recordedAtMillis >= weekStart } },
                replanDao.observeUnresolved().map { it.size },
                companionDao.observe().map { it?.streakDays ?: 0 }
            ) { checkIns, replan, streak -> Triple(checkIns, replan, streak) }
        ) { (sessions, minutes, tasks), (checkIns, replan, streak) ->
            WeeklySummary(
                focusSessions = sessions,
                focusMinutes = minutes,
                tasksCompleted = tasks,
                checkInsLogged = checkIns,
                replanQueueSize = replan,
                streakDays = streak,
                avgEstimateErrorPercent = 0,
                estimateSampleCount = 0
            )
        }.let { baseFlow ->
            combine(baseFlow, observeEstimateStats()) { summary, stats ->
                summary.copy(
                    avgEstimateErrorPercent = stats.avgErrorPercent,
                    estimateSampleCount = stats.sampleCount
                )
            }
        }
    }

    fun observeEstimateStats(): Flow<EstimateStats> {
        val zone = ZoneId.systemDefault()
        val weekStart = LocalDate.now(zone).minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()
        return sessionDao.observeCompletedWithActual(weekStart).map { sessions ->
            val errors = sessions.mapNotNull { s ->
                val planned = s.plannedMinutes
                val actual = s.actualMinutes ?: return@mapNotNull null
                if (planned <= 0) return@mapNotNull null
                kotlin.math.abs(actual - planned) * 100 / planned
            }
            EstimateStats(
                avgErrorPercent = if (errors.isEmpty()) 0 else errors.average().toInt(),
                sampleCount = errors.size
            )
        }
    }

    fun observeActivityLog(): Flow<List<ActivityLogItem>> {
        val zone = ZoneId.systemDefault()
        val weekStart = LocalDate.now(zone).minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val timeFmt = DateTimeFormatter.ofPattern("EEE h:mm a")
        return combine(
            sessionDao.observeRecent(),
            checkInDao.observeRecent(),
            taskDao.observeRecentlyCompleted(weekStart, 15)
        ) { sessions, checkIns, tasks ->
            val items = mutableListOf<ActivityLogItem>()
            sessions.filter { it.startedAtMillis >= weekStart }.forEach { s ->
                val whenStr = java.time.Instant.ofEpochMilli(s.startedAtMillis).atZone(zone).format(timeFmt)
                items += ActivityLogItem(
                    s.startedAtMillis,
                    if (s.completed) "Focus completed" else "Focus ended early",
                    "$whenStr · ${s.actualMinutes ?: s.plannedMinutes} min"
                )
            }
            checkIns.filter { it.recordedAtMillis >= weekStart }.forEach { c ->
                val tags = CheckInCodec.parseTags(c.tags)
                items += ActivityLogItem(
                    c.recordedAtMillis,
                    "Check-in",
                    CheckInCodec.formatCheckInDetail(c.energyLevel, tags)
                )
            }
            tasks.forEach { t ->
                val at = t.completedAtMillis ?: return@forEach
                items += ActivityLogItem(
                    at,
                    "Task done",
                    t.title
                )
            }
            items.sortedByDescending { it.timestampMillis }.take(20)
        }
    }

    suspend fun logCheckIn(energy: EnergyLevel, tags: Set<CheckInTag>) {
        val zone = ZoneId.systemDefault()
        val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val existing = checkInDao.observeLatestSince(todayStart).first()
        if (existing != null) {
            checkInDao.update(
                existing.copy(
                    energyLevel = energy,
                    tags = CheckInCodec.formatTags(tags),
                    recordedAtMillis = System.currentTimeMillis()
                )
            )
        } else {
            checkInDao.insert(
                CheckInEntity(
                    energyLevel = energy,
                    tags = CheckInCodec.formatTags(tags)
                )
            )
        }
    }

    suspend fun rewardFocusComplete(partialCredit: Float = 1f) {
        val current = companionDao.observe().first() ?: CompanionStateEntity()
        val activePause = current.pauseUntilMillis?.takeIf { it > System.currentTimeMillis() }
        if (activePause != null) return
        val gain = (25 * partialCredit).toInt().coerceAtLeast(5)
        val zone = ZoneId.systemDefault()
        val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val yesterdayStart = LocalDate.now(zone).minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val twoDaysAgoStart = LocalDate.now(zone).minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val weekStart = LocalDate.now(zone).minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val weekEnd = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val weeklySessions = sessionDao.observeCompletedCountBetween(weekStart, weekEnd).first()
        val newStreak = when (current.lastActiveDayMillis) {
            todayStart -> current.streakDays
            yesterdayStart -> current.streakDays + 1
            twoDaysAgoStart -> current.streakDays
            else -> 1
        }
        companionDao.upsert(
            current.copy(
                energy = (current.energy + gain).coerceAtMost(current.maxEnergy),
                streakDays = newStreak,
                lastActiveDayMillis = todayStart,
                pauseUntilMillis = activePause,
                stage = PipStages.stageForWeeklySessions(weeklySessions)
            )
        )
    }

    suspend fun rewardPartialDay() {
        rewardFocusComplete(partialCredit = 0.4f)
    }

    suspend fun pauseCompanion(days: Int) {
        val current = companionDao.observe().first() ?: CompanionStateEntity()
        val until = LocalDate.now().plusDays(days.toLong())
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        companionDao.upsert(current.copy(pauseUntilMillis = until))
    }

    suspend fun resumeCompanion() {
        val current = companionDao.observe().first() ?: CompanionStateEntity()
        companionDao.upsert(current.copy(pauseUntilMillis = null))
    }

    private fun isPaused(state: CompanionStateEntity): Boolean {
        val until = state.pauseUntilMillis ?: return false
        return System.currentTimeMillis() < until
    }
}
