package com.anchor.adhd.desktop.ui

import java.awt.Taskbar
import java.awt.Window

/**
 * Windows 11 Native Taskbar Progress Integration.
 * Displays focus timer countdown progress right on the Windows taskbar icon.
 */
object DesktopTaskbarIntegration {
    fun updateProgress(
        window: Window?,
        isFocusActive: Boolean,
        remainingSeconds: Int,
        totalSeconds: Int,
        isPaused: Boolean = !isFocusActive && remainingSeconds in 1 until totalSeconds,
    ) {
        if (!Taskbar.isTaskbarSupported() || window == null) return
        val taskbar = Taskbar.getTaskbar()
        try {
            if ((isFocusActive || isPaused) && totalSeconds > 0) {
                val percent = (((totalSeconds - remainingSeconds).toDouble() / totalSeconds) * 100).toInt().coerceIn(0, 100)
                if (taskbar.isSupported(Taskbar.Feature.PROGRESS_VALUE_WINDOW)) {
                    taskbar.setWindowProgressValue(window, percent)
                }
                if (taskbar.isSupported(Taskbar.Feature.PROGRESS_STATE_WINDOW)) {
                    taskbar.setWindowProgressState(
                        window,
                        if (isPaused) Taskbar.State.PAUSED else Taskbar.State.NORMAL,
                    )
                }
            } else {
                if (taskbar.isSupported(Taskbar.Feature.PROGRESS_STATE_WINDOW)) {
                    taskbar.setWindowProgressState(window, Taskbar.State.OFF)
                }
            }
        } catch (_: Exception) {
            // Taskbar feature not supported on current window display
        }
    }

    fun setErrorState(window: Window?) {
        if (!Taskbar.isTaskbarSupported() || window == null) return
        val taskbar = Taskbar.getTaskbar()
        try {
            if (taskbar.isSupported(Taskbar.Feature.PROGRESS_STATE_WINDOW)) {
                taskbar.setWindowProgressState(window, Taskbar.State.ERROR)
            }
        } catch (_: Exception) {
        }
    }
}
