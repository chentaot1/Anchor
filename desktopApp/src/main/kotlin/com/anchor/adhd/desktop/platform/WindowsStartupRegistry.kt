package com.anchor.adhd.desktop.platform

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

/** Uses registry APIs directly so paths never become PowerShell source or command-line arguments. */
internal class WindowsStartupRegistry(
    private val key: String = "Software\\Microsoft\\Windows\\CurrentVersion\\Run",
    private val name: String = "Anchor",
) {
    fun getCommand(): String? =
        if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, key, name)) {
            Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, key, name)
        } else null

    fun setCommand(command: String): Boolean = runCatching {
        Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, key)
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, key, name, command)
        getCommand() == command
    }.getOrDefault(false)

    fun disable(): Boolean = runCatching {
        if (getCommand() != null) Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, key, name)
        getCommand() == null
    }.getOrDefault(false)
}
