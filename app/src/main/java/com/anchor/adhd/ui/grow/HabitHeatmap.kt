package com.anchor.adhd.ui.grow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.anchor.adhd.domain.HabitHeatmapDay

private val COMPLETE_GREEN = Color(0xFF40916C)
private val GRACE_MUTED = Color(0xFF74C69D)
private val NOT_SCHEDULED = Color(0xFFE8E8E8)
private val MISSED = Color(0xFFBDBDBD)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitHeatmap(
    days: List<HabitHeatmapDay>,
    modifier: Modifier = Modifier,
    cellSize: Int = 10
) {
    if (days.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Last 12 weeks",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            days.forEach { day ->
                val color = when {
                    day.completed -> COMPLETE_GREEN
                    day.graceDay -> GRACE_MUTED.copy(alpha = 0.5f)
                    !day.scheduled -> NOT_SCHEDULED.copy(alpha = 0.4f)
                    else -> MISSED.copy(alpha = 0.6f)
                }
                Box(
                    Modifier
                        .size(cellSize.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
            }
        }
    }
}
