package com.anchor.adhd.desktop.focus

import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import com.anchor.adhd.desktop.db.DesktopBackupFiles
import com.anchor.adhd.desktop.db.DesktopTask
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DesktopTimerRecoveryTest {
    @get:Rule val folder = TemporaryFolder()
    private val file get() = File(folder.root, "timer-recovery.json")
    private val task = DesktopTask(42, "Essay ⚓", notes = "Outline first", durationMinutes = 25, difficulty = "HARD", createdAtMillis = 1792000000000)
    private fun active(): DesktopActiveTimer {
        var nanos = 0L
        val timer = DesktopCountdown(1500) { nanos }
        val ledger = DesktopFocusLedger()
        ledger.begin(task, 1500)
        timer.resume()
        nanos = 600_400_000_000L
        return DesktopActiveTimer(timer.snapshot(), ledger.snapshot(), task, true, 25, 25)
    }
    private fun db() = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), File(folder.root, "anchor.db"))
    private suspend fun save(database: AnchorDesktopDatabase, credit: DesktopFocusCredit) {
        database.recordFocusSession(credit.taskTitle, credit.plannedSeconds, credit.actualSeconds, credit.completed,
            LocalDateTime.ofInstant(Instant.ofEpochMilli(credit.endedAtMillis), ZoneId.systemDefault()), credit.saveKey)
    }

    @Test fun restartRestoresTaskAndFractionalTimePausedWithoutCreditingOfflineTime() = runBlocking {
        val original = active()
        val store = DesktopTimerRecoveryStore(file)
        store.load()
        store.stage(DesktopTimerRecovery(timer = original))
        store.persist()
        val recovered = DesktopTimerRecoveryStore(file).load()!!.restorableTimer { false }!!
        assertEquals(original.task, recovered.task)
        var nanos = 8_000_000_000_000L
        val timer = DesktopCountdown(0) { nanos }
        timer.restore(recovered.clock)
        val ledger = DesktopFocusLedger()
        ledger.restore(recovered.session!!)
        nanos += 3_600_000_000_000L
        assertEquals(900, timer.remainingSeconds)
        assertEquals(600, timer.elapsedSeconds)
        timer.resume()
        nanos += 600_000_000L
        assertEquals(899, timer.remainingSeconds)
        assertEquals(601, timer.elapsedSeconds)
        assertEquals(original.session!!.saveKey, ledger.finish(timer.elapsedSeconds, false)!!.saveKey)
    }

    @Test fun interruptedCompletionHandoffRecoversCreditWithItsOriginalIdentityAndDate() = runBlocking {
        val timer = active()
        val ledger = DesktopFocusLedger()
        ledger.restore(timer.session!!)
        val credit = ledger.finish(1500, true, 1792000000000)!!
        val store = DesktopTimerRecoveryStore(file)
        store.load()
        store.stage(DesktopTimerRecovery(credits = listOf(credit)))
        store.persist()
        val recovered = DesktopTimerRecoveryStore(file).load()!!
        assertNull(recovered.timer)
        val database = db()
        database.initialize()
        val queue = DesktopFocusSaveQueue(File(folder.root, "pending-focus"), save = { save(database, it) })
        recovered.credits.forEach(queue::enqueue)
        queue.retry()
        recovered.credits.forEach(queue::enqueue)
        queue.retry()
        assertEquals(1, database.getFocusSessions().size)
        assertEquals(credit.endedAtMillis, database.getFocusSessions().single().endedAtMillis)
        assertEquals(1, database.harborState.value.totalSessions)
    }

    @Test fun suspendedTimeCannotCompleteFocusAndRecoverySavesOnlyAwakeWork() = runBlocking {
        var awakeNanos = 0L
        var wallMillis = LocalDateTime.of(2026, 10, 11, 0, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val clock = DesktopCountdown(1500) { awakeNanos }
        val ledger = DesktopFocusLedger()
        ledger.begin(task, 1500)
        val originalKey = ledger.snapshot()!!.saveKey
        clock.resume()
        awakeNanos += 600_400_000_000L
        wallMillis += 600_400
        assertEquals(900, clock.remainingSeconds)

        // Model six hours asleep: wall time advances, Windows' awake clock does not.
        wallMillis += 6 * 60 * 60_000L
        assertEquals(900, clock.remainingSeconds)
        assertEquals(600, clock.elapsedSeconds)
        val store = DesktopTimerRecoveryStore(file)
        store.load()
        store.stage(DesktopTimerRecovery(
            timer = DesktopActiveTimer(clock.snapshot(), ledger.snapshot(), task, true, 25, 25),
            capturedAtMillis = wallMillis,
        ))
        store.persist()
        val recovered = DesktopTimerRecoveryStore(file).load()!!.timer!!
        // A new process has a new clock baseline; saved durations retain their meaning.
        awakeNanos = 10_000_000_000_000L
        val restoredClock = DesktopCountdown(0) { awakeNanos }
        restoredClock.restore(recovered.clock)
        val restoredLedger = DesktopFocusLedger()
        restoredLedger.restore(recovered.session!!)
        restoredClock.resume()
        awakeNanos += 600_000_000L
        wallMillis += 600
        assertEquals(899, restoredClock.remainingSeconds)
        val credit = restoredLedger.finish(restoredClock.elapsedSeconds, false, wallMillis)!!
        assertEquals(601, credit.actualSeconds)
        assertFalse(credit.completed)
        assertEquals(originalKey, credit.saveKey)
        val database = db()
        database.initialize()
        save(database, credit)
        assertEquals(601, database.getFocusSessions().single().actualSeconds)
        assertEquals(wallMillis, database.getFocusSessions().single().endedAtMillis)
        assertEquals(10, database.harborState.value.totalMinutes)
        assertEquals(1, database.harborState.value.totalSessions)
        assertEquals(0, database.harborState.value.streakDays)
    }

    @Test fun staleActiveCheckpointCannotResumeAlreadyCommittedOrHandedOffWork() = runBlocking {
        val timer = active()
        val ledger = DesktopFocusLedger()
        ledger.restore(timer.session!!)
        val credit = ledger.finish(1500, true)!!
        val database = db()
        database.initialize()
        save(database, credit)
        assertNull(DesktopTimerRecovery(timer = timer).restorableTimer(database::hasFocusSave))
        assertNull(DesktopTimerRecovery(timer = timer, credits = listOf(credit)).restorableTimer { error("A handed-off session needs no database read") })
    }

    @Test fun pausedBreakRetainsItsRemainingTimeAndNextWorkDuration() = runBlocking {
        val timer = DesktopActiveTimer(DesktopCountdownState(123_400_000_000L, 176_600_000_000L), null, task, false, 5, 40)
        val store = DesktopTimerRecoveryStore(file)
        store.load()
        store.stage(DesktopTimerRecovery(timer = timer))
        store.persist()
        val restored = DesktopTimerRecoveryStore(file).load()!!.restorableTimer { error("Breaks have no focus credit") }!!
        assertEquals(timer, restored)
        assertEquals(40, restored.savedWorkDurationMinutes)
        val clock = DesktopCountdown(0)
        clock.restore(restored.clock)
        assertEquals(124, clock.remainingSeconds)
    }

    @Test fun failedCheckpointWritePreservesLastGoodFileAndRetriesLatestState() = runBlocking {
        var fail = false
        val store = DesktopTimerRecoveryStore(file, write = { target, text ->
            if (fail) throw IOException("Disk full") else DesktopBackupFiles.write(target, text)
        })
        store.load()
        val original = DesktopTimerRecovery(timer = active())
        store.stage(original)
        store.persist()
        val bytes = file.readBytes()
        val latest = original.copy(timer = original.timer!!.copy(clock = DesktopCountdownState(500_000_000_000L, 1000_000_000_000L)))
        store.stage(latest)
        fail = true
        store.persist()
        assertArrayEquals(bytes, file.readBytes())
        assertNotNull(store.status.value.error)
        fail = false
        store.persist()
        assertEquals(latest, DesktopTimerRecoveryStore(file).load())
        assertNull(store.status.value.error)
    }

    @Test fun queuedWritesFinishWithTheNewestSnapshot() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var writes = 0
        val store = DesktopTimerRecoveryStore(file, write = { target, text ->
            if (writes++ == 0) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            DesktopBackupFiles.write(target, text)
        })
        store.load()
        store.stage(DesktopTimerRecovery(timer = active()))
        val first = async { store.persist() }
        assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
        val latest = DesktopTimerRecovery()
        store.stage(latest)
        release.countDown()
        first.await()
        assertTrue("Finishing an older write must not report the new snapshot as saved", store.status.value.hasPendingWrite)
        store.persist()
        assertFalse(store.status.value.hasPendingWrite)
        assertEquals(latest, DesktopTimerRecoveryStore(file).load())
    }

    @Test fun unreadableFileCannotBeOverwrittenAndExplicitArchiveKeepsExactBytes() = runBlocking {
        val original = byteArrayOf(0x7b, 0xff.toByte(), 0x01)
        file.writeBytes(original)
        val store = DesktopTimerRecoveryStore(file)
        assertNull(store.load())
        store.stage(DesktopTimerRecovery(timer = active()))
        store.persist()
        assertArrayEquals(original, file.readBytes())
        assertTrue(store.status.value.damagedFile)
        val archive = store.archiveDamagedFile()
        assertArrayEquals(original, archive.readBytes())
        store.persist()
        assertNotNull(DesktopTimerRecoveryStore(file).load()!!.timer)
        assertFalse(store.status.value.damagedFile)
    }

    @Test fun failedArchiveCannotUnlockOrOverwriteTheOriginal() = runBlocking {
        file.writeText("{broken")
        val store = DesktopTimerRecoveryStore(file, archiveCopy = { _, _ -> throw IOException("Archive failed") })
        store.load()
        store.stage(DesktopTimerRecovery())
        assertTrue(runCatching { store.archiveDamagedFile() }.isFailure)
        store.persist()
        assertEquals("{broken", file.readText())
        assertTrue(store.status.value.damagedFile)
    }

    @Test fun closingTheTimerPreventsOldWorkFromReappearingAfterRestart() = runBlocking {
        val store = DesktopTimerRecoveryStore(file)
        store.load()
        store.stage(DesktopTimerRecovery(timer = active()))
        store.persist()
        store.stage(DesktopTimerRecovery())
        store.persist()
        assertNull(DesktopTimerRecoveryStore(file).load()!!.timer)
    }

    @Test fun invalidVersionsAndClockValuesArePreservedForRepair() = runBlocking {
        for (original in listOf("""{"version":99}""", """{"version":1,"timer":{"clock":{"remainingNanos":-1,"elapsedNanos":0},"session":null,"task":null,"isWorkPhase":false,"durationMinutes":5,"savedWorkDurationMinutes":25}}""")) {
            file.writeText(original)
            val store = DesktopTimerRecoveryStore(file)
            assertNull(store.load())
            assertTrue(store.status.value.damagedFile)
            store.stage(DesktopTimerRecovery())
            store.persist()
            assertEquals(original, file.readText())
        }
    }

    @Test fun timerThatReachedZeroBeforeCrashKeepsItsOriginalCompletionDate() = runBlocking {
        val original = active()
        val endedAt = LocalDateTime.of(2026, 10, 11, 3, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val zero = original.copy(clock = DesktopCountdownState(0, 1500_000_000_000L))
        val store = DesktopTimerRecoveryStore(file)
        store.load()
        store.stage(DesktopTimerRecovery(timer = zero, capturedAtMillis = endedAt))
        store.persist()
        val recovered = DesktopTimerRecoveryStore(file).load()!!
        val clock = DesktopCountdown(0)
        clock.restore(recovered.timer!!.clock)
        val ledger = DesktopFocusLedger()
        ledger.restore(recovered.timer.session!!)
        val credit = ledger.finish(clock.elapsedSeconds, true, recovered.capturedAtMillis)!!
        val database = db()
        database.initialize()
        save(database, credit)
        assertEquals(1500, database.getFocusSessions().single().actualSeconds)
        assertEquals(endedAt, database.getFocusSessions().single().endedAtMillis)
        assertEquals("2026-10-10", database.harborState.value.lastFocusLogicalDate)
    }

    @Test fun checkpointSurvivesForcedTerminationOfASeparateJvm() = runBlocking {
        val java = File(System.getProperty("java.home"), "bin/" + if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
        val log = File(folder.root, "child.log")
        val process = ProcessBuilder(java.absolutePath, "-cp", System.getProperty("anchor.test.classpath"),
            "com.anchor.adhd.desktop.focus.TimerRecoveryCrashChild", folder.root.absolutePath)
            .redirectErrorStream(true).redirectOutput(log).start()
        try {
            withTimeout(10000) {
                while (!File(folder.root, "checkpoint-ready").exists()) {
                    check(process.isAlive) { "Recovery child exited: ${log.readText()}" }
                    delay(25)
                }
            }
            process.destroyForcibly()
            assertTrue(process.waitFor(5, TimeUnit.SECONDS))
            val recovery = DesktopTimerRecoveryStore(file).load()!!
            assertEquals("Crash-test essay", recovery.timer!!.task!!.title)
            assertEquals(600_400_000_000L, recovery.timer.clock.elapsedNanos)
            val clock = DesktopCountdown(0) { Long.MAX_VALUE }
            clock.restore(recovery.timer.clock)
            assertEquals(600, clock.elapsedSeconds)
            assertEquals(900, clock.remainingSeconds)
        } finally {
            if (process.isAlive) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS) }
        }
    }
}

/** Separate process used solely with the disposable test data directory. */
object TimerRecoveryCrashChild {
    @JvmStatic fun main(args: Array<String>): Unit = runBlocking {
        val root = File(args.single())
        val task = DesktopTask(42, "Crash-test essay", notes = "Keep this task")
        val ledger = DesktopFocusLedger()
        ledger.begin(task, 1500)
        val timer = DesktopActiveTimer(DesktopCountdownState(899_600_000_000L, 600_400_000_000L), ledger.snapshot(), task, true, 25, 25)
        val store = DesktopTimerRecoveryStore(File(root, "timer-recovery.json"))
        store.load()
        store.stage(DesktopTimerRecovery(timer = timer))
        store.persist()
        check(store.status.value.error == null)
        File(root, "checkpoint-ready").writeText("ready")
        awaitCancellation()
    }
}
