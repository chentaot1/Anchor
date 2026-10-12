package com.anchor.adhd.desktop.focus

import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import com.anchor.adhd.desktop.db.DesktopBackupFiles
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class DesktopFocusSaveQueueTest {
    @get:Rule val folder = TemporaryFolder()
    private val dbFile get() = File(folder.root, "anchor.db")
    private val recovery get() = File(folder.root, "pending-focus")
    private fun database() = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), dbFile)
    private fun connection() = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
    private fun credit(title: String = "Essay", end: LocalDateTime = LocalDateTime.of(2026, 10, 11, 10, 0)) =
        DesktopFocusCredit(title, 1500, 600, true, endedAtMillis = end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    private suspend fun save(db: AnchorDesktopDatabase, credit: DesktopFocusCredit) {
        db.recordFocusSession(credit.taskTitle, credit.plannedSeconds, credit.actualSeconds, credit.completed,
            LocalDateTime.ofInstant(Instant.ofEpochMilli(credit.endedAtMillis), ZoneId.systemDefault()), credit.saveKey)
    }
    private fun queue(db: AnchorDesktopDatabase) = DesktopFocusSaveQueue(recovery, save = { save(db, it) })

    @Test fun databaseFailureJournalsTheWholeBatchAndRestartKeepsOriginalDates() = runBlocking {
        val db = database()
        db.initialize()
        connection().use { it.createStatement().use { statement ->
            statement.execute("CREATE TRIGGER reject_progress BEFORE UPDATE ON harbor_stats BEGIN SELECT RAISE(ABORT, 'Try later'); END")
        } }
        val first = credit("Yesterday", LocalDateTime.of(2026, 10, 10, 10, 0))
        val second = credit("Today")
        val failed = queue(db)
        failed.enqueue(first)
        failed.enqueue(second)
        failed.retry()
        assertEquals(2, failed.state.value.pendingCount)
        assertEquals(0, failed.state.value.memoryOnlyCount)
        assertEquals(2, recovery.listFiles()!!.size)
        assertTrue(db.getFocusSessions().isEmpty())
        connection().use { it.createStatement().use { statement -> statement.execute("DROP TRIGGER reject_progress") } }
        val reopened = database()
        reopened.initialize()
        val restarted = queue(reopened)
        restarted.retry()
        assertEquals(listOf(first.endedAtMillis, second.endedAtMillis), reopened.getFocusSessions().map { it.endedAtMillis })
        assertEquals(2, reopened.harborState.value.totalSessions)
        assertEquals(20, reopened.harborState.value.totalMinutes)
        assertEquals(2, reopened.harborState.value.streakDays)
        assertEquals(0, restarted.state.value.pendingCount)
        assertNull(restarted.state.value.error)
        assertTrue(recovery.listFiles()!!.isEmpty())
    }

    @Test fun retryAfterCommitAndCleanupFailureDoesNotDuplicateHistoryOrRewards() = runBlocking {
        val db = database()
        db.initialize()
        val failedCleanup = DesktopFocusSaveQueue(recovery, save = { save(db, it) }, remove = { false })
        failedCleanup.enqueue(credit())
        failedCleanup.retry()
        assertEquals(1, db.getFocusSessions().size)
        assertEquals(1, failedCleanup.state.value.pendingCount)
        assertNotNull(failedCleanup.state.value.error)
        queue(db).retry()
        assertEquals(1, db.getFocusSessions().size)
        assertEquals(1, db.harborState.value.totalSessions)
        assertEquals(10, db.harborState.value.totalMinutes)
        assertTrue(recovery.listFiles()!!.isEmpty())
    }

    @Test fun failedJournalWriteKeepsWorkInMemoryAndCanBeRetried() = runBlocking {
        val db = database()
        db.initialize()
        var diskFull = true
        val pending = DesktopFocusSaveQueue(recovery, save = { save(db, it) }, write = { file, text ->
            if (diskFull) throw IOException("Disk full") else DesktopBackupFiles.write(file, text)
        })
        pending.enqueue(credit())
        pending.retry()
        assertEquals(1, pending.state.value.memoryOnlyCount)
        assertTrue(db.getFocusSessions().isEmpty())
        diskFull = false
        pending.retry()
        assertEquals(0, pending.state.value.pendingCount)
        assertEquals(1, db.getFocusSessions().size)
        assertNull(pending.state.value.error)
    }

    @Test fun concurrentRetriesSaveEachCreditOnce() = runBlocking {
        val db = database()
        db.initialize()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val pending = DesktopFocusSaveQueue(recovery, save = {
            calls++
            entered.complete(Unit)
            release.await()
            save(db, it)
        })
        pending.enqueue(credit())
        withTimeout(5000) {
            val first = async { pending.retry() }
            entered.await()
            val second = async { pending.retry() }
            release.complete(Unit)
            awaitAll(first, second)
        }
        assertEquals(1, calls)
        assertEquals(1, db.getFocusSessions().size)
        assertEquals(0, pending.state.value.pendingCount)
    }

    @Test fun unreadableRecoveryRecordIsPreservedWhileOtherWorkSaves() = runBlocking {
        val db = database()
        db.initialize()
        recovery.mkdirs()
        val broken = File(recovery, "broken.json")
        broken.writeText("{broken")
        val pending = queue(db)
        pending.enqueue(credit())
        pending.retry()
        assertEquals(1, db.getFocusSessions().size)
        assertEquals(1, pending.state.value.pendingCount)
        assertEquals("{broken", broken.readText())
        assertTrue(pending.state.value.error!!.contains(broken.absolutePath))
    }

    @Test fun cancellationAfterJournalingLeavesARecoverableSession() = runBlocking {
        val db = database()
        db.initialize()
        val entered = CompletableDeferred<Unit>()
        val interrupted = DesktopFocusSaveQueue(recovery, save = {
            entered.complete(Unit)
            awaitCancellation()
        })
        interrupted.enqueue(credit())
        withTimeout(5000) {
            val job = launch { interrupted.retry() }
            entered.await()
            job.cancelAndJoin()
        }
        assertFalse(interrupted.state.value.isSaving)
        assertEquals(1, recovery.listFiles()!!.size)
        val restarted = queue(db)
        restarted.retry()
        assertEquals(1, db.getFocusSessions().size)
        assertEquals(0, restarted.state.value.pendingCount)
    }

    @Test fun backupRestoreRetainsSaveIdentitySoRecoveryCannotAwardAgain() = runBlocking {
        val db = database()
        db.initialize()
        val original = credit()
        save(db, original)
        val backup = db.exportDataJson()
        db.importDataJson(backup, replaceExisting = true)
        save(db, original)
        assertEquals(1, db.getFocusSessions().size)
        assertEquals(1, db.harborState.value.totalSessions)
        assertEquals(10, db.harborState.value.totalMinutes)
    }

    @Test fun databaseMigrationPreservesOldHistoryAndAllowsIdempotentNewSaves() = runBlocking {
        Class.forName("org.sqlite.JDBC")
        connection().use { it.createStatement().use { statement ->
            statement.execute("CREATE TABLE focus_sessions(id INTEGER PRIMARY KEY AUTOINCREMENT,taskTitle TEXT NOT NULL,durationSeconds INTEGER NOT NULL,actualSeconds INTEGER NOT NULL,completed INTEGER DEFAULT 1,startedAtMillis INTEGER NOT NULL,endedAtMillis INTEGER NOT NULL)")
            statement.execute("INSERT INTO focus_sessions(taskTitle,durationSeconds,actualSeconds,completed,startedAtMillis,endedAtMillis) VALUES('Existing history',600,600,1,1000,601000)")
        } }
        val db = database()
        db.initialize()
        val newWork = credit()
        save(db, newWork)
        save(db, newWork)
        assertEquals(listOf("Existing history", "Essay"), db.getFocusSessions().map { it.taskTitle })
    }

    @Test fun recoveringOlderWorkDoesNotMoveCurrentStreakBackwards() = runBlocking {
        val db = database()
        db.initialize()
        save(db, credit("Day one", LocalDateTime.of(2026, 10, 10, 10, 0)))
        save(db, credit("Day two", LocalDateTime.of(2026, 10, 11, 10, 0)))
        save(db, credit("Older pending work", LocalDateTime.of(2026, 10, 9, 10, 0)))
        assertEquals(3, db.harborState.value.totalSessions)
        assertEquals(2, db.harborState.value.streakDays)
        assertEquals("2026-10-11", db.harborState.value.lastFocusLogicalDate)
    }
}
