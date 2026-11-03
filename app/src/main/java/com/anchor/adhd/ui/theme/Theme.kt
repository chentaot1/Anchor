package com.anchor.adhd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class AnchorExtras(
    val screenGradient: Brush,
    val heroGradient: Brush,
    val accentGlow: Color,
    val cardBorder: Color,
    val focusRing: Brush,
    val groveGradient: Brush,
    val focusGradient: Brush,
    val planGradient: Brush
)

/** Schemes live in shared so desktop renders the same Anchor. */
private val AnchorDark get() = anchorDarkColorScheme()
private val AnchorLight get() = anchorLightColorScheme()

private val AnchorExtrasDark = AnchorExtras(
    screenGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF0E1520), AnchorColors.HarborBackground, Color(0xFF0A1218))
    ),
    heroGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF1A3D5C), Color(0xFF152535), Color(0xFF1A2E40))
    ),
    accentGlow = Color(0x338AB4E8),
    cardBorder = Color(0xFF2A3A4E),
    focusRing = Brush.sweepGradient(
        colors = listOf(AnchorColors.HarborPrimary, AnchorColors.HarborGrowth, AnchorColors.HarborPrimary)
    ),
    groveGradient = Brush.verticalGradient(
        listOf(AnchorColors.HarborBackground, AnchorColors.HarborSurface, AnchorColors.HarborDock.copy(alpha = 0.6f))
    ),
    focusGradient = Brush.verticalGradient(
        listOf(AnchorColors.HarborBackground, AnchorColors.HarborSurface, Color(0xFF1B2838))
    ),
    planGradient = Brush.verticalGradient(
        listOf(Color(0xFF0A1420), AnchorColors.HarborBackground, Color(0xFF101828))
    )
)

private val AnchorExtrasLight = AnchorExtras(
    screenGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFFFFDF8), AnchorColors.MorningCanvas, Color(0xFFEFE7D3))
    ),
    heroGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFD4E7F2), Color(0xFFEDE7D8), Color(0xFFF7E3C8))
    ),
    accentGlow = Color(0x33D97E2B),
    cardBorder = Color(0xFFDCD5C2),
    focusRing = Brush.sweepGradient(
        colors = listOf(AnchorColors.MorningPrimary, AnchorColors.MorningSage, AnchorColors.MorningPrimary)
    ),
    groveGradient = Brush.verticalGradient(
        listOf(AnchorColors.MorningCard, AnchorColors.MorningCanvas, AnchorColors.MorningContainer)
    ),
    focusGradient = Brush.verticalGradient(
        listOf(AnchorColors.MorningCard, AnchorColors.MorningCanvas, Color(0xFFE4EBF2))
    ),
    planGradient = Brush.verticalGradient(
        listOf(Color(0xFFFFFDF8), AnchorColors.MorningCanvas, Color(0xFFEDE7D8))
    )
)

private val AnchorShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val LocalAnchorExtras = staticCompositionLocalOf { AnchorExtrasDark }

@Composable
fun AnchorTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (darkTheme) AnchorDark else AnchorLight
    val extras = if (darkTheme) AnchorExtrasDark else AnchorExtrasLight
    androidx.compose.runtime.CompositionLocalProvider(LocalAnchorExtras provides extras) {
        MaterialTheme(
            colorScheme = scheme,
            typography = anchorTypography(),
            shapes = AnchorShapes,
            content = {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides scheme.onSurface
                ) { content() }
            }
        )
    }
}
