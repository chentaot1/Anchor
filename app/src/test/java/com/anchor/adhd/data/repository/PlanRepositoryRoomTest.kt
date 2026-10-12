package com.anchor.adhd.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anchor.adhd.data.db.AnchorDatabase
import com.anchor.adhd.data.model.FocusSessionEntity
import com.anchor.adhd.data.model.ReplanItemEntity
import com.anchor.adhd.data.model.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlanRepositoryRoomTest {
    private lateinit var db: AnchorDatabase
    private lateinit var repository: PlanRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PlanRepository(
            taskDao = db.taskDao(),
            routineDao = db.routineDao(),
            calendarDao = db.calendarEventDao(),
            assignmentDao = db.assignmentDao(),
            replanDao = db.replanDao(),
            db = db
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deleteParentTask_cascadesChildrenAndReplanEntries() = runBlocking {
        val parent = TaskEntity(title = "Write research paper")
        val children = listOf(
            TaskEntity(title = "Step 1: Outline", sortOrder = 0, isNextAction = true),
            TaskEntity(title = "Step 2: Draft", sortOrder = 1)
        )
        val parentId = repository.insertParentWithChildren(parent, children)
        val storedChildren = db.taskDao().getChildren(parentId)
        assertEquals(2, storedChildren.size)

        db.replanDao().insert(ReplanItemEntity(taskId = parentId, missedOnDayMillis = 1_720_000_000_000L))
        db.replanDao().insert(ReplanItemEntity(taskId = storedChildren[0].id, missedOnDayMillis = 1_720_000_000_000L))

        repository.deleteTask(parentId)

        assertNull(db.taskDao().getById(parentId))
        assertTrue(db.taskDao().getChildren(parentId).isEmpty())
        assertTrue(db.replanDao().getAll().isEmpty())
    }

    @Test
    fun deleteChildTask_promotesNextIncompleteSiblingToNextAction() = runBlocking {
        val parent = TaskEntity(title = "Clean garage")
        val children = listOf(
            TaskEntity(title = "Sort boxes", sortOrder = 0, isNextAction = true),
            TaskEntity(title = "Sweep floor", sortOrder = 1, isNextAction = false),
            TaskEntity(title = "Donate items", sortOrder = 2, isNextAction = false)
        )
        val parentId = repository.insertParentWithChildren(parent, children)
        val storedChildren = db.taskDao().getChildren(parentId).sortedBy { it.sortOrder }

        repository.deleteTask(storedChildren[0].id)

        val remaining = db.taskDao().getChildren(parentId).sortedBy { it.sortOrder }
        assertEquals(2, remaining.size)
        assertTrue(remaining[0].isNextAction)
        assertFalse(remaining[1].isNextAction)
    }

    @Test
    fun completeTaskCascade_promotesSiblingAndAutoCompletesParentWhenLastChildFinishes() = runBlocking {
        val parent = TaskEntity(title = "File taxes")
        val children = listOf(
            TaskEntity(title = "Gather W-2", sortOrder = 0, isNextAction = true),
            TaskEntity(title = "Submit return", sortOrder = 1, isNextAction = false)
        )
        val parentId = repository.insertParentWithChildren(parent, children)
        val storedChildren = db.taskDao().getChildren(parentId).sortedBy { it.sortOrder }

        // Complete first child -> second child promoted to isNextAction, parent still incomplete
        repository.completeTaskCascade(storedChildren[0].id)

        val afterFirst = db.taskDao().getChildren(parentId).sortedBy { it.sortOrder }
        assertTrue(afterFirst[0].isCompleted)
        assertFalse(afterFirst[0].isNextAction)
        assertFalse(afterFirst[1].isCompleted)
        assertTrue(afterFirst[1].isNextAction)
        assertFalse(db.taskDao().getById(parentId)!!.isCompleted)

        // Complete second (final) child -> parent automatically marked complete
        repository.completeTaskCascade(storedChildren[1].id)

        val afterSecond = db.taskDao().getChildren(parentId).sortedBy { it.sortOrder }
        assertTrue(afterSecond[1].isCompleted)
        assertFalse(afterSecond[1].isNextAction)
        val updatedParent = db.taskDao().getById(parentId)
        assertNotNull(updatedParent)
        assertTrue(updatedParent!!.isCompleted)
        assertNotNull(updatedParent.completedAtMillis)
    }

    @Test
    fun observeCompletedMinutesBetween_usesActualMinutesWithPlannedFallback() = runBlocking {
        val start = 1_000_000L
        val end = 2_000_000L

        // Completed session with actualMinutes = 12 (planned = 25)
        db.focusSessionDao().insert(
            FocusSessionEntity(
                startedAtMillis = start + 1_000L,
                endedAtMillis = start + 721_000L,
                plannedMinutes = 25,
                actualMinutes = 12,
                completed = true
            )
        )
        // Completed session with actualMinutes = null (planned = 20)
        db.focusSessionDao().insert(
            FocusSessionEntity(
                startedAtMillis = start + 2_000L,
                endedAtMillis = start + 1_202_000L,
                plannedMinutes = 20,
                actualMinutes = null,
                completed = true
            )
        )
        // Incomplete session (should be ignored)
        db.focusSessionDao().insert(
            FocusSessionEntity(
                startedAtMillis = start + 3_000L,
                endedAtMillis = start + 303_000L,
                plannedMinutes = 30,
                actualMinutes = 5,
                completed = false
            )
        )

        val totalMinutes = db.focusSessionDao().observeCompletedMinutesBetween(start, end).first()
        assertEquals(32, totalMinutes)
    }
}
