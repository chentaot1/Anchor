package com.anchor.adhd.desktop.db

import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

internal object DesktopBackupFiles {
    fun write(file: File, json: String) {
        val target = file.toPath().toAbsolutePath()
        val parent = target.parent
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, ".anchor-backup-", ".tmp")
        try {
            Files.writeString(temporary, json, Charsets.UTF_8)
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
