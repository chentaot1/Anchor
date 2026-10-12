package com.anchor.adhd.desktop

import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import com.anchor.adhd.desktop.db.DesktopBackupFiles
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DesktopDataReplacementConcurrencyTest {
    @get:Rule val folder = TemporaryFolder()
    private class BackupPause {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var fail = false
        fun write(file: File, json: String) {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS)) { "Test backup was not released" }
            if (fail) throw IOException("Backup unavailable")
            DesktopBackupFiles.write(file, json)
        }
    }
    private fun database(pause: BackupPause) = AnchorDesktopDatabase(
        CoroutineScope(Dispatchers.Unconfined), File(folder.root, "anchor.db"), pause::write,
    )
    private suspend fun waitForQueuedAccess(db: AnchorDesktopDatabase) = withTimeout(5000) {
        while (db.waitingDataAccessCount == 0) delay(10)
    }
    private suspend fun waitForBackup(pause: BackupPause) {
        assertTrue(withContext(Dispatchers.IO) { pause.entered.await(5, TimeUnit.SECONDS) })
    }

    @Test fun taskSavedDuringResetWaitsAndSurvivesInsteadOfFallingAfterTheRecoveryCopy() = runBlocking {
        val pause = BackupPause()
        val db = database(pause)
        db.initialize()
        db.importDataJson("""{"tasks":[{"title":"Original work"}]}""", true)
        val reset = async { db.clearAllTasks() }
        try {
            waitForBackup(pause)
            val addition = async { db.insertTask("New concurrent work", 20) }
            waitForQueuedAccess(db)
            assertFalse(addition.isCompleted)
            pause.release.countDown()
            reset.await()
            addition.await()
            assertTrue(db.tasks.value.any { it.title == "New concurrent work" })
            val originalBackup = Json.parseToJsonElement(db.lastAutoBackupFile!!.readText()).jsonObject
            assertEquals(listOf("Original work"), originalBackup.getValue("tasks").jsonArray.map { it.jsonObject.getValue("title").jsonPrimitive.content })
        } finally { pause.release.countDown() }
    }

    @Test fun focusSaveDuringRestoreWaitsAndKeepsBothHistoryAndTotals() = runBlocking {
        val pause = BackupPause()
        val db = database(pause)
        db.initialize()
        db.recordFocusSession("Original focus", 600, 600, true)
        val restore = async(Dispatchers.IO) { db.restoreBackup("""{"focus_sessions":[],"harbor_stats":{"totalSessions":0,"totalMinutes":0,"streakDays":1}}""") }
        try {
            waitForBackup(pause)
            val save = async { db.recordFocusSession("Concurrent focus", 1200, 1200, true) }
            waitForQueuedAccess(db)
            assertFalse(save.isCompleted)
            pause.release.countDown()
            restore.await()
            save.await()
            assertEquals(listOf("Concurrent focus"), db.getFocusSessions().map { it.taskTitle })
            assertEquals(1, db.harborState.value.totalSessions)
            assertEquals(20, db.harborState.value.totalMinutes)
            assertTrue(db.lastAutoBackupFile!!.readText().contains("Original focus"))
        } finally { pause.release.countDown() }
    }

    @Test fun failedRecoveryBackupReleasesAccessAndPreservesOriginalAndQueuedWork() = runBlocking {
        val pause = BackupPause().apply { fail = true }
        val db = database(pause)
        db.initialize()
        db.importDataJson("""{"tasks":[{"title":"Keep original"}]}""", true)
        val reset = async { runCatching { db.resetAllData() } }
        try {
            waitForBackup(pause)
            val addition = async { db.insertTask("Queued addition", 15) }
            waitForQueuedAccess(db)
            pause.release.countDown()
            assertTrue(reset.await().isFailure)
            addition.await()
            assertEquals(setOf("Keep original", "Queued addition"), db.tasks.value.map { it.title }.toSet())
            assertEquals(0, db.waitingDataAccessCount)
        } finally { pause.release.countDown() }
    }
}
