package com.anchor.adhd.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens

enum class AnchorWellTone {
    Neutral,
    Captain,
}

/**
 * Level 3 (Committed) Deep-Harbor focal plate reserved exclusively for the ONE
 * primary "Next action" card on Home. Pairs a dark harbor plate with a 1dp structural
 * border and a crisp 2dp amber top ridge.
 */
@Composable
fun AnchorBeaconPlate(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    val shape = RoundedCornerShape(AnchorSpacing.radiusPlate)
    val ridgeColor = tokens.beaconPlateCta.copy(alpha = 0.65f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .drawWithContent {
                drawContent()
                drawRect(
                    color = ridgeColor,
                    topLeft = Offset.Zero,
                    size = Size(width = size.width, height = 2.dp.toPx()),
                )
            },
        shape = shape,
        color = tokens.beaconPlate,
        contentColor = tokens.beaconPlateInk,
        border = BorderStroke(1.dp, tokens.beaconPlateBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/**
 * Level 3 (Restrained) focal plate for single spotlight panels on secondary screens
 * (e.g. upcoming syllabus deliverable or rescue overlay).
 */
@Composable
fun AnchorFocalPlate(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    val shape = RoundedCornerShape(AnchorSpacing.radiusPlate)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = tokens.surface,
        contentColor = tokens.ink,
        border = BorderStroke(1.dp, tokens.rule),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/**
 * Level 2 borderless tonal well for secondary sections and Captain AI cues.
 */
@Composable
fun AnchorWell(
    modifier: Modifier = Modifier,
    tone: AnchorWellTone = AnchorWellTone.Neutral,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    val containerColor = when (tone) {
        AnchorWellTone.Neutral -> tokens.paper2
        AnchorWellTone.Captain -> tokens.captainSoft
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AnchorSpacing.radiusWell),
        color = containerColor,
        contentColor = tokens.ink,
        border = null,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

@Composable
fun AnchorHairline(modifier: Modifier = Modifier) {
    val tokens = LocalAnchorTokens.current
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = tokens.rule,
    )
}

/**
 * Level 1 hairline-divided list row for tasks, blocker rules, syllabus deliverables,
 * and settings without nested bordered boxes.
 */
@Composable
fun AnchorListRow(
    title: String,
    subtitle: String? = null,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    minHeight: Dp = 48.dp,
    showDivider: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAnchorTokens.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading?.invoke()

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (!trailingText.isNullOrBlank()) {
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = tokens.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            trailing?.invoke()
        }

        if (showDivider) {
            AnchorHairline()
        }
    }
}

/**
 * Sentence-case section heading with accessibility heading semantics and an optional
 * quiet trailing count or text link.
 */
@Composable
fun AnchorSectionHeader(
    title: String,
    trailingLabel: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAnchorTokens.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = tokens.ink,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (!trailingLabel.isNullOrBlank()) {
            if (onTrailingClick != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AnchorSpacing.radiusChip))
                        .clickable(role = Role.Button, onClick = onTrailingClick)
                        .heightIn(min = 40.dp)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = trailingLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = tokens.primary,
                        maxLines = 1,
                    )
                }
            } else {
                Text(
                    text = trailingLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = tokens.muted,
                    modifier = Modifier.padding(start = 12.dp),
                    maxLines = 1,
                )
            }
        }
    }
}
