package com.anchor.adhd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.theme.LocalAnchorTokens

val ScreenPadding = 16.dp
val SectionSpacing = 20.dp
val ItemSpacing = 12.dp

enum class AnchorCardStyle {
    Default,
    Hero,
    Accent
}

@Composable
fun AnchorScreenBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val tokens = LocalAnchorTokens.current
    Box(
        modifier = modifier.background(tokens.paper),
    ) {
        content()
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
  @Suppress("UNUSED_PARAMETER") accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AnchorCard(
    modifier: Modifier = Modifier,
    style: AnchorCardStyle = AnchorCardStyle.Default,
    content: @Composable ColumnScope.() -> Unit,
) {
    when (style) {
        AnchorCardStyle.Hero -> {
            AnchorFocalPlate(modifier = modifier, content = content)
        }
        AnchorCardStyle.Accent -> {
            AnchorWell(modifier = modifier, tone = AnchorWellTone.Captain, content = content)
        }
        AnchorCardStyle.Default -> {
            AnchorWell(modifier = modifier, content = content)
        }
    }
}

@Composable
fun StatusChip(
    label: String,
    color: Color = MaterialTheme.colorScheme.tertiary,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.18f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clearAndSetSemantics { }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("—", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = message,
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
    }
}