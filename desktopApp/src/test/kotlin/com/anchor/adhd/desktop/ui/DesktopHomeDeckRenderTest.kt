package com.anchor.adhd.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import com.anchor.adhd.desktop.ai.DesktopRoutingSession
import com.anchor.adhd.desktop.db.DesktopHarborState
import com.anchor.adhd.desktop.db.DesktopTask
import com.anchor.adhd.desktop.db.DesktopThoughtCaptureState
import com.anchor.adhd.desktop.theme.AnchorTheme
import com.anchor.adhd.ui.components.WeeklyTideDay
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

class DesktopHomeDeckRenderTest {
    @Test
    fun greetingAndSummaryHelpersCoverAllStates() {
        assertEquals("Good morning", greetingForHour(8))
        assertEquals("Good afternoon", greetingForHour(14))
        assertEquals("Good evening", greetingForHour(20))
        assertEquals("Good evening", greetingForHour(2))

        assertEquals("1 session today, 25 min focused", formatHomeSummary(1, 25, 3))
        assertEquals("3 sessions today, 75 min focused", formatHomeSummary(3, 75, 5))
        assertEquals("12 min focused today", formatHomeSummary(0, 12, 0))
        assertEquals("Ready for your first session today, 4-day streak", formatHomeSummary(0, 0, 4))
        assertEquals("One small step at a time", formatHomeSummary(0, 0, 0))
    }

    @Test
    fun homeDeckRendersLightAndDarkAtWideAndCompactSizesWithBeaconPlateAndZeroMotion() {
        val lightWide = renderHome(width = 1200, height = 800, darkTheme = false)
        val darkWide = renderHome(width = 1200, height = 800, darkTheme = true)
        val lightCompact = renderHome(width = 640, height = 450, darkTheme = false)
        val darkCompact = renderHome(width = 640, height = 450, darkTheme = true)

        assertTrue(lightWide.png.isNotEmpty())
        assertTrue(darkWide.png.isNotEmpty())
        assertTrue(lightCompact.png.isNotEmpty())
        assertTrue(darkCompact.png.isNotEmpty())

        assertFalse(
            "Morning Light and Night Dark wide Home renders must be distinct",
            lightWide.png.contentEquals(darkWide.png),
        )
        assertFalse(
            "Morning Light and Night Dark compact Home renders must be distinct",
            lightCompact.png.contentEquals(darkCompact.png),
        )

        assertTrue(
            "Committed Deep-Harbor beacon plate color should be visible in Morning Light wide Home",
            lightWide.hasBeaconPlateColor,
        )
        assertTrue(
            "Committed Deep-Harbor beacon plate color should be visible in Night Dark wide Home",
            darkWide.hasBeaconPlateColor,
        )
        assertTrue(
            "Committed Deep-Harbor beacon plate color should be visible in Morning Light compact Home",
            lightCompact.hasBeaconPlateColor,
        )
        assertTrue(
            "Committed Deep-Harbor beacon plate color should be visible in Night Dark compact Home",
            darkCompact.hasBeaconPlateColor,
        )

        assertArrayEquals(
            "Home screen must not run any decorative infinite animation",
            lightWide.png,
            lightWide.laterFramePng,
        )
        assertArrayEquals(
            "Dark Home screen must not run any decorative infinite animation",
            darkWide.png,
            darkWide.laterFramePng,
        )

        val dumpDir = System.getenv("ANCHOR_DUMP_HOME_SNAPSHOTS")
        if (!dumpDir.isNullOrBlank()) {
            val dir = File(dumpDir).apply { mkdirs() }
            File(dir, "home-light-1200x800.png").writeBytes(lightWide.png)
            File(dir, "home-dark-1200x800.png").writeBytes(darkWide.png)
            File(dir, "home-light-640x450.png").writeBytes(lightCompact.png)
            File(dir, "home-dark-640x450.png").writeBytes(darkCompact.png)
        }
    }

    private data class RenderedHome(
        val png: ByteArray,
        val laterFramePng: ByteArray,
        val hasBeaconPlateColor: Boolean,
    )

    private fun renderHome(
        width: Int,
        height: Int,
        darkTheme: Boolean,
    ): RenderedHome {
        val scene =
            ImageComposeScene(
                width = width,
                height = height,
                density = Density(1f),
            ) {
                AnchorTheme(darkTheme = darkTheme, reducedMotion = false) {
                    HomeRenderHarness()
                }
            }
        return try {
            scene.render(0L)
            val frame1 = scene.render(16_000_000L)
            val frame2 = scene.render(2_016_000_000L)
            val pixels = frame1.toComposeImageBitmap().toPixelMap()
            val expectedPlate = if (darkTheme) NightHarborTokens.beaconPlate else MorningHarborTokens.beaconPlate
            val hasPlate = containsColor(pixels, width, height, expectedPlate)
            RenderedHome(
                png = frame1.encodeToData(EncodedImageFormat.PNG)!!.bytes,
                laterFramePng = frame2.encodeToData(EncodedImageFormat.PNG)!!.bytes,
                hasBeaconPlateColor = hasPlate,
            )
        } finally {
            scene.close()
        }
    }

    private fun containsColor(
        pixels: androidx.compose.ui.graphics.PixelMap,
        width: Int,
        height: Int,
        target: Color,
    ): Boolean {
        for (y in 0 until height step 4) {
            for (x in 0 until width step 4) {
                if (pixels[x, y] == target) return true
            }
        }
        return false
    }
}

@Composable
private fun HomeRenderHarness() {
    val tokens = LocalAnchorTokens.current
    val scope = rememberCoroutineScope()
    val routingSession = remember { DesktopRoutingSession(scope) }
    val sampleTasks =
        remember {
            listOf(
                DesktopTask(
                    id = 1L,
                    title = "Draft cognitive psych lab introduction",
                    notes = "Open lab rubric and outline 3 key findings first.",
                    durationMinutes = 25,
                    isNextAction = true,
                ),
                DesktopTask(
                    id = 2L,
                    title = "Review statistics chapter 6 practice set",
                    notes = "Problems 1 through 5",
                    durationMinutes = 20,
                ),
                DesktopTask(
                    id = 3L,
                    title = "Email TA about section swap",
                    durationMinutes = 10,
                ),
            )
        }
    val sampleWeek =
        remember {
            listOf(
                WeeklyTideDay("T", 25, false),
                WeeklyTideDay("F", 50, false),
                WeeklyTideDay("S", 15, false),
                WeeklyTideDay("S", 0, false),
                WeeklyTideDay("M", 75, false),
                WeeklyTideDay("T", 40, false),
                WeeklyTideDay("W", 45, true),
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
                currentScreen = DesktopScreen.GROVE,
                onScreenSelected = {},
                isFocusActive = false,
                remainingSeconds = 25 * 60,
                onOpenAirlock = {},
                planPendingCount = sampleTasks.size,
                compact = compact,
            )
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                DesktopHomeDeck(
                    routingSession = routingSession,
                    canRunRouterAction = { true },
                    currentTask = sampleTasks.first(),
                    tasks = sampleTasks,
                    funLinks = emptyList(),
                    isFocusActive = false,
                    remainingSeconds = 25 * 60,
                    durationMinutes = 25,
                    todayFocusMinutes = 45,
                    deflectedCount = 6,
                    streakDays = 4,
                    onStartFocus = {},
                    onOpenDurationPicker = {},
                    onLaunchMomentumPreset = {},
                    thoughtCaptureState = DesktopThoughtCaptureState(),
                    onThoughtDraftChange = {},
                    onCaptureThought = {},
                    onMakeTaskNow = {},
                    onToggleTaskCompleted = {},
                    onNavigateToPlan = {},
                    harborState = DesktopHarborState(totalSessions = 14, totalMinutes = 340, streakDays = 4),
                    weeklyFocusDays = sampleWeek,
                    weeklySessionCount = 7,
                    todaySessionCount = 2,
                    onBreakDownWithCaptain = {},
                    onOpenCaptain = {},
                    hourOfDay = 9,
                )
            }
        }
    }
}
