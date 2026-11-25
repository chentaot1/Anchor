package com.anchor.adhd.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.theme.LocalAnchorTokens

/**
 * Calm harbor empty state shared by Android and desktop: static 44dp geometric
 * tide-ring + beacon-dot mark, plain-language title, one-sentence message, and
 * at most one quiet action.
 */
@Composable
fun HarborEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val tokens = LocalAnchorTokens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(
            modifier = Modifier
                .size(44.dp)
                .clearAndSetSemantics { },
        ) {
            val stroke = Stroke(width = 1.5.dp.toPx())
            drawCircle(
                color = tokens.rule,
                radius = size.minDimension * 0.46f,
                style = stroke,
            )
            drawCircle(
                color = tokens.rule,
                radius = size.minDimension * 0.27f,
                style = stroke,
            )
            drawCircle(
                color = tokens.beacon,
                radius = 4.dp.toPx(),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = tokens.ink,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.muted,
            textAlign = TextAlign.Center,
        )
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            AnchorQuietButton(
                label = actionLabel,
                onClick = onAction,
                outlined = true,
                tint = tokens.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
