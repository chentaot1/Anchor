package com.anchor.adhd.domain

import com.anchor.adhd.data.model.CalendarEventEntity
import com.anchor.adhd.data.model.TaskEntity
import java.time.Instant
import java.time.ZoneId

object SchedulingSlots {
    /**
     * First start time where [durationMinutes] fits between occupancy blocks,
     * scanning `[max(now, dayStart), shutdown)`. Null if nothing fits — caller
     * must leave the task unscheduled in Today, never place after shutdown.
     */
    fun firstHoleThatFits(
        nowMillis: Long,
        durationMinutes: Int,
        dayStartMillis: Long,
        shutdownMillis: Long,
        occupancy: List<TimeInterval>,
        roundMinutes: Int = 15,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long? {
        if (durationMinutes <= 0) return null
        val needed = durationMinutes * 60_000L
        if (shutdownMillis <= dayStartMillis) return null
        var cursor = roundUpToMinutes(maxOf(nowMillis, dayStartMillis), roundMinutes, zone)
        if (cursor + needed > shutdownMillis) return null

        val merged = mergeIntervals(
            occupancy.filter { it.endMillis > cursor && it.startMillis < shutdownMillis }
        )
        for (block in merged) {
            if (block.startMillis - cursor >= needed) return cursor
            cursor = maxOf(cursor, block.endMillis)
            if (cursor + needed > shutdownMillis) return null
        }
        return if (shutdownMillis - cursor >= needed) cursor else null
    }

    /** Count sequential 20-minute (or [durationMinutes]) holes until shutdown. */
    fun countHoles(
        nowMillis: Long,
        durationMinutes: Int,
        dayStartMillis: Long,
        shutdownMillis: Long,
        occupancy: List<TimeInterval>,
        roundMinutes: Int = 15,
        zone: ZoneId = ZoneId.systemDefault(),
        limit: Int = 12,
    ): Int {
        val occ = occupancy.toMutableList()
        var count = 0
        var now = nowMillis
        while (count < limit) {
            val hole = firstHoleThatFits(now, durationMinutes, dayStartMillis, shutdownMillis, occ, roundMinutes, zone)
                ?: break
            occ += TimeInterval(hole, hole + durationMinutes * 60_000L)
            now = hole + durationMinutes * 60_000L
            count++
        }
        return count
    }

    fun occupancyIntervals(
        tasks: List<TaskEntity>,
        events: List<CalendarEventEntity>
    ): List<TimeInterval> {
        val taskBlocks = tasks.mapNotNull { task ->
            val start = task.scheduledStartMillis ?: return@mapNotNull null
            if (task.isCompleted) return@mapNotNull null
            TimeInterval(start, start + task.durationMinutes.coerceAtLeast(0) * 60_000L)
        }
        val eventBlocks = events
            .filter { !it.isAllDay }
            .map { TimeInterval(it.startMillis, it.endMillis) }
        return taskBlocks + eventBlocks
    }

    internal fun roundUpToMinutes(millis: Long, roundMinutes: Int, zone: ZoneId): Long {
        if (roundMinutes <= 1) return millis
        val zdt = Instant.ofEpochMilli(millis).atZone(zone)
        val rem = (zdt.hour * 60 + zdt.minute) % roundMinutes
        val base = zdt.withSecond(0).withNano(0)
        val addMin = when {
            rem != 0 -> roundMinutes - rem
            zdt.second != 0 || zdt.nano != 0 -> roundMinutes
            else -> 0
        }
        return base.plusMinutes(addMin.toLong()).toInstant().toEpochMilli()
    }

    private fun mergeIntervals(intervals: List<TimeInterval>): List<TimeInterval> {
        if (intervals.isEmpty()) return emptyList()
        val sorted = intervals.sortedBy { it.startMillis }
        val out = mutableListOf<TimeInterval>()
        var current = sorted.first()
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            current = if (next.startMillis <= current.endMillis) {
                TimeInterval(current.startMillis, maxOf(current.endMillis, next.endMillis))
            } else {
                out += current
                next
            }
        }
        out += current
        return out
    }
}
