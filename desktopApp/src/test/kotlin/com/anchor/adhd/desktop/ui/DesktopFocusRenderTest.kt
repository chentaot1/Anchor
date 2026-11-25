package com.anchor.adhd.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import com.anchor.adhd.desktop.db.DesktopTask
import com.anchor.adhd.desktop.theme.AnchorTheme
import com.anchor.adhd.ui.theme.LocalAnchorTokens
import com.anchor.adhd.ui.theme.MorningHarborTokens
import com.anchor.adhd.ui.theme.NightHarborTokens
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DesktopFocusRenderTest {
    @Test
    fun countdownFormattingAndProgressHelpersCoverEdgeCases() {
        assertEquals("00:00", formatCountdownClock(0))
        assertEquals("00:00", formatCountdownClock(-42))
        assertEquals("00:09", formatCountdownClock(9))
        assertEquals("01:05", formatCountdownClock(65))
        assertEquals("25:00", formatCountdownClock(25 * 60))
        assertEquals("60:00", formatCountdownClock(60 * 60))

        assertEquals("Pause focus", floatingIslandToggleContentDescription(isFocusActive = true, isWorkPhase = true))
        assertEquals("Pause break", floatingIslandToggleContentDescription(isFocusActive = true, isWorkPhase = false))
        assertEquals("Resume focus", floatingIslandToggleContentDescription(isFocusActive = false, isWorkPhase = true))
        assertEquals("Resume break", floatingIslandToggleContentDescription(isFocusActive = false, isWorkPhase = false))

        assertEquals(0f, computeDesktopFocusProgress(remainingSeconds = 1500, durationMinutes = 25), 0.0001f)
        assertEquals(0.5f, computeDesktopFocusProgress(remainingSeconds = 750, durationMinutes = 25), 0.0001f)
        assertEquals(1f, computeDesktopFocusProgress(remainingSeconds = 0, durationMinutes = 25), 0.0001f)
        assertEquals(1f, computeDesktopFocusProgress(remainingSeconds = 0, durationMinutes = 0), 0.0001f)
        assertEquals(0f, computeDesktopFocusProgress(remainingSeconds = 2000, durationMinutes = 25), 0.0001f)
    }

    @Test
    fun focusScreenRendersLightAndDarkAtWideAndCompactSizesAndHonorsReducedMotion() {
        val lightWide = renderFocus(width = 1200, height = 800, darkTheme = false, reducedMotion = true)
        val darkWide = renderFocus(width = 1200, height = 800, darkTheme = true, reducedMotion = true)
        val lightCompact = renderFocus(width = 640, height = 450, darkTheme = false, reducedMotion = true)
        val darkCompact = renderFocus(width = 640, height = 450, darkTheme = true, reducedMotion = true)
        val lightAnimated = renderFocus(width = 1200, height = 800, darkTheme = false, reducedMotion = false)
        val lightComplete =
            renderFocus(
                width = 1200,
                height = 800,
                darkTheme = false,
                reducedMotion = true,
                isFocusActive = false,
                showSessionCompleteBanner = true,
            )
        val darkBreak =
            renderFocus(
                width = 640,
                height = 450,
                darkTheme = true,
                reducedMotion = true,
                isFocusActive = true,
                isWorkPhase = false,
                remainingSeconds = 180,
                durationMinutes = 5,
            )

        assertTrue(lightWide.png.isNotEmpty())
        assertTrue(darkWide.png.isNotEmpty())
        assertTrue(lightCompact.png.isNotEmpty())
        assertTrue(darkCompact.png.isNotEmpty())
        assertTrue(lightComplete.png.isNotEmpty())
        assertTrue(darkBreak.png.isNotEmpty())

        assertFalse(
            "Morning Light and Night Dark wide Focus renders must be distinct",
            lightWide.png.contentEquals(darkWide.png),
        )
        assertFalse(
            "Morning Light and Night Dark compact Focus renders must be distinct",
            lightCompact.png.contentEquals(darkCompact.png),
        )

        assertTrue("Light wide Focus should render Morning Harbor paper canvas", lightWide.hasPaperColor)
        assertTrue("Dark wide Focus should render Night Harbor paper canvas", darkWide.hasPaperColor)
        assertTrue("Light compact Focus should render Morning Harbor paper canvas", lightCompact.hasPaperColor)
        assertTrue("Dark compact Focus should render Night Harbor paper canvas", darkCompact.hasPaperColor)
        assertTrue("Light wide Focus should render Living Tide basin", lightWide.hasTideBackColor)
        assertTrue("Dark wide Focus should render Living Tide basin", darkWide.hasTideBackColor)
        assertTrue("Light compact Focus should render Living Tide basin", lightCompact.hasTideBackColor)
        assertTrue("Dark compact Focus should render Living Tide basin", darkCompact.hasTideBackColor)

        assertArrayEquals(
            "Light Focus screen must freeze cleanly under reduced motion",
            lightWide.png,
            lightWide.laterFramePng,
        )
        assertArrayEquals(
            "Dark Focus screen must freeze cleanly under reduced motion",
            darkWide.png,
            darkWide.laterFramePng,
        )
        assertFalse(
            "Living Tide water should animate when reduced motion is off",
            lightAnimated.png.contentEquals(lightAnimated.laterFramePng),
        )

        val dumpDir = System.getenv("ANCHOR_DUMP_FOCUS_SNAPSHOTS")
        if (!dumpDir.isNullOrBlank()) {
            val dir = File(dumpDir).apply { mkdirs() }
            File(dir, "focus-light-1200x800.png").writeBytes(lightWide.png)
            File(dir, "focus-dark-1200x800.png").writeBytes(darkWide.png)
            File(dir, "focus-light-640x450.png").writeBytes(lightCompact.png)
            File(dir, "focus-dark-640x450.png").writeBytes(darkCompact.png)
            File(dir, "focus-complete-light-1200x800.png").writeBytes(lightComplete.png)
            File(dir, "focus-break-dark-640x450.png").writeBytes(darkBreak.png)
        }
    }

    @Test
    fun floatingIslandSurfaceAdaptsToLightAndDarkThemesAndActiveBorder() {
        val lightActive = renderFloatingIsland(darkTheme = false, isFocusActive = true)
        val lightPaused = renderFloatingIsland(darkTheme = false, isFocusActive = false)
        val darkActive = renderFloatingIsland(darkTheme = true, isFocusActive = true)
        val darkPaused = renderFloatingIsland(darkTheme = true, isFocusActive = false)

        assertTrue(lightActive.png.isNotEmpty())
        assertTrue(darkActive.png.isNotEmpty())

        assertFalse(
            "Floating Island Light and Dark active surfaces must be distinct",
            lightActive.png.contentEquals(darkActive.png),
        )
        assertFalse(
            "Floating Island active and paused Light surfaces must be distinct",
            lightActive.png.contentEquals(lightPaused.png),
        )
        assertFalse(
            "Floating Island active and paused Dark surfaces must be distinct",
            darkActive.png.contentEquals(darkPaused.png),
        )

        assertTrue("Light Floating Island must render paper2 surface", lightActive.hasPaper2Surface)
        assertTrue("Dark Floating Island must render paper2 surface", darkActive.hasPaper2Surface)
        assertTrue("Light paused Floating Island must render paper2 surface", lightPaused.hasPaper2Surface)
        assertTrue("Dark paused Floating Island must render paper2 surface", darkPaused.hasPaper2Surface)
        assertTrue("Light active Floating Island must show sage accent", lightActive.hasActiveSageAccent)
        assertTrue("Dark active Floating Island must show sage accent", darkActive.hasActiveSageAccent)
        assertTrue("Light paused Floating Island must show rule border", lightPaused.hasRuleBorder)
        assertTrue("Dark paused Floating Island must show rule border", darkPaused.hasRuleBorder)
        assertFalse("Light paused Floating Island must not show active sage border", lightPaused.hasActiveSageAccent)
        assertFalse("Dark paused Floating Island must not show active sage border", darkPaused.hasActiveSageAccent)

        assertArrayEquals(
            "Floating Island must not run decorative infinite animation in Light mode",
            lightActive.png,
            lightActive.laterFramePng,
        )
        assertArrayEquals(
            "Floating Island must not run decorative infinite animation in Dark mode",
            darkActive.png,
            darkActive.laterFramePng,
        )

        val dumpDir = System.getenv("ANCHOR_DUMP_FOCUS_SNAPSHOTS")
        if (!dumpDir.isNullOrBlank()) {
            val dir = File(dumpDir).apply { mkdirs() }
            File(dir, "island-active-light.png").writeBytes(lightActive.png)
            File(dir, "island-paused-light.png").writeBytes(lightPaused.png)
            File(dir, "island-active-dark.png").writeBytes(darkActive.png)
            File(dir, "island-paused-dark.png").writeBytes(darkPaused.png)
        }
    }

    private data class RenderedFocus(
        val png: ByteArray,
        val laterFramePng: ByteArray,
        val hasPaperColor: Boolean,
        val hasTideBackColor: Boolean,
    )

    private data class RenderedIsland(
        val png: ByteArray,
        val laterFramePng: ByteArray,
        val hasPaper2Surface: Boolean,
        val hasActiveSageAccent: Boolean,
        val hasRuleBorder: Boolean,
    )

    private fun renderFocus(
        width: Int,
        height: Int,
        darkTheme: Boolean,
        reducedMotion: Boolean,
        isFocusActive: Boolean = true,
        isWorkPhase: Boolean = true,
        remainingSeconds: Int = 18 * 60 + 24,
        durationMinutes: Int = 25,
        showSessionCompleteBanner: Boolean = false,
    ): RenderedFocus {
        val scene =
            ImageComposeScene(
                width = width,
                height = height,
                density = Density(1f),
            ) {
                AnchorTheme(darkTheme = darkTheme, reducedMotion = reducedMotion) {
                    FocusRenderHarness(
                        isFocusActive = isFocusActive,
                        isWorkPhase = isWorkPhase,
                        remainingSeconds = remainingSeconds,
                        durationMinutes = durationMinutes,
                        showSessionCompleteBanner = showSessionCompleteBanner,
                    )
                }
            }
        return try {
            // Subcomposed BoxWithConstraints layers launch FocusCanvas's InfiniteTransition across initial frames,
            // and a follow-up render flushes any deferred snapshot invalidation from advancing time.
            scene.render(0L)
            scene.render(16_000_000L)
            val frame1 = scene.render(32_000_000L)
            scene.render(2_532_000_000L)
            val frame2 = scene.render(2_548_000_000L)
            val pixels = frame1.toComposeImageBitmap().toPixelMap()
            val expectedTokens = if (darkTheme) NightHarborTokens else MorningHarborTokens
            RenderedFocus(
                png = frame1.encodeToData(EncodedImageFormat.PNG)!!.bytes,
                laterFramePng = frame2.encodeToData(EncodedImageFormat.PNG)!!.bytes,
                hasPaperColor = containsColor(pixels, width, height, expectedTokens.paper, step = 4),
                hasTideBackColor = containsColor(pixels, width, height, expectedTokens.tideBack, step = 2),
            )
        } finally {
            scene.close()
        }
    }

    private fun renderFloatingIsland(
        darkTheme: Boolean,
        isFocusActive: Boolean,
    ): RenderedIsland {
        val width = 380
        val height = 64
        val scene =
            ImageComposeScene(
                width = width,
                height = height,
                density = Density(1f),
            ) {
                AnchorTheme(darkTheme = darkTheme, reducedMotion = false) {
                    DesktopFloatingIslandSurface(
                        taskTitle = "Draft cognitive psych lab introduction",
                        isFocusActive = isFocusActive,
                        isWorkPhase = true,
                        remainingSeconds = 18 * 60 + 24,
                        onToggleFocus = {},
                        onExpandMainApp = {},
                        onCloseHud = {},
                    )
                }
            }
        return try {
            scene.render(0L)
            scene.render(16_000_000L)
            val frame1 = scene.render(32_000_000L)
            scene.render(2_032_000_000L)
            val frame2 = scene.render(2_048_000_000L)
            val pixels = frame1.toComposeImageBitmap().toPixelMap()
            val expectedTokens = if (darkTheme) NightHarborTokens else MorningHarborTokens
            RenderedIsland(
                png = frame1.encodeToData(EncodedImageFormat.PNG)!!.bytes,
                laterFramePng = frame2.encodeToData(EncodedImageFormat.PNG)!!.bytes,
                hasPaper2Surface = containsColor(pixels, width, height, expectedTokens.paper2, step = 2),
                hasActiveSageAccent = containsColor(pixels, width, height, expectedTokens.sage, step = 1),
                hasRuleBorder = containsColor(pixels, width, height, expectedTokens.rule, step = 1),
            )
        } finally {
            scene.close()
        }
    }

    private fun containsColor(
        pixels: PixelMap,
        width: Int,
        height: Int,
        target: Color,
        step: Int = 4,
    ): Boolean {
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                if (pixels[x, y] == target) return true
            }
        }
        return false
    }
}

@Composable
private fun FocusRenderHarness(
    isFocusActive: Boolean,
    isWorkPhase: Boolean,
    remainingSeconds: Int,
    durationMinutes: Int,
    showSessionCompleteBanner: Boolean,
) {
    val tokens = LocalAnchorTokens.current
    val sampleTask =
        remember {
            DesktopTask(
                id = 1L,
                title = "Draft cognitive psych lab introduction",
                notes = "Open lab rubric and outline 3 key findings first.",
                durationMinutes = 25,
                isNextAction = true,
            )
        }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(tokens.paper),
    ) {
        val compact = isCompactSidebarWidth(maxWidth)
        Row(modifier = Modifier.fillMaxSize()) {
            DesktopNavigationSidebar(
                currentScreen = DesktopScreen.FOCUS,
                onScreenSelected = {},
                isFocusActive = isFocusActive,
                remainingSeconds = remainingSeconds,
                onOpenAirlock = {},
                planPendingCount = 3,
                compact = compact,
            )
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                DesktopFocusScreen(
                    currentTask = sampleTask,
                    isFocusActive = isFocusActive,
                    remainingSeconds = remainingSeconds,
                    durationMinutes = durationMinutes,
                    isFullscreen = false,
                    onToggleFullscreen = {},
                    onToggleFocus = {},
                    onAdjustSeconds = {},
                    onStartSpark = {},
                    onResetTimer = {},
                    onOpenDurationPicker = {},
                    onOpenAirlock = {},
                    isWorkPhase = isWorkPhase,
                    hasOpenFocusSession = isFocusActive,
                    showSessionCompleteBanner = showSessionCompleteBanner,
                    breakDurationMinutes = 5,
                    onMarkTaskCompleted = {},
                    onStartRestorativeBreak = {},
                    onDismissSessionCompleteBanner = {},
                    isFloatingIslandActive = false,
                    onToggleFloatingIsland = {},
                    onBreakDownWithCaptain = {},
                )
            }
        }
    }
}
