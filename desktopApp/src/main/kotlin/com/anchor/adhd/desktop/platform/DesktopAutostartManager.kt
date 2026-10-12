package com.anchor.adhd.desktop.platform

import com.sun.jna.Platform
import java.io.File

/**
 * Windows 11 Autostart & Background Launch Manager.
 *
 * Integrates with Windows Startup via the user-level Registry Run key:
 * `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`
 *
 * This standard non-elevated registry key allows Anchor to start automatically
 * when the user logs in, showing up cleanly in Windows Task Manager's "Startup apps" tab.
 * When autostarted or configured, launches with `--minimized` to run shields in the background
 * without interrupting the user's desktop with full-screen popups.
 */
object DesktopAutostartManager {
    private val registry = WindowsStartupRegistry()

    fun isSupported(): Boolean = Platform.isWindows()

    /**
     * Resolves the absolute path to Anchor.exe.
     */
    fun getExecutablePath(): String? {
        // 1. Try currently executing process command via Java 9+ ProcessHandle
        runCatching {
            val cmd = ProcessHandle.current().info().command().orElse(null)
            if (!cmd.isNullOrBlank() && File(cmd).exists() && cmd.endsWith("Anchor.exe", ignoreCase = true)) {
                return File(cmd).absolutePath
            }
        }

        // 2. Compose Desktop distributable binary location
        val candidateRelative = File("desktopApp/build/compose/binaries/main/app/Anchor/Anchor.exe").absoluteFile
        if (candidateRelative.exists()) {
            return candidateRelative.absolutePath
        }

        // 3. User home downloads repo location
        val userHome = System.getProperty("user.home")
        if (userHome != null) {
            val downloadCandidate = File(userHome, "Downloads/anchor-adhd/desktopApp/build/compose/binaries/main/app/Anchor/Anchor.exe")
            if (downloadCandidate.exists()) {
                return downloadCandidate.absolutePath
            }
        }

        // 4. LocalAppData install location
        val localAppData = System.getenv("LOCALAPPDATA")
        if (localAppData != null) {
            val appDataCandidate = File(localAppData, "Anchor/Anchor.exe")
            if (appDataCandidate.exists()) {
                return appDataCandidate.absolutePath
            }
        }

        // 5. Program Files install location
        val programFiles = System.getenv("ProgramFiles")
        if (programFiles != null) {
            val progCandidate = File(programFiles, "Anchor/Anchor.exe")
            if (progCandidate.exists()) {
                return progCandidate.absolutePath
            }
        }

        return null
    }

    /** Throws on a registry read failure so Settings can distinguish it from disabled startup. */
    fun queryRegisteredCommand(): String? = if (isSupported()) registry.getCommand() else null

    fun isAutostartRegistered(): Boolean = getRegisteredCommand() != null

    fun isAutostartEnabled(): Boolean = isAutostartRegistered()

    fun getRegisteredCommand(): String? = runCatching { queryRegisteredCommand() }.getOrNull()

    fun setRegisteredCommand(commandValue: String): Boolean = isSupported() && registry.setCommand(commandValue)

    fun setAutostart(enabled: Boolean, startMinimized: Boolean = true): Boolean {
        if (!isSupported()) return false
        if (!enabled) return registry.disable()
        val exePath = getExecutablePath() ?: return false
        return registry.setCommand(commandFor(exePath, startMinimized))
    }

    internal fun commandFor(exePath: String, startMinimized: Boolean): String =
        "\"$exePath\"" + if (startMinimized) " --minimized" else ""
}
