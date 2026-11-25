package com.anchor.adhd.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import com.anchor.adhd.desktop.blocker.BlockerDetection
import com.anchor.adhd.desktop.blocker.DesktopProcessMonitor
import com.anchor.adhd.desktop.blocker.DesktopRescueOverlaySurface
import com.anchor.adhd.desktop.blocker.ScaledTimeBank
import com.anchor.adhd.desktop.blocker.TimeBankStatus
import com.anchor.adhd.desktop.blocker.rescueOverlayHeadline
import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import com.anchor.adhd.desktop.prefs.DesktopUserPreferences
import com.anchor.adhd.desktop.theme.AnchorTheme
import com.anchor.adhd.ui.theme.LocalAnchorTokens
import com.anchor.adhd.ui.theme.MorningHarborTokens
import com.anchor.adhd.ui.theme.NightHarborTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DesktopBlockerAndRescueRenderTest {
    @Test
    fun rescueOverlayHeadlineMapsKnownReasonsWithoutEmoji() {
        assertEquals("Lecture shield active", rescueOverlayHeadline("Lecture Shield Active for CS 101"))
        assertEquals("Night curfew engaged", rescueOverlayHeadline("Night Curfew blocks leisure apps"))
        assertEquals("Daily quota exhausted", rescueOverlayHeadline("Daily Allowance Reached for discord"))
        assertEquals("Focus scope guard", rescueOverlayHeadline("Out-of-Scope browsing detected"))
        assertEquals("Take a breath", rescueOverlayHeadline("Manual Test of Maritime Rescue Shield"))
    }

    @Test
    fun blockerScreenRendersLightAndDarkAtWideAndCompactSizesWithPaperCanvas() {
        withPopulatedBlockerDb { db, prefs, monitor ->
            val lightWide = renderBlocker(db, prefs, monitor, width = 1200, height = 800, darkTheme = false)
            val darkWide = renderBlocker(db, prefs, monitor, width = 1200, height = 800, darkTheme = true)
            val lightCompact = renderBlocker(db, prefs, monitor, width = 640, height = 450, darkTheme = false)
            val darkCompact = renderBlocker(db, prefs, monitor, width = 640, height = 450, darkTheme = true)

            assertTrue(lightWide.png.isNotEmpty())
            assertTrue(darkWide.png.isNotEmpty())
            assertTrue(lightCompact.png.isNotEmpty())
            assertTrue(darkCompact.png.isNotEmpty())

            assertFalse(
                "Morning Light and Night Dark wide Blocker renders must be distinct",
                lightWide.png.contentEquals(darkWide.png),
            )
            assertFalse(
                "Morning Light and Night Dark compact Blocker renders must be distinct",
                lightCompact.png.contentEquals(darkCompact.png),
            )

            assertTrue("Light wide Blocker should render Morning Harbor paper canvas", lightWide.hasPaperColor)
            assertTrue("Dark wide Blocker should render Night Harbor paper canvas", darkWide.hasPaperColor)
            assertTrue("Light compact Blocker should render Morning Harbor paper canvas", lightCompact.hasPaperColor)
            assertTrue("Dark compact Blocker should render Night Harbor paper canvas", darkCompact.hasPaperColor)

            assertArrayEquals(
                "Light Blocker screen must not run decorative infinite animation",
                lightWide.png,
                lightWide.laterFramePng,
            )
            assertArrayEquals(
                "Dark Blocker screen must not run decorative infinite animation",
                darkWide.png,
                darkWide.laterFramePng,
            )
        }
    }

    @Test
    fun rescueOverlayRendersLightAndDarkAtWideAndCompactSizesWithFocalPlate() {
        val sampleDetection =
            BlockerDetection(
                isBlocked = true,
                appOrSiteName = "Discord",
                reason = "Daily Allowance Reached for Discord",
                isWebDistraction = false,
            )
        val timeBank =
            ScaledTimeBank.calculateStatus(
                focusMinutesToday = 90,
                spentMinutesToday = 0,
                baseMinutes = 60,
            )

        val lightWide = renderRescue(sampleDetection, timeBank, width = 1200, height = 800, darkTheme = false)
        val darkWide = renderRescue(sampleDetection, timeBank, width = 1200, height = 800, darkTheme = true)
        val lightCompact = renderRescue(sampleDetection, timeBank, width = 640, height = 450, darkTheme = false)
        val darkCompact = renderRescue(sampleDetection, timeBank, width = 640, height = 450, darkTheme = true)

        assertTrue(lightWide.png.isNotEmpty())
        assertTrue(darkWide.png.isNotEmpty())
        assertTrue(lightCompact.png.isNotEmpty())
        assertTrue(darkCompact.png.isNotEmpty())

        assertFalse(lightWide.png.contentEquals(darkWide.png))
        assertFalse(lightCompact.png.contentEquals(darkCompact.png))

        assertTrue(lightWide.hasPaperColor)
        assertTrue(darkWide.hasPaperColor)
        assertTrue(lightWide.hasSurfaceColor)
        assertTrue(darkWide.hasSurfaceColor)
        assertTrue(lightWide.hasBeaconColor)
        assertTrue(darkWide.hasBeaconColor)

        assertArrayEquals(lightWide.png, lightWide.laterFramePng)
        assertArrayEquals(darkWide.png, darkWide.laterFramePng)
    }

    private data class RenderedCapture(
        val png: ByteArray,
        val laterFramePng: ByteArray,
        val hasPaperColor: Boolean,
        val hasSurfaceColor: Boolean = false,
        val hasBeaconColor: Boolean = false,
    )

    private fun renderBlocker(
        db: AnchorDesktopDatabase,
        prefs: DesktopUserPreferences,
        monitor: DesktopProcessMonitor,
        width: Int,
        height: Int,
        darkTheme: Boolean,
    ): RenderedCapture =
        renderScene(width, height, darkTheme) {
            BlockerRenderHarness(db = db, prefs = prefs, processMonitor = monitor)
        }

    private fun renderRescue(
        detection: BlockerDetection,
        timeBankStatus: TimeBankStatus,
        width: Int,
        height: Int,
        darkTheme: Boolean,
    ): RenderedCapture =
        renderScene(width, height, darkTheme) {
            RescueRenderHarness(detection = detection, timeBankStatus = timeBankStatus)
        }

    private fun renderScene(
        width: Int,
        height: Int,
        darkTheme: Boolean,
        content: @Composable () -> Unit,
    ): RenderedCapture {
        val scene =
            ImageComposeScene(
                width = width,
                height = height,
                density = Density(1f),
            ) {
                AnchorTheme(darkTheme = darkTheme, reducedMotion = true) {
                    content()
                }
            }
        return try {
            scene.render(0L)
            val frame1 = scene.render(32_000_000L)
            val frame2 = scene.render(2_048_000_000L)
            val pixels = frame1.toComposeImageBitmap().toPixelMap()
            val expectedTokens = if (darkTheme) NightHarborTokens else MorningHarborTokens
            RenderedCapture(
                png = frame1.encodeToData(EncodedImageFormat.PNG)!!.bytes,
                laterFramePng = frame2.encodeToData(EncodedImageFormat.PNG)!!.bytes,
                hasPaperColor = containsColor(pixels, width, height, expectedTokens.paper, step = 4),
                hasSurfaceColor = containsColor(pixels, width, height, expectedTokens.surface, step = 3),
                hasBeaconColor = containsColor(pixels, width, height, expectedTokens.beacon, step = 3),
            )
        } finally {
            scene.close()
        }
    }

    private fun withPopulatedBlockerDb(
        block: (AnchorDesktopDatabase, DesktopUserPreferences, DesktopProcessMonitor) -> Unit,
    ) {
        val dbFile = File.createTempFile("anchor_blocker_render_", ".db")
        try {
            val db = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), customDbFile = dbFile)
            val prefs = DesktopUserPreferences()
            val monitor = DesktopProcessMonitor(CoroutineScope(Dispatchers.Unconfined), database = db) {}
            runBlocking {
                db.initialize()
                db.insertBlockRule("discord.exe", "APP", scheduleMode = "FOCUS_ONLY", dailyAllowanceMinutes = 75)
                db.insertBlockRule("steam.exe", "APP", scheduleMode = "ALWAYS_24_7", dailyAllowanceMinutes = -1)
                db.insertBlockRule("youtube.com", "WEB", scheduleMode = "LEISURE_QUOTA", dailyAllowanceMinutes = 60)
                db.insertBlockRule("reddit.com", "WEB", scheduleMode = "FOCUS_ONLY", dailyAllowanceMinutes = 30)
            }
            block(db, prefs, monitor)
        } finally {
            dbFile.delete()
            File(dbFile.absolutePath + "-wal").delete()
            File(dbFile.absolutePath + "-shm").delete()
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
private fun BlockerRenderHarness(
    db: AnchorDesktopDatabase,
    prefs: DesktopUserPreferences,
    processMonitor: DesktopProcessMonitor,
) {
    val tokens = LocalAnchorTokens.current

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(tokens.paper),
    ) {
        val compact = isCompactSidebarWidth(maxWidth)
        Row(modifier = Modifier.fillMaxSize()) {
            DesktopNavigationSidebar(
                currentScreen = DesktopScreen.BLOCKER,
                onScreenSelected = {},
                isFocusActive = false,
                remainingSeconds = 25 * 60,
                onOpenAirlock = {},
                planPendingCount = 2,
                compact = compact,
            )
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                DesktopBlockerScreen(
                    db = db,
                    prefs = prefs,
                    processMonitor = processMonitor,
                    isFocusActive = false,
                    onTestRescueShield = {},
                )
            }
        }
    }
}

@Composable
private fun RescueRenderHarness(
    detection: BlockerDetection,
    timeBankStatus: TimeBankStatus,
) {
    DesktopRescueOverlaySurface(
        detection = detection,
        taskTitle = "Finish problem set 4",
        remainingMinutes = 22,
        onReturnToFocus = {},
        onEmergencyPass = {},
        timeBankStatus = timeBankStatus,
        quickPassesRemaining = 3,
        onQuickPass = {},
    )
}