package com.anchor.adhd.data

import android.content.Context
import androidx.room.withTransaction
import com.anchor.adhd.data.db.AnchorDatabase
import com.anchor.adhd.data.model.AssignmentEntity
import com.anchor.adhd.data.model.BlockRuleEntity
import com.anchor.adhd.data.model.BlockRuleType
import com.anchor.adhd.data.model.CalendarEventEntity
import com.anchor.adhd.data.model.CheckInEntity
import com.anchor.adhd.data.model.CompanionStateEntity
import com.anchor.adhd.data.model.EnergyLevel
import com.anchor.adhd.data.model.FocusEndTag
import com.anchor.adhd.data.model.FocusGardenEntity
import com.anchor.adhd.data.model.FocusSessionEntity
import com.anchor.adhd.data.model.FunLinkEntity
import com.anchor.adhd.data.model.HabitAutoSource
import com.anchor.adhd.data.model.HabitCompletionEntity
import com.anchor.adhd.data.model.HabitEntity
import com.anchor.adhd.data.model.HabitScheduleType
import com.anchor.adhd.data.model.InboxState
import com.anchor.adhd.data.model.ReplanItemEntity
import com.anchor.adhd.data.model.RoutineEntity
import com.anchor.adhd.data.model.RoutineStepEntity
import com.anchor.adhd.data.model.TaskDifficulty
import com.anchor.adhd.data.model.TaskEntity
import com.anchor.adhd.data.model.TemptationBundleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

data class BackupImportSummary(
    val version: Int,
    val tasksImported: Int = 0,
    val assignmentsImported: Int = 0,
    val calendarEventsImported: Int = 0,
    val routinesImported: Int = 0,
    val routineStepsImported: Int = 0,
    val habitsImported: Int = 0,
    val habitCompletionsImported: Int = 0,
    val focusSessionsImported: Int = 0,
    val focusGardenImported: Int = 0,
    val companionRestored: Boolean = false,
    val checkInsImported: Int = 0,
    val blockRulesImported: Int = 0,
    val temptationBundlesImported: Int = 0,
    val replanItemsImported: Int = 0,
    val funLinksImported: Int = 0
) {
    val sessionsImported: Int get() = focusSessionsImported
    val focusGardensImported: Int get() = focusGardenImported

    val totalItems: Int
        get() = tasksImported +
            assignmentsImported +
            calendarEventsImported +
            routinesImported +
            routineStepsImported +
            habitsImported +
            habitCompletionsImported +
            focusSessionsImported +
            focusGardenImported +
            (if (companionRestored) 1 else 0) +
            checkInsImported +
            blockRulesImported +
            temptationBundlesImported +
            replanItemsImported +
            funLinksImported

    fun formatMessage(): String {
        val parts = mutableListOf<String>()
        if (tasksImported > 0) parts += "$tasksImported tasks"
        if (assignmentsImported > 0) parts += "$assignmentsImported assignments"
        if (routinesImported > 0) parts += "$routinesImported routines"
        if (habitsImported > 0) parts += "$habitsImported habits"
        if (focusSessionsImported > 0) parts += "$focusSessionsImported focus sessions"
        return if (parts.isEmpty()) {
            "Backup restored ($totalItems items)"
        } else {
            "Restored ${parts.joinToString(", ")} ($totalItems total items)"
        }
    }
}

class BackupManager(
    private val context: Context,
    private val database: AnchorDatabase
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }

    suspend fun exportJsonString(): String = withContext(Dispatchers.IO) {
        val companion = database.companionDao().getState()
        val root = JSONObject()
            .put("version", CURRENT_SCHEMA_VERSION)
            .put("exportedAt", Instant.now().toString())
            .put("tasks", JSONArray(database.taskDao().getAll().map { it.toJson() }))
            .put("assignments", JSONArray(database.assignmentDao().getAll().map { it.toJson() }))
            .put("calendar", JSONArray(database.calendarEventDao().getAll().map { it.toJson() }))
            .put("routines", JSONArray(database.routineDao().getAll().map { it.toJson() }))
            .put("routineSteps", JSONArray(database.routineDao().getAllSteps().map { it.toJson() }))
            .put("habits", JSONArray(database.habitDao().getAll().map { it.toJson() }))
            .put("habitCompletions", JSONArray(database.habitDao().getAllCompletions().map { it.toJson() }))
            .put("focusSessions", JSONArray(database.focusSessionDao().getAll().map { it.toJson() }))
            .put("focusGarden", JSONArray(database.companionDao().getAllGarden().map { it.toJson() }))
            .putNullable("companionState", companion?.toJson())
            .put("checkIns", JSONArray(database.checkInDao().getAll().map { it.toJson() }))
            .put("blockRules", JSONArray(database.blockRuleDao().getAll().map { it.toJson() }))
            .put("temptationBundles", JSONArray(database.temptationBundleDao().getAll().map { it.toJson() }))
            .put("replanItems", JSONArray(database.replanDao().getAll().map { it.toJson() }))
            .put("funLinks", JSONArray(database.funLinkDao().getAll().map { it.toJson() }))
        root.toString(2)
    }

    suspend fun exportToCache(): File = withContext(Dispatchers.IO) {
        val json = exportJsonString()
        val file = File(context.cacheDir, "anchor-backup-${Instant.now().epochSecond}.json")
        file.writeText(json)
        file
    }

    suspend fun importFromJson(
        jsonString: String,
        replaceExisting: Boolean = false
    ): BackupImportSummary = withContext(Dispatchers.IO) {
        val root = JSONObject(jsonString)
        val version = root.optInt("version", 1)

        val coursesById = buildMap<Long, String> {
            val coursesArr = root.optJSONArray("courses")
            if (coursesArr != null) {
                for (i in 0 until coursesArr.length()) {
                    val c = coursesArr.optJSONObject(i) ?: continue
                    val id = c.optLong("id", -1L)
                    val code = c.optString("code", "").ifBlank { c.optString("name", "") }
                    if (id >= 0L && code.isNotBlank()) {
                        put(id, code)
                    }
                }
            }
        }

        val tasks = root.optJSONArray("tasks").mapObjects { it.toTaskEntity() }
        val taskIdsByTitle = tasks.associate { it.title to it.id }
        val explicitAssignments = root.optJSONArray("assignments").mapObjects { it.toAssignmentEntity(coursesById) }
        val syllabusAssignments = (root.optJSONArray("syllabus_items") ?: root.optJSONArray("syllabusItems"))
            .mapObjects { it.toSyllabusAssignmentEntity(coursesById) }
        val assignments = explicitAssignments + syllabusAssignments
        val calendarArray = root.optJSONArray("calendar")
            ?: root.optJSONArray("calendarEvents")
            ?: root.optJSONArray("calendar_events")
        val calendarEvents = calendarArray.mapObjects { it.toCalendarEventEntity() }
        val routines = root.optJSONArray("routines").mapObjects { it.toRoutineEntity() }
        val routineStepsArray = root.optJSONArray("routineSteps") ?: root.optJSONArray("routine_steps")
        val routineSteps = routineStepsArray.mapObjects { it.toRoutineStepEntity() }
        val habits = root.optJSONArray("habits").mapObjects { it.toHabitEntity() }
        val habitCompletionsArray = root.optJSONArray("habitCompletions") ?: root.optJSONArray("habit_completions")
        val habitCompletions = habitCompletionsArray.mapObjects { it.toHabitCompletionEntity() }
        val focusSessions = (
            root.optJSONArray("focusSessions").mapObjects { it.toFocusSessionEntity() } +
                root.optJSONArray("sessions").mapObjects { it.toFocusSessionEntity() } +
                root.optJSONArray("focus_sessions").mapObjects { it.toFocusSessionEntity(taskIdsByTitle) }
            )
        val focusGardenArray = root.optJSONArray("focusGarden")
            ?: root.optJSONArray("focusGardens")
            ?: root.optJSONArray("focus_garden")
        val focusGarden = focusGardenArray.mapObjects { it.toFocusGardenEntity() }
        val explicitCompanionState = when {
            !root.has("companionState") || root.isNull("companionState") -> null
            root.optJSONObject("companionState") != null -> root.getJSONObject("companionState").toCompanionStateEntity()
            root.optJSONArray("companionState") != null -> {
                val arr = root.getJSONArray("companionState")
                if (arr.length() > 0) arr.getJSONObject(0).toCompanionStateEntity() else null
            }
            else -> null
        }
        val companionState = explicitCompanionState ?: root.extractHarborStatsCompanion()
        val checkInsArray = root.optJSONArray("checkIns") ?: root.optJSONArray("check_ins")
        val checkIns = checkInsArray.mapObjects { it.toCheckInEntity() }
        val blockRulesArray = root.optJSONArray("blockRules") ?: root.optJSONArray("block_rules")
        val blockRules = blockRulesArray.mapObjects { it.toBlockRuleEntity() }
        val temptationBundlesArray = root.optJSONArray("temptationBundles") ?: root.optJSONArray("temptation_bundles")
        val temptationBundles = temptationBundlesArray.mapObjects { it.toTemptationBundleEntity() }
        val replanItemsArray = root.optJSONArray("replanItems") ?: root.optJSONArray("replan_items")
        val replanItems = replanItemsArray.mapObjects { it.toReplanItemEntity() }
        val funLinks = (
            root.optJSONArray("funLinks").mapObjects { it.toFunLinkEntity() } +
                root.optJSONArray("fun_links").mapObjects { it.toFunLinkEntity() }
            )

        database.withTransaction {
            if (replaceExisting) {
                database.replanDao().deleteAll()
                database.taskDao().deleteAll()
                database.assignmentDao().deleteAll()
                database.calendarEventDao().deleteAll()
                database.routineDao().deleteAllSteps()
                database.routineDao().deleteAllRoutines()
                database.habitDao().deleteAllCompletions()
                database.habitDao().deleteAll()
                database.companionDao().deleteAllGarden()
                database.companionDao().deleteAllCompanion()
                database.focusSessionDao().clear()
                database.checkInDao().deleteAll()
                database.blockRuleDao().clearAll()
                database.temptationBundleDao().deleteAll()
                database.funLinkDao().deleteAll()
            }

            if (tasks.isNotEmpty()) database.taskDao().insertAll(tasks)
            if (assignments.isNotEmpty()) database.assignmentDao().insertAll(assignments)
            if (calendarEvents.isNotEmpty()) database.calendarEventDao().upsertAll(calendarEvents)
            if (routines.isNotEmpty()) database.routineDao().insertAllRoutines(routines)
            if (routineSteps.isNotEmpty()) database.routineDao().insertSteps(routineSteps)
            if (habits.isNotEmpty()) database.habitDao().insertAll(habits)
            if (habitCompletions.isNotEmpty()) database.habitDao().insertAllCompletions(habitCompletions)
            if (focusSessions.isNotEmpty()) database.focusSessionDao().insertAll(focusSessions)
            if (focusGarden.isNotEmpty()) database.companionDao().insertAllGarden(focusGarden)
            if (companionState != null) database.companionDao().upsert(companionState)
            if (checkIns.isNotEmpty()) database.checkInDao().insertAll(checkIns)
            if (blockRules.isNotEmpty()) database.blockRuleDao().insertAll(blockRules)
            if (temptationBundles.isNotEmpty()) database.temptationBundleDao().insertAll(temptationBundles)
            if (replanItems.isNotEmpty()) database.replanDao().insertAll(replanItems)
            if (funLinks.isNotEmpty()) database.funLinkDao().insertAll(funLinks)
        }

        BackupImportSummary(
            version = version,
            tasksImported = tasks.size,
            assignmentsImported = assignments.size,
            calendarEventsImported = calendarEvents.size,
            routinesImported = routines.size,
            routineStepsImported = routineSteps.size,
            habitsImported = habits.size,
            habitCompletionsImported = habitCompletions.size,
            focusSessionsImported = focusSessions.size,
            focusGardenImported = focusGarden.size,
            companionRestored = companionState != null,
            checkInsImported = checkIns.size,
            blockRulesImported = blockRules.size,
            temptationBundlesImported = temptationBundles.size,
            replanItemsImported = replanItems.size,
            funLinksImported = funLinks.size
        )
    }

    private fun TaskEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("notes", notes)
        .put("inboxState", inboxState.name)
        .putNullable("scheduledStartMillis", scheduledStartMillis)
        .put("durationMinutes", durationMinutes)
        .put("difficulty", difficulty.name)
        .put("isNextAction", isNextAction)
        .put("isCompleted", isCompleted)
        .putNullable("parentTaskId", parentTaskId)
        .putNullable("routineId", routineId)
        .put("aiGenerated", aiGenerated)
        .putNullable("estimatedMinutes", estimatedMinutes)
        .putNullable("actualMinutes", actualMinutes)
        .putNullable("dueAtMillis", dueAtMillis)
        .putNullable("ifThen", ifThen)
        .put("createdAtMillis", createdAtMillis)
        .putNullable("completedAtMillis", completedAtMillis)
        .put("sortOrder", sortOrder)

    private fun JSONObject.toTaskEntity(): TaskEntity {
        val resolvedInboxState = when {
            has("inboxState") && !isNull("inboxState") -> optEnum("inboxState", InboxState.TODAY)
            optBoolean("isInbox", false) -> InboxState.TODAY
            else -> InboxState.TODAY
        }
        val resolvedDifficulty = when (optStringOrNull("difficulty")?.trim()?.uppercase()) {
            "HARD", "DEEP" -> TaskDifficulty.DEEP
            "EASY", "LIGHT" -> TaskDifficulty.LIGHT
            "MEDIUM" -> TaskDifficulty.MEDIUM
            else -> TaskDifficulty.MEDIUM
        }
        val estMinutes = optIntOrNull("estimatedMinutes")
        val durMinutes = optIntOrNull("durationMinutes") ?: estMinutes ?: 20
        return TaskEntity(
            id = optLong("id", 0L),
            title = optString("title", ""),
            notes = optString("notes", ""),
            inboxState = resolvedInboxState,
            scheduledStartMillis = optLongOrNull("scheduledStartMillis") ?: optLongOrNull("scheduledBlockStartMillis"),
            durationMinutes = durMinutes,
            difficulty = resolvedDifficulty,
            isNextAction = optBoolean("isNextAction", false),
            isCompleted = optBoolean("isCompleted", false),
            parentTaskId = optLongOrNull("parentTaskId"),
            routineId = optLongOrNull("routineId"),
            aiGenerated = optBoolean("aiGenerated", false),
            estimatedMinutes = estMinutes,
            actualMinutes = optIntOrNull("actualMinutes"),
            dueAtMillis = optLongOrNull("dueAtMillis") ?: optLongOrNull("dueDateMillis"),
            ifThen = optStringOrNull("ifThen"),
            createdAtMillis = if (has("createdAtMillis") && !isNull("createdAtMillis")) optLong("createdAtMillis") else System.currentTimeMillis(),
            completedAtMillis = optLongOrNull("completedAtMillis"),
            sortOrder = optInt("sortOrder", 0)
        )
    }

    private fun AssignmentEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("course", course)
        .put("dueAtMillis", dueAtMillis)
        .put("notes", notes)
        .put("isCompleted", isCompleted)

    private fun JSONObject.toAssignmentEntity(coursesById: Map<Long, String> = emptyMap()): AssignmentEntity = AssignmentEntity(
        id = optLong("id", 0L),
        title = optString("title", ""),
        course = optString("course", "").ifBlank {
            optString("courseCode", "").ifBlank {
                coursesById[optLong("courseId", -1L)].orEmpty()
            }
        },
        dueAtMillis = optLongOrNull("dueAtMillis") ?: optLong("dueDateMillis", 0L),
        notes = optString("notes", ""),
        isCompleted = optBoolean("isCompleted", false)
    )

    private fun JSONObject.toSyllabusAssignmentEntity(coursesById: Map<Long, String>): AssignmentEntity {
        val rawNotes = optString("notes", "").trim()
        val prepArr = optJSONArray("prepSteps")
        val prepList = buildList {
            if (prepArr != null) {
                for (i in 0 until prepArr.length()) {
                    val step = prepArr.optString(i, "").trim()
                    if (step.isNotEmpty()) add(step)
                }
            }
        }
        val combinedNotes = when {
            rawNotes.isNotEmpty() && prepList.isNotEmpty() -> "$rawNotes\n" + prepList.joinToString("\n")
            prepList.isNotEmpty() -> prepList.joinToString("\n")
            else -> rawNotes
        }
        return AssignmentEntity(
            id = optLong("id", 0L),
            title = optString("title", ""),
            course = optString("courseCode", "").ifBlank {
                optString("course", "").ifBlank {
                    coursesById[optLong("courseId", -1L)].orEmpty()
                }
            },
            dueAtMillis = optLongOrNull("dueDateMillis") ?: optLong("dueAtMillis", 0L),
            notes = combinedNotes,
            isCompleted = optBoolean("isCompleted", false)
        )
    }

    private fun CalendarEventEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("startMillis", startMillis)
        .put("endMillis", endMillis)
        .put("source", source)
        .put("isAllDay", isAllDay)

    private fun JSONObject.toCalendarEventEntity(): CalendarEventEntity = CalendarEventEntity(
        id = optString("id", ""),
        title = optString("title", ""),
        startMillis = optLong("startMillis", 0L),
        endMillis = optLong("endMillis", 0L),
        source = if (has("source") && !isNull("source")) optString("source", "google") else "google",
        isAllDay = optBoolean("isAllDay", false)
    )

    private fun RoutineEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("cue", cue)
        .putNullable("ifThen", ifThen)
        .put("isEnabled", isEnabled)

    private fun JSONObject.toRoutineEntity(): RoutineEntity = RoutineEntity(
        id = optLong("id", 0L),
        name = optString("name", ""),
        cue = optString("cue", ""),
        ifThen = optStringOrNull("ifThen"),
        isEnabled = optBoolean("isEnabled", true)
    )

    private fun RoutineStepEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("routineId", routineId)
        .put("title", title)
        .put("durationMinutes", durationMinutes)
        .put("sortOrder", sortOrder)

    private fun JSONObject.toRoutineStepEntity(): RoutineStepEntity = RoutineStepEntity(
        id = optLong("id", 0L),
        routineId = optLong("routineId", 0L),
        title = optString("title", ""),
        durationMinutes = optInt("durationMinutes", 5),
        sortOrder = optInt("sortOrder", 0)
    )

    private fun HabitEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("scheduleType", scheduleType.name)
        .put("scheduleDays", scheduleDays)
        .put("targetPerWeek", targetPerWeek)
        .put("gracePerWeek", gracePerWeek)
        .put("autoSource", autoSource.name)
        .put("autoThreshold", autoThreshold)
        .put("sortOrder", sortOrder)
        .put("isArchived", isArchived)
        .put("createdAtMillis", createdAtMillis)

    private fun JSONObject.toHabitEntity(): HabitEntity = HabitEntity(
        id = optLong("id", 0L),
        name = optString("name", ""),
        scheduleType = optEnum("scheduleType", HabitScheduleType.DAILY),
        scheduleDays = optString("scheduleDays", ""),
        targetPerWeek = optInt("targetPerWeek", 7),
        gracePerWeek = optInt("gracePerWeek", 1),
        autoSource = optEnum("autoSource", HabitAutoSource.NONE),
        autoThreshold = optInt("autoThreshold", 0),
        sortOrder = optInt("sortOrder", 0),
        isArchived = optBoolean("isArchived", false),
        createdAtMillis = if (has("createdAtMillis") && !isNull("createdAtMillis")) optLong("createdAtMillis") else System.currentTimeMillis()
    )

    private fun HabitCompletionEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("habitId", habitId)
        .put("dayMillis", dayMillis)
        .put("completed", completed)
        .put("autoCompleted", autoCompleted)

    private fun JSONObject.toHabitCompletionEntity(): HabitCompletionEntity = HabitCompletionEntity(
        id = optLong("id", 0L),
        habitId = optLong("habitId", 0L),
        dayMillis = optLong("dayMillis", 0L),
        completed = optBoolean("completed", true),
        autoCompleted = optBoolean("autoCompleted", false)
    )

    private fun FocusSessionEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .putNullable("taskId", taskId)
        .put("startedAtMillis", startedAtMillis)
        .putNullable("endedAtMillis", endedAtMillis)
        .put("plannedMinutes", plannedMinutes)
        .putNullable("actualMinutes", actualMinutes)
        .put("completed", completed)
        .put("locked", locked)
        .putNullable("endTag", endTag?.name)
        .put("blockedAttempts", blockedAttempts)
        .putNullable("topBlockedPackage", topBlockedPackage)

    private fun JSONObject.toFocusSessionEntity(taskIdsByTitle: Map<String, Long> = emptyMap()): FocusSessionEntity {
        val plannedMins = optIntOrNull("plannedMinutes")
            ?: optIntOrNull("durationSeconds")?.let { (it / 60).coerceAtLeast(1) }
            ?: 20
        val actualMins = optIntOrNull("actualMinutes")
            ?: optIntOrNull("actualSeconds")?.let { secs ->
                if (secs > 0) (secs / 60).coerceAtLeast(1) else 0
            }
        return FocusSessionEntity(
            id = optLong("id", 0L),
            taskId = optLongOrNull("taskId") ?: taskIdsByTitle[optString("taskTitle", "")],
            startedAtMillis = optLong("startedAtMillis", 0L),
            endedAtMillis = optLongOrNull("endedAtMillis"),
            plannedMinutes = plannedMins,
            actualMinutes = actualMins,
            completed = optBoolean("completed", false),
            locked = optBoolean("locked", true),
            endTag = optEnumOrNull<FocusEndTag>("endTag"),
            blockedAttempts = optInt("blockedAttempts", 0),
            topBlockedPackage = optStringOrNull("topBlockedPackage")
        )
    }

    private fun FocusGardenEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("sessionId", sessionId)
        .put("treeType", treeType)
        .put("plantedAtMillis", plantedAtMillis)

    private fun JSONObject.toFocusGardenEntity(): FocusGardenEntity = FocusGardenEntity(
        id = optLong("id", 0L),
        sessionId = optLong("sessionId", 0L),
        treeType = optString("treeType", "oak"),
        plantedAtMillis = optLong("plantedAtMillis", 0L)
    )

    private fun CompanionStateEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("energy", energy)
        .put("maxEnergy", maxEnergy)
        .putNullable("pauseUntilMillis", pauseUntilMillis)
        .put("streakDays", streakDays)
        .putNullable("lastActiveDayMillis", lastActiveDayMillis)
        .put("stage", stage)

    private fun JSONObject.toCompanionStateEntity(): CompanionStateEntity = CompanionStateEntity(
        id = optInt("id", 1),
        name = optString("name", "Pip"),
        energy = optInt("energy", 0),
        maxEnergy = optInt("maxEnergy", 100),
        pauseUntilMillis = optLongOrNull("pauseUntilMillis"),
        streakDays = optInt("streakDays", 0),
        lastActiveDayMillis = optLongOrNull("lastActiveDayMillis"),
        stage = optInt("stage", 0)
    )

    private fun JSONObject.extractHarborStatsCompanion(): CompanionStateEntity? {
        val statsObj = when {
            optJSONObject("harbor_stats") != null -> getJSONObject("harbor_stats")
            optJSONObject("harborStats") != null -> getJSONObject("harborStats")
            optJSONArray("harbor_stats") != null && getJSONArray("harbor_stats").length() > 0 ->
                getJSONArray("harbor_stats").optJSONObject(0)
            optJSONArray("harborStats") != null && getJSONArray("harborStats").length() > 0 ->
                getJSONArray("harborStats").optJSONObject(0)
            else -> null
        } ?: return null

        val gardenCount = statsObj.optIntOrNull("focusGardenCount") ?: statsObj.optInt("totalSessions", 0)
        val streak = statsObj.optInt("streakDays", 0)
        val lastActive = statsObj.optLongOrNull("lastActiveDateMillis")
            ?: statsObj.optLongOrNull("lastActiveDayMillis")
            ?: statsObj.optStringOrNull("last_focus_logical_date")?.let { date ->
                runCatching { java.time.LocalDate.parse(date).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
            }
        val pauseUntil = statsObj.optLongOrNull("graceEndMillis")
            ?: statsObj.optLongOrNull("pauseUntilMillis")
        return CompanionStateEntity(
            id = 1,
            name = statsObj.optString("name", "Pip").ifBlank { "Pip" },
            energy = statsObj.optIntOrNull("energy") ?: (gardenCount * 25).coerceIn(0, 100),
            maxEnergy = statsObj.optInt("maxEnergy", 100),
            pauseUntilMillis = pauseUntil,
            streakDays = streak,
            lastActiveDayMillis = lastActive,
            stage = statsObj.optIntOrNull("stage") ?: (gardenCount / 4).coerceAtLeast(0)
        )
    }

    private fun CheckInEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("energyLevel", energyLevel.name)
        .put("tags", tags)
        .put("recordedAtMillis", recordedAtMillis)

    private fun JSONObject.toCheckInEntity(): CheckInEntity = CheckInEntity(
        id = optLong("id", 0L),
        energyLevel = optEnum("energyLevel", optEnum("energy", EnergyLevel.OK)),
        tags = optString("tags", ""),
        recordedAtMillis = if (has("recordedAtMillis") && !isNull("recordedAtMillis")) optLong("recordedAtMillis", 0L) else optLong("timestampMillis", 0L)
    )

    private fun BlockRuleEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("packageName", packageName)
        .put("ruleType", ruleType.name)
        .putNullable("startHour", startHour)
        .putNullable("startMinute", startMinute)
        .putNullable("endHour", endHour)
        .putNullable("endMinute", endMinute)
        .put("enabled", enabled)

    private fun JSONObject.toBlockRuleEntity(): BlockRuleEntity = BlockRuleEntity(
        id = optLong("id", 0L),
        packageName = optString("packageName", ""),
        ruleType = optEnum("ruleType", BlockRuleType.SESSION),
        startHour = optIntOrNull("startHour"),
        startMinute = optIntOrNull("startMinute"),
        endHour = optIntOrNull("endHour"),
        endMinute = optIntOrNull("endMinute"),
        enabled = optBoolean("enabled", true)
    )

    private fun TemptationBundleEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("rewardPackageName", rewardPackageName)
        .put("unlockMinutes", unlockMinutes)
        .put("enabled", enabled)
        .put("unlockUntilMillis", unlockUntilMillis)

    private fun JSONObject.toTemptationBundleEntity(): TemptationBundleEntity = TemptationBundleEntity(
        id = optLong("id", 0L),
        name = optString("name", ""),
        rewardPackageName = optString("rewardPackageName", ""),
        unlockMinutes = optInt("unlockMinutes", 15),
        enabled = optBoolean("enabled", true),
        unlockUntilMillis = optLong("unlockUntilMillis", 0L)
    )

    private fun ReplanItemEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("taskId", taskId)
        .put("missedOnDayMillis", missedOnDayMillis)
        .put("resolved", resolved)

    private fun JSONObject.toReplanItemEntity(): ReplanItemEntity = ReplanItemEntity(
        id = optLong("id", 0L),
        taskId = optLong("taskId", 0L),
        missedOnDayMillis = optLong("missedOnDayMillis", 0L),
        resolved = optBoolean("resolved", false)
    )

    private fun FunLinkEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("url", url)
        .put("emoji", emoji)
        .put("sortOrder", sortOrder)
        .put("enabled", enabled)

    private fun JSONObject.toFunLinkEntity(): FunLinkEntity = FunLinkEntity(
        id = optLong("id", 0L),
        name = optString("name", "").ifBlank { optString("title", "") },
        url = optString("url", ""),
        emoji = optString("emoji", "🔗"),
        sortOrder = optInt("sortOrder", 0),
        enabled = optBoolean("enabled", true)
    )

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key)

    private inline fun <reified T : Enum<T>> JSONObject.optEnum(key: String, default: T): T =
        optStringOrNull(key)?.let { raw -> runCatching { enumValueOf<T>(raw) }.getOrNull() } ?: default

    private inline fun <reified T : Enum<T>> JSONObject.optEnumOrNull(key: String): T? =
        optStringOrNull(key)?.let { raw -> runCatching { enumValueOf<T>(raw) }.getOrNull() }

    private fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (i in 0 until length()) {
                val obj = optJSONObject(i) ?: continue
                add(transform(obj))
            }
        }
    }
}
