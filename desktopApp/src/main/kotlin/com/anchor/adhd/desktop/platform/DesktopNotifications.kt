package com.anchor.adhd.desktop.platform

import java.awt.EventQueue
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon

/** A background focus timer needs a way back to its window and a visible completion cue. */
internal class DesktopNotifications(private val onShowAnchor: () -> Unit) : AutoCloseable {
    private var trayIcon: TrayIcon? = null
    private var closed = false

    fun install() = onEventThread {
        if (closed || trayIcon != null || !SystemTray.isSupported()) return@onEventThread
        runCatching {
            val showAnchor = MenuItem("Open Anchor").apply { addActionListener { onShowAnchor() } }
            val menu = PopupMenu().apply { add(showAnchor) }
            val icon = TrayIcon(DesktopAppIcon.image(32), "Anchor — Focus and planning", menu).apply {
                isImageAutoSize = true
                addActionListener { onShowAnchor() }
            }
            SystemTray.getSystemTray().add(icon)
            trayIcon = icon
        }
    }

    fun focusCompleted(taskTitle: String, actualSeconds: Int) = onEventThread {
        if (!closed) {
            val minutes = actualSeconds / 60
            trayIcon?.displayMessage(
                "Focus complete",
                "$minutes minute${if (minutes == 1) "" else "s"} of focus on $taskTitle. Open Anchor to mark it done or take a break.",
                TrayIcon.MessageType.INFO,
            )
        }
    }

    fun breakCompleted() = onEventThread {
        if (!closed) {
            trayIcon?.displayMessage("Break complete", "Ready when you are. Open Anchor to choose your next step.", TrayIcon.MessageType.INFO)
        }
    }

    fun timerRecovered(isWorkPhase: Boolean, remainingSeconds: Int) = onEventThread {
        if (!closed) {
            val remaining = "%d:%02d".format(remainingSeconds / 60, remainingSeconds % 60)
            trayIcon?.displayMessage(
                if (isWorkPhase) "Focus timer restored" else "Break timer restored",
                "Paused with $remaining remaining. Open Anchor when you are ready to continue.",
                TrayIcon.MessageType.INFO,
            )
        }
    }

    override fun close() = onEventThread {
        closed = true
        trayIcon?.let { SystemTray.getSystemTray().remove(it) }
        trayIcon = null
    }

    private fun onEventThread(action: () -> Unit) {
        if (EventQueue.isDispatchThread()) action() else EventQueue.invokeLater(action)
    }

}
