package com.anchor.adhd.data.seed

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anchor.adhd.data.db.AnchorDatabase
import com.anchor.adhd.data.prefs.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SeedDataTest {
    private lateinit var context: Context
    private lateinit var db: AnchorDatabase
    private lateinit var preferences: UserPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = UserPreferences(context)
        runBlocking { preferences.resetSeedFlagForTests() }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun seedIfNeeded_doesNotDuplicateOnSecondCall() = runBlocking {
        SeedData.seedIfNeeded(db, preferences)
        val routinesAfterFirst = db.routineDao().observeEnabled().first().size
        val blocksAfterFirst = db.blockRuleDao().observeAll().first().size

        SeedData.seedIfNeeded(db, preferences)

        assertEquals(routinesAfterFirst, db.routineDao().observeEnabled().first().size)
        assertEquals(blocksAfterFirst, db.blockRuleDao().observeAll().first().size)
        assertEquals(true, preferences.isSeedComplete())
        assertTrue(routinesAfterFirst >= 2)
    }
}
