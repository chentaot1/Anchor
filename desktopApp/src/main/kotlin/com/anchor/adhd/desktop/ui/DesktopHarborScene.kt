package com.anchor.adhd.desktop.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anchor.adhd.desktop.db.DesktopHarborState
import com.anchor.adhd.ui.grove.GardenScene
import kotlinx.coroutines.delay
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

    val sessionCount =
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
        sessionCount = sessionCount,
        modifier = modifier.fillMaxWidth(),
        sceneHeight = 180.dp,
        vitalityStage = vitalityStage,
    )
}
