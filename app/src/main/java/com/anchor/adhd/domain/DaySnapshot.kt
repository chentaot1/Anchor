package com.anchor.adhd.domain

import com.anchor.adhd.data.model.AssignmentEntity
import com.anchor.adhd.data.model.CalendarEventEntity
import com.anchor.adhd.data.model.CheckInTag
import com.anchor.adhd.data.model.EnergyLevel
import com.anchor.adhd.data.model.NowSource
import com.anchor.adhd.data.model.NowTask
import com.anchor.adhd.data.model.RoutineEntity
import com.anchor.adhd.data.model.TaskDifficulty
import com.anchor.adhd.data.model.TaskEntity

data class DaySnapshot(
    val energy: EnergyLevel,
    val tag: String?,
    val nowTitle: String?,
    val nextTitles: List<String>,
    val inboxCount: Int,
    val replanCount: Int,
    val dueSoonCount: Int,
    val holeCount: Int,
    val blockedLabels: List<String>,
    val now: NowTask,
) {
    fun compactPrompt(): String {
        val next = nextTitles.take(3).joinToString(";")
        val blocked = blockedLabels.take(3).joinToString(",")
        return buildString {
            append("energy=${energy.name}")
            append(" tag=${tag ?: "-"}")
            append(" now=${nowTitle ?: "-"}")
            append(" next=$next")
            append(" inbox=$inboxCount")
            append(" replan=$replanCount")
            append(" due=$dueSoonCount")
            append(" holes=$holeCount")
            append(" blocked=$blocked")
        }
    }

    fun compactWhatsToday(): String {
        val parts = mutableListOf<String>()
        nowTitle?.let { parts.add("Now: $it") }
        if (nextTitles.isNotEmpty()) {
            parts.add("Next: ${nextTitles.take(3).joinToString()}")
        }
        if (inboxCount > 0) parts.add("Inbox: $inboxCount")
        if (replanCount > 0) parts.add("To replan: $replanCount")
        if (dueSoonCount > 0) parts.add("Due soon: $dueSoonCount")
        if (holeCount > 0) parts.add("Gaps before shutdown: $holeCount")
        return if (parts.isEmpty()) {
            "Your day is clear — capture something or start a focus block."
        } else {
            parts.joinToString("\n")
        }
    }
}

fun inboxForPick(
    inboxToday: List<TaskEntity>,
    energy: EnergyLevel,
    overwhelmed: Boolean,
): List<TaskEntity> {
    val hideDeep = energy == EnergyLevel.LOW || overwhelmed
    val filtered = if (hideDeep) inboxToday.filter { it.difficulty != TaskDifficulty.DEEP } else inboxToday
    return EnergyTaskSorter.sort(filtered, energy)
}

fun buildDaySnapshot(
    scheduled: List<TaskEntity>,
    inboxToday: List<TaskEntity>,
    energy: EnergyLevel,
    routines: List<RoutineEntity>,
    checkInTags: Set<CheckInTag>,
    replanCount: Int,
    dueSoon: List<AssignmentEntity>,
    occupancy: List<TimeInterval>,
    nowMillis: Long,
    dayStartMillis: Long,
    shutdownMillis: Long,
    blockedPackages: List<String>,
): DaySnapshot {
    val overwhelmed = CheckInTag.OVERWHELMED in checkInTags
    val pickInbox = inboxForPick(inboxToday, energy, overwhelmed)
    val pickScheduled = if (energy == EnergyLevel.LOW || overwhelmed) {
        scheduled.filter { it.difficulty != TaskDifficulty.DEEP }
    } else {
        scheduled
    }
    val now = NowTaskPicker.pick(pickScheduled, pickInbox, energy, routines, nowMillis)
    val later = NowTaskPicker.laterToday(pickScheduled, now, nowMillis)
    val holeCount = SchedulingSlots.countHoles(
        nowMillis = nowMillis,
        durationMinutes = 20,
        dayStartMillis = dayStartMillis,
        shutdownMillis = shutdownMillis,
        occupancy = occupancy
    )
    val tag = checkInTags.firstOrNull()?.name
    return DaySnapshot(
        energy = energy,
        tag = tag,
        nowTitle = now.task?.title ?: now.routine?.name,
        nextTitles = later.map { it.title }.take(3),
        inboxCount = pickInbox.size,
        replanCount = replanCount,
        dueSoonCount = dueSoon.size,
        holeCount = holeCount,
        blockedLabels = blockedPackages.map { BlockedAppAliases.labelForPackage(it) }.distinct().take(3),
        now = now,
    )
}

fun pickOneTask(
    snapshot: DaySnapshot,
    minutes: Int?,
    inboxToday: List<TaskEntity>,
    scheduled: List<TaskEntity>,
    energy: EnergyLevel,
    overwhelmed: Boolean,
    nowMillis: Long,
): NowTask {
    if (minutes == null) return snapshot.now
    val hideDeep = energy == EnergyLevel.LOW || overwhelmed
    fun fits(task: TaskEntity): Boolean {
        if (hideDeep && task.difficulty == TaskDifficulty.DEEP) return false
        return task.durationMinutes <= minutes
    }
    val timed = scheduled.filter(::fits)
    val inbox = inboxForPick(inboxToday.filter(::fits), energy, overwhelmed)
    return NowTaskPicker.pick(timed, inbox, energy, emptyList(), nowMillis)
}
