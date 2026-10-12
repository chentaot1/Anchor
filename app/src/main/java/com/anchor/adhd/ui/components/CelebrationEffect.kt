package com.anchor.adhd.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.theme.AnchorColors
import com.anchor.adhd.ui.theme.rememberReducedMotion
import kotlin.random.Random

data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val size: Float
)

@Composable
fun CelebrationEffect(
    active: Boolean,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {}
) {
    if (!active) return
    val reducedMotion = rememberReducedMotion()
    if (reducedMotion) {
        LaunchedEffect(active) { onFinished() }
        return
    }
    val density = LocalDensity.current
    val particles = remember {
        List(24) {
            ConfettiParticle(
                x = Random.nextFloat(),
                y = -0.1f,
                vx = (Random.nextFloat() - 0.5f) * 0.004f,
                vy = Random.nextFloat() * 0.006f + 0.002f,
                color = listOf(AnchorColors.HarborAction, AnchorColors.HarborPrimary, AnchorColors.HarborGrowth, AnchorColors.HarborAi).random(),
                size = Random.nextFloat() * 8f + 4f
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(active) {
        progress.animateTo(1f, tween(1200))
        onFinished()
    }
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val t = progress.value
            particles.forEach { p ->
                val px = (p.x + p.vx * t * 1000) * w
                val py = (p.y + p.vy * t * 1000) * h
                drawCircle(p.color, p.size * density.density, Offset(px, py))
            }
        }
    }
}
