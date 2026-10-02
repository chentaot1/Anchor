package com.anchor.adhd.data

import android.content.Context
import com.anchor.adhd.data.db.AnchorDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

class BackupManager(
    private val context: Context,
    private val database: AnchorDatabase
) {
    suspend fun exportToCache(): File = withContext(Dispatchers.IO) {
        val root = JSONObject()
            .put("version", 1)
            .put("exportedAt", Instant.now().toString())
            .put("tasks", JSONArray(database.taskDao().getAll().map { it.toJson() }))
            .put("assignments", JSONArray(database.assignmentDao().getAll().map { it.toJson() }))
            .put("calendar", JSONArray(database.calendarEventDao().getAll().map { it.toJson() }))
        val file = File(context.cacheDir, "anchor-backup-${Instant.now().epochSecond}.json")
        file.writeText(root.toString(2))
        file
    }

    private fun com.anchor.adhd.data.model.TaskEntity.toJson() = JSONObject()
        .put("id", id).put("title", title).put("notes", notes)
        .put("inboxState", inboxState.name).put("scheduledStartMillis", scheduledStartMillis)
        .put("durationMinutes", durationMinutes).put("isCompleted", isCompleted)

    private fun com.anchor.adhd.data.model.AssignmentEntity.toJson() = JSONObject()
        .put("id", id).put("title", title).put("course", course)
        .put("dueAtMillis", dueAtMillis).put("isCompleted", isCompleted)

    private fun com.anchor.adhd.data.model.CalendarEventEntity.toJson() = JSONObject()
        .put("id", id).put("title", title)
        .put("startMillis", startMillis).put("endMillis", endMillis)
}
