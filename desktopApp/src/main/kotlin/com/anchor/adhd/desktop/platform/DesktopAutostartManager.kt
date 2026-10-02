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
    private const val REG_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val APP_NAME = "Anchor"

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

    /**
     * Checks if Anchor is currently registered in Windows Startup.
     */
    fun isAutostartRegistered(): Boolean {
        if (!isSupported()) return false
        return try {
            val process = ProcessBuilder("reg.exe", "query", REG_KEY, "/v", APP_NAME)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            exitCode == 0 && output.contains(APP_NAME, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Registers or unregisters Anchor from Windows Startup.
     *
     * @param enabled whether Anchor should launch at Windows login
     * @param startMinimized if true, appends `--minimized` so Anchor starts silently in taskbar/tray
     */
    fun setAutostart(enabled: Boolean, startMinimized: Boolean = true): Boolean {
        if (!isSupported()) return false
        return if (enabled) {
            val exePath = getExecutablePath() ?: return false
            val commandValue = if (startMinimized) "\"$exePath\" --minimized" else "\"$exePath\""

            // Attempt 1: reg.exe
            val regSuccess = runCatching {
                val process = ProcessBuilder(
                    "reg.exe", "add", REG_KEY, "/v", APP_NAME, "/t", "REG_SZ", "/d", commandValue, "/f"
                ).redirectErrorStream(true).start()
                process.waitFor() == 0
            }.getOrDefault(false)

            if (regSuccess) return true

            // Attempt 2: PowerShell Set-ItemProperty
            runCatching {
                val psScript = "Set-ItemProperty -Path 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run' -Name '$APP_NAME' -Value '$commandValue'"
                val process = ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", psScript
                ).redirectErrorStream(true).start()
                process.waitFor() == 0
            }.getOrDefault(false)
        } else {
            // Attempt 1: reg.exe delete
            val regSuccess = runCatching {
                val process = ProcessBuilder(
                    "reg.exe", "delete", REG_KEY, "/v", APP_NAME, "/f"
                ).redirectErrorStream(true).start()
                process.waitFor() == 0
            }.getOrDefault(false)

            if (regSuccess) return true

            // Attempt 2: PowerShell Remove-ItemProperty
            runCatching {
                val psScript = "Remove-ItemProperty -Path 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run' -Name '$APP_NAME' -ErrorAction SilentlyContinue"
                val process = ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", psScript
                ).redirectErrorStream(true).start()
                process.waitFor() == 0
            }.getOrDefault(false)
        }
    }
}
