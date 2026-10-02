package com.anchor.adhd.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

private val AnchorDark = darkColorScheme(
    primary = AnchorColors.HarborPrimary,
    onPrimary = Color(0xFF002A4A),
    primaryContainer = Color(0xFF1A4A6E),
    onPrimaryContainer = Color(0xFFD6EBFF),
    secondary = AnchorColors.HarborAi,
    onSecondary = Color(0xFF1A1030),
    secondaryContainer = Color(0xFF2D2448),
    onSecondaryContainer = Color(0xFFE8DEFF),
    tertiary = AnchorColors.HarborAction,
    onTertiary = Color(0xFF3D2200),
    tertiaryContainer = Color(0xFF5C3D1A),
    onTertiaryContainer = Color(0xFFFFE8D0),
    background = AnchorColors.HarborBackground,
    onBackground = Color(0xFFE8EDF3),
    surface = Color(0xFF121820),
    onSurface = Color(0xFFE8EDF3),
    surfaceVariant = Color(0xFF1A2330),
    onSurfaceVariant = Color(0xFFA8B4C0),
    outline = Color(0xFF3A4D62),
    outlineVariant = Color(0xFF243040),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val AnchorExtrasDark = AnchorExtras(
    screenGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF0E1520), AnchorColors.HarborBackground, Color(0xFF0A1218))
    ),
    heroGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF1A3D5C), Color(0xFF152535), Color(0xFF1A2E40))
    ),
    accentGlow = Color(0x337EB8FF),
    cardBorder = Color(0xFF2A3A4E),
    focusRing = Brush.sweepGradient(
        colors = listOf(AnchorColors.HarborPrimary, AnchorColors.HarborGrowth, AnchorColors.HarborPrimary)
    ),
    groveGradient = Brush.verticalGradient(
        listOf(AnchorColors.HarborBackground, Color(0xFF121820), AnchorColors.HarborDock.copy(alpha = 0.6f))
    ),
    focusGradient = Brush.verticalGradient(
        listOf(AnchorColors.HarborBackground, Color(0xFF121820), Color(0xFF1B2838))
    ),
    planGradient = Brush.verticalGradient(
        listOf(Color(0xFF0A1420), AnchorColors.HarborBackground, Color(0xFF101828))
    )
)

private val AnchorShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val BaseTypography = Typography()

val LocalAnchorExtras = staticCompositionLocalOf { AnchorExtrasDark }

@Composable
fun AnchorTheme(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalAnchorExtras provides AnchorExtrasDark) {
        MaterialTheme(
            colorScheme = AnchorDark,
            typography = anchorTypography(BaseTypography),
            shapes = AnchorShapes,
            content = {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides AnchorDark.onSurface
                ) { content() }
            }
        )
    }
}
