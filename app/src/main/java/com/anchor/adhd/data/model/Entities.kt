package com.anchor.adhd.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val inboxState: InboxState = InboxState.TODAY,
    val scheduledStartMillis: Long? = null,
    val durationMinutes: Int = 20,
    val difficulty: TaskDifficulty = TaskDifficulty.MEDIUM,
    val isNextAction: Boolean = false,
    val isCompleted: Boolean = false,
    val parentTaskId: Long? = null,
    val routineId: Long? = null,
    val aiGenerated: Boolean = false,
    val estimatedMinutes: Int? = null,
    val actualMinutes: Int? = null,
    val dueAtMillis: Long? = null,
    val ifThen: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val completedAtMillis: Long? = null,
    val sortOrder: Int = 0
)

/**
 * A chat turn in Anchor Chat (persisted for session memory + reminder context).
 * `taskId`/`context` optionally link a turn to a task so a future reminder can
 * surface "we talked about X".
 */
@Entity(tableName = "chat_turns")
data class ChatTurnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromUser: Boolean,
    val text: String,
    val taskId: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "assignments")
data class AssignmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val course: String = "",
    val dueAtMillis: Long,
    val notes: String = "",
    val isCompleted: Boolean = false
)

@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val source: String = "google",
    val isAllDay: Boolean = false
)

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val cue: String = "",
    val ifThen: String? = null,
    val isEnabled: Boolean = true
)

@Entity(tableName = "routine_steps")
data class RoutineStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val title: String,
    val durationMinutes: Int = 5,
    val sortOrder: Int = 0
)

@Entity(tableName = "replan_items")
data class ReplanItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val missedOnDayMillis: Long,
    val resolved: Boolean = false
)

@Entity(tableName = "chat_undo_ops")
data class ChatUndoOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val kind: String,
    val payload: String,
    val createdAtMillis: Long
)

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long? = null,
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    val plannedMinutes: Int,
    val actualMinutes: Int? = null,
    val completed: Boolean = false,
    val locked: Boolean = true,
    val endTag: FocusEndTag? = null,
    val blockedAttempts: Int = 0,
    val topBlockedPackage: String? = null
)

@Entity(tableName = "block_rules")
data class BlockRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val ruleType: BlockRuleType,
    val startHour: Int? = null,
    val startMinute: Int? = null,
    val endHour: Int? = null,
    val endMinute: Int? = null,
    val enabled: Boolean = true
)

@Entity(tableName = "check_ins")
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val energyLevel: EnergyLevel,
    val tags: String = "",
    val recordedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "companion_state")
data class CompanionStateEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "Pip",
    val energy: Int = 0,
    val maxEnergy: Int = 100,
    val pauseUntilMillis: Long? = null,
    val streakDays: Int = 0,
    val lastActiveDayMillis: Long? = null,
    val stage: Int = 0
)

@Entity(tableName = "focus_garden")
data class FocusGardenEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val treeType: String = "oak",
    val plantedAtMillis: Long = System.currentTimeMillis()
)

data class PlantedTile(
    val gardenId: Long,
    val sessionId: Long,
    val treeType: String,
    val plantedAtMillis: Long,
    val actualMinutes: Int?,
    val plannedMinutes: Int?,
    val taskTitle: String?
)

@Entity(tableName = "ai_jobs")
data class AiJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: AiJobType,
    val inputText: String,
    val outputJson: String? = null,
    val rawOutput: String? = null,
    val status: AiJobStatus = AiJobStatus.PENDING,
    val tokensGenerated: Int = 0,
    val durationMs: Long = 0,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "cbt_cards")
data class CbtCardEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val sortOrder: Int,
    val momentTag: CbtMomentTag = CbtMomentTag.GENERAL
)

@Entity(tableName = "temptation_bundles")
data class TemptationBundleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val rewardPackageName: String,
    val unlockMinutes: Int = 15,
    val enabled: Boolean = true,
    val unlockUntilMillis: Long = 0L
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val scheduleType: HabitScheduleType = HabitScheduleType.DAILY,
    val scheduleDays: String = "",
    val targetPerWeek: Int = 7,
    val gracePerWeek: Int = 1,
    val autoSource: HabitAutoSource = HabitAutoSource.NONE,
    val autoThreshold: Int = 0,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "habit_completions", indices = [Index(value = ["habitId", "dayMillis"], unique = true)])
data class HabitCompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val dayMillis: Long,
    val completed: Boolean = true,
    val autoCompleted: Boolean = false
)
