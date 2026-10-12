package com.anchor.adhd.desktop.platform

import java.io.Closeable
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** Owns exclusive access to one profile before preferences, recovery, or startup changes. */
internal object DesktopInstanceLock {
    sealed interface Result {
        class Acquired(val lease: Lease) : Result
        data object AlreadyRunning : Result
        class Failed(val file: Path, val cause: Exception) : Result
    }

    class Lease internal constructor(private val channel: FileChannel, private val lock: FileLock) : Closeable {
        override fun close() {
            try {
                if (lock.isValid) lock.release()
            } finally {
                channel.close()
            }
        }
    }

    fun acquire(appDataRoot: Path): Result {
        val file = appDataRoot.resolve("Anchor/anchor.lock").toAbsolutePath().normalize()
        var channel: FileChannel? = null
        return try {
            Files.createDirectories(file.parent)
            channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (lock == null) {
                channel.close()
                Result.AlreadyRunning
            } else {
                Result.Acquired(Lease(channel, lock))
            }
        } catch (error: Exception) {
            try {
                channel?.close()
            } catch (closeError: Exception) {
                error.addSuppressed(closeError)
            }
            Result.Failed(file, error)
        }
    }
}
