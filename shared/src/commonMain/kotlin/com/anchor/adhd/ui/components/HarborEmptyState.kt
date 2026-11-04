package com.anchor.adhd.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Calm harbor empty state shared by Android and desktop: a small beacon
 * glyph (lantern dot + tide rings on a tonal pool) with a plain-language
 * title, one-line message, and at most one action. One screen, one action.
 */
@Composable
fun HarborEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Canvas(Modifier.size(72.dp)) {
            drawCircle(scheme.secondaryContainer.copy(alpha = 0.55f), size.minDimension / 2f)
            drawCircle(
                scheme.tertiary.copy(alpha = 0.35f),
                size.minDimension * 0.32f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                scheme.tertiary.copy(alpha = 0.6f),
                size.minDimension * 0.18f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )
            drawCircle(scheme.tertiary, size.minDimension * 0.07f)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 4.dp)) {
                Text(actionLabel)
            }
        }
    }
}
