package com.anchor.adhd.desktop.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.TimeUnit

/** Runs update commands without blocking cancellation or leaving child processes behind. */
internal class DesktopCommandRunner(private val workingDirectory: File) {
    suspend fun run(
        vararg command: String,
        timeoutSeconds: Long = 30,
    ): DesktopUpdateManager.CommandResult = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(*command).directory(workingDirectory).start()
            val stdout = async { runCatching { process.inputStream.bufferedReader().use { it.readText() } }.getOrDefault("") }
            val stderr = async { runCatching { process.errorStream.bufferedReader().use { it.readText() } }.getOrDefault("") }
            try {
                withTimeoutOrNull(timeoutSeconds * 1000L) {
                    while (!process.waitFor(100, TimeUnit.MILLISECONDS)) ensureActive()
                    DesktopUpdateManager.CommandResult(process.exitValue(), stdout.await(), stderr.await())
                } ?: DesktopUpdateManager.CommandResult(-1, "", "Command timed out after ${timeoutSeconds}s")
            } finally {
                // A Git credential helper or build subprocess can also keep the output pipes open.
                if (process.isAlive || !stdout.isCompleted || !stderr.isCompleted) {
                    process.descendants().use { children -> children.forEach { it.destroyForcibly() } }
                    process.destroyForcibly()
                    process.inputStream.close()
                    process.errorStream.close()
                }
                stdout.cancel()
                stderr.cancel()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            DesktopUpdateManager.CommandResult(-1, "", error.message ?: "Execution error")
        }
    }
}
