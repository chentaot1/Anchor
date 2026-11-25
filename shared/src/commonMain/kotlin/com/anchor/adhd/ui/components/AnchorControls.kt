package com.anchor.adhd.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.theme.AnchorMotion
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens
import com.anchor.adhd.ui.theme.LocalReducedMotion

/**
 * Quiet secondary action button (outlined 1dp rule or plain text), calibrated to stay
 * visually subordinate to [HarborCtaButton].
 */
@Composable
fun AnchorQuietButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = true,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val tokens = LocalAnchorTokens.current
    val resolvedTint = when {
        tint != Color.Unspecified -> tint
        outlined -> tokens.ink
        else -> tokens.primary
    }
    val shape = RoundedCornerShape(AnchorSpacing.radiusButton)
    val textStyle = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")
    val buttonContent: @Composable RowScope.() -> Unit = {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = textStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 40.dp),
            enabled = enabled,
            shape = shape,
            border = BorderStroke(
                width = 1.dp,
                color = if (enabled) tokens.rule else tokens.rule.copy(alpha = 0.45f),
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = resolvedTint,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = resolvedTint.copy(alpha = 0.45f),
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            content = buttonContent,
        )
    } else {
        TextButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 40.dp),
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.textButtonColors(
                containerColor = Color.Transparent,
                contentColor = resolvedTint,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = resolvedTint.copy(alpha = 0.45f),
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            content = buttonContent,
        )
    }
}

/**
 * Tactile selection chip with 10dp rounding, tabular figures, and crisp hairline borders.
 * Uses primary tokens by default and starboard teal tokens when [captainAccent] is true.
 */
@Composable
fun AnchorChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    captainAccent: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val tokens = LocalAnchorTokens.current
    val reducedMotion = LocalReducedMotion.current
    val shape = RoundedCornerShape(AnchorSpacing.radiusChip)

    val targetContainer = when {
        !selected -> tokens.paper
        captainAccent -> tokens.captainSoft
        else -> tokens.primarySoft
    }
    val targetBorder = when {
        !selected -> tokens.rule
        captainAccent -> tokens.captain
        else -> tokens.primary
    }
    val targetContent = when {
        !selected -> tokens.ink2
        captainAccent -> tokens.captain
        else -> tokens.primary
    }

    val containerColor = if (reducedMotion) {
        targetContainer
    } else {
        animateColorAsState(
            targetValue = targetContainer,
            animationSpec = tween(durationMillis = AnchorMotion.MICRO_MS),
            label = "chipContainer",
        ).value
    }
    val borderColor = if (reducedMotion) {
        targetBorder
    } else {
        animateColorAsState(
            targetValue = targetBorder,
            animationSpec = tween(durationMillis = AnchorMotion.MICRO_MS),
            label = "chipBorder",
        ).value
    }
    val contentColor = if (reducedMotion) {
        targetContent
    } else {
        animateColorAsState(
            targetValue = targetContent,
            animationSpec = tween(durationMillis = AnchorMotion.MICRO_MS),
            label = "chipContent",
        ).value
    }

    val effectiveBorder = if (enabled) borderColor else borderColor.copy(alpha = 0.45f)
    val effectiveContent = if (enabled) contentColor else contentColor.copy(alpha = 0.45f)

    Surface(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .semantics { this.selected = selected }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = shape,
        color = containerColor,
        contentColor = effectiveContent,
        border = BorderStroke(1.dp, effectiveBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leadingIcon != null) {
                CompositionLocalProvider(LocalContentColor provides effectiveContent) {
                    leadingIcon()
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    fontFeatureSettings = "tnum",
                ),
                color = effectiveContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
