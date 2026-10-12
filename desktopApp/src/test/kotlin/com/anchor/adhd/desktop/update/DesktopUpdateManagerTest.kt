package com.anchor.adhd.desktop.update

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class DesktopUpdateManagerTest {
    @Test
    fun restartWaitsForOldInstanceAndUsesUpdatedSource() {
        val script = DesktopUpdateManager.restartScript(File("C:/Apps/Account's Anchor"), 12345)
        assertTrue(script.startsWith("Wait-Process -Id 12345"))
        assertTrue(script.contains("Account''s Anchor"))
        assertTrue(script.contains(":desktopApp:run"))
        assertFalse(script.contains("Launch-Anchor.bat"))
        assertFalse(script.contains("Anchor.exe"))
    }

    @Test
    fun testProjectRootDetection() {
        val root = DesktopUpdateManager.projectRoot
        assertNotNull(root)
        assertTrue("Project root should exist", root.exists())
        val settingsFile = File(root, "settings.gradle.kts")
        assertTrue("settings.gradle.kts should exist in root", settingsFile.exists())
    }

    @Test
    fun testRefreshLocalInfo() =
        runBlocking {
            DesktopUpdateManager.refreshLocalInfo()
            assertNotNull(DesktopUpdateManager.currentCommit)
            assertNotNull(DesktopUpdateManager.currentVersionTag)
            assertTrue("Commit should not be empty", DesktopUpdateManager.currentCommit.isNotEmpty())
        }
}
