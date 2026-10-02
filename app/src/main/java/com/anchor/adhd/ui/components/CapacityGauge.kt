package com.anchor.adhd.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.anchor.adhd.data.model.WorkloadStatus
import androidx.compose.ui.graphics.Color
import com.anchor.adhd.ui.theme.AnchorColors

@Composable
fun CapacityGauge(
    usedMinutes: Int,
    capacityMinutes: Int,
    status: WorkloadStatus,
    modifier: Modifier = Modifier
) {
    val progress = if (capacityMinutes <= 0) 0f else (usedMinutes.toFloat() / capacityMinutes).coerceIn(0f, 1.2f)
    val color = when (status) {
        WorkloadStatus.OVER -> AnchorColors.HarborAction
        WorkloadStatus.WARNING -> AnchorColors.HarborPrimary
        WorkloadStatus.OK -> AnchorColors.HarborGrowth
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        Text(
            "$usedMinutes / $capacityMinutes min",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val stroke = 6.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(0f, 0f),
                size = Size(size.width, size.height * 2),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = 180f * progress.coerceAtMost(1f),
                useCenter = false,
                topLeft = Offset(0f, 0f),
                size = Size(size.width, size.height * 2),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
    }
}
