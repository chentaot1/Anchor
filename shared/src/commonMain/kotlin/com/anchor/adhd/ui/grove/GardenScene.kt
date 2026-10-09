package com.anchor.adhd.ui.grove

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anchor.adhd.domain.PlantSpecies
import com.anchor.adhd.ui.theme.AnchorDesignPrinciples
import java.time.LocalTime
import java.util.Random
import kotlinx.coroutines.delay

data class HarborSkyPalette(
    val skyTop: Color,
    val skyHorizon: Color,
    val waterBase: Color,
    val specularAccent: Color
)

/**
 * Calculates continuous, non-snapping harbor colors linearly interpolated across 24 hours.
 */
fun calculateHarborPalette(minuteOfDay: Int): HarborSkyPalette {
    val m = minuteOfDay.coerceIn(0, 1439)

    val dawn = HarborSkyPalette(
        skyTop = Color(0xFF16203B),
        skyHorizon = Color(0xFFFFC699),
        waterBase = Color(0xFF1E3554),
        specularAccent = Color(0xFFFFAB6B)
    )
    val day = HarborSkyPalette(
        skyTop = Color(0xFF0F233D),
        skyHorizon = Color(0xFF7EB8FF),
        waterBase = Color(0xFF244872),
        specularAccent = Color(0xFFE2E8F0)
    )
    val dusk = HarborSkyPalette(
        skyTop = Color(0xFF1B1233),
        skyHorizon = Color(0xFFFF8252),
        waterBase = Color(0xFF2E1C3F),
        specularAccent = Color(0xFFA78BFA)
    )
    val night = HarborSkyPalette(
        skyTop = Color(0xFF0A1120),
        skyHorizon = Color(0xFF182845),
        waterBase = Color(0xFF0E1A2C),
        specularAccent = Color(0xFF7EB8FF)
    )

    val (start, end, fraction) = when {
        m in 390 until 720 -> Triple(dawn, day, (m - 390f) / 330f)
        m in 720 until 1110 -> Triple(day, dusk, (m - 720f) / 390f)
        m in 1110 until 1380 -> Triple(dusk, night, (m - 1110f) / 270f)
        else -> {
            val dist = if (m >= 1380) m - 1380f else 60f + m
            Triple(night, dawn, dist / 450f)
        }
    }

    return HarborSkyPalette(
        skyTop = lerp(start.skyTop, end.skyTop, fraction),
        skyHorizon = lerp(start.skyHorizon, end.skyHorizon, fraction),
        waterBase = lerp(start.waterBase, end.waterBase, fraction),
        specularAccent = lerp(start.specularAccent, end.specularAccent, fraction)
    )
}

/**
 * Living Illustrated Harbor Canvas.
 *
 * Renders the handcrafted high-fidelity harbor illustration as the base visual canvas,
 * overlaid with live dynamic Compose animations:
 * - Breathing amber radial lantern glows on dock piling posts.
 * - Dynamic 24h ambient daylight/midnight lighting modulation.
 * - Specular animated water ripple shimmer.
 * - Shooting star animations across the upper twilight sky.
 * - Evolving lifetime milestones permanently tied to focus sessions (treeCount).
 * - Drifting vitality fireflies on lush shoreline trees for consistent weekly focus.
 */
@Composable
fun GardenScene(
    treeCount: Int,
    modifier: Modifier = Modifier,
    vitalityStage: Int = 0,
    sceneHeight: Dp = 240.dp,
    basePainter: Painter? = null,
    boatPainter: Painter? = null
) {
    val now by produceState(initialValue = LocalTime.now()) {
        while (true) {
            delay(60_000L)
            value = LocalTime.now()
        }
    }
    val minuteOfDay = now.hour * 60 + now.minute
    val palette = remember(minuteOfDay) { calculateHarborPalette(minuteOfDay) }
    val isNightTime = minuteOfDay >= 1260 || minuteOfDay <= 360 // 9pm - 6am
    val isDayTime = minuteOfDay in 540..1020 // 9am - 5pm

    // Roll rare ambient easter egg once on entry
    val easterEggSeed = remember { Random().nextInt(100) }

    // Live continuous animation controllers
    val infiniteTransition = rememberInfiniteTransition(label = "harborAnimations")

    // Breathing warmth glow for dock lanterns
    val lanternPulse by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lanternPulse"
    )

    // Gentle specular drift for water ripples
    val rippleShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleShift"
    )

    // Periodic shooting star streak in the upper sky
    val shootingStarProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shootingStarProgress"
    )

    // Firefly / vitality floating drift
    val fireflyDrift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fireflyDrift"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(sceneHeight)
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette.skyTop,
                        palette.skyHorizon,
                        palette.waterBase
                    )
                )
            )
    ) {
        // 1. Base Layer: High-Fidelity Living Illustrated Harbor Base
        if (basePainter != null) {
            Image(
                painter = basePainter,
                contentDescription = "Living Harbor Scene",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 1b. Milestone 1+: Voyager's Handcrafted Moored Skiff arrives at the dock
        if (treeCount >= 1 && boatPainter != null) {
            Image(
                painter = boatPainter,
                contentDescription = "Moored Rowboat",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Dynamic Overlay Layer: Real-time lighting, animations, and milestones
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // --- 24h Time-of-Day Ambient Lighting Modulation ---
            if (isNightTime) {
                // Peaceful midnight sapphire veil
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x380A1120),
                            Color(0x20070E1A),
                            Color(0x15040810)
                        )
                    ),
                    size = size
                )

                // Twinkling stars in upper night sky
                val starPositions = listOf(
                    0.12f to 0.08f, 0.22f to 0.14f, 0.35f to 0.06f,
                    0.68f to 0.09f, 0.78f to 0.15f, 0.88f to 0.07f,
                    0.16f to 0.20f, 0.42f to 0.18f, 0.82f to 0.22f
                )
                starPositions.forEachIndexed { idx, (sx, sy) ->
                    val starTwinkle = if ((idx % 2 == 0)) lanternPulse else (1.65f - lanternPulse)
                    drawCircle(
                        color = Color.White.copy(alpha = (0.35f * starTwinkle).coerceIn(0.15f, 0.90f)),
                        radius = (1.2f + (idx % 2) * 0.6f).dp.toPx(),
                        center = Offset(w * sx, h * sy)
                    )
                }
            } else if (isDayTime) {
                // Subtle daylight ambient warmth wash
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x14FFEAA7),
                            Color(0x0A7EB8FF),
                            Color.Transparent
                        )
                    ),
                    size = size
                )
            }

            // --- Animated Shooting Star in Upper Sky ---
            if (shootingStarProgress in 0.20f..0.35f) {
                val p = (shootingStarProgress - 0.20f) / 0.15f
                val startX = w * (0.35f + p * 0.22f)
                val startY = h * (0.04f + p * 0.10f)
                val lengthPx = 36.dp.toPx()
                val tailX = startX - lengthPx * 0.85f
                val tailY = startY - lengthPx * 0.45f
                val alpha = if (p < 0.2f) p / 0.2f else if (p > 0.8f) (1f - p) / 0.2f else 1f

                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x807EB8FF).copy(alpha = 0.5f * alpha),
                            Color.White.copy(alpha = alpha)
                        ),
                        start = Offset(tailX, tailY),
                        end = Offset(startX, startY)
                    ),
                    start = Offset(tailX, tailY),
                    end = Offset(startX, startY),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = 2.2.dp.toPx(),
                    center = Offset(startX, startY)
                )
            }

            // --- Milestone 30+: Aurora Borealis Shimmering Veil ---
            if (treeCount >= 30 || easterEggSeed in 10..13) {
                val auroraPath = Path().apply {
                    moveTo(0f, h * 0.18f)
                    cubicTo(w * 0.30f, h * 0.10f, w * 0.65f, h * 0.22f, w, h * 0.12f)
                    lineTo(w, h * 0.22f)
                    cubicTo(w * 0.65f, h * 0.32f, w * 0.30f, h * 0.20f, 0f, h * 0.26f)
                    close()
                }
                drawPath(
                    auroraPath,
                    Brush.horizontalGradient(
                        listOf(
                            Color(0x0033A78B),
                            Color(0x3074C69D),
                            Color(0x35A78BFA),
                            Color(0x0033A78B)
                        )
                    )
                )
            }

            // --- Milestone 50+: Stone Lighthouse on Mountain Saddle ---
            if (treeCount >= 50) {
                val lhX = w * 0.485f
                val lhY = h * 0.455f
                // Stone tower
                drawRoundRect(
                    color = Color(0xFFE2E8F0),
                    topLeft = Offset(lhX - 3.dp.toPx(), lhY - 14.dp.toPx()),
                    size = Size(6.dp.toPx(), 14.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx())
                )
                // Amber lantern room
                drawRect(
                    color = Color(0xFFFFD166),
                    topLeft = Offset(lhX - 2.5f.dp.toPx(), lhY - 17.dp.toPx()),
                    size = Size(5.dp.toPx(), 3.dp.toPx())
                )
                // Lantern cap
                drawRect(
                    color = Color(0xFFE53E3E),
                    topLeft = Offset(lhX - 3.5f.dp.toPx(), lhY - 18.5f.dp.toPx()),
                    size = Size(7.dp.toPx(), 2.dp.toPx())
                )

                // Milestone 100+: Sweeping Lighthouse Beam
                if (treeCount >= 100) {
                    val beamPath = Path().apply {
                        moveTo(lhX, lhY - 15.5f.dp.toPx())
                        lineTo(lhX - w * 0.36f, h * 0.58f)
                        lineTo(lhX - w * 0.22f, h * 0.58f)
                        close()
                    }
                    drawPath(
                        beamPath,
                        Brush.linearGradient(
                            listOf(Color(0x55FFDAB9), Color(0x00FFDAB9)),
                            start = Offset(lhX, lhY - 15.5f.dp.toPx()),
                            end = Offset(lhX - w * 0.30f, h * 0.58f)
                        )
                    )
                }
            }

            // --- Specular Water Shimmer / Gentle Lake Ripples ---
            val waterRippleYs = listOf(0.61f, 0.65f, 0.70f, 0.75f)
            waterRippleYs.forEachIndexed { idx, yFrac ->
                val baseRx = 0.32f + (idx % 2) * 0.08f
                val driftX = (rippleShift * 0.04f) * if (idx % 2 == 0) 1f else -1f
                val rippleX = (baseRx + driftX).coerceIn(0.25f, 0.70f) * w
                val rippleY = yFrac * h
                val rippleWidth = (w * 0.24f) + (idx * 12.dp.toPx())
                val rippleAlpha = (0.16f + 0.08f * lanternPulse).coerceIn(0.10f, 0.30f)

                drawLine(
                    color = Color(0xFFFFD5A5).copy(alpha = rippleAlpha),
                    start = Offset(rippleX, rippleY),
                    end = Offset(rippleX + rippleWidth, rippleY),
                    strokeWidth = 1.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // --- Milestone 50+: Leaping Whale Fluke in Lake ---
            if (treeCount >= 50 || easterEggSeed in 23..24) {
                val whaleX = w * 0.22f
                val whaleY = h * 0.64f
                val fluke = Path().apply {
                    moveTo(whaleX, whaleY)
                    quadraticTo(whaleX - 7.dp.toPx(), whaleY - 10.dp.toPx(), whaleX - 14.dp.toPx(), whaleY - 12.dp.toPx())
                    quadraticTo(whaleX - 5.dp.toPx(), whaleY - 5.dp.toPx(), whaleX, whaleY - 2.5f.dp.toPx())
                    quadraticTo(whaleX + 5.dp.toPx(), whaleY - 5.dp.toPx(), whaleX + 14.dp.toPx(), whaleY - 12.dp.toPx())
                    quadraticTo(whaleX + 7.dp.toPx(), whaleY - 10.dp.toPx(), whaleX, whaleY)
                    close()
                }
                drawPath(fluke, Color(0xFF1E2E42))
                drawOval(
                    color = Color(0x40FFD5A5),
                    topLeft = Offset(whaleX - 12.dp.toPx(), whaleY - 1.5f.dp.toPx()),
                    size = Size(24.dp.toPx(), 3.5f.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // --- Milestone 5+: Gentle Mooring Wake & Shoreline Shimmer ---
            if (treeCount >= 5) {
                val wakeX = w * 0.42f
                val wakeY = h * 0.71f
                drawOval(
                    color = Color(0x35E2E8F0),
                    topLeft = Offset(wakeX - 18.dp.toPx(), wakeY),
                    size = Size(36.dp.toPx(), 2.dp.toPx())
                )
            }

            // --- Milestone 30+: Animated Breathing Warmth on Dock Lanterns ---
            if (treeCount >= 30) {
                // Left Lantern Center (Dock Post 2)
                val leftLanternCenter = Offset(w * 0.297f, h * 0.732f)
                val leftRadius = 26.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xD8FFB066).copy(alpha = 0.50f * lanternPulse),
                            Color(0x66FF8A3D).copy(alpha = 0.22f * lanternPulse),
                            Color.Transparent
                        ),
                        center = leftLanternCenter,
                        radius = leftRadius
                    ),
                    center = leftLanternCenter,
                    radius = leftRadius
                )
                // Left lantern reflection in water below post
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x40FFB066).copy(alpha = 0.30f * lanternPulse),
                            Color.Transparent
                        ),
                        startY = h * 0.76f,
                        endY = h * 0.84f
                    ),
                    topLeft = Offset(leftLanternCenter.x - 8.dp.toPx(), h * 0.76f),
                    size = Size(16.dp.toPx(), h * 0.08f)
                )

                // Right Lantern Center (Dock Post 6)
                val rightLanternCenter = Offset(w * 0.736f, h * 0.736f)
                val rightRadius = 26.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xD8FFB066).copy(alpha = 0.50f * lanternPulse),
                            Color(0x66FF8A3D).copy(alpha = 0.22f * lanternPulse),
                            Color.Transparent
                        ),
                        center = rightLanternCenter,
                        radius = rightRadius
                    ),
                    center = rightLanternCenter,
                    radius = rightRadius
                )
                // Right lantern reflection in water below post
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x40FFB066).copy(alpha = 0.30f * lanternPulse),
                            Color.Transparent
                        ),
                        startY = h * 0.76f,
                        endY = h * 0.84f
                    ),
                    topLeft = Offset(rightLanternCenter.x - 8.dp.toPx(), h * 0.76f),
                    size = Size(16.dp.toPx(), h * 0.08f)
                )
            }

            // --- Vitality Stage 3+: Drifting Fireflies / Pollen Motes near Grove Trees ---
            if (vitalityStage >= 3) {
                val fireflyOffsets = listOf(
                    0.80f to 0.52f, 0.88f to 0.48f, 0.94f to 0.56f,
                    0.77f to 0.62f, 0.84f to 0.68f, 0.92f to 0.65f
                )
                fireflyOffsets.forEachIndexed { i, (fx, fy) ->
                    val driftY = (fireflyDrift - 0.5f) * 8.dp.toPx() * if (i % 2 == 0) 1f else -1f
                    val driftX = (fireflyDrift - 0.5f) * 6.dp.toPx() * if (i % 3 == 0) -1f else 1f
                    val alpha = (0.45f + 0.35f * lanternPulse).coerceIn(0f, 1f)
                    drawCircle(
                        color = Color(0xFFFFF275).copy(alpha = alpha),
                        radius = (1.4f + (i % 2) * 0.5f).dp.toPx(),
                        center = Offset(w * fx + driftX, h * fy + driftY)
                    )
                    drawCircle(
                        color = Color(0x35FFF275).copy(alpha = alpha * 0.5f),
                        radius = (3.5f + (i % 2) * 1.0f).dp.toPx(),
                        center = Offset(w * fx + driftX, h * fy + driftY)
                    )
                }
            }
        }
    }
}

/**
 * Backward-compatible overload supporting legacy preview functions and tests.
 */
@Composable
fun GardenScene(
    plants: List<PlantSpecies>,
    vitalityProgress: Float,
    modifier: Modifier = Modifier
) {
    GardenScene(
        treeCount = plants.size,
        modifier = modifier,
        vitalityStage = (vitalityProgress.coerceIn(0f, 1f) * 4f).toInt()
    )
}
