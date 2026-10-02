package com.anchor.adhd.desktop.platform

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Frame

class DesktopFullscreenManagerTest {
    @Test
    fun verifyJnaWindowAndMonitorApis() {
        if (!Platform.isWindows()) return

        val frame = Frame("Anchor Test Frame")
        try {
            frame.setSize(600, 400)
            frame.isUndecorated = false
            frame.addNotify()

            val windowPointer = Native.getWindowPointer(frame)
            assertNotNull(windowPointer)

            val hwnd = HWND(windowPointer)
            val style = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE)
            assertTrue("Expected style to be non-zero", style != 0)

            val hasCaption = (style and WinUser.WS_CAPTION) == WinUser.WS_CAPTION
            assertTrue("Expected WS_CAPTION on decorated frame", hasCaption)

            val monitor = User32.INSTANCE.MonitorFromWindow(hwnd, WinUser.MONITOR_DEFAULTTONEAREST)
            assertNotNull(monitor)

            val monitorInfo = WinUser.MONITORINFO()
            val getInfoSuccess = User32.INSTANCE.GetMonitorInfo(monitor, monitorInfo)
            assertTrue("Expected GetMonitorInfo to succeed", getInfoSuccess.booleanValue())
            assertTrue("Expected monitor width > 0", monitorInfo.rcMonitor.right > monitorInfo.rcMonitor.left)
            assertTrue("Expected monitor height > 0", monitorInfo.rcMonitor.bottom > monitorInfo.rcMonitor.top)
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun verifyDesktopFullscreenManagerEnterAndExit() {
        if (!Platform.isWindows()) return

        val frame = Frame("Anchor Fullscreen Test Frame")
        try {
            frame.setSize(800, 600)
            frame.isUndecorated = false
            frame.addNotify()

            val hwnd = HWND(Native.getWindowPointer(frame))
            val initialStyle = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE)
            assertTrue("Initial frame must have WS_CAPTION", (initialStyle and WinUser.WS_CAPTION) == WinUser.WS_CAPTION)

            // Test enterFullscreen
            val enterSuccess = DesktopFullscreenManager.enterFullscreen(frame)
            assertTrue("enterFullscreen should return true", enterSuccess)
            assertTrue("DesktopFullscreenManager.isFullscreen should be true", DesktopFullscreenManager.isFullscreen)

            val fsStyle = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE)
            assertTrue("WS_CAPTION must be stripped in fullscreen", (fsStyle and WinUser.WS_CAPTION) == 0)
            assertTrue("WS_THICKFRAME must be stripped in fullscreen", (fsStyle and WinUser.WS_THICKFRAME) == 0)

            // Test exitFullscreen
            val exitSuccess = DesktopFullscreenManager.exitFullscreen(frame)
            assertTrue("exitFullscreen should return true", exitSuccess)
            assertFalse("DesktopFullscreenManager.isFullscreen should be false", DesktopFullscreenManager.isFullscreen)

            val restoredStyle = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE)
            assertTrue("WS_CAPTION must be restored upon exit", (restoredStyle and WinUser.WS_CAPTION) == WinUser.WS_CAPTION)
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun verifyDesktopFullscreenManagerFromMaximizedState() {
        if (!Platform.isWindows()) return

        val frame = Frame("Anchor Maximized Fullscreen Test Frame")
        try {
            frame.setSize(800, 600)
            frame.extendedState = Frame.MAXIMIZED_BOTH
            frame.isUndecorated = false
            frame.addNotify()

            val hwnd = HWND(Native.getWindowPointer(frame))

            val enterSuccess = DesktopFullscreenManager.enterFullscreen(frame)
            assertTrue("enterFullscreen should succeed from maximized frame", enterSuccess)
            assertTrue("DesktopFullscreenManager.isFullscreen should be true", DesktopFullscreenManager.isFullscreen)

            val fsStyle = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE)
            assertTrue("WS_CAPTION must be stripped in fullscreen", (fsStyle and WinUser.WS_CAPTION) == 0)

            val exitSuccess = DesktopFullscreenManager.exitFullscreen(frame)
            assertTrue("exitFullscreen should succeed", exitSuccess)
            assertFalse("DesktopFullscreenManager.isFullscreen should be false", DesktopFullscreenManager.isFullscreen)
        } finally {
            frame.dispose()
        }
    }
}
