package com.anchor.adhd.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.anchor.adhd.desktop.theme.AnchorTheme
import com.anchor.adhd.ui.theme.LocalAnchorTokens
import com.anchor.adhd.ui.theme.MorningHarborTokens
import com.anchor.adhd.ui.theme.NightHarborTokens
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DesktopNavigationSidebarTest {
    @Test
    fun desktopNavItemsMatchUnifiedLabelsOrderAndIcons() {
        assertEquals(
            listOf("Home", "Plan", "Focus", "Blocker", "Syllabus", "Captain", "Settings"),
            DESKTOP_NAV_ITEMS.map { it.label },
        )
        assertEquals(
            listOf(
                DesktopScreen.GROVE,
                DesktopScreen.PLAN,
                DesktopScreen.FOCUS,
                DesktopScreen.BLOCKER,
                DesktopScreen.SYLLABUS,
                DesktopScreen.AI,
                DesktopScreen.SETTINGS,
            ),
            DESKTOP_NAV_ITEMS.map { it.screen },
        )
        assertEquals(
            listOf(
                Icons.Outlined.Home,
                Icons.Outlined.CalendarMonth,
                Icons.Outlined.Timer,
                Icons.Outlined.Shield,
                Icons.Outlined.School,
                Icons.Outlined.AutoAwesome,
                Icons.Outlined.Settings,
            ),
            DESKTOP_NAV_ITEMS.map { it.icon },
        )
        assertEquals(
            listOf(
                Icons.Filled.Home,
                Icons.Filled.CalendarMonth,
                Icons.Filled.Timer,
                Icons.Filled.Shield,
                Icons.Filled.School,
                Icons.Filled.AutoAwesome,
                Icons.Filled.Settings,
            ),
            DESKTOP_NAV_ITEMS.map { it.selectedIcon },
        )
    }

    @Test
    fun keyboardShortcutsOneThroughSevenMapOneToOneWithSidebarOrder() {
        assertEquals(DesktopScreen.GROVE, desktopScreenForShortcutNumber(1))
        assertEquals(DesktopScreen.PLAN, desktopScreenForShortcutNumber(2))
        assertEquals(DesktopScreen.FOCUS, desktopScreenForShortcutNumber(3))
        assertEquals(DesktopScreen.BLOCKER, desktopScreenForShortcutNumber(4))
        assertEquals(DesktopScreen.SYLLABUS, desktopScreenForShortcutNumber(5))
        assertEquals(DesktopScreen.AI, desktopScreenForShortcutNumber(6))
        assertEquals(DesktopScreen.SETTINGS, desktopScreenForShortcutNumber(7))
        assertNull(desktopScreenForShortcutNumber(0))
        assertNull(desktopScreenForShortcutNumber(8))
    }

    @Test
    fun compactBreakpointCollapsesSidebarBelow760Dp() {
        assertTrue(isCompactSidebarWidth(640.dp))
        assertTrue(isCompactSidebarWidth(759.dp))
        assertFalse(isCompactSidebarWidth(760.dp))
        assertFalse(isCompactSidebarWidth(1200.dp))
    }

    @Test
    fun sidebarRendersExpandedAndCompactInMorningLightAndNightDarkWithoutMotion() {
        val lightExpanded =
            renderShellScene(
                width = 900,
                height = 600,
                darkTheme = false,
                currentScreen = DesktopScreen.GROVE,
            )
        val darkExpanded =
            renderShellScene(
                width = 900,
                height = 600,
                darkTheme = true,
                currentScreen = DesktopScreen.AI,
            )
        val lightCompact =
            renderShellScene(
                width = 640,
                height = 450,
                darkTheme = false,
                currentScreen = DesktopScreen.GROVE,
            )
        val darkCompact =
            renderShellScene(
                width = 640,
                height = 450,
                darkTheme = true,
                currentScreen = DesktopScreen.AI,
            )

        assertTrue(lightExpanded.png.isNotEmpty())
        assertTrue(darkExpanded.png.isNotEmpty())
        assertTrue(lightCompact.png.isNotEmpty())
        assertTrue(darkCompact.png.isNotEmpty())
        assertFalse(
            "Morning Harbor Light and Night Harbor Dark expanded sidebars should render distinctly",
            lightExpanded.png.contentEquals(darkExpanded.png),
        )
        assertFalse(
            "Morning Harbor Light and Night Harbor Dark compact rails should render distinctly",
            lightCompact.png.contentEquals(darkCompact.png),
        )

        // Expanded sidebar is 200dp wide with a 1dp right hairline at x=199 and paper canvas at x=210.
        assertEquals(MorningHarborTokens.rule, lightExpanded.hairlinePixel)
        assertEquals(MorningHarborTokens.paper, lightExpanded.canvasPixel)
        assertEquals(NightHarborTokens.rule, darkExpanded.hairlinePixel)
        assertEquals(NightHarborTokens.paper, darkExpanded.canvasPixel)

        // Compact rail (<760dp) is 64dp wide with a 1dp right hairline at x=63 and paper canvas at x=80.
        assertEquals(MorningHarborTokens.rule, lightCompact.hairlinePixel)
        assertEquals(MorningHarborTokens.paper, lightCompact.canvasPixel)
        assertEquals(NightHarborTokens.rule, darkCompact.hairlinePixel)
        assertEquals(NightHarborTokens.paper, darkCompact.canvasPixel)
        assertTrue(
            "Captain active pill must remain visible without scrolling in 640x450 compact rail",
            darkCompact.hasActivePillInSidebar,
        )

        // Verify zero decorative infinite animation: frame at t=0s and t=2s are byte-identical.
        assertArrayEquals(
            "Sidebar must not run any decorative infinite transition",
            lightExpanded.png,
            lightExpanded.laterFramePng,
        )

        val dumpDir = System.getenv("ANCHOR_DUMP_SIDEBAR_SNAPSHOTS")
        if (!dumpDir.isNullOrBlank()) {
            val dir = File(dumpDir).apply { mkdirs() }
            File(dir, "sidebar-light-expanded.png").writeBytes(lightExpanded.png)
            File(dir, "sidebar-dark-expanded.png").writeBytes(darkExpanded.png)
            File(dir, "sidebar-light-compact.png").writeBytes(lightCompact.png)
            File(dir, "sidebar-dark-compact.png").writeBytes(darkCompact.png)
        }
    }

    private data class RenderedShell(
        val png: ByteArray,
        val laterFramePng: ByteArray,
        val hairlinePixel: androidx.compose.ui.graphics.Color,
        val canvasPixel: androidx.compose.ui.graphics.Color,
        val hasActivePillInSidebar: Boolean,
    )

    private fun renderShellScene(
        width: Int,
        height: Int,
        darkTheme: Boolean,
        currentScreen: DesktopScreen,
    ): RenderedShell {
        val scene =
            ImageComposeScene(
                width = width,
                height = height,
                density = Density(1f),
            ) {
                AnchorTheme(darkTheme = darkTheme, reducedMotion = false) {
                    SidebarShellHarness(currentScreen = currentScreen)
                }
            }
        return try {
            scene.render(0L)
            val frame1 = scene.render(16_000_000L)
            val frame2 = scene.render(2_016_000_000L)
            val pixels = frame1.toComposeImageBitmap().toPixelMap()
            val expectedWidth = if (width < 760) 64 else 200
            val expectedPillColor = if (darkTheme) NightHarborTokens.paper3 else MorningHarborTokens.paper3
            val hasActivePill =
                (0 until height).any { y -> pixels[14, y] == expectedPillColor }
            RenderedShell(
                png = frame1.encodeToData(EncodedImageFormat.PNG)!!.bytes,
                laterFramePng = frame2.encodeToData(EncodedImageFormat.PNG)!!.bytes,
                hairlinePixel = pixels[expectedWidth - 1, height / 2],
                canvasPixel = pixels[expectedWidth + 16, height / 2],
                hasActivePillInSidebar = hasActivePill,
            )
        } finally {
            scene.close()
        }
    }
}

@Composable
private fun SidebarShellHarness(currentScreen: DesktopScreen) {
    val tokens = LocalAnchorTokens.current
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(tokens.paper),
    ) {
        val compact = isCompactSidebarWidth(maxWidth)
        Row(modifier = Modifier.fillMaxSize()) {
            DesktopNavigationSidebar(
                currentScreen = currentScreen,
                onScreenSelected = {},
                isFocusActive = true,
                remainingSeconds = 1185,
                onOpenAirlock = {},
                onToggleFloatingIsland = {},
                isFloatingIslandActive = false,
                isFullscreen = false,
                onToggleFullscreen = {},
                hasUpdateBadge = true,
                isStandingShieldActive = true,
                planPendingCount = 4,
                compact = compact,
            )
            Box(modifier = Modifier.weight(1f).fillMaxSize())
        }
    }
}
