package com.anchor.adhd.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.anchor.adhd.ui.theme.AnchorColors
import com.anchor.adhd.ui.theme.rememberReducedMotion

@Composable
fun RaindropAnimation(active: Boolean, modifier: Modifier = Modifier) {
    if (!active) return
    val reducedMotion = rememberReducedMotion()
    if (reducedMotion) return
    var dropY by remember { mutableFloatStateOf(0f) }
    val y by animateFloatAsState(dropY, tween(800), label = "rain")
    LaunchedEffect(active) {
        dropY = 1f
    }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val x = size.width * 0.5f
            val startY = size.height * 0.15f
            val endY = size.height * 0.55f
            val cy = startY + (endY - startY) * y
            drawCircle(AnchorColors.HarborPrimary.copy(alpha = 0.8f), 6f, Offset(x, cy))
        }
    }
}
