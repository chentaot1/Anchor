package com.anchor.adhd.desktop.platform

import com.sun.jna.Platform
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DesktopAutostartManagerTest {

    @Test
    fun autostartIsSupportedOnWindows() {
        if (!Platform.isWindows()) return
        assertTrue(DesktopAutostartManager.isSupported())
    }

    @Test
    fun executablePathResolution_findsAnchorExe() {
        if (!Platform.isWindows()) return
        val exePath = DesktopAutostartManager.getExecutablePath()
        assertNotNull("Executable path for Anchor.exe must resolve", exePath)
        val file = File(exePath!!)
        assertTrue("Anchor.exe file should exist at $exePath", file.exists())
        assertTrue("Path must end with Anchor.exe", file.name.equals("Anchor.exe", ignoreCase = true))
    }

    @Test
    fun autostartRegistration_setsAndVerifiesWindowsRunKey() {
        if (!Platform.isWindows()) return
        val registered = DesktopAutostartManager.setAutostart(enabled = true, startMinimized = true)
        assertTrue("Setting autostart should succeed", registered)
        assertTrue("Anchor must be reported as registered in Windows Startup", DesktopAutostartManager.isAutostartRegistered())
    }
}
