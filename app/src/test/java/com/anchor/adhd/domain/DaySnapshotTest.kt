package com.anchor.adhd.domain

import com.anchor.adhd.data.model.CheckInTag
import com.anchor.adhd.data.model.EnergyLevel
import com.anchor.adhd.data.model.InboxState
import com.anchor.adhd.data.model.TaskDifficulty
import com.anchor.adhd.data.model.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DaySnapshotTest {
    @Test
    fun compactPrompt_includesCountsAndNow() {
        val snap = buildDaySnapshot(
            scheduled = emptyList(),
            inboxToday = listOf(TaskEntity(title = "Open doc", inboxState = InboxState.TODAY)),
            energy = EnergyLevel.OK,
            routines = emptyList(),
            checkInTags = emptySet(),
            replanCount = 1,
            dueSoon = emptyList(),
            occupancy = emptyList(),
            nowMillis = 0L,
            dayStartMillis = 0L,
            shutdownMillis = 8 * 60 * 60_000L,
            blockedPackages = listOf("com.google.android.youtube"),
        )
        val prompt = snap.compactPrompt()
        assertTrue(prompt.contains("energy=OK"))
        assertTrue(prompt.contains("now=Open doc"))
        assertTrue(prompt.contains("inbox=1"))
        assertTrue(prompt.contains("replan=1"))
        assertTrue(prompt.contains("blocked=YouTube"))
        assertTrue(snap.holeCount > 0)
    }

    @Test
    fun lowEnergy_hidesDeepFromPick() {
        val inbox = listOf(
            TaskEntity(title = "Deep work", difficulty = TaskDifficulty.DEEP),
            TaskEntity(title = "Quick win", difficulty = TaskDifficulty.LIGHT),
        )
        val snap = buildDaySnapshot(
            scheduled = emptyList(),
            inboxToday = inbox,
            energy = EnergyLevel.LOW,
            routines = emptyList(),
            checkInTags = setOf(CheckInTag.OVERWHELMED),
            replanCount = 0,
            dueSoon = emptyList(),
            occupancy = emptyList(),
            nowMillis = 0L,
            dayStartMillis = 0L,
            shutdownMillis = 8 * 60 * 60_000L,
            blockedPackages = emptyList(),
        )
        assertEquals("Quick win", snap.nowTitle)
        assertFalse(snap.nextTitles.contains("Deep work"))
    }
}
