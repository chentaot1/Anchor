package com.anchor.adhd.domain

import com.anchor.adhd.data.model.CbtCardEntity
import com.anchor.adhd.data.model.CbtMomentTag
import com.anchor.adhd.data.model.CheckInTag

enum class StrategyAction {
    START_FOCUS_5,
    START_FOCUS_10,
    OPEN_REPLAN,
    RUN_MORNING_REPLAN
}

object StrategyCatalog {
    const val SEED_VERSION = 2

    fun actionsFor(cardId: String): List<StrategyAction> = when (cardId) {
        "five_minute_start", "zoned_out", "unclear_first_step" -> listOf(StrategyAction.START_FOCUS_10)
        "bad_day", "after_missed_day" -> listOf(StrategyAction.OPEN_REPLAN, StrategyAction.RUN_MORNING_REPLAN)
        "when_avoiding", "low_drive_nudge" -> listOf(StrategyAction.START_FOCUS_5)
        "task_switching" -> listOf(StrategyAction.START_FOCUS_10)
        else -> emptyList()
    }

    fun actionLabel(action: StrategyAction): String = when (action) {
        StrategyAction.START_FOCUS_5 -> "Start 5 min"
        StrategyAction.START_FOCUS_10 -> "Start 10 min"
        StrategyAction.OPEN_REPLAN -> "Open replan"
        StrategyAction.RUN_MORNING_REPLAN -> "Run morning replan"
    }

    fun cardsForCheckIn(tags: Set<CheckInTag>): List<String> = tags.flatMap { tag ->
        when (tag) {
            CheckInTag.FOGGY -> listOf("zoned_out", "unclear_first_step")
            CheckInTag.SCATTERED -> listOf("task_switching", "zoned_out")
            CheckInTag.OVERWHELMED -> listOf("good_enough", "bad_day")
            CheckInTag.UNMOTIVATED -> listOf("when_avoiding", "low_drive_nudge", "five_minute_start")
        }
    }.distinct()

    fun allStrategyCards(): List<CbtCardEntity> = listOf(
        CbtCardEntity(
            id = "five_minute_start",
            title = "5-minute start",
            body = "Set a 10-minute timer. Do the smallest physical step first. Stop when it rings if you want.",
            sortOrder = 1,
            momentTag = CbtMomentTag.DIDNT_START
        ),
        CbtCardEntity(
            id = "good_enough",
            title = "Good enough",
            body = "Ship the version that exists. Revise later if you need to.",
            sortOrder = 2,
            momentTag = CbtMomentTag.OVERWHELMED
        ),
        CbtCardEntity(
            id = "when_avoiding",
            title = "When I'm avoiding",
            body = "Name it: bored, scared, unclear, or tired? Pick one fix: smaller step, ask for clarity, or switch to a light task and return.",
            sortOrder = 3,
            momentTag = CbtMomentTag.AVOIDING
        ),
        CbtCardEntity(
            id = "time_lie",
            title = "Time estimates",
            body = "Guess the duration, then add 50%. Track actual minutes when you can.",
            sortOrder = 4,
            momentTag = CbtMomentTag.GENERAL
        ),
        CbtCardEntity(
            id = "bad_day",
            title = "After a missed day",
            body = "Open Replan, pick one task. Run one focus session if you can.",
            sortOrder = 5,
            momentTag = CbtMomentTag.GENERAL
        ),
        CbtCardEntity(
            id = "interrupted",
            title = "After interruption",
            body = "Note where you stopped. Restart with one small step on the same task.",
            sortOrder = 6,
            momentTag = CbtMomentTag.GENERAL
        ),
        CbtCardEntity(
            id = "zoned_out",
            title = "I zoned out",
            body = "Write the last thing you remember doing. Set a 2-minute timer and repeat that one step.",
            sortOrder = 7,
            momentTag = CbtMomentTag.SCATTERED
        ),
        CbtCardEntity(
            id = "unclear_first_step",
            title = "Don't know where to start",
            body = "Write the physical first action only: open the doc, find page 12, put pen on paper. Not the whole task.",
            sortOrder = 8,
            momentTag = CbtMomentTag.DIDNT_START
        ),
        CbtCardEntity(
            id = "not_good_enough",
            title = "Not good enough yet",
            body = "Define done enough in one sentence. What would you accept if a friend turned this in?",
            sortOrder = 9,
            momentTag = CbtMomentTag.OVERWHELMED
        ),
        CbtCardEntity(
            id = "task_switching",
            title = "Keep switching tasks",
            body = "Pick one task. Write why it matters today. Commit to 10 minutes only on that one.",
            sortOrder = 10,
            momentTag = CbtMomentTag.SCATTERED
        ),
        CbtCardEntity(
            id = "low_drive_nudge",
            title = "Low drive",
            body = "You don't need motivation to start — only a smaller first step. Two minutes counts.",
            sortOrder = 11,
            momentTag = CbtMomentTag.AVOIDING
        ),
        CbtCardEntity(
            id = "after_missed_day",
            title = "Catch-up trap",
            body = "One task, not a full recovery. Missed days don't get erased in one session.",
            sortOrder = 12,
            momentTag = CbtMomentTag.GENERAL
        )
    )
}
