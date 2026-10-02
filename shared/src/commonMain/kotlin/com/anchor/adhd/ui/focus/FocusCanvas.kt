package com.anchor.adhd.ui.focus

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.adhd.domain.PlantSpecies
import com.anchor.adhd.ui.theme.AnchorColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FocusCanvas(
    progress: Float,
    modifier: Modifier = Modifier,
    species: PlantSpecies = PlantSpecies.OAK,
    isWorkPhase: Boolean = true,
    remainingSeconds: Long = 0L,
    taskTitle: String? = null
) {
    // 0.1 Hz breathing resonance (10-second full cycle = 10,000ms)
    val infiniteTransition = rememberInfiniteTransition(label = "luminousHarbor")
    val timeState by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "tidePhase"
    )

    // Pre-allocated reusable geometry objects to guarantee zero allocation per frame
    val wavePath1 = remember { Path() }
    val wavePath2 = remember { Path() }
    val waveCrest1 = remember { Path() }
    val waveCrest2 = remember { Path() }
    val clipCirclePath = remember { Path() }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val width = size.width
                    val height = size.height
                    val baseRadius = (width.coerceAtMost(height) / 2f) * 0.74f
                    val center = Offset(width / 2f, height / 2f)

                    val a1Px = 9.dp.toPx()
                    val a2Px = 6.dp.toPx()
                    val lambda1 = width * 0.75f
                    val lambda2 = width * 0.48f

                    // Palettes
                    val primaryAuraColor = if (isWorkPhase) AnchorColors.HarborPrimary else AnchorColors.HarborFoliage
                    val accentAuraColor = if (isWorkPhase) AnchorColors.HarborAction else AnchorColors.HarborGrowth
                    val arcStartColor = if (isWorkPhase) Color(0xFF38BDF8) else Color(0xFF34D399)
                    val arcEndColor = if (isWorkPhase) Color(0xFFF59E0B) else Color(0xFF10B981)

                    onDrawBehind {
                        val t = timeState
                        // 0.1 Hz sine modulation for radius dilation and alpha pulsing
                        val breathProgress = ((1f + sin(t - (PI / 2.0).toFloat())) / 2f) // 0f .. 1f
                        val breathAlpha = 0.25f + 0.45f * breathProgress
                        val dynamicRadius = baseRadius * (1.0f + 0.025f * sin(t - (PI / 2.0).toFloat()))
                        val clampedProgress = progress.coerceIn(0f, 1f)

                        // 1. Outer Breathing Resonance Halos (0.1 Hz)
                        drawCircle(
                            color = primaryAuraColor.copy(alpha = 0.08f + 0.12f * breathProgress),
                            radius = dynamicRadius + 24.dp.toPx(),
                            center = center
                        )
                        drawCircle(
                            color = primaryAuraColor.copy(alpha = 0.15f + 0.18f * breathProgress),
                            radius = dynamicRadius + 12.dp.toPx(),
                            center = center
                        )
                        drawCircle(
                            color = accentAuraColor.copy(alpha = 0.18f + 0.22f * breathProgress),
                            radius = dynamicRadius + 4.dp.toPx(),
                            center = center
                        )

                        // 2. Gauge Track Ring
                        drawCircle(
                            color = Color(0xFF1E293B).copy(alpha = 0.90f),
                            radius = dynamicRadius,
                            center = center,
                            style = Stroke(width = 3.dp.toPx())
                        )

                        // 3. 60 Chronometer Graduation Ticks
                        for (i in 0 until 60) {
                            val tickAngleDeg = -90f + i * 6f
                            val tickAngleRad = (tickAngleDeg * PI / 180.0).toFloat()
                            val cosA = cos(tickAngleRad)
                            val sinA = sin(tickAngleRad)

                            val isMajor = (i % 5 == 0)
                            val tickLength = if (isMajor) 9.dp.toPx() else 4.5f.dp.toPx()
                            val tickWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx()

                            val tickProgressFraction = i / 60f
                            val isCompleted = clampedProgress >= tickProgressFraction

                            val tickColor = when {
                                isCompleted && isMajor -> accentAuraColor
                                isCompleted -> primaryAuraColor.copy(alpha = 0.70f)
                                isMajor -> Color(0xFF475569)
                                else -> Color(0xFF334155).copy(alpha = 0.45f)
                            }

                            val innerR = dynamicRadius - tickLength
                            val outerR = dynamicRadius + 1.dp.toPx()

                            val pStart = Offset(center.x + innerR * cosA, center.y + innerR * sinA)
                            val pEnd = Offset(center.x + outerR * cosA, center.y + outerR * sinA)

                            drawLine(
                                color = tickColor,
                                start = pStart,
                                end = pEnd,
                                strokeWidth = tickWidth,
                                cap = StrokeCap.Round
                            )
                        }

                        // 4. Luminous Neon Progress Arc & Head Bead
                        val sweepAngle = clampedProgress * 360f
                        if (sweepAngle > 0.5f) {
                            val arcRect = Rect(
                                center.x - dynamicRadius,
                                center.y - dynamicRadius,
                                center.x + dynamicRadius,
                                center.y + dynamicRadius
                            )

                            // Outer glowing arc bloom
                            drawArc(
                                color = primaryAuraColor.copy(alpha = 0.30f),
                                startAngle = -90f,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = arcRect.topLeft,
                                size = arcRect.size,
                                style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Core neon progress arc
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(arcStartColor, arcEndColor),
                                    center = center
                                ),
                                startAngle = -90f,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = arcRect.topLeft,
                                size = arcRect.size,
                                style = Stroke(width = 4.5f.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Glowing Head Bead at current progress
                            val headRad = ((-90f + sweepAngle) * PI / 180.0).toFloat()
                            val headX = center.x + dynamicRadius * cos(headRad)
                            val headY = center.y + dynamicRadius * sin(headRad)
                            val headOffset = Offset(headX, headY)

                            drawCircle(
                                color = arcEndColor.copy(alpha = 0.55f),
                                radius = 7.dp.toPx(),
                                center = headOffset
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 3.5f.dp.toPx(),
                                center = headOffset
                            )
                        }

                        // 5. Inner Harbor Reservoir & Waves
                        val innerRadius = dynamicRadius - 6.dp.toPx()
                        clipCirclePath.rewind()
                        clipCirclePath.addOval(
                            Rect(
                                center.x - innerRadius,
                                center.y - innerRadius,
                                center.x + innerRadius,
                                center.y + innerRadius
                            )
                        )

                        clipPath(clipCirclePath) {
                            // Deep nocturnal reservoir backdrop
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFF0F1B2E), Color(0xFF060A12)),
                                    center = Offset(center.x, center.y - innerRadius * 0.4f),
                                    radius = innerRadius * 1.5f
                                )
                            )

                            // Bioluminescent water particles
                            val particleY1 = center.y + innerRadius * 0.4f + 6.dp.toPx() * sin(t * 1.1f)
                            val particleY2 = center.y + innerRadius * 0.6f + 4.dp.toPx() * cos(t * 0.8f)
                            val particleY3 = center.y + innerRadius * 0.2f + 5.dp.toPx() * sin(t * 0.9f + 1f)
                            drawCircle(primaryAuraColor.copy(alpha = 0.35f), 1.5f.dp.toPx(), Offset(center.x - innerRadius * 0.45f, particleY1))
                            drawCircle(accentAuraColor.copy(alpha = 0.40f), 2.dp.toPx(), Offset(center.x + innerRadius * 0.35f, particleY2))
                            drawCircle(primaryAuraColor.copy(alpha = 0.30f), 1.5f.dp.toPx(), Offset(center.x + innerRadius * 0.10f, particleY3))

                            // Baseline tide level: rises from bottom (35% filled at 0%) to top (90% filled at 100%)
                            val circleBottom = center.y + innerRadius
                            val circleTop = center.y - innerRadius
                            val reservoirHeight = circleBottom - circleTop
                            val waterHeight = reservoirHeight * (0.35f + 0.55f * clampedProgress)
                            val baseLevel = circleBottom - waterHeight
                            val stepX = 16.dp.toPx()

                            // Wave 1: Deep Current (A1 = 9dp, λ1 = 0.75W, ω1 = 1.25 rad/s)
                            wavePath1.rewind()
                            waveCrest1.rewind()
                            wavePath1.moveTo(0f, height)
                            var x = 0f
                            val k1 = (2f * PI.toFloat()) / lambda1
                            var isFirst1 = true
                            while (x <= width + stepX) {
                                val y = baseLevel + a1Px * sin(k1 * x + 1.25f * t)
                                wavePath1.lineTo(x, y)
                                if (isFirst1) {
                                    waveCrest1.moveTo(x, y)
                                    isFirst1 = false
                                } else {
                                    waveCrest1.lineTo(x, y)
                                }
                                x += stepX
                            }
                            wavePath1.lineTo(width, height)
                            wavePath1.close()

                            val wave1Brush = Brush.verticalGradient(
                                colors = if (isWorkPhase) {
                                    listOf(Color(0xD91E40AF), Color(0xF20B192C))
                                } else {
                                    listOf(Color(0xD915803D), Color(0xF20B2015))
                                },
                                startY = baseLevel - a1Px,
                                endY = circleBottom
                            )
                            drawPath(wavePath1, wave1Brush)
                            drawPath(
                                waveCrest1,
                                if (isWorkPhase) Color(0xFF60A5FA).copy(alpha = 0.75f) else Color(0xFF86EFAC).copy(alpha = 0.75f),
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Wave 2: Glassy Surface Ripple (A2 = 6dp, λ2 = 0.48W, ω2 = 0.95 rad/s, phase offset π/3)
                            wavePath2.rewind()
                            waveCrest2.rewind()
                            wavePath2.moveTo(0f, height)
                            x = 0f
                            val k2 = (2f * PI.toFloat()) / lambda2
                            val phi2 = (PI / 3.0).toFloat()
                            var isFirst2 = true
                            while (x <= width + stepX) {
                                val y = baseLevel + a2Px * sin(k2 * x - 0.95f * t + phi2)
                                wavePath2.lineTo(x, y)
                                if (isFirst2) {
                                    waveCrest2.moveTo(x, y)
                                    isFirst2 = false
                                } else {
                                    waveCrest2.lineTo(x, y)
                                }
                                x += stepX
                            }
                            wavePath2.lineTo(width, height)
                            wavePath2.close()

                            val wave2Brush = Brush.verticalGradient(
                                colors = if (isWorkPhase) {
                                    listOf(Color(0x9938BDF8), Color(0x400284C7))
                                } else {
                                    listOf(Color(0x9934D399), Color(0x40059669))
                                },
                                startY = baseLevel - a2Px,
                                endY = circleBottom
                            )
                            drawPath(wavePath2, wave2Brush)
                            drawPath(
                                waveCrest2,
                                if (isWorkPhase) Color(0xFFBAE6FD).copy(alpha = 0.85f) else Color(0xFFA7F3D0).copy(alpha = 0.85f),
                                style = Stroke(width = 1.5f.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                }
        )

        // Center HUD: Countdown timer digits & task title
        Column(
            modifier = Modifier
                .padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val mins = remainingSeconds / 60
            val secs = remainingSeconds % 60
            val minsStr = if (mins < 10) "0$mins" else "$mins"
            val secsStr = if (secs < 10) "0$secs" else "$secs"
            val timeText = "$minsStr:$secsStr"

            Text(
                text = timeText,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 2.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            if (!taskTitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.padding(top = 8.dp))
                Text(
                    text = taskTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = AnchorColors.HarborPrimary.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
