package com.anchor.adhd.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppearanceModeTest {
    @Test
    fun labelsMatchHarborDesignSystem() {
        assertEquals("System", AppearanceMode.SYSTEM.label)
        assertEquals("Morning Harbor", AppearanceMode.LIGHT.label)
        assertEquals("Night Harbor", AppearanceMode.DARK.label)
    }

    @Test
    fun resolveDarkThemeHonorsOverrideAndSystemFallback() {
        assertTrue(AppearanceMode.SYSTEM.resolveDarkTheme(systemDark = true))
        assertFalse(AppearanceMode.SYSTEM.resolveDarkTheme(systemDark = false))
        assertFalse(AppearanceMode.LIGHT.resolveDarkTheme(systemDark = true))
        assertFalse(AppearanceMode.LIGHT.resolveDarkTheme(systemDark = false))
        assertTrue(AppearanceMode.DARK.resolveDarkTheme(systemDark = false))
        assertTrue(AppearanceMode.DARK.resolveDarkTheme(systemDark = true))
    }

    @Test
    fun fromStorageParsesCaseInsensitivelyAndFallsBackToSystem() {
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStorage(null))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStorage(""))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStorage("   "))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStorage("sepia"))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStorage("system"))
        assertEquals(AppearanceMode.LIGHT, AppearanceMode.fromStorage("LIGHT"))
        assertEquals(AppearanceMode.LIGHT, AppearanceMode.fromStorage("light"))
        assertEquals(AppearanceMode.DARK, AppearanceMode.fromStorage("Dark"))
        assertEquals(AppearanceMode.DARK, AppearanceMode.fromStorage(" DARK "))
    }
}
