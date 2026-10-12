package com.anchor.adhd.desktop

import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import com.anchor.adhd.desktop.db.DesktopBackupSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.sql.DriverManager

class DesktopBackupSnapshotTest {
    @get:Rule val folder = TemporaryFolder()
    private val file get() = File(folder.root, "anchor.db")
    private fun database() = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), file)
    private fun connection() = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")

    @Test fun aSaveBetweenSectionReadsCannotMixOldTasksWithNewHistory() = runBlocking {
        val db = database()
        db.initialize()
        db.importDataJson("""{"tasks":[{"title":"Before save"}],"focus_sessions":[],"harbor_stats":{"totalSessions":0,"totalMinutes":0,"streakDays":1}}""", true)
        val backup = connection().use { reader ->
            DesktopBackupSnapshot.read(reader) { section ->
                if (section == "tasks") {
                    connection().use { writer ->
                        writer.autoCommit = false
                        writer.createStatement().use {
                            it.executeUpdate("UPDATE tasks SET title = 'After save'")
                            it.executeUpdate("INSERT INTO focus_sessions (taskTitle,durationSeconds,actualSeconds,completed,startedAtMillis,endedAtMillis) VALUES ('After save',600,600,1,1000,601000)")
                            it.executeUpdate("UPDATE harbor_stats SET totalSessions = 1, totalMinutes = 10")
                        }
                        writer.commit()
                    }
                }
            }
        }
        val old = Json.parseToJsonElement(backup).jsonObject
        assertEquals("Before save", old.getValue("tasks").jsonArray.single().jsonObject.getValue("title").jsonPrimitive.content)
        assertTrue(old.getValue("focus_sessions").jsonArray.isEmpty())
        assertEquals("0", old.getValue("harbor_stats").jsonObject.getValue("totalSessions").jsonPrimitive.content)
        val latest = Json.parseToJsonElement(db.exportDataJson()).jsonObject
        assertEquals("After save", latest.getValue("tasks").jsonArray.single().jsonObject.getValue("title").jsonPrimitive.content)
        assertEquals(1, latest.getValue("focus_sessions").jsonArray.size)
        assertEquals("1", latest.getValue("harbor_stats").jsonObject.getValue("totalSessions").jsonPrimitive.content)
    }

    @Test fun fullExportRestoresMetadataDisabledLinksAndLongTimestamps() = runBlocking {
        val db = database()
        db.initialize()
        db.importDataJson("""{
          "tasks":[{"title":"Essay ⚓","notes":"Keep my notes","durationMinutes":40,"difficulty":"HARD","isNextAction":true,"createdAtMillis":1792000000123}],
          "courses":[{"code":"ENG201","name":"English","colorHex":"#123456","createdAtMillis":1792000000123}],
          "syllabus_items":[{"courseCode":"ENG201","title":"Draft","itemType":"PROJECT","dueDateText":"Friday","dueDateMillis":1793000000123,"prepSteps":["  Keep spacing  ","Outline"],"isCompleted":true,"createdAtMillis":1792000000123}],
          "class_schedules":[{"courseCode":"ENG201","courseName":"English","dayOfWeek":2,"startMinuteOfDay":600,"endMinuteOfDay":660}],
          "block_rules":[{"target":"example.com","ruleType":"WEB","enabled":false,"scheduleMode":"ALWAYS","lockUntilEpoch":1793000000123,"quotaGroup":"reading"}],
          "focus_sessions":[{"taskTitle":"Essay ⚓","durationSeconds":2400,"actualSeconds":600,"completed":false,"startedAtMillis":1792000000123,"endedAtMillis":1792000600123}],
          "fun_links":[{"name":"Later","url":"https://example.com/play","emoji":"🎲","enabled":false}],
          "harbor_stats":{"totalSessions":1,"totalMinutes":10,"streakDays":3,"last_focus_logical_date":"2026-10-11"}
        }""", true)
        val saved = Json.parseToJsonElement(db.exportDataJson()).jsonObject
        assertEquals("1792000000123", saved.getValue("tasks").jsonArray.single().jsonObject.getValue("createdAtMillis").jsonPrimitive.content)
        assertEquals("false", saved.getValue("fun_links").jsonArray.single().jsonObject.getValue("enabled").jsonPrimitive.content)
        assertEquals("  Keep spacing  ", saved.getValue("syllabus_items").jsonArray.single().jsonObject.getValue("prepSteps").jsonArray.first().jsonPrimitive.content)
        db.importDataJson("""{"tasks":[],"fun_links":[]}""", true)
        db.importDataJson(saved.toString(), true)
        val restored = Json.parseToJsonElement(db.exportDataJson()).jsonObject
        for (section in saved.keys - "exportedAtMillis") {
            // Imported records receive new local IDs; their content must survive the round trip.
            fun content(root: JsonObject) = root[section].let { value ->
                if (value is JsonArray) JsonArray(value.map { JsonObject(it.jsonObject - "id") }) else value
            }
            assertEquals("Section: $section", content(saved), content(restored))
        }
    }

    @Test fun failedSnapshotReleasesItsTransactionAndConnectionRemainsUsable() = runBlocking {
        database().initialize()
        connection().use { reader ->
            assertTrue(runCatching {
                DesktopBackupSnapshot.read(reader) { throw IllegalStateException("Interrupted export") }
            }.isFailure)
            assertTrue(reader.autoCommit)
            reader.createStatement().use { it.executeUpdate("UPDATE tasks SET notes = 'Still writable'") }
            assertTrue(DesktopBackupSnapshot.read(reader).contains("Still writable"))
        }
    }

    @Test fun failedProgressUpdateRollsBackTheSessionAndPreservesTotals() = runBlocking {
        val db = database()
        db.initialize()
        val prior = db.harborState.value
        val priorSessions = db.getFocusSessions()
        connection().use { it.createStatement().use { statement ->
            statement.execute("CREATE TRIGGER reject_progress BEFORE UPDATE ON harbor_stats BEGIN SELECT RAISE(ABORT, 'No progress write'); END")
        } }
        assertTrue(runCatching { db.recordFocusSession("Do not half-save", 600, 600, true) }.isFailure)
        val reopened = database()
        reopened.initialize()
        assertEquals(priorSessions, reopened.getFocusSessions())
        assertEquals(prior, reopened.harborState.value)
    }
}
