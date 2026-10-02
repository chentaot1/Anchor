package com.anchor.adhd.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.anchor.adhd.data.model.AiJobEntity
import com.anchor.adhd.data.model.AssignmentEntity
import com.anchor.adhd.data.model.CalendarEventEntity
import com.anchor.adhd.data.model.CbtCardEntity
import com.anchor.adhd.data.model.CbtMomentTag
import com.anchor.adhd.data.model.CompanionStateEntity
import com.anchor.adhd.data.model.FocusGardenEntity
import com.anchor.adhd.data.model.FocusSessionEntity
import com.anchor.adhd.data.model.InboxState
import com.anchor.adhd.data.model.CheckInEntity
import com.anchor.adhd.data.model.ReplanItemEntity
import com.anchor.adhd.data.model.RoutineEntity
import com.anchor.adhd.data.model.RoutineStepEntity
import com.anchor.adhd.data.model.TaskEntity
import com.anchor.adhd.data.model.BlockRuleEntity
import com.anchor.adhd.data.model.HabitCompletionEntity
import com.anchor.adhd.data.model.HabitEntity
import com.anchor.adhd.data.model.FunLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("UPDATE tasks SET inboxState = :state WHERE id = :id")
    suspend fun setInboxState(id: Long, state: InboxState)

    @Query("UPDATE tasks SET inboxState = :state, scheduledStartMillis = NULL WHERE id = :id")
    suspend fun moveToSomeday(id: Long, state: InboxState = InboxState.SOMEDAY)

    @Query("UPDATE tasks SET scheduledStartMillis = :start, durationMinutes = :duration WHERE id = :id")
    suspend fun updateSchedule(id: Long, start: Long, duration: Int)

    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND parentTaskId IS NULL ORDER BY sortOrder, createdAtMillis")
    fun observeActiveTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE inboxState = :state AND isCompleted = 0 AND parentTaskId IS NULL ORDER BY sortOrder, createdAtMillis")
    fun observeInbox(state: InboxState): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE scheduledStartMillis IS NOT NULL AND isCompleted = 0 AND parentTaskId IS NULL ORDER BY scheduledStartMillis")
    fun observeScheduledTasks(): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks WHERE scheduledStartMillis IS NOT NULL AND isCompleted = 0 AND parentTaskId IS NULL
        AND scheduledStartMillis >= :dayStart AND scheduledStartMillis < :dayEnd
        ORDER BY scheduledStartMillis
        """
    )
    fun observeScheduledForDay(dayStart: Long, dayEnd: Long): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks WHERE scheduledStartMillis IS NOT NULL AND isCompleted = 0
        AND scheduledStartMillis >= :dayStart AND scheduledStartMillis < :dayEnd
        ORDER BY scheduledStartMillis
        """
    )
    suspend fun getScheduledBlocksForDay(dayStart: Long, dayEnd: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentId AND isCompleted = 0 ORDER BY sortOrder, createdAtMillis")
    fun observeChildren(parentId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentId")
    suspend fun getChildren(parentId: Long): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>): List<Long>

    @Transaction
    suspend fun insertParentWithChildren(parent: TaskEntity, children: List<TaskEntity>): Long {
        val parentId = insert(parent)
        if (children.isNotEmpty()) {
            insertAll(children.map { it.copy(parentTaskId = parentId) })
        }
        return parentId
    }

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET isCompleted = 1, completedAtMillis = :at WHERE id = :id")
    suspend fun markComplete(id: Long, at: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET isCompleted = 1, completedAtMillis = :at WHERE parentTaskId = :parentId")
    suspend fun markChildrenComplete(parentId: Long, at: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET isCompleted = 0, completedAtMillis = NULL WHERE id = :id")
    suspend fun unmarkComplete(id: Long)

    @Query("UPDATE tasks SET isCompleted = 0, completedAtMillis = NULL WHERE parentTaskId = :parentId")
    suspend fun unmarkChildrenComplete(parentId: Long)

    @Transaction
    suspend fun completeTaskCascade(id: Long, isParent: Boolean, at: Long = System.currentTimeMillis()) {
        markComplete(id, at)
        if (isParent) markChildrenComplete(id, at)
    }

    @Transaction
    suspend fun unCompleteTaskCascade(id: Long, isParent: Boolean) {
        unmarkComplete(id)
        if (isParent) unmarkChildrenComplete(id)
    }

    @Query("UPDATE tasks SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)

    @Query("SELECT COUNT(*) FROM tasks WHERE isCompleted = 1 AND parentTaskId IS NULL AND completedAtMillis >= :start AND completedAtMillis < :end")
    fun observeCompletedCountBetween(start: Long, end: Long): Flow<Int>

    @Query("SELECT * FROM tasks WHERE isCompleted = 1 AND parentTaskId IS NULL AND completedAtMillis >= :start ORDER BY completedAtMillis DESC LIMIT :limit")
    fun observeRecentlyCompleted(start: Long, limit: Int = 10): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks WHERE isCompleted = 0 AND scheduledStartMillis IS NOT NULL AND parentTaskId IS NULL
        AND (scheduledStartMillis + durationMinutes * 60000) < :now
        AND scheduledStartMillis < :dayEnd
        AND (:excludeTaskId IS NULL OR id != :excludeTaskId)
        """
    )
    suspend fun getMissedScheduledBefore(now: Long, dayEnd: Long, excludeTaskId: Long? = null): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE inboxState = 'TODAY' AND scheduledStartMillis IS NULL AND isCompleted = 0 AND parentTaskId IS NULL")
    suspend fun getUnscheduledTodayTasks(): List<TaskEntity>

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface AssignmentDao {
    @Query("SELECT * FROM assignments")
    suspend fun getAll(): List<AssignmentEntity>

    @Query("SELECT * FROM assignments WHERE isCompleted = 0 ORDER BY dueAtMillis")
    fun observeOpen(): Flow<List<AssignmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(assignment: AssignmentEntity): Long

    @Update
    suspend fun update(assignment: AssignmentEntity)

    @Query("SELECT * FROM assignments WHERE isCompleted = 0 AND dueAtMillis <= :deadline ORDER BY dueAtMillis")
    fun observeDueBefore(deadline: Long): Flow<List<AssignmentEntity>>
}

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events")
    suspend fun getAll(): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_events WHERE startMillis >= :from AND startMillis < :to ORDER BY startMillis")
    fun observeBetween(from: Long, to: Long): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE endMillis > :from AND startMillis < :to ORDER BY startMillis")
    suspend fun getOverlapping(from: Long, to: Long): List<CalendarEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(events: List<CalendarEventEntity>)

    @Query("DELETE FROM calendar_events WHERE id NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<String>)

    @Query("DELETE FROM calendar_events WHERE source = :source AND id NOT IN (:ids)")
    suspend fun deleteSourceNotIn(source: String, ids: List<String>)

    @Query("DELETE FROM calendar_events WHERE source = :source")
    suspend fun deleteSource(source: String)

    @Query("DELETE FROM calendar_events WHERE source = :source AND startMillis >= :from AND startMillis < :to")
    suspend fun deleteSourceInRange(source: String, from: Long, to: Long)

    @Query("DELETE FROM calendar_events WHERE source = :source AND startMillis >= :from AND startMillis < :to AND id NOT IN (:ids)")
    suspend fun deleteSourceInRangeNotIn(source: String, from: Long, to: Long, ids: List<String>)

    @Query("DELETE FROM calendar_events")
    suspend fun deleteAll()
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines WHERE isEnabled = 1")
    fun observeEnabled(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routine_steps WHERE routineId = :routineId ORDER BY sortOrder")
    fun observeSteps(routineId: Long): Flow<List<RoutineStepEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<RoutineStepEntity>)
}

@Dao
interface ReplanDao {
    @Query("SELECT * FROM replan_items WHERE id = :id")
    suspend fun getById(id: Long): ReplanItemEntity?

    @Query("SELECT * FROM replan_items WHERE resolved = 0 ORDER BY missedOnDayMillis")
    fun observeUnresolved(): Flow<List<ReplanItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ReplanItemEntity): Long

    @Query(
        "SELECT COUNT(*) FROM replan_items WHERE taskId = :taskId AND missedOnDayMillis = :dayMillis AND resolved = 0"
    )
    suspend fun countUnresolvedForTaskDay(taskId: Long, dayMillis: Long): Int

    @Query("UPDATE replan_items SET resolved = 1 WHERE id = :id")
    suspend fun resolve(id: Long)
}

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions WHERE endedAtMillis IS NULL ORDER BY startedAtMillis DESC LIMIT 1")
    suspend fun getActiveSession(): FocusSessionEntity?

    @Query("UPDATE focus_sessions SET endedAtMillis = :endedAt, completed = :completed WHERE id = :id AND endedAtMillis IS NULL")
    suspend fun endSessionIfActive(id: Long, endedAt: Long, completed: Boolean): Int

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun getById(id: Long): FocusSessionEntity?

    @Query("UPDATE focus_sessions SET actualMinutes = :actualMinutes, endTag = :endTag WHERE id = :id")
    suspend fun updateWrapUp(id: Long, actualMinutes: Int?, endTag: String?)

    @Query("UPDATE focus_sessions SET blockedAttempts = :attempts, topBlockedPackage = :pkg WHERE id = :id")
    suspend fun snapshotLeaks(id: Long, attempts: Int, pkg: String?)

    @Query("SELECT COALESCE(SUM(blockedAttempts), 0) FROM focus_sessions WHERE taskId = :taskId")
    suspend fun leakAttemptsForTask(taskId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: FocusSessionEntity): Long

    @Update
    suspend fun update(session: FocusSessionEntity)

    @Query("SELECT COUNT(*) FROM focus_sessions WHERE completed = 1 AND startedAtMillis >= :dayStart AND startedAtMillis < :dayEnd")
    fun observeCompletedCountForDay(dayStart: Long, dayEnd: Long): Flow<Int>

    @Query("SELECT * FROM focus_sessions ORDER BY startedAtMillis DESC LIMIT 20")
    fun observeRecent(): Flow<List<FocusSessionEntity>>

    @Query("SELECT COUNT(*) FROM focus_sessions WHERE completed = 1 AND startedAtMillis >= :start AND startedAtMillis < :end")
    fun observeCompletedCountBetween(start: Long, end: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(plannedMinutes), 0) FROM focus_sessions WHERE completed = 1 AND startedAtMillis >= :start AND startedAtMillis < :end")
    fun observeCompletedMinutesBetween(start: Long, end: Long): Flow<Int>

    @Query("SELECT * FROM focus_sessions WHERE completed = 1 AND actualMinutes IS NOT NULL AND startedAtMillis >= :start")
    fun observeCompletedWithActual(start: Long): Flow<List<FocusSessionEntity>>

    @Query("DELETE FROM focus_sessions")
    suspend fun clear()
}

@Dao
interface BlockRuleDao {
    @Query("SELECT * FROM block_rules ORDER BY packageName")
    fun observeAll(): Flow<List<BlockRuleEntity>>

    @Query("SELECT * FROM block_rules WHERE enabled = 1")
    fun observeEnabled(): Flow<List<BlockRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: BlockRuleEntity): Long

    @Update
    suspend fun update(rule: BlockRuleEntity)

    @Query("UPDATE block_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM block_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM block_rules")
    suspend fun clearAll()
}

@Dao
interface CheckInDao {
    @Insert
    suspend fun insert(entry: CheckInEntity): Long

    @Update
    suspend fun update(entry: CheckInEntity)

    @Query("SELECT * FROM check_ins ORDER BY recordedAtMillis DESC LIMIT 30")
    fun observeRecent(): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_ins WHERE recordedAtMillis >= :sinceMillis ORDER BY recordedAtMillis DESC LIMIT 1")
    fun observeLatestSince(sinceMillis: Long): Flow<CheckInEntity?>
}

@Dao
interface CompanionDao {
    @Query("SELECT * FROM companion_state WHERE id = 1")
    fun observe(): Flow<CompanionStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: CompanionStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTree(tree: FocusGardenEntity): Long

    @Query("SELECT COUNT(*) FROM focus_garden WHERE sessionId = :sessionId")
    suspend fun countTreesForSession(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM focus_garden")
    fun observeTreeCount(): Flow<Int>

    @Query("SELECT * FROM focus_garden ORDER BY plantedAtMillis DESC")
    fun observeGarden(): Flow<List<FocusGardenEntity>>

    @Query(
        """
        SELECT g.id AS gardenId, g.sessionId AS sessionId, g.treeType AS treeType,
               g.plantedAtMillis AS plantedAtMillis,
               s.actualMinutes AS actualMinutes, s.plannedMinutes AS plannedMinutes,
               t.title AS taskTitle
        FROM focus_garden g
        LEFT JOIN focus_sessions s ON s.id = g.sessionId
        LEFT JOIN tasks t ON t.id = s.taskId
        ORDER BY g.plantedAtMillis DESC
        """
    )
    fun observePlantedTiles(): Flow<List<com.anchor.adhd.data.model.PlantedTile>>
}

@Dao
interface AiJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: AiJobEntity): Long

    @Update
    suspend fun update(job: AiJobEntity)

    @Query("SELECT * FROM ai_jobs ORDER BY createdAtMillis DESC LIMIT 20")
    fun observeRecent(): Flow<List<AiJobEntity>>

    @Query("DELETE FROM ai_jobs")
    suspend fun clear()
}

@Dao
interface CbtCardDao {
    @Query("SELECT * FROM cbt_cards ORDER BY sortOrder")
    fun observeAll(): Flow<List<CbtCardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<CbtCardEntity>)

    @Query("SELECT * FROM cbt_cards WHERE momentTag = :tag OR momentTag = 'GENERAL' ORDER BY sortOrder")
    fun observeForMoment(tag: CbtMomentTag): Flow<List<CbtCardEntity>>
}

@Dao
interface TemptationBundleDao {
    @Query("SELECT * FROM temptation_bundles ORDER BY name")
    fun observeAll(): Flow<List<com.anchor.adhd.data.model.TemptationBundleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bundle: com.anchor.adhd.data.model.TemptationBundleEntity): Long

    @Update
    suspend fun update(bundle: com.anchor.adhd.data.model.TemptationBundleEntity)

    @Query("DELETE FROM temptation_bundles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE temptation_bundles SET unlockUntilMillis = :until WHERE id = :id")
    suspend fun setUnlockUntil(id: Long, until: Long)
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE isArchived = 0 ORDER BY sortOrder, createdAtMillis")
    fun observeActive(): Flow<List<HabitEntity>>

    @Query("SELECT COUNT(*) FROM habits WHERE isArchived = 0")
    suspend fun countActive(): Int

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: Long): HabitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Query("UPDATE habits SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId AND dayMillis >= :from AND dayMillis < :to")
    fun observeCompletionsBetween(habitId: Long, from: Long, to: Long): Flow<List<HabitCompletionEntity>>

    @Query("SELECT * FROM habit_completions WHERE habitId IN (:habitIds) AND dayMillis >= :from AND dayMillis < :to")
    fun observeCompletionsForHabits(habitIds: List<Long>, from: Long, to: Long): Flow<List<HabitCompletionEntity>>

    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId AND dayMillis = :dayMillis LIMIT 1")
    suspend fun getCompletion(habitId: Long, dayMillis: Long): HabitCompletionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletion(completion: HabitCompletionEntity): Long

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId AND dayMillis = :dayMillis")
    suspend fun deleteCompletion(habitId: Long, dayMillis: Long)

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId")
    suspend fun deleteCompletionsForHabit(habitId: Long)
}

@Dao
interface FunLinkDao {
    @Query("SELECT * FROM fun_links WHERE enabled = 1 ORDER BY sortOrder ASC")
    fun observeActiveFunLinks(): Flow<List<FunLinkEntity>>

    @Query("SELECT * FROM fun_links ORDER BY sortOrder ASC")
    fun observeAllFunLinks(): Flow<List<FunLinkEntity>>

    @Query("SELECT * FROM fun_links WHERE id = :id")
    suspend fun getById(id: Long): FunLinkEntity?

    @Query("SELECT COUNT(*) FROM fun_links")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(links: List<FunLinkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(link: FunLinkEntity): Long

    @Update
    suspend fun update(link: FunLinkEntity)

    @Query("DELETE FROM fun_links WHERE id = :id")
    suspend fun deleteById(id: Long)
}

