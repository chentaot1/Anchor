package com.anchor.adhd.desktop.platform

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinUser
import java.awt.Frame
import java.awt.Window

/**
 * Windows 11 True Borderless Fullscreen Manager.
 *
 * Eliminates the standard OS window caption/title bar (WS_CAPTION, WS_THICKFRAME,
 * and system buttons) on Windows 11 by dynamically adjusting Win32 HWND styles
 * and repositioning the window across the active monitor with SWP_FRAMECHANGED.
 *
 * Cleanly restores original window styles, bounds, and maximized states upon exit.
 */
object DesktopFullscreenManager {
    private var isFullscreenMode: Boolean = false
    private var savedStyle: Int = 0
    private var savedRect: RECT = RECT()
    private var wasMaximized: Boolean = false

    val isFullscreen: Boolean
        get() = isFullscreenMode

    fun toggleFullscreen(window: Window): Boolean =
        if (isFullscreenMode) {
            exitFullscreen(window)
            false
        } else {
            enterFullscreen(window)
            true
        }

    fun enterFullscreen(window: Window): Boolean {
        if (!Platform.isWindows()) {
            if (window is Frame) {
                window.extendedState = Frame.MAXIMIZED_BOTH
            }
            isFullscreenMode = true
            return true
        }

        try {
            val windowPointer = Native.getWindowPointer(window) ?: return false
            val hwnd = HWND(windowPointer)

            val currentStyle = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE)
            if (currentStyle == 0) return false

            savedStyle = currentStyle
            wasMaximized = (currentStyle and WinUser.WS_MAXIMIZE) != 0

            savedRect = RECT()
            User32.INSTANCE.GetWindowRect(hwnd, savedRect)

            val monitor = User32.INSTANCE.MonitorFromWindow(hwnd, WinUser.MONITOR_DEFAULTTONEAREST) ?: return false
            val monitorInfo = WinUser.MONITORINFO()
            if (!User32.INSTANCE.GetMonitorInfo(monitor, monitorInfo).booleanValue()) {
                return false
            }

            // Strip caption, thick resizing frame, system menu, minimize, maximize buttons
            val borderlessStyle =
                currentStyle and
                    (
                        WinUser.WS_CAPTION or
                            WinUser.WS_THICKFRAME or
                            WinUser.WS_MINIMIZEBOX or
                            WinUser.WS_MAXIMIZEBOX or
                            WinUser.WS_SYSMENU or
                            WinUser.WS_MAXIMIZE
                    ).inv()

            User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_STYLE, borderlessStyle)

            val width = monitorInfo.rcMonitor.right - monitorInfo.rcMonitor.left
            val height = monitorInfo.rcMonitor.bottom - monitorInfo.rcMonitor.top

            User32.INSTANCE.SetWindowPos(
                hwnd,
                HWND(Pointer.createConstant(0)), // HWND_TOP
                monitorInfo.rcMonitor.left,
                monitorInfo.rcMonitor.top,
                width,
                height,
                WinUser.SWP_FRAMECHANGED or WinUser.SWP_SHOWWINDOW,
            )

            isFullscreenMode = true
            return true
        } catch (e: Throwable) {
            System.err.println("DesktopFullscreenManager: Failed to enter fullscreen: ${e.message}")
            return false
        }
    }

    fun exitFullscreen(window: Window): Boolean {
        if (!Platform.isWindows()) {
            if (window is Frame) {
                window.extendedState = Frame.NORMAL
            }
            isFullscreenMode = false
            return true
        }

        try {
            val windowPointer = Native.getWindowPointer(window) ?: return false
            val hwnd = HWND(windowPointer)

            if (savedStyle != 0) {
                User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_STYLE, savedStyle)
            }

            if (wasMaximized) {
                User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_MAXIMIZE)
                User32.INSTANCE.SetWindowPos(
                    hwnd,
                    null,
                    0,
                    0,
                    0,
                    0,
                    WinUser.SWP_NOMOVE or
                        WinUser.SWP_NOSIZE or
                        WinUser.SWP_NOZORDER or
                        WinUser.SWP_FRAMECHANGED or
                        WinUser.SWP_SHOWWINDOW,
                )
            } else {
                val width = (savedRect.right - savedRect.left).coerceAtLeast(800)
                val height = (savedRect.bottom - savedRect.top).coerceAtLeast(600)

                User32.INSTANCE.SetWindowPos(
                    hwnd,
                    null,
                    savedRect.left,
                    savedRect.top,
                    width,
                    height,
                    WinUser.SWP_NOZORDER or WinUser.SWP_FRAMECHANGED or WinUser.SWP_SHOWWINDOW,
                )
            }

            isFullscreenMode = false
            return true
        } catch (e: Throwable) {
            System.err.println("DesktopFullscreenManager: Failed to exit fullscreen: ${e.message}")
            return false
        }
    }
}
