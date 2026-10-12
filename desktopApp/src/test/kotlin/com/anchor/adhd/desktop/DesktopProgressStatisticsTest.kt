package com.anchor.adhd.desktop

import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import com.anchor.adhd.desktop.db.DesktopStreak
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.sql.DriverManager
import java.time.LocalDate
import java.time.LocalDateTime

class DesktopProgressStatisticsTest {
    @get:Rule val folder = TemporaryFolder()
    private val file get() = File(folder.root, "anchor.db")
    private fun database() = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), file)

    @Test fun freshProfileAndPartialWorkHaveNoEarnedStreakOrExtraMinute() = runBlocking {
        val db = database()
        db.initialize()
        assertEquals(0, db.harborState.value.streakDays)
        db.recordFocusSession("Partial", 60, 30, false)
        assertEquals(0, db.harborState.value.streakDays)
        assertEquals(0, db.harborState.value.totalMinutes)
        db.recordFocusSession("Short completion", 60, 30, true)
        assertEquals(1, db.harborState.value.streakDays)
        assertEquals(1, db.harborState.value.totalMinutes)
        assertEquals(1, db.getTodayFocusMinutes())
    }

    @Test fun oldEmptySeedIsMigratedButExplicitImportedProgressSurvivesRestart() = runBlocking {
        val db = database()
        db.initialize()
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeUpdate("DELETE FROM meta_kv WHERE key = 'zero_streak_seed_v1';")
                stmt.executeUpdate("UPDATE harbor_stats SET streakDays = 1;")
            }
        }
        db.initialize()
        assertEquals(0, db.harborState.value.streakDays)
        db.importDataJson("""{"harbor_stats":{"streakDays":1}}""", true)
        val reopened = database()
        reopened.initialize()
        assertEquals(1, reopened.harborState.value.streakDays)
    }

    @Test fun fractionalMinutesSurviveHistoryImportAndPreserveExplicitAggregateProgress() = runBlocking {
        val db = database()
        db.initialize()
        db.recordFocusSession("Partial one", 60, 30, false)
        db.recordFocusSession("Partial two", 60, 30, false)
        val root = Json.parseToJsonElement(db.exportDataJson()) as JsonObject
        db.importDataJson(JsonObject(mapOf("focus_sessions" to root.getValue("focus_sessions"))).toString(), true)
        assertEquals(1, db.harborState.value.totalMinutes)
        db.recordFocusSession("Partial three", 60, 30, false)
        assertEquals(1, db.harborState.value.totalMinutes)
        db.importDataJson("""{"harbor_stats":{"totalSessions":3,"totalMinutes":100,"streakDays":0}}""", true)
        db.recordFocusSession("Partial four", 60, 30, false)
        assertEquals(101, db.harborState.value.totalMinutes)
    }

    @Test fun resetAndZeroStreakBackupRoundTripKeepProgressAtZero() = runBlocking {
        val db = database()
        db.initialize()
        db.recordFocusSession("Done", 600, 600, true)
        db.resetFocusHistoryAndStreak()
        assertEquals(0, db.harborState.value.streakDays)
        val exported = db.exportDataJson()
        db.importDataJson(exported, true)
        assertEquals(0, db.harborState.value.streakDays)
        db.recordFocusSession("Done again", 600, 600, true)
        db.resetAllData()
        assertEquals(0, db.harborState.value.streakDays)
    }

    @Test fun historyOnlyRestoreDerivesDistinctConsecutiveLogicalDays() = runBlocking {
        val db = database()
        db.initialize()
        val dates = listOf(
            LocalDateTime.of(2026, 10, 5, 12, 0), LocalDateTime.of(2026, 10, 6, 12, 0),
            LocalDateTime.of(2026, 10, 7, 12, 0), LocalDateTime.of(2026, 10, 8, 3, 30),
            LocalDateTime.of(2026, 10, 1, 12, 0),
        )
        dates.forEach { db.recordFocusSession("Done", 600, 600, true, it) }
        val root = Json.parseToJsonElement(db.exportDataJson()) as JsonObject
        val history = JsonObject(mapOf("focus_sessions" to root.getValue("focus_sessions"))).toString()
        db.recordFocusSession("Newer work to replace", 600, 600, true, LocalDateTime.of(2026, 10, 20, 12, 0))
        db.importDataJson(history, true)
        assertEquals(3, db.harborState.value.streakDays)
        assertEquals("2026-10-07", db.harborState.value.lastFocusLogicalDate)
        assertEquals(5, db.harborState.value.totalSessions)
        assertEquals(50, db.harborState.value.totalMinutes)
    }

    @Test fun emptyOrIncompleteHistoryCannotRetainAReplacedStreak() = runBlocking {
        val db = database()
        db.initialize()
        db.recordFocusSession("Done", 600, 600, true)
        db.importDataJson("""{"focus_sessions":[{"taskTitle":"Partial","actualSeconds":300,"completed":false}]}""", true)
        assertEquals(0, db.harborState.value.streakDays)
        assertEquals("", db.harborState.value.lastFocusLogicalDate)
        db.importDataJson("""{"focus_sessions":[]}""", true)
        assertEquals(0, db.harborState.value.streakDays)
        assertEquals(0, db.harborState.value.totalMinutes)
    }

    @Test fun currentStreakExpiresAtTheFourAmBoundaryAfterAMissedDay() {
        val before = AnchorDesktopDatabase.getLogicalDate(LocalDateTime.of(2026, 10, 8, 3, 59))
        val after = AnchorDesktopDatabase.getLogicalDate(LocalDateTime.of(2026, 10, 8, 4, 0))
        assertEquals(4, DesktopStreak.currentDays(4, "2026-10-06", before))
        assertEquals(0, DesktopStreak.currentDays(4, "2026-10-06", after))
        assertEquals(4, DesktopStreak.currentDays(4, "2026-10-08", after))
    }

    @Test fun futureOrMalformedDatesDoNotShowAnActiveStreakAndUndatedLegacyProgressIsRetained() {
        val today = LocalDate.of(2026, 10, 8)
        assertEquals(0, DesktopStreak.currentDays(4, "2026-10-09", today))
        assertEquals(0, DesktopStreak.currentDays(4, "broken", today))
        assertEquals(7, DesktopStreak.currentDays(7, "", today))
    }
}
