package com.anchor.adhd.desktop.platform

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

object DesktopSystemAppearance {
    private const val PERSONALIZE_KEY =
        "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"
    private const val APPS_USE_LIGHT_THEME = "AppsUseLightTheme"
    private const val SPI_GETCLIENTAREAANIMATION = 0x1042

    // jna-platform 5.14.0's User32 interface omits SystemParametersInfo; bind user32 directly.
    private interface User32Parameters : StdCallLibrary {
        fun SystemParametersInfo(
            uiAction: WinDef.UINT,
            uiParam: WinDef.UINT,
            pvParam: Pointer,
            fWinIni: WinDef.UINT,
        ): WinDef.BOOL
    }

    private val user32Parameters: User32Parameters by lazy {
        Native.load("user32", User32Parameters::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }

    fun isSystemDarkTheme(): Boolean {
        if (!Platform.isWindows()) return false
        return runCatching {
            Advapi32Util.registryGetIntValue(
                WinReg.HKEY_CURRENT_USER,
                PERSONALIZE_KEY,
                APPS_USE_LIGHT_THEME,
            ) == 0
        }.getOrDefault(false)
    }

    fun isReducedMotionEnabled(): Boolean {
        if (!Platform.isWindows()) return false
        return runCatching {
            val enabled = IntByReference(1)
            val ok =
                user32Parameters
                    .SystemParametersInfo(
                        WinDef.UINT(SPI_GETCLIENTAREAANIMATION.toLong()),
                        WinDef.UINT(0),
                        enabled.pointer,
                        WinDef.UINT(0),
                    ).booleanValue()
            ok && enabled.value == 0
        }.getOrDefault(false)
    }
}
