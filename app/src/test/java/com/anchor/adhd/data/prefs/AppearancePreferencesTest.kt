package com.anchor.adhd.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.anchor.adhd.domain.AppearanceMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppearancePreferencesTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var preferences: UserPreferences

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        preferences = UserPreferences(context)
        preferences.clearAll()
    }

    @Test
    fun appearanceModeDefaultsToSystemAndPersistsChanges() = runBlocking {
        assertEquals(AppearanceMode.SYSTEM, preferences.appearanceMode.first())

        preferences.setAppearanceMode(AppearanceMode.LIGHT)
        assertEquals(AppearanceMode.LIGHT, preferences.appearanceMode.first())

        preferences.setAppearanceMode(AppearanceMode.DARK)
        assertEquals(AppearanceMode.DARK, preferences.appearanceMode.first())

        preferences.setAppearanceMode(AppearanceMode.SYSTEM)
        assertEquals(AppearanceMode.SYSTEM, preferences.appearanceMode.first())
    }

    @Test
    fun unknownStoredAppearanceModeFallsBackToSystem() = runBlocking {
        val owner = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(owner + Dispatchers.IO),
            produceFile = { File(tempFolder.root, "appearance_unknown.preferences_pb") },
        )
        try {
            val prefs = UserPreferences(context, store)
            store.edit { it[UserPreferences.APPEARANCE_MODE] = "unknown_mode" }
            assertEquals(AppearanceMode.SYSTEM, prefs.appearanceMode.first())
        } finally {
            owner.cancelAndJoin()
        }
    }
}
