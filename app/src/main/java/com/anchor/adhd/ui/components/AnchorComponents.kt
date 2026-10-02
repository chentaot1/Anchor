package com.anchor.adhd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.theme.LocalAnchorExtras

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
    val extras = LocalAnchorExtras.current
    Box(
        modifier = modifier.background(extras.screenGradient)
    ) {
        content()
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .padding(top = 4.dp)
                .width(3.dp)
                .height(28.dp)
                .clip(MaterialTheme.shapes.small)
                .background(accentColor)
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AnchorCard(
    modifier: Modifier = Modifier,
    style: AnchorCardStyle = AnchorCardStyle.Default,
    content: @Composable ColumnScope.() -> Unit
) {
    val extras = LocalAnchorExtras.current
    val containerColor = when (style) {
        AnchorCardStyle.Default -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        AnchorCardStyle.Hero -> Color.Transparent
        AnchorCardStyle.Accent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    }
    val borderBrush = when (style) {
        AnchorCardStyle.Hero -> Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                extras.cardBorder,
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
            )
        )
        else -> Brush.linearGradient(listOf(extras.cardBorder, extras.cardBorder))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (style == AnchorCardStyle.Hero) {
                    Modifier
                        .background(extras.heroGradient, MaterialTheme.shapes.large)
                        .border(1.dp, borderBrush, MaterialTheme.shapes.large)
                } else {
                    Modifier.border(1.dp, borderBrush, MaterialTheme.shapes.large)
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (style == AnchorCardStyle.Hero) 6.dp else 2.dp
        )
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
fun StatusChip(
    label: String,
    color: Color = MaterialTheme.colorScheme.tertiary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.18f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    centered: Boolean = false
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Text("—", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = message,
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start
        )
    }
}
