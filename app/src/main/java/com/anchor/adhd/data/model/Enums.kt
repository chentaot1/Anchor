package com.anchor.adhd.data.model

enum class InboxState {
    TODAY,
    SOMEDAY,
    WAITING
}

enum class TaskDifficulty {
    LIGHT,
    MEDIUM,
    DEEP
}

enum class EnergyLevel {
    LOW,
    OK,
    HIGH
}

/** Inattentive-ADHD state tags for brief EMA check-ins (attention, load, motivation). */
enum class CheckInTag {
    FOGGY,
    SCATTERED,
    OVERWHELMED,
    UNMOTIVATED
}

enum class BlockRuleType {
    SESSION,
    SCHEDULED
}

enum class AiJobType {
    BREAKDOWN,
    BRAINDUMP,
    TRIAGE,
    REPLAN,
    WEEKLY,
    IF_THEN
}

enum class AiJobStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED
}

enum class FocusEndTag {
    STUCK,
    AVOIDING,
    TIRED,
    INTERRUPTED,
    NONE
}

enum class CbtMomentTag {
    GENERAL,
    DIDNT_START,
    OVERWHELMED,
    AVOIDING,
    SCATTERED
}

enum class WorkloadStatus {
    OK,
    WARNING,
    OVER
}

enum class BreakdownGranularity {
    MILD,
    NORMAL,
    SPICY
}

enum class HabitScheduleType {
    DAILY,
    WEEKDAYS,
    SPECIFIC_DAYS,
    X_PER_WEEK
}

enum class HabitAutoSource {
    NONE,
    STEPS,
    EXERCISE
}

enum class BlockListMode {
    BLOCKLIST,
    WHITELIST
}
