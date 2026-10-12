package com.anchor.adhd.domain

import com.anchor.adhd.data.model.CalendarEventEntity
import com.anchor.adhd.data.model.TaskEntity
import com.anchor.adhd.data.model.WorkloadStatus

data class WorkloadResult(
    val plannedMinutes: Int,
    val capacityMinutes: Int,
    val busyMinutes: Int,
    val effectiveLoad: Int,
    val status: WorkloadStatus
)

data class TimeInterval(val startMillis: Long, val endMillis: Long)

object WorkloadCalculator {
    fun calculate(
        tasks: List<TaskEntity>,
        calendarEvents: List<CalendarEventEntity>,
        capacityMinutes: Int
    ): WorkloadResult {
        val taskIntervals = tasks
            .filter { !it.isCompleted && it.scheduledStartMillis != null }
            .map { task ->
                val start = task.scheduledStartMillis!!
                TimeInterval(start, start + task.durationMinutes * 60_000L)
            }

        val calendarIntervals = calendarEvents
            .filter { !it.isAllDay }
            .map { event -> TimeInterval(event.startMillis, event.endMillis) }

        val plannedMinutes = tasks
            .filter { !it.isCompleted }
            .sumOf { it.durationMinutes }

        val mergedTaskMinutes = unionMinutes(taskIntervals)
        val mergedCalendarMinutes = unionMinutes(calendarIntervals)
        val allDayMinutes = tasks
            .filter { !it.isCompleted && it.scheduledStartMillis == null }
            .sumOf { it.durationMinutes }
        val effectiveLoad = unionMinutes(taskIntervals + calendarIntervals) + allDayMinutes
        val busyMinutes = mergedCalendarMinutes

        val status = when {
            capacityMinutes <= 0 -> WorkloadStatus.OVER
            effectiveLoad >= capacityMinutes -> WorkloadStatus.OVER
            effectiveLoad >= (capacityMinutes * 0.8).toInt() -> WorkloadStatus.WARNING
            else -> WorkloadStatus.OK
        }

        return WorkloadResult(
            plannedMinutes = plannedMinutes,
            capacityMinutes = capacityMinutes,
            busyMinutes = busyMinutes,
            effectiveLoad = effectiveLoad,
            status = status
        )
    }

    internal fun unionMinutes(intervals: List<TimeInterval>): Int {
        if (intervals.isEmpty()) return 0
        val sorted = intervals.sortedBy { it.startMillis }
        var total = 0L
        var currentStart = sorted.first().startMillis
        var currentEnd = sorted.first().endMillis

        for (i in 1 until sorted.size) {
            val interval = sorted[i]
            if (interval.startMillis <= currentEnd) {
                currentEnd = maxOf(currentEnd, interval.endMillis)
            } else {
                total += currentEnd - currentStart
                currentStart = interval.startMillis
                currentEnd = interval.endMillis
            }
        }
        total += currentEnd - currentStart
        return (total / 60_000L).toInt()
    }
}
