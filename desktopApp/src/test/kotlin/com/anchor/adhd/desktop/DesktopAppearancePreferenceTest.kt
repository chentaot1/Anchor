package com.anchor.adhd.desktop

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.anchor.adhd.desktop.platform.DesktopSystemAppearance
import com.anchor.adhd.desktop.prefs.DesktopUserPreferences
import com.anchor.adhd.domain.AppearanceMode
import com.sun.jna.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DesktopAppearancePreferenceTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun appearanceModeDefaultsToSystemAndPersistsChanges() =
        runTest {
            val file = tempFolder.newFile("appearance.preferences_pb")
            val owner = SupervisorJob()
            val ds =
                PreferenceDataStoreFactory.createWithPath(
                    scope = CoroutineScope(owner + Dispatchers.IO),
                    produceFile = { file.absolutePath.toPath() },
                )
            try {
                val prefs = DesktopUserPreferences(ds)

                assertEquals(AppearanceMode.SYSTEM, prefs.appearanceModeFlow.first())
                assertTrue(AppearanceMode.SYSTEM.resolveDarkTheme(systemDark = true))
                assertFalse(AppearanceMode.SYSTEM.resolveDarkTheme(systemDark = false))
                assertFalse(AppearanceMode.LIGHT.resolveDarkTheme(systemDark = true))
                assertFalse(AppearanceMode.LIGHT.resolveDarkTheme(systemDark = false))
                assertTrue(AppearanceMode.DARK.resolveDarkTheme(systemDark = false))
                assertTrue(AppearanceMode.DARK.resolveDarkTheme(systemDark = true))

                prefs.setAppearanceMode(AppearanceMode.LIGHT)
                assertEquals(AppearanceMode.LIGHT, prefs.appearanceModeFlow.first())

                prefs.setAppearanceMode(AppearanceMode.DARK)
                assertEquals(AppearanceMode.DARK, prefs.appearanceModeFlow.first())

                prefs.setAppearanceMode(AppearanceMode.SYSTEM)
                assertEquals(AppearanceMode.SYSTEM, prefs.appearanceModeFlow.first())
            } finally {
                owner.cancelAndJoin()
            }
        }

    @Test
    fun appearanceModeSurvivesReopeningAndFallsBackOnUnknownStorageValue() =
        runBlocking {
            val file = File(tempFolder.root, "appearance-reopen.preferences_pb")

            val writeOwner = SupervisorJob()
            val writeStore =
                PreferenceDataStoreFactory.createWithPath(
                    scope = CoroutineScope(writeOwner + Dispatchers.IO),
                    produceFile = { file.absolutePath.toPath() },
                )
            try {
                val prefs = DesktopUserPreferences(writeStore)
                prefs.setAppearanceMode(AppearanceMode.DARK)
                assertEquals(AppearanceMode.DARK, prefs.getAppearanceModeSnapshot())
            } finally {
                writeOwner.cancelAndJoin()
            }

            val readOwner = SupervisorJob()
            val readStore =
                PreferenceDataStoreFactory.createWithPath(
                    scope = CoroutineScope(readOwner + Dispatchers.IO),
                    produceFile = { file.absolutePath.toPath() },
                )
            try {
                val prefs = DesktopUserPreferences(readStore)
                assertEquals(AppearanceMode.DARK, prefs.appearanceModeFlow.first())

                readStore.edit { it[DesktopUserPreferences.APPEARANCE_MODE] = "unexpected_value" }
                assertEquals(AppearanceMode.SYSTEM, prefs.appearanceModeFlow.first())
            } finally {
                readOwner.cancelAndJoin()
            }
        }

    @Test
    fun desktopSystemAppearanceSafelyDefaultsOnNonWindows() {
        if (!Platform.isWindows()) {
            assertFalse(DesktopSystemAppearance.isSystemDarkTheme())
            assertFalse(DesktopSystemAppearance.isReducedMotionEnabled())
        }
    }
}
