package com.anchor.adhd.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.anchor.adhd.desktop.ai.DesktopAirlockMode
import com.anchor.adhd.desktop.ai.DesktopAirlockSession
import com.anchor.adhd.desktop.ai.DesktopRoutingSession
import com.anchor.adhd.desktop.ai.SmartRouteResult
import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
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

class DesktopCaptainAndAirlockRenderTest {
    @Test
    fun cleanRouteHeadlineStripsLeadingEmojiAndSymbolPrefixes() {
        assertEquals("Starting 25-Minute Focus", cleanRouteHeadline("\u23F1 Starting 25-Minute Focus"))
        assertEquals("3-Minute Activation Spark", cleanRouteHeadline("\u26A1 3-Minute Activation Spark"))
        assertEquals("Distraction Shield Activated", cleanRouteHeadline("\uD83D\uDEE1 Distraction Shield Activated"))
        assertEquals("Smart Task Decomposition", cleanRouteHeadline("\uD83E\uDE84 Smart Task Decomposition"))
        assertEquals("25-Minute Focus on \"essay\"", cleanRouteHeadline("\u23F1 25-Minute Focus on \"essay\""))
        assertEquals("Plain headline", cleanRouteHeadline("Plain headline"))
    }

    @Test
    fun captainBreakdownScreenRendersLightAndDarkAtWideAndCompactSizesWithZeroMotion() {
        withPopulatedCaptainDb { db ->
            val lightWide = renderCaptain(db = db, width = 1200, height = 800, darkTheme = false, initialTool = 0)
            val darkWide = renderCaptain(db = db, width = 1200, height = 800, darkTheme = true, initialTool = 0)
            val lightCompact = renderCaptain(db = db, width = 640, height = 450, darkTheme = false, initialTool = 0)
            val darkCompact = renderCaptain(db = db, width = 640, height = 450, darkTheme = true, initialTool = 0)

            assertTrue(lightWide.png.isNotEmpty())
            assertTrue(darkWide.png.isNotEmpty())
            assertTrue(lightCompact.png.isNotEmpty())
            assertTrue(darkCompact.png.isNotEmpty())

            assertFalse(
                "Morning Light and Night Dark wide Captain Breakdown renders must be distinct",
                lightWide.png.contentEquals(darkWide.png),
            )
            assertFalse(
                "Morning Light and Night Dark compact Captain Breakdown renders must be distinct",
                lightCompact.png.contentEquals(darkCompact.png),
            )

            assertTrue("Light wide Captain should render Morning Harbor paper canvas", lightWide.hasPaperColor)
            assertTrue("Dark wide Captain should render Night Harbor paper canvas", darkWide.hasPaperColor)
            assertTrue("Light compact Captain should render Morning Harbor paper canvas", lightCompact.hasPaperColor)
            assertTrue("Dark compact Captain should render Night Harbor paper canvas", darkCompact.hasPaperColor)

            assertTrue("Light wide Captain should render Captain soft accent well", lightWide.hasCaptainSoftColor)
            assertTrue("Dark wide Captain should render Captain soft accent well", darkWide.hasCaptainSoftColor)
            assertTrue("Light wide Captain should render Captain teal accent", lightWide.hasCaptainColor)
            assertTrue("Dark wide Captain should render Captain teal accent", darkWide.hasCaptainColor)
            assertTrue("Light wide Captain should render Beacon Focus CTA", lightWide.hasBeaconColor)
            assertTrue("Dark wide Captain should render Beacon Focus CTA", darkWide.hasBeaconColor)

            assertArrayEquals("Light Captain screen must not animate idly", lightWide.png, lightWide.laterFramePng)
            assertArrayEquals("Dark Captain screen must not animate idly", darkWide.png, darkWide.laterFramePng)

            dumpSnapshotIfRequested("captain-breakdown-light-1200x800.png", lightWide.png)
            dumpSnapshotIfRequested("captain-breakdown-dark-1200x800.png", darkWide.png)
            dumpSnapshotIfRequested("captain-breakdown-light-640x450.png", lightCompact.png)
            dumpSnapshotIfRequested("captain-breakdown-dark-640x450.png", darkCompact.png)
        }
    }

    @Test
    fun captainChatTabRendersLightAndDarkAtWideAndCompactSizesWithCaptainWellsAndZeroMotion() {
        withPopulatedCaptainDb { db ->
            val lightWide = renderCaptain(db = db, width = 1200, height = 800, darkTheme = false, initialTool = 4)
            val darkWide = renderCaptain(db = db, width = 1200, height = 800, darkTheme = true, initialTool = 4)
            val lightCompact = renderCaptain(db = db, width = 640, height = 450, darkTheme = false, initialTool = 4)
            val darkCompact = renderCaptain(db = db, width = 640, height = 450, darkTheme = true, initialTool = 4)

            assertTrue(lightWide.png.isNotEmpty())
            assertTrue(darkWide.png.isNotEmpty())
            assertTrue(lightCompact.png.isNotEmpty())
            assertTrue(darkCompact.png.isNotEmpty())

            assertFalse(lightWide.png.contentEquals(darkWide.png))
            assertFalse(lightCompact.png.contentEquals(darkCompact.png))

            assertTrue(lightWide.hasPaperColor)
            assertTrue(darkWide.hasPaperColor)
            assertTrue(lightWide.hasCaptainSoftColor)
            assertTrue(darkWide.hasCaptainSoftColor)
            assertTrue(lightCompact.hasCaptainSoftColor)
            assertTrue(darkCompact.hasCaptainSoftColor)

            assertArrayEquals("Light Captain chat must not animate idly", lightWide.png, lightWide.laterFramePng)
            assertArrayEquals("Dark Captain chat must not animate idly", darkWide.png, darkWide.laterFramePng)

            dumpSnapshotIfRequested("captain-chat-light-1200x800.png", lightWide.png)
            dumpSnapshotIfRequested("captain-chat-dark-1200x800.png", darkWide.png)
            dumpSnapshotIfRequested("captain-chat-light-640x450.png", lightCompact.png)
            dumpSnapshotIfRequested("captain-chat-dark-640x450.png", darkCompact.png)
        }
    }

    @Test
    fun smartOmniBarRendersLightAndDarkAtWideAndCompactSizesWithExpandedRouteAndZeroMotion() {
        val lightWide = renderOmniBar(width = 1200, height = 800, darkTheme = false)
        val darkWide = renderOmniBar(width = 1200, height = 800, darkTheme = true)
        val lightCompact = renderOmniBar(width = 640, height = 450, darkTheme = false)
        val darkCompact = renderOmniBar(width = 640, height = 450, darkTheme = true)

        assertTrue(lightWide.png.isNotEmpty())
        assertTrue(darkWide.png.isNotEmpty())
        assertTrue(lightCompact.png.isNotEmpty())
        assertTrue(darkCompact.png.isNotEmpty())

        assertFalse(lightWide.png.contentEquals(darkWide.png))
        assertFalse(lightCompact.png.contentEquals(darkCompact.png))

        assertTrue("Light OmniBar should render Morning Harbor paper canvas", lightWide.hasPaperColor)
        assertTrue("Dark OmniBar should render Night Harbor paper canvas", darkWide.hasPaperColor)
        assertTrue("Light OmniBar should render surface input plate", lightWide.hasSurfaceColor)
        assertTrue("Dark OmniBar should render surface input plate", darkWide.hasSurfaceColor)
        assertTrue("Light OmniBar should render Captain soft result well", lightWide.hasCaptainSoftColor)
        assertTrue("Dark OmniBar should render Captain soft result well", darkWide.hasCaptainSoftColor)
        assertTrue("Light OmniBar should render Beacon CTA button", lightWide.hasBeaconColor)
        assertTrue("Dark OmniBar should render Beacon CTA button", darkWide.hasBeaconColor)

        assertArrayEquals("Light OmniBar must not animate idly", lightWide.png, lightWide.laterFramePng)
        assertArrayEquals("Dark OmniBar must not animate idly", darkWide.png, darkWide.laterFramePng)

        dumpSnapshotIfRequested("omnibar-light-1200x800.png", lightWide.png)
        dumpSnapshotIfRequested("omnibar-dark-1200x800.png", darkWide.png)
        dumpSnapshotIfRequested("omnibar-light-640x450.png", lightCompact.png)
        dumpSnapshotIfRequested("omnibar-dark-640x450.png", darkCompact.png)
    }

    @Test
    fun airlockDialogSurfaceRendersLightAndDarkAtWideAndCompactSizesWithPreviewAndZeroMotion() {
        val lightWide = renderAirlock(width = 1200, height = 800, darkTheme = false)
        val darkWide = renderAirlock(width = 1200, height = 800, darkTheme = true)
        val lightCompact = renderAirlock(width = 640, height = 450, darkTheme = false)
        val darkCompact = renderAirlock(width = 640, height = 450, darkTheme = true)

        assertTrue(lightWide.png.isNotEmpty())
        assertTrue(darkWide.png.isNotEmpty())
        assertTrue(lightCompact.png.isNotEmpty())
        assertTrue(darkCompact.png.isNotEmpty())

        assertFalse(lightWide.png.contentEquals(darkWide.png))
        assertFalse(lightCompact.png.contentEquals(darkCompact.png))

        assertTrue("Light Airlock should render Morning Harbor paper backdrop", lightWide.hasPaperColor)
        assertTrue("Dark Airlock should render Night Harbor paper backdrop", darkWide.hasPaperColor)
        assertTrue("Light Airlock should render dialog surface plate", lightWide.hasSurfaceColor)
        assertTrue("Dark Airlock should render dialog surface plate", darkWide.hasSurfaceColor)
        assertTrue("Light Airlock should render Captain soft header badge", lightWide.hasCaptainSoftColor)
        assertTrue("Dark Airlock should render Captain soft header badge", darkWide.hasCaptainSoftColor)
        assertTrue("Light Airlock should render Captain solid decompose button", lightWide.hasCaptainColor)
        assertTrue("Dark Airlock should render Captain solid decompose button", darkWide.hasCaptainColor)
        assertTrue("Light Airlock should render Beacon Save CTA", lightWide.hasBeaconColor)
        assertTrue("Dark Airlock should render Beacon Save CTA", darkWide.hasBeaconColor)

        assertArrayEquals("Light Airlock dialog must not animate idly", lightWide.png, lightWide.laterFramePng)
        assertArrayEquals("Dark Airlock dialog must not animate idly", darkWide.png, darkWide.laterFramePng)

        dumpSnapshotIfRequested("airlock-light-1200x800.png", lightWide.png)
        dumpSnapshotIfRequested("airlock-dark-1200x800.png", darkWide.png)
        dumpSnapshotIfRequested("airlock-light-640x450.png", lightCompact.png)
        dumpSnapshotIfRequested("airlock-dark-640x450.png", darkCompact.png)
    }

    private data class RenderedCapture(
        val png: ByteArray,
        val laterFramePng: ByteArray,
        val hasPaperColor: Boolean,
        val hasSurfaceColor: Boolean,
        val hasCaptainSoftColor: Boolean,
        val hasCaptainColor: Boolean,
        val hasBeaconColor: Boolean,
    )

    private fun renderCaptain(
        db: AnchorDesktopDatabase,
        width: Int,
        height: Int,
        darkTheme: Boolean,
        initialTool: Int,
    ): RenderedCapture {
        val initialTask = if (initialTool == 0) "Write cognitive psychology lab report" else ""
        return renderScene(width = width, height = height, darkTheme = darkTheme) {
            CaptainRenderHarness(
                db = db,
                initialTask = initialTask,
                initialTool = initialTool,
            )
        }
    }

    private fun renderOmniBar(
        width: Int,
        height: Int,
        darkTheme: Boolean,
    ): RenderedCapture {
        val sampleRoute =
            SmartRouteResult.TaskBreakdown(
                taskTitle = "Finish data structures problem set 5",
                microSteps =
                    listOf(
                        "Open problem set 5 PDF and highlight question 1",
                        "Sketch the example graph on scratch paper",
                        "Trace Dijkstra weights for the first 3 nodes",
                        "Write pseudocode for the priority queue loop",
                    ),
                step1Starter = "Open problem set 5 PDF and highlight question 1",
                suggestedMinutes = 15,
            )
        val routingSession =
            DesktopRoutingSession(
                scope = CoroutineScope(Dispatchers.Unconfined),
                quick = { sampleRoute },
                enrich = { sampleRoute },
            ).apply {
                edit("Finish data structures problem set 5")
                submit()
            }

        return renderScene(width = width, height = height, darkTheme = darkTheme) {
            OmniBarRenderHarness(routingSession = routingSession)
        }
    }

    private fun renderAirlock(
        width: Int,
        height: Int,
        darkTheme: Boolean,
    ): RenderedCapture {
        val airlockSession =
            DesktopAirlockSession(
                scope = CoroutineScope(Dispatchers.Unconfined),
                quick = { _, _ ->
                    listOf(
                        "Open chemistry lab notebook and write the title line",
                        "Enter the three titration measurements into the table",
                        "Draft the two-sentence error observation paragraph",
                    )
                },
                canRefine = { false },
                save = {},
            ).apply {
                open()
                edit("Finish chem lab report; email professor about extension; buy groceries")
                generate(DesktopAirlockMode.STEPS)
            }

        return renderScene(width = width, height = height, darkTheme = darkTheme) {
            AirlockRenderHarness(session = airlockSession)
        }
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
                AnchorTheme(darkTheme = darkTheme, reducedMotion = false) {
                    content()
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
            RenderedCapture(
                png = frame1.encodeToData(EncodedImageFormat.PNG)!!.bytes,
                laterFramePng = frame2.encodeToData(EncodedImageFormat.PNG)!!.bytes,
                hasPaperColor = containsColor(pixels, width, height, expectedTokens.paper, step = 4),
                hasSurfaceColor = containsColor(pixels, width, height, expectedTokens.surface, step = 4),
                hasCaptainSoftColor = containsColor(pixels, width, height, expectedTokens.captainSoft, step = 2),
                hasCaptainColor = containsColor(pixels, width, height, expectedTokens.captain, step = 2),
                hasBeaconColor = containsColor(pixels, width, height, expectedTokens.beacon, step = 2),
            )
        } finally {
            scene.close()
        }
    }

    private fun withPopulatedCaptainDb(block: (AnchorDesktopDatabase) -> Unit) {
        val dbFile = File.createTempFile("anchor_captain_render_", ".db")
        try {
            val db = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), customDbFile = dbFile)
            runBlocking {
                db.initialize()
                db.importDataJson(
                    """
                    {
                      "tasks": [
                        {"title": "Write cognitive psychology lab report", "durationMinutes": 25, "difficulty": "HARD", "isNextAction": true},
                        {"title": "Reply to professor about office hours", "durationMinutes": 10, "difficulty": "EASY"},
                        {"title": "Review statistics chapter 7 slides", "durationMinutes": 20, "difficulty": "MEDIUM"}
                      ]
                    }
                    """.trimIndent(),
                    replaceExisting = true,
                )
            }
            block(db)
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

    private fun dumpSnapshotIfRequested(
        fileName: String,
        bytes: ByteArray,
    ) {
        val dumpDir = System.getenv("ANCHOR_DUMP_CAPTAIN_AIRLOCK_SNAPSHOTS")
        if (!dumpDir.isNullOrBlank()) {
            val dir = File(dumpDir).apply { mkdirs() }
            File(dir, fileName).writeBytes(bytes)
        }
    }
}

@Composable
private fun CaptainRenderHarness(
    db: AnchorDesktopDatabase,
    initialTask: String,
    initialTool: Int,
) {
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
                currentScreen = DesktopScreen.AI,
                onScreenSelected = {},
                isFocusActive = false,
                remainingSeconds = 25 * 60,
                onOpenAirlock = {},
                planPendingCount = 3,
                compact = compact,
            )
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                DesktopAiScreen(
                    db = db,
                    initialTask = initialTask,
                    onStartFocus = {},
                    onOpenSyllabus = {},
                    initialTool = initialTool,
                )
            }
        }
    }
}

@Composable
private fun OmniBarRenderHarness(routingSession: DesktopRoutingSession) {
    val tokens = LocalAnchorTokens.current

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(tokens.paper)
                .padding(20.dp),
    ) {
        DesktopSmartOmniBar(
            routingSession = routingSession,
            canRunAction = { true },
            onStartFocusWithTask = { _, _ -> },
            onSaveTasksToInbox = { _, _ -> },
            onApplyBlockerRule = { _, _ -> },
            onResetData = {},
            onNavigateToSyllabus = {},
        )
    }
}

@Composable
private fun AirlockRenderHarness(session: DesktopAirlockSession) {
    val tokens = LocalAnchorTokens.current

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(tokens.paper)
                .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        DesktopAirlockDialogSurface(
            state = session.state.value,
            session = session,
        )
    }
}
