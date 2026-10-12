package com.anchor.adhd.desktop.focus

import com.anchor.adhd.desktop.db.DesktopTask
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
internal data class DesktopFocusCredit(
    val taskTitle: String,
    val plannedSeconds: Int,
    val actualSeconds: Int,
    val completed: Boolean,
    val saveKey: String = UUID.randomUUID().toString(),
    val endedAtMillis: Long = System.currentTimeMillis(),
)

@Serializable
internal data class DesktopFocusSessionState(val task: DesktopTask?, val plannedSeconds: Int, val saveKey: String)

/** Keeps session attribution fixed and hands each finished session to persistence once. */
internal class DesktopFocusLedger {
    private var task: DesktopTask? = null
    private var plannedSeconds = 0
    private var saveKey = ""
    var isOpen = false
        private set

    fun begin(task: DesktopTask?, plannedSeconds: Int) {
        check(!isOpen) { "Finish the existing session before starting another" }
        this.task = task
        this.plannedSeconds = plannedSeconds.coerceAtLeast(1)
        saveKey = UUID.randomUUID().toString()
        isOpen = true
    }

    fun finish(actualSeconds: Int, completed: Boolean, endedAtMillis: Long = System.currentTimeMillis()): DesktopFocusCredit? {
        if (!isOpen) return null
        isOpen = false
        val actual = actualSeconds.coerceAtLeast(0)
        if (!completed && actual < 5 * 60) return null
        return DesktopFocusCredit(task?.title ?: "Deep Work", plannedSeconds, actual, completed, saveKey, endedAtMillis)
    }

    fun snapshot(): DesktopFocusSessionState? = if (isOpen) DesktopFocusSessionState(task, plannedSeconds, saveKey) else null

    fun restore(state: DesktopFocusSessionState) {
        check(!isOpen) { "Finish the existing session before restoring another" }
        require(state.plannedSeconds in 1..86400)
        require(UUID.fromString(state.saveKey).toString() == state.saveKey)
        task = state.task
        plannedSeconds = state.plannedSeconds
        saveKey = state.saveKey
        isOpen = true
    }

    fun setPlannedSeconds(seconds: Int) {
        check(isOpen) { "Start a session before changing its planned duration" }
        plannedSeconds = seconds.coerceAtLeast(1)
    }
}
