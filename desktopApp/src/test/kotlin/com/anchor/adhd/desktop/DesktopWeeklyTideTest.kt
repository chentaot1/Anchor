package com.anchor.adhd.desktop

import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DesktopWeeklyTideTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val zone = ZoneId.of("America/New_York")

    private fun at(text: String): ZonedDateTime = ZonedDateTime.parse(text).withZoneSameInstant(zone)

    private fun database() = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), File(folder.root, "anchor.db"))

    private suspend fun seed(
        db: AnchorDesktopDatabase,
        vararg sessions: Triple<ZonedDateTime, Int, Boolean>,
    ) {
        db.initialize()
        val backup =
            buildJsonObject {
                put(
                    "focus_sessions",
                    buildJsonArray {
                        sessions.forEach { (end, seconds, completed) ->
                            add(
                                buildJsonObject {
                                    put("taskTitle", "Saved work")
                                    put("actualSeconds", seconds)
                                    put("durationSeconds", seconds.coerceAtLeast(60))
                                    put("completed", completed)
                                    put("startedAtMillis", end.toInstant().toEpochMilli() - seconds * 1000L)
                                    put("endedAtMillis", end.toInstant().toEpochMilli())
                                },
                            )
                        }
                    },
                )
            }
        db.importDataJson(backup.toString(), true)
    }

    @Test
    fun emptyDatabaseReturnsSevenZeroDaysWithOnlyFinalEntryMarkedToday() =
        runBlocking {
            val db = database()
            db.initialize()
            // Wednesday, Oct 14, 2026 at 10:00 AM -> logical days Thu Oct 8 .. Wed Oct 14
            val now = at("2026-10-14T10:00:00-04:00")
            val days = db.getLast7LogicalDaysFocusMinutes(now.toInstant().toEpochMilli(), zone)

            assertEquals(7, days.size)
            assertEquals(listOf("T", "F", "S", "S", "M", "T", "W"), days.map { it.shortLabel })
            assertEquals(listOf(0, 0, 0, 0, 0, 0, 0), days.map { it.minutes })
            assertEquals(listOf(false, false, false, false, false, false, true), days.map { it.isToday })
        }

    @Test
    fun aggregatesMultipleSessionsPerLogicalDayAndExcludesOutOfWindowSessions() =
        runBlocking {
            val db = database()
            // Today logical = Wed Oct 14, 2026; 7-day window = Thu Oct 8 04:00 .. Wed Oct 14 15:00
            val now = at("2026-10-14T15:00:00-04:00")
            seed(
                db,
                // 8 days ago (Wed Oct 7, 03:59 on Oct 8) -> excluded
                Triple(at("2026-10-08T03:59:59-04:00"), 1500, true),
                // Day 0 (Thu Oct 8, 04:00 boundary) -> 20m
                Triple(at("2026-10-08T04:00:00-04:00"), 1200, true),
                // Day 2 (Sat Oct 10) -> 25m + 15m = 40m
                Triple(at("2026-10-10T09:30:00-04:00"), 1500, true),
                Triple(at("2026-10-10T18:45:00-04:00"), 900, true),
                // Day 5 (Tue Oct 13) -> ended at 02:30 AM on Oct 14 (still Tue Oct 13 logical day!) -> 30m
                Triple(at("2026-10-14T02:30:00-04:00"), 1800, true),
                // Day 6 (Wed Oct 14, today) -> 25m + 30s + 30s = 26m
                Triple(at("2026-10-14T09:00:00-04:00"), 1500, true),
                Triple(at("2026-10-14T10:00:00-04:00"), 30, false),
                Triple(at("2026-10-14T11:00:00-04:00"), 30, true),
                // Future session after nowMillis -> excluded
                Triple(at("2026-10-14T16:00:00-04:00"), 1800, true),
            )

            val days = db.getLast7LogicalDaysFocusMinutes(now.toInstant().toEpochMilli(), zone)

            assertEquals(7, days.size)
            assertEquals(listOf(20, 0, 40, 0, 0, 30, 26), days.map { it.minutes })
            assertEquals(listOf(false, false, false, false, false, false, true), days.map { it.isToday })
            assertTrue(days.last().isToday)
            assertFalse(days.first().isToday)
        }

    @Test
    fun beforeFourAmUsesPreviousCalendarDayAsTodayLogicalBucket() =
        runBlocking {
            val db = database()
            // Sunday Oct 11 at 02:30 AM -> logical today is Saturday Oct 10 (window Sun Oct 4 .. Sat Oct 10)
            val now = at("2026-10-11T02:30:00-04:00")
            seed(
                db,
                Triple(at("2026-10-10T22:00:00-04:00"), 900, true),
                Triple(at("2026-10-11T01:45:00-04:00"), 600, true),
            )

            val days = db.getLast7LogicalDaysFocusMinutes(now.toInstant().toEpochMilli(), zone)

            assertEquals(7, days.size)
            assertEquals(listOf("S", "M", "T", "W", "T", "F", "S"), days.map { it.shortLabel })
            assertEquals(listOf(0, 0, 0, 0, 0, 0, 25), days.map { it.minutes })
            assertEquals(listOf(false, false, false, false, false, false, true), days.map { it.isToday })
        }

    @Test
    fun weeklyFocusDaysStateFlowRefreshesOnRecordAndReset() =
        runBlocking {
            val db = database()
            db.initialize()
            val initialDays = db.weeklyFocusDays.value
            assertEquals(7, initialDays.size)
            assertEquals(0, initialDays.last().minutes)
            assertTrue(initialDays.last().isToday)

            db.recordFocusSession(durationMinutes = 25, taskTitle = "Deep work", now = LocalDateTime.now())
            val afterRecord = db.weeklyFocusDays.value
            assertEquals(25, afterRecord.last().minutes)
            assertEquals(1, db.weeklySessionCount.value)
            assertEquals(1, db.todaySessionCount.value)

            db.resetFocusHistoryAndStreak()
            val afterReset = db.weeklyFocusDays.value
            assertEquals(7, afterReset.size)
            assertEquals(0, afterReset.last().minutes)
            assertEquals(0, db.weeklySessionCount.value)
            assertEquals(0, db.todaySessionCount.value)
        }

    @Test
    fun springForwardDstUsesLocalFourAmBoundaryAndDefaultZoneSignatureWorks() =
        runBlocking {
            val db = database()
            // Sun Mar 8, 2026 springs forward at 2:00 AM -> 3:00 AM in America/New_York
            val beforeReset = at("2026-03-08T03:59:00-04:00")
            seed(
                db,
                Triple(at("2026-03-07T03:59:59-05:00"), 300, true), // logical Fri Mar 6 -> 5m
                Triple(at("2026-03-07T04:00:00-05:00"), 600, true), // logical Sat Mar 7 -> 10m
                Triple(at("2026-03-08T03:30:00-04:00"), 300, true), // logical Sat Mar 7 -> +5m = 15m
            )

            val days = db.getLast7LogicalDaysFocusMinutes(beforeReset.toInstant().toEpochMilli(), zone)
            assertEquals(listOf(0, 0, 0, 0, 0, 5, 15), days.map { it.minutes })
            assertTrue(days.last().isToday)

            // Verify 1-arg default-zone signature compiles and always returns 7 entries
            val defaultZoneDays = db.getLast7LogicalDaysFocusMinutes(System.currentTimeMillis())
            assertEquals(7, defaultZoneDays.size)
            assertEquals(1, defaultZoneDays.count { it.isToday })
            assertTrue(defaultZoneDays.last().isToday)
        }
}
