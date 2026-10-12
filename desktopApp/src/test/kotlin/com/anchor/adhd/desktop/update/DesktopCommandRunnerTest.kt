package com.anchor.adhd.desktop.update

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.system.measureTimeMillis

class DesktopCommandRunnerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun drainsBothOutputStreamsAndPreservesFailureStatus() = runBlocking {
        assumeTrue(System.getProperty("os.name").contains("Windows"))
        val runner = DesktopCommandRunner(temporaryFolder.root)
        val result = runner.run(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
            "[Console]::Out.Write(('o' * 100000)); [Console]::Error.Write(('e' * 100000)); exit 7",
        )
        assertEquals(7, result.exitCode)
        assertEquals(100_000, result.stdout.length)
        assertEquals(100_000, result.stderr.length)
    }

    @Test
    fun timeoutTerminatesAStalledCommand() = runBlocking {
        assumeTrue(System.getProperty("os.name").contains("Windows"))
        val runner = DesktopCommandRunner(temporaryFolder.root)
        var result: DesktopUpdateManager.CommandResult? = null
        val duration = measureTimeMillis {
            result = runner.run(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                "Start-Sleep -Seconds 30", timeoutSeconds = 1,
            )
        }
        assertEquals(-1, result!!.exitCode)
        assertTrue(result!!.stderr.contains("timed out"))
        assertTrue("Timeout must not wait for the 30-second process", duration < 10_000)
    }

    @Test
    fun timeoutAlsoTerminatesChildProcesses() = runBlocking {
        assumeTrue(System.getProperty("os.name").contains("Windows"))
        val childFile = File(temporaryFolder.root, "child-id.txt")
        val result = DesktopCommandRunner(temporaryFolder.root).run(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
            "\$child = Start-Process powershell.exe -WindowStyle Hidden -PassThru " +
                "-ArgumentList '-NoProfile','-NonInteractive','-Command','Start-Sleep -Seconds 30'; " +
                "[IO.File]::WriteAllText('child-id.txt', [string]\$child.Id); Start-Sleep -Seconds 30",
            timeoutSeconds = 3,
        )
        assertEquals(-1, result.exitCode)
        assertTrue(result.stderr.contains("timed out"))
        assertTrue("Child process should have started", childFile.exists())
        val child = ProcessHandle.of(childFile.readText().trim().toLong()).orElse(null)
        if (child != null) {
            repeat(50) { if (child.isAlive) delay(20) }
            assertFalse("Timed-out update must not leave its child running", child.isAlive)
        }
    }

    @Test
    fun cancellationStopsTheProcessInsteadOfReturningAnError() = runBlocking {
        assumeTrue(System.getProperty("os.name").contains("Windows"))
        val pidFile = File(temporaryFolder.root, "process-id.txt")
        val runner = DesktopCommandRunner(temporaryFolder.root)
        val command = async {
            runner.run(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                "[IO.File]::WriteAllText('process-id.txt', [string]\$PID); Start-Sleep -Seconds 30",
            )
        }
        try {
            repeat(100) {
                if (!pidFile.exists()) delay(50)
            }
            assertTrue("Command should have started", pidFile.exists())
            val processId = pidFile.readText().trim().toLong()
            val duration = measureTimeMillis {
                command.cancel()
                command.join()
            }
            assertTrue(command.isCancelled)
            assertTrue("Cancellation should be prompt", duration < 5_000)
            val process = ProcessHandle.of(processId).orElse(null)
            if (process != null) {
                repeat(50) { if (process.isAlive) delay(20) }
                assertFalse("Cancelled update must not leave its process running", process.isAlive)
            }
        } finally {
            command.cancel()
            command.join()
        }
    }
}
