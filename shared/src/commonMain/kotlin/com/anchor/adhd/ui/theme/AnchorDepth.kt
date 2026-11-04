package com.anchor.adhd.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Premium depth + entrance language, shared by Android and desktop.
 * One fake light source: cool white from above (sheen), warm accent
 * glow below heroes. Matte calm surfaces everywhere else.
 */
fun Modifier.beaconGlow(shape: Shape = RoundedCornerShape(14.dp)): Modifier =
    this.shadow(
        elevation = 14.dp,
        shape = shape,
        spotColor = Color(0x59FF9E4F),
        ambientColor = Color.Transparent
    )

fun Modifier.harborGlow(shape: Shape = RoundedCornerShape(20.dp)): Modifier =
    this.shadow(
        elevation = 10.dp,
        shape = shape,
        spotColor = Color(0x405EA8EE),
        ambientColor = Color.Transparent
    )

/** 2dp cool-white top edge inside [shape]: sells glass on dark surfaces. */
fun Modifier.topSheen(shape: Shape, alpha: Float = 0.10f): Modifier =
    this
        .clip(shape)
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = alpha), Color.Transparent)
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, 2.dp.toPx())
            )
        }

/**
 * Staggered hero entrance: fade + 24dp rise on a soft spring, delayed by
 * [index] * 60ms (capped). Callers pass `animate = false` when platform
 * reduced-motion is on.
 */
fun Modifier.harborEntrance(index: Int, animate: Boolean = true): Modifier = composed {
    if (!animate) return@composed this
    val alpha = androidx.compose.runtime.remember { Animatable(0f) }
    val rise = androidx.compose.runtime.remember { Animatable(24f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        delay((index * 60L).coerceAtMost(300L))
        launch { alpha.animateTo(1f, tween(AnchorMotion.STANDARD_MS)) }
        rise.animateTo(0f, spring(stiffness = 380f, dampingRatio = 0.85f))
    }
    this.graphicsLayer {
        this.alpha = alpha.value
        this.translationY = rise.value
    }
}
