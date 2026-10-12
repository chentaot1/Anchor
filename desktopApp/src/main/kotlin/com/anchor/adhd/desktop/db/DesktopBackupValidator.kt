package com.anchor.adhd.desktop.db

import com.anchor.adhd.desktop.DesktopDurationLimits

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.util.UUID

data class DesktopBackupSummary(val sectionCounts: Map<String, Int>) {
    val totalRecords: Int get() = sectionCounts.values.sum()
}

internal object DesktopBackupValidator {
    val recordSections = linkedMapOf(
        "tasks" to "Tasks",
        "courses" to "Courses",
        "syllabus_items" to "Assignments",
        "syllabusItems" to "Assignments",
        "assignments" to "Assignments",
        "class_schedules" to "Class schedules",
        "classSchedules" to "Class schedules",
        "block_rules" to "Protection rules",
        "blockRules" to "Protection rules",
        "focus_sessions" to "Focus sessions",
        "focusSessions" to "Focus sessions",
        "fun_links" to "Fun links",
        "funLinks" to "Fun links",
    )
    private val statsSections = setOf("harbor_stats", "harborStats", "companionState")

    fun validate(root: JsonObject) {
        require(root.keys.any { it in recordSections || it in statsSections }) {
            "This file does not contain supported Anchor backup data."
        }
        root["version"]?.let { version ->
            require(version is JsonPrimitive && version.intOrNull in 1..2) { "Unsupported Anchor backup version." }
        }
        for ((canonical, alias) in listOf(
            "syllabus_items" to "syllabusItems", "class_schedules" to "classSchedules",
            "block_rules" to "blockRules", "focus_sessions" to "focusSessions",
            "fun_links" to "funLinks", "harbor_stats" to "harborStats",
        )) {
            require(canonical !in root || alias !in root) { "Backup contains both '$canonical' and '$alias'; use one section name." }
        }
        for ((key, _) in recordSections) {
            val value = root[key] ?: continue
            require(value is JsonArray) { "Backup section '$key' must be a list." }
            val focusSaveKeys = mutableSetOf<String>()
            value.forEachIndexed { index, item ->
                require(item is JsonObject) { "Record ${index + 1} in '$key' must be an object." }
                val requiredText = when (key) {
                    "tasks", "assignments" -> listOf("title")
                    "courses" -> listOf("code")
                    "syllabus_items", "syllabusItems" -> listOf("title", "courseCode")
                    "class_schedules", "classSchedules" -> listOf("courseCode")
                    "block_rules", "blockRules" -> listOf("target")
                    "fun_links", "funLinks" -> listOf("url")
                    else -> emptyList()
                }
                for (field in requiredText) {
                    val text = item[field] as? JsonPrimitive
                    require(text?.isString == true && text.contentOrNull?.isNotBlank() == true) {
                        "Record ${index + 1} in '$key' needs a non-empty '$field'."
                    }
                }
                when (key) {
                    "tasks" -> validateInt(item, "durationMinutes", DesktopDurationLimits.MIN_MINUTES..DesktopDurationLimits.MAX_MINUTES)
                    "focus_sessions", "focusSessions" -> {
                        item["saveKey"]?.takeIf { it != JsonNull }?.let { identity ->
                            val saveKey = (identity as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
                            require(saveKey != null && runCatching { UUID.fromString(saveKey).toString() == saveKey }.getOrDefault(false)) {
                                "Backup focus save identity is invalid."
                            }
                            require(focusSaveKeys.add(saveKey)) { "Backup repeats a focus save identity." }
                        }
                        validateInt(item, "durationSeconds", 1..86400)
                        validateInt(item, "actualSeconds", 0..Int.MAX_VALUE)
                        validateInt(item, "plannedMinutes", 1..1440)
                        validateInt(item, "actualMinutes", 0..1440)
                    }
                    "class_schedules", "classSchedules" -> {
                        validateInt(item, "dayOfWeek", 1..7, required = true)
                        validateInt(item, "startMinuteOfDay", 0..1439, required = true)
                        validateInt(item, "endMinuteOfDay", 1..1440, required = true)
                        val start = (item["startMinuteOfDay"] as JsonPrimitive).intOrNull!!
                        val end = (item["endMinuteOfDay"] as JsonPrimitive).intOrNull!!
                        require(end > start) { "Class schedules must end after they start." }
                    }
                }
            }
        }
        for (key in statsSections) {
            val value = root[key] ?: continue
            require(value is JsonObject || (key == "companionState" && value is JsonArray && value.all { it is JsonObject })) {
                "Backup section '$key' has an invalid format."
            }
            val stats = if (value is JsonObject) listOf(value) else (value as JsonArray).map { it as JsonObject }
            stats.forEach { item ->
                validateInt(item, "totalSessions", 0..Int.MAX_VALUE)
                validateInt(item, "totalMinutes", 0..Int.MAX_VALUE)
                validateInt(item, "streakDays", 0..Int.MAX_VALUE)
            }
        }
    }

    private fun validateInt(item: JsonObject, field: String, range: IntRange, required: Boolean = false) {
        val value = item[field]
        if (!required && (value == null || value == JsonNull)) return
        require(value is JsonPrimitive && value.intOrNull in range) { "Backup '$field' must be within ${range.first}–${range.last}." }
    }

    fun summarize(root: JsonObject): DesktopBackupSummary {
        val counts = linkedMapOf<String, Int>()
        for ((key, label) in recordSections) {
            (root[key] as? JsonArray)?.let { if (label !in counts) counts[label] = it.size }
        }
        if (root["harbor_stats"] is JsonObject || root["harborStats"] is JsonObject) counts["Harbor progress"] = 1
        return DesktopBackupSummary(counts)
    }
}
