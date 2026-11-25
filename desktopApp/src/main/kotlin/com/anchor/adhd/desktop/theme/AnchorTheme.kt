package com.anchor.adhd.desktop.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.anchor.adhd.ui.theme.NightHarborTokens
import com.anchor.adhd.ui.theme.SharedAnchorTheme
import com.anchor.adhd.ui.theme.AnchorColors as SharedColors
import com.anchor.adhd.ui.theme.AnchorSpacing as SharedSpacing

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
    val HarborPilotInk get() = SharedColors.HarborPilotInk
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
    val screenHorizontal get() = SharedSpacing.screenHorizontalDesktop
    val screenVertical get() = SharedSpacing.screenVertical
    val sectionGap get() = SharedSpacing.sectionGap
    val itemGap get() = SharedSpacing.itemGap
    val radiusPlate get() = SharedSpacing.radiusPlate
    val radiusWell get() = SharedSpacing.radiusWell
    val radiusButton get() = SharedSpacing.radiusButton
    val radiusScene get() = SharedSpacing.radiusScene
    val radiusCard get() = SharedSpacing.radiusCard
    val radiusChip get() = SharedSpacing.radiusChip
    val radiusSheet get() = SharedSpacing.radiusSheet
    val radiusPill get() = SharedSpacing.radiusPill
    val elevationFloating get() = SharedSpacing.elevationFloating
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
        cardBorder = NightHarborTokens.rule,
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
                listOf(AnchorColors.HarborBackground, Color(0xFF121820), AnchorColors.HarborSkyNight),
            ),
        planGradient =
            Brush.verticalGradient(
                listOf(Color(0xFF0A1420), AnchorColors.HarborBackground, Color(0xFF101828)),
            ),
    )

val LocalAnchorExtras = staticCompositionLocalOf { AnchorExtrasDark }

@Composable
fun AnchorTheme(
    darkTheme: Boolean = true,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAnchorExtras provides AnchorExtrasDark) {
        SharedAnchorTheme(
            darkTheme = darkTheme,
            displayFont = NewsreaderFontFamily,
            bodyFont = AtkinsonFontFamily,
            reducedMotion = reducedMotion,
            content = content,
        )
    }
}
