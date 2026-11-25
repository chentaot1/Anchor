package com.anchor.adhd.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.anchor.adhd.domain.GroveLevel
import com.anchor.adhd.ui.theme.AnchorMotion
import com.anchor.adhd.ui.theme.LocalAnchorTokens

@Composable
fun VitalityBar(
    weeklyFocusSessions: Int,
    modifier: Modifier = Modifier,
    label: String = GroveLevel.vitalityLabel(GroveLevel.vitalityStageForWeeklySessions(weeklyFocusSessions)),
) {
    val tokens = LocalAnchorTokens.current
    val target = GroveLevel.vitalityProgress(weeklyFocusSessions)
    val progress by animateFloatAsState(target, AnchorMotion.slowGlow(), label = "vitality")
    val pct = (progress * 100).toInt()
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Vitality", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$label · $pct%", style = MaterialTheme.typography.labelMedium, color = tokens.sage)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = tokens.sage,
            trackColor = tokens.paper3,
        )
    }
}

@Composable
fun DifficultyStripe(difficulty: com.anchor.adhd.data.model.TaskDifficulty, modifier: Modifier = Modifier) {
    val tokens = LocalAnchorTokens.current
    val color = when (difficulty) {
        com.anchor.adhd.data.model.TaskDifficulty.LIGHT -> tokens.sage
        com.anchor.adhd.data.model.TaskDifficulty.MEDIUM -> tokens.beacon
        com.anchor.adhd.data.model.TaskDifficulty.DEEP -> tokens.clayRose
    }
    Canvas(modifier = modifier.height(24.dp).fillMaxWidth(0.02f)) {
        drawLine(color, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 6f)
    }
}