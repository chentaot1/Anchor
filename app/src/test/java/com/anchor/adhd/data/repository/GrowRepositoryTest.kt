package com.anchor.adhd.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anchor.adhd.data.db.AnchorDatabase
import com.anchor.adhd.data.model.CompanionStateEntity
import com.anchor.adhd.data.prefs.UserPreferences
import kotlinx.coroutines.flow.first
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
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GrowRepositoryTest {
    private lateinit var db: AnchorDatabase
    private lateinit var repository: GrowRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = GrowRepository(
            checkInDao = db.checkInDao(),
            companionDao = db.companionDao(),
            sessionDao = db.focusSessionDao(),
            taskDao = db.taskDao(),
            replanDao = db.replanDao(),
            preferences = UserPreferences(context)
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun rewardFocusComplete_incrementsStreakOnConsecutiveDayAndCapsEnergy() = runBlocking {
        val zone = ZoneId.systemDefault()
        val yesterdayStart = LocalDate.now(zone).minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

        db.companionDao().upsert(
            CompanionStateEntity(
                energy = 85,
                maxEnergy = 100,
                streakDays = 4,
                lastActiveDayMillis = yesterdayStart
            )
        )

        repository.rewardFocusComplete()

        val updated = repository.observeCompanion().first()
        assertNotNull(updated)
        assertEquals(100, updated!!.energy) // capped at maxEnergy = 100
        assertEquals(5, updated.streakDays)
        assertEquals(todayStart, updated.lastActiveDayMillis)
        assertNull(updated.pauseUntilMillis)
    }

    @Test
    fun rewardFocusComplete_afterOneDayGap_preservesStreakWithoutBlockingSameDaySessions() = runBlocking {
        val zone = ZoneId.systemDefault()
        val twoDaysAgoStart = LocalDate.now(zone).minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()

        db.companionDao().upsert(
            CompanionStateEntity(
                energy = 20,
                maxEnergy = 100,
                streakDays = 6,
                lastActiveDayMillis = twoDaysAgoStart
            )
        )

        // First session after 1-day gap preserves streak = 6 and does NOT set a future pause
        repository.rewardFocusComplete()
        val afterFirst = repository.observeCompanion().first()!!
        assertEquals(45, afterFirst.energy)
        assertEquals(6, afterFirst.streakDays)
        assertNull(afterFirst.pauseUntilMillis)

        // Second session on the same day still awards energy (+25 -> 70)
        repository.rewardFocusComplete()
        val afterSecond = repository.observeCompanion().first()!!
        assertEquals(70, afterSecond.energy)
        assertEquals(6, afterSecond.streakDays)
    }

    @Test
    fun rewardFocusComplete_respectsActivePauseAndClearsExpiredPause() = runBlocking {
        val futurePause = System.currentTimeMillis() + 3_600_000L
        db.companionDao().upsert(
            CompanionStateEntity(
                energy = 10,
                streakDays = 3,
                pauseUntilMillis = futurePause
            )
        )

        // While paused in the future, rewardFocusComplete is a no-op
        repository.rewardFocusComplete()
        val whilePaused = repository.observeCompanion().first()!!
        assertEquals(10, whilePaused.energy)
        assertEquals(futurePause, whilePaused.pauseUntilMillis)

        // When pause has expired, rewardFocusComplete clears pauseUntilMillis and awards energy
        val expiredPause = System.currentTimeMillis() - 60_000L
        db.companionDao().upsert(whilePaused.copy(pauseUntilMillis = expiredPause))
        repository.rewardFocusComplete()
        val afterExpired = repository.observeCompanion().first()!!
        assertEquals(35, afterExpired.energy)
        assertNull(afterExpired.pauseUntilMillis)
    }
}
