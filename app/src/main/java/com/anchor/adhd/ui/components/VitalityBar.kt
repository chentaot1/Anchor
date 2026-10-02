package com.anchor.adhd.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.anchor.adhd.domain.GroveLevel
import com.anchor.adhd.ui.theme.AnchorColors

@Composable
fun VitalityBar(
    weeklyFocusSessions: Int,
    modifier: Modifier = Modifier,
    label: String = GroveLevel.vitalityLabel(GroveLevel.vitalityStageForWeeklySessions(weeklyFocusSessions))
) {
    val target = GroveLevel.vitalityProgress(weeklyFocusSessions)
    val progress by animateFloatAsState(target, label = "vitality")
    val pct = (progress * 100).toInt()
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            Text("Vitality", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$label · $pct%", style = MaterialTheme.typography.labelMedium, color = AnchorColors.HarborGrowth)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = AnchorColors.HarborGrowth,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun DifficultyStripe(difficulty: com.anchor.adhd.data.model.TaskDifficulty, modifier: Modifier = Modifier) {
    val color = when (difficulty) {
        com.anchor.adhd.data.model.TaskDifficulty.LIGHT -> Color(0xFF6FD89A)
        com.anchor.adhd.data.model.TaskDifficulty.MEDIUM -> AnchorColors.HarborAction
        com.anchor.adhd.data.model.TaskDifficulty.DEEP -> Color(0xFFE57373)
    }
    Canvas(modifier = modifier.height(24.dp).fillMaxWidth(0.02f)) {
        drawLine(color, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 6f)
    }
}
