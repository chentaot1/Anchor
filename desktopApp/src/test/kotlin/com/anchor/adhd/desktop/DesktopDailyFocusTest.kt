package com.anchor.adhd.desktop

import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

class DesktopDailyFocusTest {
    @get:Rule val folder = TemporaryFolder()
    private val zone = ZoneId.of("America/New_York")
    private fun at(text: String) = ZonedDateTime.parse(text).withZoneSameInstant(zone)
    private fun database() = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), File(folder.root, "anchor.db"))
    private suspend fun seed(db: AnchorDesktopDatabase, vararg sessions: Pair<ZonedDateTime, Int>) {
        db.initialize()
        val backup = buildJsonObject {
            put("focus_sessions", buildJsonArray {
                sessions.forEach { (end, seconds) -> add(buildJsonObject {
                    put("taskTitle", "Saved work")
                    put("actualSeconds", seconds)
                    put("durationSeconds", seconds.coerceAtLeast(60))
                    put("completed", true)
                    put("startedAtMillis", end.toInstant().toEpochMilli() - seconds * 1000L)
                    put("endedAtMillis", end.toInstant().toEpochMilli())
                }) }
            })
        }
        db.importDataJson(backup.toString(), true)
    }

    @Test fun sessionCrossingResetIsCreditedToItsCompletionDay() = runBlocking {
        val db = database()
        seed(db, at("2026-10-11T04:05:00-04:00") to 1500)
        assertEquals(25, db.getTodayFocusMinutes(at("2026-10-11T04:10:00-04:00")))
        assertEquals(0, db.getTodayFocusMinutes(at("2026-10-11T03:59:00-04:00")))
    }

    @Test fun fourAmBoundaryIncludesItsCompletionAndClearsThePreviousLogicalDay() = runBlocking {
        val db = database()
        seed(db, at("2026-10-11T03:59:59-04:00") to 600, at("2026-10-11T04:00:00-04:00") to 300)
        assertEquals(10, db.getTodayFocusMinutes(at("2026-10-11T03:59:59-04:00")))
        assertEquals(5, db.getTodayFocusMinutes(at("2026-10-11T04:00:00-04:00")))
        assertEquals(0, db.getTodayFocusMinutes(at("2026-10-12T04:00:00-04:00")))
    }

    @Test fun midnightKeepsTheSameLogicalDayAndFutureWorkIsExcluded() = runBlocking {
        val db = database()
        seed(db, at("2026-10-10T23:55:00-04:00") to 600, at("2026-10-11T02:00:00-04:00") to 300,
            at("2026-10-11T03:00:00-04:00") to 1200, at("2026-10-12T10:00:00-04:00") to 1500)
        assertEquals(15, db.getTodayFocusMinutes(at("2026-10-11T02:30:00-04:00")))
    }

    @Test fun springForwardUsesTheLocalFourAmBoundaryInsteadOfTwentyFourHoursAgo() = runBlocking {
        val db = database()
        seed(db, at("2026-03-07T03:59:59-05:00") to 300, at("2026-03-07T04:00:00-05:00") to 600,
            at("2026-03-08T03:30:00-04:00") to 300)
        assertEquals(15, db.getTodayFocusMinutes(at("2026-03-08T03:59:00-04:00")))
        assertEquals(0, db.getTodayFocusMinutes(at("2026-03-08T04:00:00-04:00")))
    }

    @Test fun fallBackIncludesBothOccurrencesOfTheRepeatedHour() = runBlocking {
        val db = database()
        seed(db, at("2026-10-31T04:00:00-04:00") to 600, at("2026-11-01T01:30:00-04:00") to 300,
            at("2026-11-01T01:30:00-05:00") to 300)
        assertEquals(15, db.getTodayFocusMinutes(at("2026-11-01T01:45:00-04:00")))
        assertEquals(20, db.getTodayFocusMinutes(at("2026-11-01T01:45:00-05:00")))
        assertEquals(0, db.getTodayFocusMinutes(at("2026-11-01T04:00:00-05:00")))
    }

    @Test fun fractionalSecondsCombineOnlyWithinTheSelectedLogicalDay() = runBlocking {
        val db = database()
        seed(db, at("2026-10-11T03:59:00-04:00") to 30, at("2026-10-11T04:01:00-04:00") to 30,
            at("2026-10-11T04:02:00-04:00") to 30)
        assertEquals(1, db.getTodayFocusMinutes(at("2026-10-11T04:03:00-04:00")))
    }
}
