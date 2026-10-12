package com.anchor.adhd.desktop

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.anchor.adhd.desktop.prefs.DesktopUserPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DesktopDurationPreferencesTest {
    @get:Rule val temporary = TemporaryFolder()

    private suspend fun withPreferences(file: File, action: suspend (DesktopUserPreferences) -> Unit) {
        val owner = SupervisorJob()
        val store = PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(owner + Dispatchers.IO),
            produceFile = { file.absolutePath.toPath() },
        )
        try {
            action(DesktopUserPreferences(store))
        } finally {
            owner.cancelAndJoin()
        }
    }

    @Test fun absentDurationsKeepTheirExistingDefaults() = runBlocking {
        withPreferences(File(temporary.root, "defaults.preferences_pb")) { prefs ->
            assertEquals(25, prefs.focusWorkMinutes.first())
            assertEquals(25, prefs.getFocusWorkMinutesSnapshot())
            assertEquals(5, prefs.focusBreakMinutes.first())
        }
    }

    @Test fun exactDurationsAndBothBoundsSurviveReopeningThePreferenceFile() = runBlocking {
        val file = File(temporary.root, "exact.preferences_pb")
        for (minutes in listOf(1, 13, 91, 239, 240)) {
            withPreferences(file) { prefs ->
                prefs.setFocusWorkMinutes(minutes)
                prefs.setFocusBreakMinutes(minutes)
                assertEquals(minutes, prefs.focusWorkMinutes.first())
                assertEquals(minutes, prefs.focusBreakMinutes.first())
            }
            withPreferences(file) { prefs ->
                assertEquals(minutes, prefs.getFocusWorkMinutesSnapshot())
                assertEquals(minutes, prefs.focusWorkMinutes.first())
                assertEquals(minutes, prefs.focusBreakMinutes.first())
            }
        }
    }

    @Test fun invalidWritesLeaveBothSavedDurationsUnchanged() = runBlocking {
        withPreferences(File(temporary.root, "invalid.preferences_pb")) { prefs ->
            prefs.setFocusWorkMinutes(13)
            prefs.setFocusBreakMinutes(7)
            for (minutes in listOf(Int.MIN_VALUE, -1, 0, 241, Int.MAX_VALUE)) {
                try {
                    prefs.setFocusWorkMinutes(minutes)
                    fail("Invalid work duration must be rejected")
                } catch (_: IllegalArgumentException) { }
                try {
                    prefs.setFocusBreakMinutes(minutes)
                    fail("Invalid break duration must be rejected")
                } catch (_: IllegalArgumentException) { }
                assertEquals(13, prefs.getFocusWorkMinutesSnapshot())
                assertEquals(7, prefs.focusBreakMinutes.first())
            }
        }
    }

    @Test fun legacyOutOfRangePreferencesAreBoundedForEveryTimerReadWithoutRewritingThem() = runBlocking {
        val owner = SupervisorJob()
        val store = PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(owner + Dispatchers.IO),
            produceFile = { File(temporary.root, "legacy.preferences_pb").absolutePath.toPath() },
        )
        try {
            val prefs = DesktopUserPreferences(store)
            for ((work, rest) in listOf(Int.MIN_VALUE to Int.MAX_VALUE, 0 to 241, 300 to -1)) {
                store.edit {
                    it[DesktopUserPreferences.FOCUS_WORK_MINUTES] = work
                    it[DesktopUserPreferences.FOCUS_BREAK_MINUTES] = rest
                }
                assertEquals(work.coerceIn(1, 240), prefs.focusWorkMinutes.first())
                assertEquals(work.coerceIn(1, 240), prefs.getFocusWorkMinutesSnapshot())
                assertEquals(rest.coerceIn(1, 240), prefs.focusBreakMinutes.first())
                assertEquals(work, store.data.first()[DesktopUserPreferences.FOCUS_WORK_MINUTES])
                assertEquals(rest, store.data.first()[DesktopUserPreferences.FOCUS_BREAK_MINUTES])
            }
        } finally {
            owner.cancelAndJoin()
        }
    }
}
