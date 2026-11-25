package com.anchor.adhd.ui.focus

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.anchor.adhd.ui.theme.AnchorMotion
import com.anchor.adhd.ui.theme.LocalAnchorTokens
import com.anchor.adhd.ui.theme.LocalReducedMotion
import kotlin.math.PI
import kotlin.math.sin

internal fun formatFocusCountdown(remainingSeconds: Long): String {
    val safeSeconds = remainingSeconds.coerceAtLeast(0L)
    val mins = safeSeconds / 60
    val secs = safeSeconds % 60
    val minsStr = if (mins < 10) "0$mins" else "$mins"
    val secsStr = if (secs < 10) "0$secs" else "$secs"
    return "$minsStr:$secsStr"
}

/**
 * Living Tide focus chronometer shared by Android and desktop.
 * Adapts to Morning Harbor (Light) and Night Harbor (Dark) via [LocalAnchorTokens],
 * and freezes to a calm static water horizon with zero infinite transition when
 * [LocalReducedMotion] is enabled.
 */
@Composable
fun FocusCanvas(
    progress: Float,
    modifier: Modifier = Modifier,
    isWorkPhase: Boolean = true,
    remainingSeconds: Long = 0L,
    taskTitle: String? = null,
) {
    val tokens = LocalAnchorTokens.current
    val reducedMotion = LocalReducedMotion.current

    // Only instantiate the 0.1 Hz tide transition when motion is allowed.
    val tidePhaseState = if (!reducedMotion) {
        val infiniteTransition = rememberInfiniteTransition(label = "livingTide")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = AnchorMotion.TIDE_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "tidePhase",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val wavePath1 = remember { Path() }
    val wavePath2 = remember { Path() }
    val clipCirclePath = remember { Path() }

    val clampedProgress = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    val timeText = formatFocusCountdown(remainingSeconds)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(if (isWorkPhase) "Focus timer: " else "Break timer: ")
                    append(timeText)
                    if (!taskTitle.isNullOrBlank()) {
                        append(", ")
                        append(taskTitle)
                    }
                    append(", ")
                    append((clampedProgress * 100).toInt())
                    append(" percent complete")
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val minDim = min(maxWidth, maxHeight)
        val hudHorizontalPadding = (minDim * 0.13f).coerceIn(16.dp, 36.dp)
        val displayScale = (minDim / 250.dp).coerceIn(0.62f, 1f)
        val baseDisplay = MaterialTheme.typography.displayLarge
        val timerStyle = if (displayScale < 0.999f) {
            baseDisplay.copy(
                fontSize = baseDisplay.fontSize * displayScale,
                lineHeight = baseDisplay.lineHeight * displayScale,
                fontFeatureSettings = "tnum",
            )
        } else {
            baseDisplay.copy(fontFeatureSettings = "tnum")
        }

        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val width = size.width
                    val height = size.height
                    val center = Offset(width / 2f, height / 2f)
                    val ringStrokePx = 5.dp.toPx()
                    val ringRadius = ((width.coerceAtMost(height) / 2f) - ringStrokePx - 6.dp.toPx())
                        .coerceAtLeast(1f)
                    val innerRadius = (ringRadius - ringStrokePx * 0.5f - 4.dp.toPx())
                        .coerceAtLeast(1f)

                    val a1Px = 7.dp.toPx()
                    val a2Px = 5.dp.toPx()
                    val lambda1 = (innerRadius * 2.2f).coerceAtLeast(1f)
                    val lambda2 = (innerRadius * 1.5f).coerceAtLeast(1f)
                    val stepX = 12.dp.toPx().coerceAtLeast(1f)
                    val staticLayerOffset = 3.dp.toPx()
                    val frontWaveOffset = 4.dp.toPx()

                    // Soft readability veil over rising water so Newsreader digits remain
                    // WCAG AA/AAA legible across 0%..100% progress in both Light and Dark.
                    val centerReadabilityVeil = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to tokens.tideBack.copy(alpha = 0.56f),
                            0.55f to tokens.tideBack.copy(alpha = 0.36f),
                            1.0f to Color.Transparent,
                        ),
                        center = center,
                        radius = (innerRadius * 0.92f).coerceAtLeast(1f),
                    )

                    val trackColor = tokens.paper3
                    val basinRuleColor = tokens.rule
                    val progressArcColor = if (isWorkPhase) tokens.beacon else tokens.sage
                    val tideBackColor = tokens.tideBack
                    val backWaveColor = (if (isWorkPhase) tokens.tideWave1 else tokens.tideBreakWave1)
                        .copy(alpha = 0.75f)
                    val frontWaveColor = (if (isWorkPhase) tokens.tideWave2 else tokens.tideBreakWave2)
                        .copy(alpha = 0.55f)
                    val horizonColor = (if (isWorkPhase) tokens.tideWave2 else tokens.tideBreakWave2)
                        .copy(alpha = 0.75f)

                    onDrawBehind {
                        if (width <= 1f || height <= 1f) return@onDrawBehind

                        val phase = tidePhaseState.value

                        // 1. Clipped Circular Tide Basin
                        val circleLeft = center.x - innerRadius
                        val circleTop = center.y - innerRadius
                        val circleRight = center.x + innerRadius
                        val circleBottom = center.y + innerRadius

                        clipCirclePath.rewind()
                        clipCirclePath.addOval(
                            Rect(circleLeft, circleTop, circleRight, circleBottom),
                        )

                        clipPath(clipCirclePath) {
                            drawRect(
                                color = tideBackColor,
                                topLeft = Offset(circleLeft, circleTop),
                                size = Size(innerRadius * 2f, innerRadius * 2f),
                            )

                            val reservoirHeight = circleBottom - circleTop
                            val waterHeight = reservoirHeight * (0.18f + 0.68f * clampedProgress)
                            val baseLevel = circleBottom - waterHeight

                            if (reducedMotion) {
                                // Calm flat static water horizon with layered depth band
                                val backTop = (baseLevel - staticLayerOffset).coerceIn(circleTop, circleBottom)
                                val frontTop = (baseLevel + staticLayerOffset).coerceIn(circleTop, circleBottom)
                                drawRect(
                                    color = backWaveColor,
                                    topLeft = Offset(circleLeft, backTop),
                                    size = Size(innerRadius * 2f, (circleBottom - backTop).coerceAtLeast(0f)),
                                )
                                drawRect(
                                    color = frontWaveColor,
                                    topLeft = Offset(circleLeft, frontTop),
                                    size = Size(innerRadius * 2f, (circleBottom - frontTop).coerceAtLeast(0f)),
                                )
                                drawLine(
                                    color = horizonColor,
                                    start = Offset(circleLeft, backTop),
                                    end = Offset(circleRight, backTop),
                                    strokeWidth = 1.dp.toPx(),
                                )
                            } else {
                                // Back wave
                                wavePath1.rewind()
                                wavePath1.moveTo(circleLeft, circleBottom)
                                val k1 = (2f * PI.toFloat()) / lambda1
                                var x = circleLeft
                                while (x <= circleRight + stepX) {
                                    val y = baseLevel + a1Px * sin(k1 * (x - circleLeft) + phase)
                                    wavePath1.lineTo(x, y)
                                    x += stepX
                                }
                                wavePath1.lineTo(x, circleBottom)
                                wavePath1.close()
                                drawPath(wavePath1, color = backWaveColor)

                                // Front counter-wave
                                wavePath2.rewind()
                                wavePath2.moveTo(circleLeft, circleBottom)
                                val k2 = (2f * PI.toFloat()) / lambda2
                                val phi2 = (PI / 3.0).toFloat()
                                x = circleLeft
                                while (x <= circleRight + stepX) {
                                    val y = baseLevel + frontWaveOffset + a2Px * sin(k2 * (x - circleLeft) - phase + phi2)
                                    wavePath2.lineTo(x, y)
                                    x += stepX
                                }
                                wavePath2.lineTo(x, circleBottom)
                                wavePath2.close()
                                drawPath(wavePath2, color = frontWaveColor)
                            }

                            // Soft center veil to protect timer legibility as the tide rises
                            drawCircle(
                                brush = centerReadabilityVeil,
                                radius = (innerRadius * 0.92f).coerceAtLeast(1f),
                                center = center,
                            )
                        }

                        // Subtle structural hairline around the inner basin
                        drawCircle(
                            color = basinRuleColor,
                            radius = innerRadius,
                            center = center,
                            style = Stroke(width = 1.dp.toPx()),
                        )

                        // 2. Outer Track Ring
                        drawCircle(
                            color = trackColor,
                            radius = ringRadius,
                            center = center,
                            style = Stroke(width = ringStrokePx),
                        )

                        // 3. Solid Progress Arc
                        val sweepAngle = clampedProgress * 360f
                        if (sweepAngle > 0.5f) {
                            drawArc(
                                color = progressArcColor,
                                startAngle = -90f,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                                size = Size(ringRadius * 2f, ringRadius * 2f),
                                style = Stroke(width = ringStrokePx, cap = StrokeCap.Round),
                            )
                        }
                    }
                },
        )

        // Center HUD: Newsreader tabular countdown & optional task title
        Column(
            modifier = Modifier.padding(horizontal = hudHorizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = timeText,
                style = timerStyle,
                color = tokens.ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )

            if (!taskTitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = taskTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.ink2,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
