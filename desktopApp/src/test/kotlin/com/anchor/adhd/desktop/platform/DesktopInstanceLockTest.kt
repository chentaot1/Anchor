package com.anchor.adhd.desktop.platform

import com.anchor.adhd.desktop.withSingleInstance
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DesktopInstanceLockTest {
    @get:Rule val folder = TemporaryFolder()

    private fun acquire(root: Path): DesktopInstanceLock.Lease {
        val result = DesktopInstanceLock.acquire(root)
        assertTrue("Expected ownership, received $result", result is DesktopInstanceLock.Result.Acquired)
        return (result as DesktopInstanceLock.Result.Acquired).lease
    }

    @Test fun anOverlappingLaunchDoesNotStartOrReportAnIoFailure() {
        val root = folder.root.toPath()
        acquire(root).use {
            assertEquals(DesktopInstanceLock.Result.AlreadyRunning, DesktopInstanceLock.acquire(root))
            var restored = 0
            withSingleInstance(root,
                onAlreadyRunning = { restored++ },
                onFailure = { throw AssertionError("Contention reported as IO failure", it.cause) },
            ) { throw AssertionError("A second instance started") }
            assertEquals(1, restored)
        }
        acquire(root).close()
    }

    @Test fun aParentFileStopsStartupBeforeAnyApplicationWork() {
        val root = folder.newFile("not-a-directory").toPath()
        var reported: DesktopInstanceLock.Result.Failed? = null
        withSingleInstance(root,
            onAlreadyRunning = { throw AssertionError("IO failure reported as contention") },
            onFailure = { reported = it },
        ) { throw AssertionError("Application started without exclusive access") }
        assertEquals(root.resolve("Anchor/anchor.lock"), reported!!.file)
        assertTrue(root.toFile().isFile)
    }

    @Test fun aDirectoryInPlaceOfTheLockFileStopsStartupAndIsPreserved() {
        val lockDirectory = folder.newFolder("Anchor", "anchor.lock")
        val sentinel = File(lockDirectory, "keep.txt").apply { writeText("preserve") }
        var failed = false
        withSingleInstance(folder.root.toPath(), onFailure = { failed = true }) {
            throw AssertionError("Application started when its lock could not be opened")
        }
        assertTrue(failed)
        assertEquals("preserve", sentinel.readText())
    }

    @Test fun independentProfilesCanRunAtTheSameTime() {
        acquire(folder.newFolder("first").toPath()).use {
            acquire(folder.newFolder("second").toPath()).use { }
        }
    }

    @Test fun aStartupExceptionReleasesTheLease() {
        val failure = IllegalStateException("startup failed")
        try {
            withSingleInstance(folder.root.toPath()) { throw failure }
            throw AssertionError("Startup exception swallowed")
        } catch (actual: IllegalStateException) {
            assertTrue(actual === failure)
        }
        acquire(folder.root.toPath()).close()
    }

    @Test fun anOldLockFileDoesNotBlockStartupAndClosingIsIdempotent() {
        val lockFile = File(folder.newFolder("Anchor"), "anchor.lock").apply { writeText("old file") }
        val lease = acquire(folder.root.toPath())
        lease.close()
        lease.close()
        assertEquals("old file", lockFile.readText())
        acquire(folder.root.toPath()).close()
    }

    @Test fun separateProcessesContendAndForcedTerminationReleasesOwnership() = runBlocking {
        val java = File(System.getProperty("java.home"), "bin/" +
            if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
        fun launch(mode: String, log: File) = ProcessBuilder(
            java.absolutePath, "-cp", System.getProperty("anchor.test.classpath"),
            "com.anchor.adhd.desktop.platform.InstanceLockChild", folder.root.absolutePath, mode,
        ).redirectErrorStream(true).redirectOutput(log).start()
        val log = File(folder.root, "holder.log")
        val holder = launch("hold", log)
        try {
            withTimeout(10000) {
                while (!File(folder.root, "holder-ready").exists()) {
                    check(holder.isAlive) { "Lock child exited: ${log.readText()}" }
                    delay(25)
                }
            }
            assertEquals(DesktopInstanceLock.Result.AlreadyRunning, DesktopInstanceLock.acquire(folder.root.toPath()))
            val probeLog = File(folder.root, "probe.log")
            val probe = launch("probe", probeLog)
            try {
                assertTrue("Probe did not exit", probe.waitFor(10, TimeUnit.SECONDS))
                assertEquals(probeLog.readText(), 0, probe.exitValue())
                assertEquals("BUSY", probeLog.readText().trim())
            } finally {
                if (probe.isAlive) { probe.destroyForcibly(); probe.waitFor(5, TimeUnit.SECONDS) }
            }
            holder.destroyForcibly()
            assertTrue(holder.waitFor(5, TimeUnit.SECONDS))
            // Windows may signal process exit just before its last file lock is released.
            withTimeout(5000) {
                while (true) {
                    when (val result = DesktopInstanceLock.acquire(folder.root.toPath())) {
                        is DesktopInstanceLock.Result.Acquired -> { result.lease.close(); break }
                        DesktopInstanceLock.Result.AlreadyRunning -> delay(25)
                        is DesktopInstanceLock.Result.Failed -> throw result.cause
                    }
                }
            }
        } finally {
            if (holder.isAlive) { holder.destroyForcibly(); holder.waitFor(5, TimeUnit.SECONDS) }
        }
    }
}

/** Test-only child; ownership never reaches preferences, a database, or Windows startup. */
object InstanceLockChild {
    @JvmStatic fun main(args: Array<String>) {
        val root = File(args[0])
        if (args[1] == "error-dialog") {
            val blocked = File(root, "blocked-profile").apply { mkdirs() }
            val obstruction = File(blocked, "Anchor")
            check(!obstruction.exists() || obstruction.isFile)
            obstruction.writeText("Disposable startup-error fixture")
            withSingleInstance(blocked.toPath(), onAlreadyRunning = { error("Unexpected contention") }) {
                error("Startup was allowed through an obstructed data folder")
            }
            return
        }
        when (val result = DesktopInstanceLock.acquire(root.toPath())) {
            is DesktopInstanceLock.Result.Acquired -> result.lease.use {
                if (args[1] == "hold") {
                    File(root, "holder-ready").writeText("ready")
                    CountDownLatch(1).await()
                } else {
                    println("ACQUIRED")
                }
            }
            DesktopInstanceLock.Result.AlreadyRunning -> println("BUSY")
            is DesktopInstanceLock.Result.Failed -> throw result.cause
        }
    }
}
