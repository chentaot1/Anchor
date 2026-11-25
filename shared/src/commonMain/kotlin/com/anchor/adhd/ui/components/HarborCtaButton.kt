package com.anchor.adhd.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.theme.AnchorMotion
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens
import com.anchor.adhd.ui.theme.LocalReducedMotion

/**
 * The ONE next-action button per screen, shared by Android and desktop:
 * solid warm beacon fill with dark beacon-ink content and subtle tactile
 * press scale (disabled under reduced motion).
 */
@Composable
fun HarborCtaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(AnchorSpacing.radiusButton),
    enabled: Boolean = true,
    onBeaconPlate: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scaleState = if (reducedMotion) {
        null
    } else {
        animateFloatAsState(
            targetValue = if (isPressed && enabled) AnchorMotion.PRESS_SCALE else 1f,
            animationSpec = tween(durationMillis = AnchorMotion.MICRO_MS),
            label = "ctaPressScale",
        )
    }

    val containerColor = if (onBeaconPlate) tokens.beaconPlateCta else tokens.beacon

    Button(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            val scale = scaleState?.value ?: 1f
            scaleX = scale
            scaleY = scale
        },
        shape = shape,
        enabled = enabled,
        interactionSource = interactionSource,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = tokens.beaconInk,
            disabledContainerColor = containerColor.copy(alpha = 0.45f),
            disabledContentColor = tokens.beaconInk.copy(alpha = 0.45f),
        ),
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
        ) {
            content()
        }
    }
}
