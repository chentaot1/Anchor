package com.anchor.adhd.desktop.focus

import com.anchor.adhd.desktop.db.DesktopBackupFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID

internal data class DesktopFocusSaveState(
    val isReady: Boolean = false,
    val isSaving: Boolean = false,
    val pendingCount: Int = 0,
    val memoryOnlyCount: Int = 0,
    val error: String? = null,
    val savedRevision: Int = 0,
)

/** Journals finished work before database writes and retains it until an idempotent save succeeds. */
internal class DesktopFocusSaveQueue(
    private val directory: File,
    private val save: suspend (DesktopFocusCredit) -> Unit,
    private val write: (File, String) -> Unit = DesktopBackupFiles::write,
    private val remove: (File) -> Boolean = File::delete,
) {
    private val json = Json { encodeDefaults = true }
    private val mutex = Mutex()
    private val pending = linkedMapOf<String, DesktopFocusCredit>()
    private val durable = mutableSetOf<String>()
    private val unreadable = mutableSetOf<String>()
    private val _state = MutableStateFlow(DesktopFocusSaveState())
    val state = _state.asStateFlow()

    @Synchronized
    fun enqueue(credit: DesktopFocusCredit) {
        validate(credit)
        pending.putIfAbsent(credit.saveKey, credit)
        publishCounts()
    }

    @Synchronized
    fun pendingCredits(): List<DesktopFocusCredit> = pending.values.toList()

    @Synchronized
    fun contains(saveKey: String): Boolean = saveKey in pending

    suspend fun retry() = mutex.withLock {
        withContext(Dispatchers.IO) {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                check(directory.isDirectory || directory.mkdirs()) { "Could not open the focus recovery folder." }
                val files = directory.listFiles { file -> file.extension == "json" }
                    ?: throw IOException("Could not read the focus recovery folder.")
                synchronized(this@DesktopFocusSaveQueue) { unreadable.clear() }
                for (file in files) {
                    try {
                        val credit = json.decodeFromString<DesktopFocusCredit>(file.readText(Charsets.UTF_8))
                        validate(credit)
                        check(file.nameWithoutExtension == credit.saveKey) { "Recovery record name does not match its session." }
                        synchronized(this@DesktopFocusSaveQueue) {
                            pending.putIfAbsent(credit.saveKey, credit)
                            durable.add(credit.saveKey)
                        }
                    } catch (error: Exception) {
                        synchronized(this@DesktopFocusSaveQueue) { unreadable.add(file.name) }
                        _state.update { it.copy(error = "A focus recovery record could not be read: ${file.absolutePath}") }
                    }
                }
                _state.update { it.copy(isReady = true) }
                publishCounts()
                val credits = synchronized(this@DesktopFocusSaveQueue) { pending.values.sortedBy { it.endedAtMillis } }
                for (credit in credits) {
                    val file = File(directory, "${credit.saveKey}.json")
                    // Also retry journal writes that failed earlier; keep the in-memory credit on failure.
                    if (synchronized(this@DesktopFocusSaveQueue) { credit.saveKey !in durable }) {
                        check(synchronized(this@DesktopFocusSaveQueue) { file.name !in unreadable }) {
                            "An unreadable recovery record needs attention before this session can be saved."
                        }
                        write(file, json.encodeToString(DesktopFocusCredit.serializer(), credit))
                        synchronized(this@DesktopFocusSaveQueue) { durable.add(credit.saveKey) }
                        publishCounts()
                    }
                }
                // Journal the whole batch before saving: a failure on the first database write
                // must not leave the other completed sessions only in memory.
                for (credit in credits) {
                    val file = File(directory, "${credit.saveKey}.json")
                    save(credit)
                    _state.update { it.copy(savedRevision = it.savedRevision + 1) }
                    check(!file.exists() || remove(file)) { "Saved focus work, but could not remove its recovery record." }
                    synchronized(this@DesktopFocusSaveQueue) {
                        pending.remove(credit.saveKey)
                        durable.remove(credit.saveKey)
                    }
                    publishCounts()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message ?: "Focus history could not be saved.") }
            } finally {
                publishCounts()
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    @Synchronized
    private fun publishCounts() {
        _state.update { it.copy(pendingCount = pending.size + unreadable.size, memoryOnlyCount = pending.keys.count { key -> key !in durable }) }
    }

    private fun validate(credit: DesktopFocusCredit) {
        require(UUID.fromString(credit.saveKey).toString() == credit.saveKey) { "Invalid focus recovery identity." }
        require(credit.taskTitle.isNotBlank() && credit.plannedSeconds in 1..86400 && credit.actualSeconds >= 0 && credit.endedAtMillis > 0) {
            "Invalid focus recovery record."
        }
    }
}
