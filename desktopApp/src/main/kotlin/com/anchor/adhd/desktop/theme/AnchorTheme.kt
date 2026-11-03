package com.anchor.adhd.desktop.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.theme.AnchorColors as SharedColors
import com.anchor.adhd.ui.theme.anchorDarkColorScheme
import com.anchor.adhd.ui.theme.anchorLightColorScheme
import com.anchor.adhd.ui.theme.anchorTypography

/**
 * Desktop theme delegates to shared (`com.anchor.adhd.ui.theme`) so Windows
 * and Android render one Anchor. Do not fork hex codes, fonts, or motion
 * here — change shared and both apps follow.
 */
object AnchorColors {
    val HarborBackground get() = SharedColors.HarborBackground
    val HarborPrimary get() = SharedColors.HarborPrimary
    val HarborAction get() = SharedColors.HarborAction
    val HarborAi get() = SharedColors.HarborAi
    val HarborGrowth get() = SharedColors.HarborGrowth
    val HarborFoliage get() = SharedColors.HarborFoliage
    val HarborFoliageDark get() = SharedColors.HarborFoliageDark
    val HarborDock get() = SharedColors.HarborDock
    val HarborMist get() = SharedColors.HarborMist
    val HarborSkyNight get() = SharedColors.HarborSkyNight
    val HarborWater get() = SharedColors.HarborWater
    val HarborBeaconAmber get() = SharedColors.HarborBeaconAmber
}

object AnchorSpacing {
    val screenHorizontal = 24.dp
    val screenVertical = 16.dp
    val sectionGap = 24.dp
    val itemGap = 12.dp
    val radiusScene = 28.dp
    val radiusCard = 16.dp
    val radiusChip = 12.dp
    val radiusPill = 999.dp
    val elevationFloating = 6.dp
}

@Immutable
data class AnchorExtras(
    val screenGradient: Brush,
    val heroGradient: Brush,
    val accentGlow: Color,
    val cardBorder: Color,
    val focusRing: Brush,
    val groveGradient: Brush,
    val focusGradient: Brush,
    val planGradient: Brush,
)

val AnchorExtrasDark =
    AnchorExtras(
        screenGradient =
            Brush.verticalGradient(
                colors = listOf(Color(0xFF0E1520), AnchorColors.HarborBackground, Color(0xFF0A1218)),
            ),
        heroGradient =
            Brush.linearGradient(
                colors = listOf(Color(0xFF1A3D5C), Color(0xFF152535), Color(0xFF1A2E40)),
            ),
        accentGlow = Color(0x338AB4E8),
        cardBorder = Color(0xFF2A3A4E),
        focusRing =
            Brush.sweepGradient(
                colors = listOf(AnchorColors.HarborPrimary, AnchorColors.HarborGrowth, AnchorColors.HarborPrimary),
            ),
        groveGradient =
            Brush.verticalGradient(
                listOf(AnchorColors.HarborBackground, Color(0xFF121820), AnchorColors.HarborDock.copy(alpha = 0.6f)),
            ),
        focusGradient =
            Brush.verticalGradient(
                listOf(AnchorColors.HarborBackground, Color(0xFF121820), Color(0xFF1B2838)),
            ),
        planGradient =
            Brush.verticalGradient(
                listOf(Color(0xFF0A1420), AnchorColors.HarborBackground, Color(0xFF101828)),
            ),
    )

private val AnchorShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

val LocalAnchorExtras = staticCompositionLocalOf { AnchorExtrasDark }

@Composable
fun AnchorTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    // Desktop stays dark-first: many surfaces use hardcoded white-on-dark.
    // Light follows once those migrate to MaterialTheme tokens.
    val scheme = if (darkTheme) anchorDarkColorScheme() else anchorLightColorScheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalAnchorExtras provides AnchorExtrasDark) {
        MaterialTheme(
            colorScheme = scheme,
            typography = anchorTypography(display = NunitoFontFamily, body = AtkinsonFontFamily),
            shapes = AnchorShapes,
            content = content,
        )
    }
}
