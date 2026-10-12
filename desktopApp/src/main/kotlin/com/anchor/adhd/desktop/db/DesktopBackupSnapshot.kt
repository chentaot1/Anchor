package com.anchor.adhd.desktop.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.sql.Connection

/** Reads every exported section from the same SQLite transaction, without changing UI state. */
internal object DesktopBackupSnapshot {
    private val json = Json { prettyPrint = true }
    private val sections = linkedMapOf(
        "tasks" to "SELECT * FROM tasks ORDER BY isCompleted ASC, isNextAction DESC, sortOrder ASC, id DESC",
        "courses" to "SELECT * FROM courses ORDER BY code ASC",
        "syllabus_items" to "SELECT * FROM syllabus_items ORDER BY isCompleted ASC, dueDateMillis ASC, id ASC",
        "class_schedules" to "SELECT * FROM class_schedules ORDER BY dayOfWeek ASC, startMinuteOfDay ASC",
        "block_rules" to "SELECT * FROM block_rules ORDER BY ruleType ASC, id ASC",
        "focus_sessions" to "SELECT * FROM focus_sessions ORDER BY startedAtMillis ASC, id ASC",
        "fun_links" to "SELECT * FROM fun_links ORDER BY sortOrder ASC, id ASC",
        "harbor_stats" to "SELECT totalSessions, totalMinutes, streakDays, last_focus_logical_date FROM harbor_stats WHERE id = 1",
    )
    private val booleanColumns = setOf("isCompleted", "isNextAction", "enabled", "completed")

    fun read(connection: Connection, afterSectionRead: (String) -> Unit = {}): String {
        check(connection.autoCommit) { "Backup needs its own database connection." }
        connection.autoCommit = false
        try {
            val root = buildJsonObject {
                put("version", 1)
                put("exportedAtMillis", System.currentTimeMillis())
                for ((name, query) in sections) {
                    val records = readRows(connection, query)
                    if (name == "harbor_stats") {
                        check(records.size == 1) { "Harbor progress is missing; backup was not saved." }
                        put(name, records.single())
                    } else {
                        put(name, records)
                    }
                    afterSectionRead(name)
                }
            }
            return json.encodeToString(JsonObject.serializer(), root)
        } finally {
            // A read transaction has nothing to commit. Release the snapshot even if serialization fails.
            try {
                connection.rollback()
            } finally {
                connection.autoCommit = true
            }
        }
    }

    private fun readRows(connection: Connection, query: String): JsonArray =
        connection.createStatement().use { statement ->
            statement.executeQuery(query).use { rows ->
                val columns = (1..rows.metaData.columnCount).map { rows.metaData.getColumnLabel(it) }
                buildJsonArray {
                    while (rows.next()) add(buildJsonObject {
                        for (column in columns) {
                            val value = rows.getObject(column) ?: continue
                            when {
                                column in booleanColumns -> put(column, (value as Number).toInt() == 1)
                                column == "prepSteps" -> put(column, buildJsonArray {
                                    value.toString().split("|||")
                                        .filter(String::isNotBlank).forEach { add(JsonPrimitive(it)) }
                                })
                                value is Number -> put(column, JsonPrimitive(value))
                                else -> put(column, value.toString())
                            }
                        }
                    })
                }
            }
        }
}
