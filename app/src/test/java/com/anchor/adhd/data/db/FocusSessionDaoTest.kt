package com.anchor.adhd.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anchor.adhd.data.model.FocusSessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FocusSessionDaoTest {
    private lateinit var db: AnchorDatabase
    private lateinit var sessionDao: FocusSessionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionDao = db.focusSessionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun endSessionIfActive_onlyUpdatesOpenSessionOnce() = runBlocking {
        val sessionId = sessionDao.insert(
            FocusSessionEntity(
                startedAtMillis = System.currentTimeMillis(),
                plannedMinutes = 20,
                locked = true
            )
        )
        val endedAt = System.currentTimeMillis()

        assertEquals(1, sessionDao.endSessionIfActive(sessionId, endedAt, completed = true))
        assertEquals(0, sessionDao.endSessionIfActive(sessionId, endedAt, completed = true))

        val session = sessionDao.getById(sessionId)
        assertNotNull(session?.endedAtMillis)
        assertEquals(true, session?.completed)
    }
}
