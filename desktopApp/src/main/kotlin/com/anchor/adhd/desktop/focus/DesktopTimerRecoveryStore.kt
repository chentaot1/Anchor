package com.anchor.adhd.desktop.focus

import com.anchor.adhd.desktop.DesktopDurationLimits

import com.anchor.adhd.desktop.db.DesktopBackupFiles
import com.anchor.adhd.desktop.db.DesktopTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.UUID

@Serializable
internal data class DesktopActiveTimer(
    val clock: DesktopCountdownState,
    val session: DesktopFocusSessionState?,
    val task: DesktopTask?,
    val isWorkPhase: Boolean,
    val durationMinutes: Int,
    val savedWorkDurationMinutes: Int,
)

@Serializable
internal data class DesktopTimerRecovery(
    val version: Int = 1,
    val timer: DesktopActiveTimer? = null,
    val credits: List<DesktopFocusCredit> = emptyList(),
    val capturedAtMillis: Long = System.currentTimeMillis(),
) {
    fun restorableTimer(isFinished: (String) -> Boolean): DesktopActiveTimer? {
        val active = timer ?: return null
        val key = active.session?.saveKey ?: return active
        return if (credits.any { it.saveKey == key } || isFinished(key)) null else active
    }
}

internal data class DesktopTimerRecoveryStatus(
    val loaded: Boolean = false,
    val error: String? = null,
    val damagedFile: Boolean = false,
    val hasPendingWrite: Boolean = false,
)

/** Coalesces immutable snapshots and never overwrites an unreadable recovery file. */
internal class DesktopTimerRecoveryStore(
    val file: File,
    private val write: (File, String) -> Unit = DesktopBackupFiles::write,
    private val archiveCopy: (File, File) -> Unit = { original, archive ->
        Files.copy(original.toPath(), archive.toPath())
        FileChannel.open(archive.toPath(), StandardOpenOption.WRITE).use { it.force(true) }
    },
) {
    private val json = Json { encodeDefaults = true }
    private val mutex = Mutex()
    private var desired = DesktopTimerRecovery()
    private var revision = 0L
    @Volatile private var writable = true
    private val _status = MutableStateFlow(DesktopTimerRecoveryStatus())
    val status = _status.asStateFlow()

    @Synchronized fun stage(snapshot: DesktopTimerRecovery) {
        desired = snapshot
        revision++
        _status.update { it.copy(hasPendingWrite = true) }
    }

    fun protectOriginal(message: String) {
        writable = false
        _status.update { it.copy(error = message, damagedFile = true) }
    }

    suspend fun load(): DesktopTimerRecovery? = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val snapshot = if (file.exists()) json.decodeFromString<DesktopTimerRecovery>(file.readText(Charsets.UTF_8)) else DesktopTimerRecovery()
                validate(snapshot)
                stage(snapshot)
                writable = true
                _status.value = DesktopTimerRecoveryStatus(loaded = true)
                snapshot
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                writable = false
                _status.value = DesktopTimerRecoveryStatus(true, "Timer recovery could not be read: ${file.absolutePath}", damagedFile = true)
                null
            }
        }
    }

    suspend fun persist() = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                check(_status.value.loaded && writable) { "The existing timer recovery file needs attention: ${file.absolutePath}" }
                val (snapshot, stagedRevision) = synchronized(this@DesktopTimerRecoveryStore) { desired to revision }
                validate(snapshot)
                write(file, json.encodeToString(DesktopTimerRecovery.serializer(), snapshot))
                synchronized(this@DesktopTimerRecoveryStore) {
                    _status.update { it.copy(error = null, hasPendingWrite = revision != stagedRevision) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _status.update { it.copy(error = error.message ?: "Timer recovery could not be saved.") }
            }
        }
    }

    /** Explicit repair keeps the original bytes in a separate file before allowing new checkpoints. */
    suspend fun archiveDamagedFile(): File = mutex.withLock {
        withContext(Dispatchers.IO) {
            check(_status.value.damagedFile) { "No damaged timer recovery file to archive." }
            val archive = File(file.parentFile, "timer-recovery-unreadable-${UUID.randomUUID()}.json")
            archiveCopy(file, archive)
            writable = true
            _status.update { it.copy(damagedFile = false, error = null) }
            archive
        }
    }

    private fun validate(snapshot: DesktopTimerRecovery) {
        require(snapshot.version == 1 && snapshot.capturedAtMillis > 0)
        snapshot.timer?.let { timer ->
            DesktopCountdown(0).restore(timer.clock)
            require(timer.durationMinutes in DesktopDurationLimits.MIN_MINUTES..DesktopDurationLimits.MAX_MINUTES && timer.savedWorkDurationMinutes in DesktopDurationLimits.MIN_MINUTES..DesktopDurationLimits.MAX_MINUTES)
            require((timer.session != null) == timer.isWorkPhase)
            if (timer.isWorkPhase) require(timer.session?.task == timer.task)
            timer.session?.let { DesktopFocusLedger().restore(it) }
        }
        require(snapshot.credits.map { it.saveKey }.distinct().size == snapshot.credits.size)
        snapshot.credits.forEach {
            require(UUID.fromString(it.saveKey).toString() == it.saveKey && it.taskTitle.isNotBlank())
            require(it.plannedSeconds in 1..86400 && it.actualSeconds >= 0 && it.endedAtMillis > 0)
        }
    }
}
