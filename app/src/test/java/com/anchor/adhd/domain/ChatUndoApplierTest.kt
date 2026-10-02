package com.anchor.adhd.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anchor.adhd.data.db.AnchorDatabase
import com.anchor.adhd.data.model.InboxState
import com.anchor.adhd.data.model.TaskEntity
import com.anchor.adhd.data.repository.PlanRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChatUndoApplierTest {
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
    fun createdTasks_skipsCompletedKeepsUnedited() = runBlocking {
        val inboxId = db.taskDao().insert(TaskEntity(title = "milk", inboxState = InboxState.TODAY))
        val doneId = db.taskDao().insert(TaskEntity(title = "done", inboxState = InboxState.TODAY, isCompleted = true))
        val scheduledId = db.taskDao().insert(
            TaskEntity(title = "timed", inboxState = InboxState.TODAY, scheduledStartMillis = 1_000L)
        )
        val movedId = db.taskDao().insert(TaskEntity(title = "later", inboxState = InboxState.SOMEDAY))

        val msg = ChatUndoApplier.undo(
            ChatUndoOp.CreatedTasks(listOf(inboxId, doneId, scheduledId, movedId)),
            repository
        )
        assertNotNull(msg)
        assertNull(db.taskDao().getById(inboxId))
        assertNotNull(db.taskDao().getById(doneId))
        assertNotNull(db.taskDao().getById(scheduledId))
        assertNotNull(db.taskDao().getById(movedId))
    }
}
