package com.anchor.adhd.ui.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.theme.AnchorColors
import kotlin.math.atan2
import kotlin.math.PI

@Composable
fun CircularDurationPicker(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minMinutes: Int = 5,
    maxMinutes: Int = 120
) {
    var angle by remember(minutes) { mutableFloatStateOf(minutesToAngle(minutes, minMinutes, maxMinutes)) }
    Box(modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val a = atan2((change.position.y - cy).toDouble(), (change.position.x - cx).toDouble()).toFloat()
                        angle = a
                        onMinutesChange(angleToMinutes(a, minMinutes, maxMinutes))
                    }
                }
        ) {
            drawCircle(AnchorColors.HarborPrimary.copy(alpha = 0.2f), style = Stroke(12f))
            val sweep = normalizeSweep(angle)
            drawArc(
                color = AnchorColors.HarborAction,
                startAngle = -90f,
                sweepAngle = sweep.coerceIn(5f, 360f),
                useCenter = false,
                style = Stroke(12f, cap = StrokeCap.Round)
            )
        }
        Text("$minutes min", style = MaterialTheme.typography.headlineMedium, color = AnchorColors.HarborAction)
    }
}

private fun normalizeSweep(angle: Float): Float {
    val normalized = ((angle + (PI / 2).toFloat()) % (2 * PI).toFloat()) / (2 * PI).toFloat()
    return normalized * 360f
}

private fun minutesToAngle(minutes: Int, min: Int, max: Int): Float {
    val t = (minutes - min).toFloat() / (max - min).coerceAtLeast(1)
    return (t * 2 * PI - PI / 2).toFloat()
}

private fun angleToMinutes(angle: Float, min: Int, max: Int): Int {
    val normalized = ((angle + (PI / 2).toFloat()) % (2 * PI).toFloat()) / (2 * PI).toFloat()
    return (min + normalized * (max - min)).toInt().coerceIn(min, max)
}
