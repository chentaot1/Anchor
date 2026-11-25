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
import androidx.compose.ui.unit.dp
import com.anchor.adhd.domain.HabitHeatmapDay
import com.anchor.adhd.ui.theme.LocalAnchorTokens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitHeatmap(
    days: List<HabitHeatmapDay>,
    modifier: Modifier = Modifier,
    cellSize: Int = 10,
) {
    if (days.isEmpty()) return
    val tokens = LocalAnchorTokens.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Last 12 weeks",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            days.forEach { day ->
                val color = when {
                    day.completed -> tokens.sage
                    day.graceDay -> tokens.sage.copy(alpha = 0.55f)
                    !day.scheduled -> tokens.paper3
                    else -> tokens.sage.copy(alpha = 0.22f)
                }
                Box(
                    Modifier
                        .size(cellSize.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color),
                )
            }
        }
    }
}