package com.anchor.adhd.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anchor.adhd.data.db.AnchorDatabase
import com.anchor.adhd.data.model.FocusSessionEntity
import com.anchor.adhd.data.prefs.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FocusRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: AnchorDatabase
    private lateinit var preferences: UserPreferences
    private lateinit var repository: FocusRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        // Ensure DataStore directory exists across Robolectric per-test temp dirs
        context.filesDir.mkdirs()
        java.io.File(context.filesDir, "datastore").mkdirs()
        db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = UserPreferences(context)
        while (preferences.consumeFunCredit()) {
            // drain any leftover credits
        }
        repository = FocusRepository(
            context = context,
            sessionDao = db.focusSessionDao(),
            blockRuleDao = db.blockRuleDao(),
            bundleDao = db.temptationBundleDao(),
            taskDao = db.taskDao(),
            preferences = preferences,
            companionDao = db.companionDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun finishSession_completedSessionPlantsOneTreeUnlocksBundleAndAwardsSingleFunCredit() = runBlocking {
        repository.addTemptationBundle("Podcasts", "com.example.podcasts", 20)
        val sessionId = db.focusSessionDao().insert(
            FocusSessionEntity(
                startedAtMillis = System.currentTimeMillis() - 25 * 60_000L,
                plannedMinutes = 25,
                locked = true
            )
        )
        val creditsBefore = preferences.funCreditsFlow.first()

        val firstFinish = repository.finishSession(sessionId, completed = true, actualMinutes = 25)
        assertTrue(firstFinish)

        val session = db.focusSessionDao().getById(sessionId)
        assertNotNull(session?.endedAtMillis)
        assertTrue(session!!.completed)
        assertEquals(25, session.actualMinutes)
        assertEquals(1, db.companionDao().countTreesForSession(sessionId))
        assertEquals(creditsBefore + 1, preferences.funCreditsFlow.first())

        val bundle = repository.observeBundles().first().single()
        assertTrue(bundle.unlockUntilMillis > System.currentTimeMillis())

        // Second finishSession call on same sessionId is idempotent: returns false, no extra tree or credit
        val secondFinish = repository.finishSession(sessionId, completed = true, actualMinutes = 25)
        assertFalse(secondFinish)
        assertEquals(1, db.companionDao().countTreesForSession(sessionId))
        assertEquals(creditsBefore + 1, preferences.funCreditsFlow.first())
    }

    @Test
    fun finishSession_incompleteSessionDoesNotPlantTreeOrAwardFunCredit() = runBlocking {
        val sessionId = db.focusSessionDao().insert(
            FocusSessionEntity(
                startedAtMillis = System.currentTimeMillis() - 10 * 60_000L,
                plannedMinutes = 25,
                locked = true
            )
        )
        val creditsBefore = preferences.funCreditsFlow.first()

        val finished = repository.finishSession(sessionId, completed = false, actualMinutes = 10)
        assertTrue(finished)

        val session = db.focusSessionDao().getById(sessionId)
        assertNotNull(session?.endedAtMillis)
        assertFalse(session!!.completed)
        assertEquals(10, session.actualMinutes)
        assertEquals(0, db.companionDao().countTreesForSession(sessionId))
        assertEquals(creditsBefore, preferences.funCreditsFlow.first())
    }
}
