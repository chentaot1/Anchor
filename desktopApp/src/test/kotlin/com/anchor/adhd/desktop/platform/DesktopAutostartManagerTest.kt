package com.anchor.adhd.desktop.platform

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

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
    fun autostartRegistrationPreservesApostrophesSpacesAndMinimizedArgument() = withTestRegistry { registry, _ ->
        val path = "C:\\Test account's folder\\Anchor App\\Anchor.exe"
        val command = DesktopAutostartManager.commandFor(path, true)
        assertEquals("\"$path\" --minimized", command)
        assertTrue(registry.setCommand(command))
        assertEquals(command, registry.getCommand())
        assertTrue(registry.setCommand(DesktopAutostartManager.commandFor(path, false)))
        assertEquals("\"$path\"", registry.getCommand())
    }

    @Test
    fun disablingStartupIsSuccessfulEvenWhenAlreadyDisabled() = withTestRegistry { registry, _ ->
        assertNull(registry.getCommand())
        assertTrue(registry.disable())
        assertTrue(registry.setCommand("\"C:\\Anchor\\Anchor.exe\""))
        assertTrue(registry.disable())
        assertNull(registry.getCommand())
        assertTrue(registry.disable())
    }

    @Test
    fun disablingAnchorDoesNotRemoveOtherStartupEntries() = withTestRegistry { registry, key ->
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, key, "Other app", "keep-this")
        assertTrue(registry.setCommand("\"C:\\Anchor\\Anchor.exe\""))
        assertTrue(registry.disable())
        assertEquals("keep-this", Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, key, "Other app"))
    }

    private fun withTestRegistry(test: (WindowsStartupRegistry, String) -> Unit) {
        assumeTrue(Platform.isWindows())
        val key = "Software\\AnchorStartupTest-${UUID.randomUUID()}"
        Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, key)
        try {
            test(WindowsStartupRegistry(key), key)
        } finally {
            Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, key)
        }
    }
}
