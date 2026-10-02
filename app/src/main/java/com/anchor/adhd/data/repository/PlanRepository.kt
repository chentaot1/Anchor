package com.anchor.adhd.data.repository

import com.anchor.adhd.data.db.AnchorDatabase
import com.anchor.adhd.data.db.AssignmentDao
import com.anchor.adhd.data.db.CalendarEventDao
import com.anchor.adhd.data.db.ReplanDao
import com.anchor.adhd.data.db.RoutineDao
import com.anchor.adhd.data.db.TaskDao
import com.anchor.adhd.data.model.AssignmentEntity
import com.anchor.adhd.data.model.CalendarEventEntity
import com.anchor.adhd.data.model.EnergyLevel
import com.anchor.adhd.data.model.InboxState
import com.anchor.adhd.data.model.ReplanItemEntity
import com.anchor.adhd.data.model.RoutineEntity
import com.anchor.adhd.data.model.RoutineStepEntity
import com.anchor.adhd.data.model.TaskEntity
import com.anchor.adhd.calendar.DeviceCalendarOccupancy
import com.anchor.adhd.domain.EnergyTaskSorter
import com.anchor.adhd.domain.SchedulingSlots
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

class PlanRepository(
    private val taskDao: TaskDao,
    private val routineDao: RoutineDao,
    private val calendarDao: CalendarEventDao,
    private val assignmentDao: AssignmentDao,
    private val replanDao: ReplanDao,
    private val db: AnchorDatabase,
    var activeFocusTaskIdProvider: (() -> Long?)? = null
) {
    private val zone get() = ZoneId.systemDefault()

    private fun todayBounds(): Pair<Long, Long> {
        val today = LocalDate.now(zone)
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return dayStart to dayEnd
    }

    fun observeInbox(state: InboxState): Flow<List<TaskEntity>> = taskDao.observeInbox(state)

    fun observeTask(id: Long): Flow<TaskEntity?> = taskDao.observeById(id)

    fun observeChildren(parentId: Long): Flow<List<TaskEntity>> = taskDao.observeChildren(parentId)

    fun observeInboxForEnergy(state: InboxState, energy: Flow<EnergyLevel>): Flow<List<TaskEntity>> =
        combine(observeInbox(state), energy) { tasks, level ->
            EnergyTaskSorter.sort(tasks, level)
        }

    fun observeScheduledForEnergy(energy: Flow<EnergyLevel>): Flow<List<TaskEntity>> =
        combine(observeScheduledToday(), energy) { tasks, level ->
            EnergyTaskSorter.sort(tasks, level)
        }

    fun observeScheduledToday(): Flow<List<TaskEntity>> {
        val (dayStart, dayEnd) = todayBounds()
        return combine(
            taskDao.observeScheduledForDay(dayStart, dayEnd),
            taskDao.observeInbox(InboxState.TODAY)
        ) { scheduled, inbox ->
            val allDay = inbox.filter { it.scheduledStartMillis == null }
            scheduled + allDay
        }
    }

    fun observeScheduled(): Flow<List<TaskEntity>> = observeScheduledToday()

    fun observeAssignments(): Flow<List<AssignmentEntity>> = assignmentDao.observeOpen()
    fun observeRoutines(): Flow<List<RoutineEntity>> = routineDao.observeEnabled()
    fun observeRoutineSteps(routineId: Long): Flow<List<RoutineStepEntity>> = routineDao.observeSteps(routineId)
    fun observeReplan(): Flow<List<ReplanItemEntity>> = replanDao.observeUnresolved()

    fun observeAssignmentsDueSoon(withinDays: Long = 7): Flow<List<AssignmentEntity>> {
        val deadline = System.currentTimeMillis() + withinDays * 86_400_000L
        return assignmentDao.observeDueBefore(deadline)
    }

    fun observeReplanWithTasks(): Flow<List<Pair<ReplanItemEntity, TaskEntity?>>> =
        combine(observeReplan(), taskDao.observeActiveTasks()) { items, tasks ->
            val byId = tasks.associateBy { it.id }
            items.map { it to byId[it.taskId] }
        }

    fun observeCalendarDay(date: LocalDate): Flow<List<CalendarEventEntity>> {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return calendarDao.observeBetween(start, end)
    }

    suspend fun addInboxTask(title: String, state: InboxState = InboxState.TODAY, scheduledStartMillis: Long? = null) {
        val ids = captureToInbox(listOf(title), state)
        val id = ids.firstOrNull() ?: return
        if (scheduledStartMillis != null) {
            val task = taskDao.getById(id) ?: return
            scheduleTask(id, scheduledStartMillis, task.durationMinutes)
        }
    }

    /**
     * Capture door: Today/Someday/Waiting inbox, never a start time.
     * Panic dump, share, comma lists, and brain-dump Apply all go through here.
     */
    suspend fun captureToInbox(
        titles: List<String>,
        state: InboxState = InboxState.TODAY,
        aiGenerated: Boolean = false
    ): List<Long> {
        return titles.mapNotNull { raw ->
            val title = raw.trim()
            if (title.isBlank()) null
            else taskDao.insert(
                TaskEntity(title = title, inboxState = state, scheduledStartMillis = null, aiGenerated = aiGenerated)
            )
        }
    }

    suspend fun moveInbox(taskId: Long, state: InboxState) {
        if (state == InboxState.SOMEDAY) {
            taskDao.moveToSomeday(taskId)
        } else {
            taskDao.setInboxState(taskId, state)
        }
    }

    suspend fun scheduleTask(taskId: Long, startMillis: Long, durationMinutes: Int) {
        val task = taskDao.getById(taskId) ?: return
        taskDao.update(
            task.copy(
                scheduledStartMillis = startMillis,
                durationMinutes = durationMinutes,
                inboxState = InboxState.TODAY
            )
        )
    }

    /**
     * Places [taskId] in the first hole that fits before [shutdownMillis].
     * Returns the start time, or null if nothing fits — task stays Today, unscheduled.
     */
    suspend fun scheduleTaskAtNextSlot(
        taskId: Long,
        durationMinutes: Int,
        nowMillis: Long = System.currentTimeMillis(),
        dayStartMillis: Long? = null,
        shutdownMillis: Long? = null
    ): Long? {
        val (defaultStart, defaultEnd) = todayBounds()
        val dayStart = dayStartMillis ?: defaultStart
        val shutdown = shutdownMillis ?: defaultEnd
        val scheduled = taskDao.getScheduledBlocksForDay(dayStart, shutdown)
            .filter { it.id != taskId }
        val events = calendarDao.getOverlapping(dayStart, shutdown)
        val occupancy = SchedulingSlots.occupancyIntervals(scheduled, events)
        val hole = SchedulingSlots.firstHoleThatFits(
            nowMillis = nowMillis,
            durationMinutes = durationMinutes,
            dayStartMillis = dayStart,
            shutdownMillis = shutdown,
            occupancy = occupancy
        )
        if (hole == null) {
            val task = taskDao.getById(taskId) ?: return null
            taskDao.update(task.copy(inboxState = InboxState.TODAY, scheduledStartMillis = null))
            return null
        }
        scheduleTask(taskId, hole, durationMinutes)
        return hole
    }

    /** Schedule door: place each id in the next hole, sequentially updating occupancy. */
    suspend fun scheduleIntoHoles(
        taskIds: List<Long>,
        nowMillis: Long = System.currentTimeMillis(),
        dayStartMillis: Long? = null,
        shutdownMillis: Long? = null
    ): List<Long?> = taskIds.map { id ->
        val task = taskDao.getById(id) ?: return@map null
        scheduleTaskAtNextSlot(id, task.durationMinutes, nowMillis, dayStartMillis, shutdownMillis)
    }

    suspend fun scheduleChildrenIntoHoles(
        parentId: Long,
        nowMillis: Long,
        dayStartMillis: Long,
        shutdownMillis: Long
    ): List<Long?> {
        val ids = taskDao.getChildren(parentId).sortedBy { it.sortOrder }.map { it.id }
        return scheduleIntoHoles(ids, nowMillis, dayStartMillis, shutdownMillis)
    }

    suspend fun completeTask(taskId: Long) = taskDao.markComplete(taskId)

    /** Completes a parent task and all its child steps (cascading). */
    suspend fun completeTaskCascade(taskId: Long) {
        val task = taskDao.getById(taskId) ?: return
        taskDao.completeTaskCascade(task.id, isParent = task.parentTaskId == null)
    }

    /** Un-completes a parent task and its child steps (cascading), or just the child if it is one. */
    suspend fun unCompleteTaskCascade(taskId: Long) {
        val task = taskDao.getById(taskId) ?: return
        taskDao.unCompleteTaskCascade(task.id, isParent = task.parentTaskId == null)
    }

    /** Adds an inbox task and returns its generated id. Capture only — no start time. */
    suspend fun addInboxTaskReturningId(title: String, state: InboxState = InboxState.TODAY): Long =
        captureToInbox(listOf(title), state).first()

    suspend fun addAssignment(title: String, course: String, dueAtMillis: Long) {
        assignmentDao.insert(AssignmentEntity(title = title, course = course, dueAtMillis = dueAtMillis))
    }

    suspend fun completeAssignment(id: Long) {
        val a = assignmentDao.observeOpen().first().find { it.id == id } ?: return
        assignmentDao.update(a.copy(isCompleted = true))
    }

    suspend fun importCalendarEvents(events: List<CalendarEventEntity>) {
        calendarDao.upsertAll(events)
        val ids = events.map { it.id }
        if (ids.isEmpty()) {
            calendarDao.deleteSource("google")
        } else {
            calendarDao.deleteSourceNotIn("google", ids)
        }
    }

    /** Today-only device Instances upsert. Does not delete tomorrow or Google rows. */
    suspend fun replaceDeviceCalendarWindow(
        from: Long,
        to: Long,
        events: List<CalendarEventEntity>
    ) {
        calendarDao.upsertAll(events)
        val ids = events.map { it.id }
        if (ids.isEmpty()) {
            calendarDao.deleteSourceInRange(DeviceCalendarOccupancy.SOURCE, from, to)
        } else {
            calendarDao.deleteSourceInRangeNotIn(DeviceCalendarOccupancy.SOURCE, from, to, ids)
        }
    }

    suspend fun queueReplanForMissedTasks(day: LocalDate, excludeTaskId: Long? = null) {
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val missed = taskDao.getMissedScheduledBefore(now, dayEnd, excludeTaskId)
        val unscheduledToday = taskDao.getUnscheduledTodayTasks().filter { task ->
            excludeTaskId == null || task.id != excludeTaskId
        }
        val allMissed = missed + unscheduledToday
        allMissed.forEach { task ->
            if (replanDao.countUnresolvedForTaskDay(task.id, dayStart) == 0) {
                replanDao.insert(ReplanItemEntity(taskId = task.id, missedOnDayMillis = dayStart))
            }
        }
    }

    suspend fun resolveReplanItem(id: Long) = replanDao.resolve(id)

    suspend fun replanRescheduleToday(
        replanId: Long,
        taskId: Long,
        durationMinutes: Int,
        nowMillis: Long = System.currentTimeMillis(),
        dayStartMillis: Long? = null,
        shutdownMillis: Long? = null
    ) {
        if (com.anchor.adhd.domain.FocusSessionGuard.isTaskLocked(taskId)) return
        scheduleTaskAtNextSlot(taskId, durationMinutes, nowMillis, dayStartMillis, shutdownMillis)
        replanDao.resolve(replanId)
    }

    suspend fun replanDeferSomeday(replanId: Long, taskId: Long) {
        if (com.anchor.adhd.domain.FocusSessionGuard.isTaskLocked(taskId)) return
        moveInbox(taskId, InboxState.SOMEDAY)
        val task = taskDao.getById(taskId) ?: return
        taskDao.update(task.copy(scheduledStartMillis = null))
        replanDao.resolve(replanId)
    }

    suspend fun insertTasks(tasks: List<TaskEntity>) {
        taskDao.insertAll(tasks)
    }

    suspend fun insertTasksAndReturnIds(tasks: List<TaskEntity>): List<Long> {
        return taskDao.insertAll(tasks)
    }

    /** Inserts a parent task and its child steps in one Room transaction.
     * When a hole window is given, steps that fit are scheduled inside that same transaction;
     * the rest stay Today inbox (unscheduled). */
    suspend fun insertParentWithChildren(
        parent: TaskEntity,
        children: List<TaskEntity>,
        nowMillis: Long? = null,
        dayStartMillis: Long? = null,
        shutdownMillis: Long? = null
    ): Long {
        return db.withTransaction {
            val placed = if (nowMillis != null && dayStartMillis != null && shutdownMillis != null) {
                placeChildrenInHoles(children, nowMillis, dayStartMillis, shutdownMillis)
            } else {
                children.map { it.copy(scheduledStartMillis = null) }
            }
            taskDao.insertParentWithChildren(parent.copy(scheduledStartMillis = null), placed)
        }
    }

    private suspend fun placeChildrenInHoles(
        children: List<TaskEntity>,
        nowMillis: Long,
        dayStartMillis: Long,
        shutdownMillis: Long
    ): List<TaskEntity> {
        val scheduled = taskDao.getScheduledBlocksForDay(dayStartMillis, shutdownMillis)
        val events = calendarDao.getOverlapping(dayStartMillis, shutdownMillis)
        val occupancy = SchedulingSlots.occupancyIntervals(scheduled, events).toMutableList()
        return children.map { child ->
            val duration = child.durationMinutes.coerceAtLeast(5)
            val hole = SchedulingSlots.firstHoleThatFits(
                nowMillis = nowMillis,
                durationMinutes = duration,
                dayStartMillis = dayStartMillis,
                shutdownMillis = shutdownMillis,
                occupancy = occupancy
            )
            if (hole != null) {
                occupancy += com.anchor.adhd.domain.TimeInterval(hole, hole + duration * 60_000L)
                child.copy(scheduledStartMillis = hole)
            } else {
                child.copy(scheduledStartMillis = null, inboxState = InboxState.TODAY)
            }
        }
    }

    suspend fun getChildren(parentId: Long): List<TaskEntity> = taskDao.getChildren(parentId)

    suspend fun applyTriageOrder(titles: List<String>) {
        val todayTasks = taskDao.observeInbox(InboxState.TODAY).first()
        titles.forEachIndexed { index, title ->
            val task = todayTasks.find { it.title.equals(title, ignoreCase = true) } ?: return@forEachIndexed
            taskDao.updateSortOrder(task.id, index)
        }
    }

    suspend fun getTask(id: Long): TaskEntity? = taskDao.getById(id)

    suspend fun applyReplanSuggestions(
        queue: List<Pair<ReplanItemEntity, TaskEntity?>>,
        recommendedTitles: List<String>,
        deferTitles: List<String>,
        nowMillis: Long = System.currentTimeMillis(),
        dayStartMillis: Long? = null,
        shutdownMillis: Long? = null
    ) {
        val titleToItem = queue.associateBy { (_, task) -> task?.title?.lowercase().orEmpty() }
        recommendedTitles.forEach { title ->
            val entry = titleToItem[title.lowercase()] ?: return@forEach
            val task = entry.second ?: return@forEach
            replanRescheduleToday(
                entry.first.id,
                task.id,
                task.durationMinutes,
                nowMillis,
                dayStartMillis,
                shutdownMillis
            )
        }
        deferTitles.forEach { title ->
            val entry = titleToItem[title.lowercase()] ?: return@forEach
            val task = entry.second ?: return@forEach
            replanDeferSomeday(entry.first.id, task.id)
        }
    }

    suspend fun createRoutine(name: String, cue: String, ifThen: String?, stepTitles: List<String>) {
        val routineId = routineDao.insertRoutine(
            RoutineEntity(name = name.trim(), cue = cue.trim(), ifThen = ifThen?.trim())
        )
        val steps = stepTitles.filter { it.isNotBlank() }.mapIndexed { index, title ->
            RoutineStepEntity(routineId = routineId, title = title.trim(), durationMinutes = 5, sortOrder = index)
        }
        if (steps.isNotEmpty()) routineDao.insertSteps(steps)
    }

    suspend fun setTaskActualMinutes(taskId: Long, actualMinutes: Int) {
        val task = taskDao.getById(taskId) ?: return
        taskDao.update(task.copy(actualMinutes = actualMinutes))
    }

    suspend fun updateTask(task: TaskEntity) {
        taskDao.update(task)
    }

    suspend fun deleteTask(taskId: Long) {
        taskDao.deleteById(taskId)
    }

    suspend fun deferTaskToTomorrow(taskId: Long) {
        val task = taskDao.getById(taskId) ?: return
        val start = task.scheduledStartMillis ?: System.currentTimeMillis()
        val zone = zone
        val local = java.time.Instant.ofEpochMilli(start).atZone(zone).toLocalDateTime()
        val tomorrow = local.plusDays(1)
        val nextStart = tomorrow.atZone(zone).toInstant().toEpochMilli()
        taskDao.update(task.copy(scheduledStartMillis = nextStart))
    }

    fun observeUnresolvedTodayTasks(): Flow<List<TaskEntity>> {
        val (dayStart, dayEnd) = todayBounds()
        return taskDao.observeScheduledForDay(dayStart, dayEnd)
    }

    suspend fun observeTomorrowFirstBlock(): TaskEntity? {
        val tomorrow = LocalDate.now(zone).plusDays(1)
        val start = tomorrow.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = tomorrow.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return taskDao.observeScheduledForDay(start, end).first().firstOrNull()
    }
}
