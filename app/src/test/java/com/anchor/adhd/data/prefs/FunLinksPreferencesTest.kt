package com.anchor.adhd.data.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
class FunLinksPreferencesTest {
    private lateinit var preferences: UserPreferences

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferences = UserPreferences(context)
        preferences.clearAll()
    }

    @Test
    fun addAndConsumeFunCredits() = runBlocking {
        assertEquals(0, preferences.funCreditsFlow.first())

        // Consuming with 0 credits returns false
        val consumedEmpty = preferences.consumeFunCredit()
        assertFalse(consumedEmpty)
        assertEquals(0, preferences.funCreditsFlow.first())

        // Add 2 credits
        preferences.addFunCredit(2)
        assertEquals(2, preferences.funCreditsFlow.first())

        // Consume 1 credit
        val consumed1 = preferences.consumeFunCredit()
        assertTrue(consumed1)
        assertEquals(1, preferences.funCreditsFlow.first())

        // Consume 2nd credit
        val consumed2 = preferences.consumeFunCredit()
        assertTrue(consumed2)
        assertEquals(0, preferences.funCreditsFlow.first())

        // Cannot consume when back at 0
        assertFalse(preferences.consumeFunCredit())
    }

    @Test
    fun strictModeToggle() = runBlocking {
        assertFalse(preferences.funStrictModeFlow.first())

        preferences.setFunStrictMode(true)
        assertTrue(preferences.funStrictModeFlow.first())

        preferences.setFunStrictMode(false)
        assertFalse(preferences.funStrictModeFlow.first())
    }

    @Test
    fun dailyFreshStart_standardMode_grantsOneCreditIfEmpty() = runBlocking {
        preferences.setFunStrictMode(false)
        assertEquals(0, preferences.funCreditsFlow.first())

        // Day 1: fresh start gives 1 credit
        preferences.checkDailyFreshStart("2026-09-18")
        assertEquals(1, preferences.funCreditsFlow.first())

        // Calling same day again does not grant more
        preferences.checkDailyFreshStart("2026-09-18")
        assertEquals(1, preferences.funCreditsFlow.first())

        // If user already has 3 credits, fresh start doesn't overwrite it
        preferences.addFunCredit(2)
        assertEquals(3, preferences.funCreditsFlow.first())

        // Day 2 with existing credits
        preferences.checkDailyFreshStart("2026-09-19")
        assertEquals(3, preferences.funCreditsFlow.first())
    }

    @Test
    fun dailyFreshStart_strictMode_doesNotGrantFreeCredit() = runBlocking {
        preferences.setFunStrictMode(true)
        assertEquals(0, preferences.funCreditsFlow.first())

        preferences.checkDailyFreshStart("2026-09-18")
        assertEquals(0, preferences.funCreditsFlow.first())

        preferences.addFunCredit(1)
        assertEquals(1, preferences.funCreditsFlow.first())

        preferences.checkDailyFreshStart("2026-09-19")
        assertEquals(1, preferences.funCreditsFlow.first())
    }
}
