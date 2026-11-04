package com.anchor.adhd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.theme.AnchorGradients
import com.anchor.adhd.ui.theme.beaconGlow

/**
 * The ONE next-action button per screen, shared by Android and desktop:
 * warm beacon gradient pour with dark beacon-ink content. If a screen
 * needs a second button, it must not be this one.
 */
@Composable
fun HarborCtaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .beaconGlow(shape)
            .background(AnchorGradients.beaconCta(), shape),
        shape = shape,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color(0xFF1A1206),
            disabledContainerColor = Color.Transparent,
            disabledContentColor = Color(0xFF1A1206).copy(alpha = 0.4f)
        ),
        content = content
    )
}
