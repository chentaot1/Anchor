package com.anchor.adhd.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anchor.adhd.data.model.FocusSessionEntity
import com.anchor.adhd.data.repository.FocusRepository
import com.anchor.adhd.data.prefs.UserPreferences
import com.anchor.adhd.service.FocusBlockService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FocusRepositoryFinishTest {
    private lateinit var context: Context
    private lateinit var db: AnchorDatabase
    private lateinit var repository: FocusRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FocusRepository(
            context = context,
            sessionDao = db.focusSessionDao(),
            blockRuleDao = db.blockRuleDao(),
            bundleDao = db.temptationBundleDao(),
            taskDao = db.taskDao(),
            preferences = UserPreferences(context),
            companionDao = db.companionDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun finishSession_isIdempotentAndInsertsSingleTree() = runBlocking {
        val sessionId = db.focusSessionDao().insert(
            FocusSessionEntity(
                startedAtMillis = System.currentTimeMillis() - 20 * 60_000L,
                plannedMinutes = 20,
                locked = true
            )
        )

        assertTrue(repository.finishSession(sessionId, completed = true, actualMinutes = 20))
        assertFalse(repository.finishSession(sessionId, completed = true, actualMinutes = 20))

        val session = db.focusSessionDao().getById(sessionId)!!
        assertEquals(true, session.completed)
        assertEquals(20, session.actualMinutes)
        assertEquals(1, db.companionDao().countTreesForSession(sessionId))
    }

    @Test
    fun finishSession_snapshotsBlockedAttempts() = runBlocking {
        FocusBlockService.blockedAttempts = 3
        FocusBlockService.topBlockedPackage = "com.google.android.youtube"
        val sessionId = db.focusSessionDao().insert(
            FocusSessionEntity(
                startedAtMillis = System.currentTimeMillis() - 10 * 60_000L,
                plannedMinutes = 20,
                locked = true
            )
        )
        assertTrue(repository.finishSession(sessionId, completed = true, actualMinutes = 10))
        val session = db.focusSessionDao().getById(sessionId)!!
        assertEquals(3, session.blockedAttempts)
        assertEquals("com.google.android.youtube", session.topBlockedPackage)
        FocusBlockService.resetSessionBlocks()
    }
}
