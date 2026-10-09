package com.anchor.adhd.desktop.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.dp
import com.anchor.adhd.desktop.db.DesktopHarborState
import com.anchor.adhd.ui.grove.GardenScene
import kotlinx.coroutines.delay
import java.io.InputStream
import java.time.LocalTime

/**
 * Desktop Living Harbor Scene.
 * Integrates the shared Compose Multiplatform GardenScene with Skia hardware acceleration.
 * Overlays live dynamic animations: 24h sky gradients, breathing dock lanterns with water reflections,
 * sweeping lighthouse beam, drifting fireflies, and shooting stars.
 */
@Composable
fun DesktopHarborScene(
    harborState: DesktopHarborState,
    modifier: Modifier = Modifier,
) {
    val currentTime by produceState(initialValue = LocalTime.now()) {
        while (true) {
            delay(60_000L)
            value = LocalTime.now()
        }
    }
    // Keep currentTime read in composition scope so time-of-day sky transitions stay reactive on long sessions
    @Suppress("UNUSED_EXPRESSION")
    currentTime.hour

    val basePainter = remember { loadResourcePainter("grove_harbor_base.webp") }
    val boatPainter = remember { loadResourcePainter("grove_harbor_boat.webp") }

    val treeCount =
        if (harborState.unlockedSweepingBeam) {
            100
        } else if (harborState.unlockedLanterns) {
            35
        } else if (harborState.unlockedAurora) {
            30
        } else if (harborState.unlockedWake) {
            5
        } else if (harborState.unlockedBoat) {
            1
        } else {
            0
        }

    val vitalityStage =
        if (harborState.unlockedSweepingBeam) {
            4
        } else if (harborState.unlockedLanterns) {
            3
        } else if (harborState.unlockedAurora) {
            2
        } else if (harborState.unlockedWake) {
            1
        } else {
            0
        }

    GardenScene(
        treeCount = treeCount,
        modifier = modifier.fillMaxWidth(),
        sceneHeight = 180.dp,
        vitalityStage = vitalityStage,
        basePainter = basePainter,
        boatPainter = boatPainter,
    )
}

private fun loadResourcePainter(name: String): BitmapPainter? =
    try {
        val stream: InputStream? = Thread.currentThread().contextClassLoader.getResourceAsStream(name)
        if (stream != null) {
            BitmapPainter(
                androidx.compose.ui.res
                    .loadImageBitmap(stream),
            )
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
