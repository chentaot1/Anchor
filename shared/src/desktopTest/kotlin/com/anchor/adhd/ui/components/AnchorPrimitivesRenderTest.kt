package com.anchor.adhd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.focus.FocusCanvas
import com.anchor.adhd.ui.theme.LocalAnchorTokens
import com.anchor.adhd.ui.theme.MorningHarborTokens
import com.anchor.adhd.ui.theme.SharedAnchorTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AnchorPrimitivesRenderTest {
    private fun renderPng(
        width: Int,
        height: Int,
        nanoTime: Long = 0L,
        content: @Composable () -> Unit,
    ): ByteArray {
        val scene = ImageComposeScene(
            width = width,
            height = height,
            density = Density(1f),
            content = content,
        )
        return try {
            val image = scene.render(nanoTime)
            val data = image.encodeToData(EncodedImageFormat.PNG)
                ?: error("Failed to encode PNG snapshot")
            data.bytes
        } finally {
            scene.close()
        }
    }

    @Test
    fun focusCanvasAnimatesWhenMotionAllowedAndFreezesUnderReducedMotion() {
        val animatedScene = ImageComposeScene(
            width = 320,
            height = 320,
            density = Density(1f),
        ) {
            SharedAnchorTheme(
                darkTheme = false,
                displayFont = FontFamily.Serif,
                bodyFont = FontFamily.SansSerif,
                reducedMotion = false,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(LocalAnchorTokens.current.paper),
                ) {
                    FocusCanvas(
                        progress = 0.65f,
                        isWorkPhase = true,
                        remainingSeconds = 525L,
                        taskTitle = "Draft chemistry lab intro",
                    )
                }
            }
        }
        val frame0: ByteArray
        val frameQuarter: ByteArray
        try {
            val t = 0L
            animatedScene.render(t)
            frame0 = animatedScene.render(t + 16_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
            frameQuarter = animatedScene.render(2_516_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        } finally {
            animatedScene.close()
        }
        assertTrue(frame0.isNotEmpty())
        assertFalse(
            frame0.contentEquals(frameQuarter),
            "FocusCanvas tide wave should animate over time when reducedMotion is false",
        )

        val reducedScene = ImageComposeScene(
            width = 320,
            height = 320,
            density = Density(1f),
        ) {
            SharedAnchorTheme(
                darkTheme = false,
                displayFont = FontFamily.Serif,
                bodyFont = FontFamily.SansSerif,
                reducedMotion = true,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(LocalAnchorTokens.current.paper),
                ) {
                    FocusCanvas(
                        progress = 0.65f,
                        isWorkPhase = true,
                        remainingSeconds = 525L,
                        taskTitle = "Draft chemistry lab intro",
                    )
                }
            }
        }
        val reducedFrame0: ByteArray
        val reducedFrameQuarter: ByteArray
        try {
            val t = 0L
            reducedScene.render(t)
            reducedFrame0 = reducedScene.render(t + 16_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
            reducedFrameQuarter = reducedScene.render(2_516_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        } finally {
            reducedScene.close()
        }
        assertContentEquals(
            reducedFrame0,
            reducedFrameQuarter,
            "FocusCanvas must remain completely static under LocalReducedMotion",
        )

        val dumpDir = System.getenv("ANCHOR_DUMP_PRIMITIVE_SNAPSHOTS")
        if (!dumpDir.isNullOrBlank()) {
            val dir = File(dumpDir).apply { mkdirs() }
            File(dir, "task-3-focus-reduced-motion.png").writeBytes(reducedFrame0)
        }
    }

    @Test
    fun sharedPrimitivesRenderInMorningLightAndNightDark() {
        val lightBytes = renderPng(width = 960, height = 920, nanoTime = 1_500_000_000L) {
            SharedPrimitivesSpecimen(darkTheme = false)
        }
        val darkBytes = renderPng(width = 960, height = 920, nanoTime = 1_500_000_000L) {
            SharedPrimitivesSpecimen(darkTheme = true)
        }

        assertTrue(lightBytes.isNotEmpty())
        assertTrue(darkBytes.isNotEmpty())
        assertFalse(
            lightBytes.contentEquals(darkBytes),
            "Morning Harbor Light and Night Harbor Dark specimens should render distinctly",
        )

        val dumpDir = System.getenv("ANCHOR_DUMP_PRIMITIVE_SNAPSHOTS")
        if (!dumpDir.isNullOrBlank()) {
            val dir = File(dumpDir).apply { mkdirs() }
            File(dir, "task-3-primitives-light.png").writeBytes(lightBytes)
            File(dir, "task-3-primitives-dark.png").writeBytes(darkBytes)
        }
    }

    @Test
    fun focusCanvasDrawsInsideUnboundedVerticalScrollWithMinHeight() {
        val blankCanvasBytes = renderPng(width = 360, height = 420) {
            SharedAnchorTheme(
                darkTheme = false,
                displayFont = FontFamily.Serif,
                bodyFont = FontFamily.SansSerif,
                reducedMotion = true,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(LocalAnchorTokens.current.paper),
                )
            }
        }

        // Case 1: FocusCanvas directly inside verticalScroll with heightIn(min = 310.dp)
        val directScene = ImageComposeScene(
            width = 360,
            height = 420,
            density = Density(1f),
        ) {
            SharedAnchorTheme(
                darkTheme = false,
                displayFont = FontFamily.Serif,
                bodyFont = FontFamily.SansSerif,
                reducedMotion = true,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalAnchorTokens.current.paper)
                        .verticalScroll(rememberScrollState()),
                ) {
                    FocusCanvas(
                        progress = 0.72f,
                        isWorkPhase = true,
                        remainingSeconds = 600L,
                        taskTitle = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 310.dp),
                    )
                }
            }
        }
        try {
            val image = directScene.render(0L)
            val pngBytes = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            assertTrue(pngBytes.isNotEmpty())
            assertFalse(
                pngBytes.contentEquals(blankCanvasBytes),
                "FocusCanvas inside verticalScroll must draw visible pixels",
            )
            // Verify water basin pixels well below the center text (y = 250 inside 310dp tall ring)
            val pixels = image.toComposeImageBitmap().toPixelMap()
            assertNotEquals(
                MorningHarborTokens.paper,
                pixels[180, 250],
                "FocusCanvas tide basin collapsed under unbounded verticalScroll constraints",
            )
        } finally {
            directScene.close()
        }

        // Case 2: FocusCanvas(Modifier.fillMaxSize()) inside Box(heightIn(min = 310.dp)) in verticalScroll
        // (mirrors Android FocusScreen.kt)
        val wrappedScene = ImageComposeScene(
            width = 360,
            height = 420,
            density = Density(1f),
        ) {
            SharedAnchorTheme(
                darkTheme = false,
                displayFont = FontFamily.Serif,
                bodyFont = FontFamily.SansSerif,
                reducedMotion = true,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalAnchorTokens.current.paper)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 310.dp),
                    ) {
                        FocusCanvas(
                            progress = 0.72f,
                            isWorkPhase = true,
                            remainingSeconds = 600L,
                            taskTitle = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        try {
            val image = wrappedScene.render(0L)
            val pngBytes = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            assertTrue(pngBytes.isNotEmpty())
            assertFalse(
                pngBytes.contentEquals(blankCanvasBytes),
                "Wrapped FocusCanvas inside verticalScroll must draw visible pixels",
            )
            val pixels = image.toComposeImageBitmap().toPixelMap()
            assertNotEquals(
                MorningHarborTokens.paper,
                pixels[180, 250],
                "Wrapped FocusCanvas tide basin collapsed under Box(heightIn(min = 310.dp)) in verticalScroll",
            )
        } finally {
            wrappedScene.close()
        }
    }
}

@Composable
private fun SharedPrimitivesSpecimen(darkTheme: Boolean) {
    SharedAnchorTheme(
        darkTheme = darkTheme,
        displayFont = FontFamily.Serif,
        bodyFont = FontFamily.SansSerif,
        reducedMotion = false,
    ) {
        val tokens = LocalAnchorTokens.current
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.paper)
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AnchorBeaconPlate(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Next action",
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.beaconPlateCta,
                    )
                    Text(
                        text = "Finish organic chemistry summary",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.beaconPlateInk,
                    )
                    Text(
                        text = "First step: open chapter 4 notes to page 12",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.beaconPlateInk2,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HarborCtaButton(onClick = {}, onBeaconPlate = true) {
                            Text("Start 25 min focus")
                        }
                        AnchorQuietButton(
                            label = "Break down",
                            onClick = {},
                            outlined = false,
                            tint = tokens.beaconPlateCaptain,
                        )
                    }
                }

                AnchorTodayTideStrip(
                    todayMinutes = 45,
                    goalMinutes = 90,
                    stageLabel = "Growing",
                )

                AnchorWell(
                    tone = AnchorWellTone.Captain,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Harbor Captain",
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.captain,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnchorChip(label = "25m", selected = true, onClick = {})
                        AnchorChip(label = "15m", selected = false, onClick = {})
                        AnchorChip(label = "Break down", selected = true, onClick = {}, captainAccent = true)
                    }
                }

                AnchorFocalPlate(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AnchorSectionHeader(
                        title = "Today's plan",
                        trailingLabel = "2 left",
                        onTrailingClick = {},
                    )
                    AnchorListRow(
                        title = "Email TA about lab extension",
                        subtitle = "Before 5:00 PM",
                        trailingText = "10m",
                        onClick = {},
                    )
                    AnchorListRow(
                        title = "Review flashcards",
                        trailingText = "20m",
                        showDivider = false,
                        onClick = {},
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HarborCtaButton(onClick = {}) {
                        Text("Primary CTA")
                    }
                    HarborCtaButton(onClick = {}, enabled = false) {
                        Text("Disabled")
                    }
                    AnchorQuietButton(label = "Secondary", onClick = {})
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(210.dp)) {
                        FocusCanvas(
                            progress = 0.72f,
                            isWorkPhase = true,
                            remainingSeconds = 420L,
                            taskTitle = "Organic chem notes",
                        )
                    }
                    Box(Modifier.size(210.dp)) {
                        FocusCanvas(
                            progress = 0.85f,
                            isWorkPhase = false,
                            remainingSeconds = 75L,
                            taskTitle = "Restorative break",
                        )
                    }
                }

                AnchorWeeklyTide(
                    days = listOf(
                        WeeklyTideDay("M", 25, false),
                        WeeklyTideDay("T", 50, false),
                        WeeklyTideDay("W", 0, false),
                        WeeklyTideDay("T", 75, false),
                        WeeklyTideDay("F", 40, false),
                        WeeklyTideDay("S", 30, false),
                        WeeklyTideDay("S", 60, true),
                    ),
                    stageLabel = "Rooted",
                    totalMinutes = 280,
                    todayMinutes = 60,
                    deflectedCount = 4,
                )

                AnchorFocalPlate {
                    HarborEmptyState(
                        title = "Inbox is clear",
                        message = "Capture thoughts whenever they come up.",
                        actionLabel = "Add task",
                        onAction = {},
                    )
                }
            }
        }
    }
}
