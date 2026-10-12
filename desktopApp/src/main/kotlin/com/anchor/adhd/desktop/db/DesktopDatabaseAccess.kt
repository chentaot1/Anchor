package com.anchor.adhd.desktop.db

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

/** Ordinary connections share access; replacement reserves the entire backup/transaction sequence. */
internal class DesktopDatabaseAccess {
    private val lock = ReentrantReadWriteLock(true)
    val waitingCount: Int get() = lock.queueLength
    fun <T> connection(action: () -> T): T = lock.readLock().withLock(action)
    fun <T> replace(action: () -> T): T = lock.writeLock().withLock(action)
}
