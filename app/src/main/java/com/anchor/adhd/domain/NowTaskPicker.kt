package com.anchor.adhd.domain

import com.anchor.adhd.data.model.EnergyLevel
import com.anchor.adhd.data.model.NowSource
import com.anchor.adhd.data.model.NowTask
import com.anchor.adhd.data.model.RoutineEntity
import com.anchor.adhd.data.model.TaskEntity
import com.anchor.adhd.domain.EnergyTaskSorter
import java.time.LocalDate
import java.time.ZoneId

object NowTaskPicker {
    fun pick(
        scheduled: List<TaskEntity>,
        inboxToday: List<TaskEntity>,
        energy: EnergyLevel,
        routines: List<RoutineEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): NowTask {
        val zone = ZoneId.systemDefault()
        val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

        val morningRoutine = routines.find { it.name.contains("Morning", ignoreCase = true) }
        if (morningRoutine != null && nowMillis < todayStart + 3 * 3_600_000L && scheduled.isEmpty()) {
            return NowTask(null, NowSource.EMPTY, morningRoutine)
        }

        val overdue = scheduled.filter { task ->
            val start = task.scheduledStartMillis ?: return@filter false
            val end = start + task.durationMinutes * 60_000L
            nowMillis > end
        }
        if (overdue.isNotEmpty()) {
            return NowTask(overdue.first(), NowSource.SCHEDULED_NOW)
        }

        val activeOrNext = scheduled.firstOrNull { task ->
            val start = task.scheduledStartMillis ?: return@firstOrNull false
            val end = start + task.durationMinutes * 60_000L
            nowMillis in start..end || start >= nowMillis
        }
        if (activeOrNext != null) {
            return NowTask(activeOrNext, NowSource.SCHEDULED_NOW)
        }

        val sortedInbox = EnergyTaskSorter.sort(inboxToday, energy)
        val nextInbox = sortedInbox.firstOrNull()
        if (nextInbox != null) {
            return NowTask(nextInbox, NowSource.INBOX)
        }

        val studyRoutine = routines.find { it.name.contains("Study", ignoreCase = true) }
        if (studyRoutine != null) {
            return NowTask(null, NowSource.EMPTY, studyRoutine)
        }

        return NowTask(null, NowSource.EMPTY)
    }

    fun laterToday(scheduled: List<TaskEntity>, nowTask: NowTask?, nowMillis: Long = System.currentTimeMillis()): List<TaskEntity> {
        val currentId = nowTask?.task?.id
        return scheduled.filter { task ->
            val start = task.scheduledStartMillis ?: return@filter false
            task.id != currentId && start > nowMillis
        }
    }
}

object PipStages {
    val labels = listOf("Seed", "Sprout", "Growing", "Rooted", "Flourishing")

    fun stageForWeeklySessions(count: Int): Int = when {
        count <= 0 -> 0
        count <= 2 -> 1
        count <= 5 -> 2
        count <= 9 -> 3
        else -> 4
    }

    fun label(stage: Int): String = labels.getOrElse(stage.coerceIn(0, labels.lastIndex)) { labels.first() }
}
