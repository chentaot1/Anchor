package com.anchor.adhd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

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

private val AnchorExtrasDark = AnchorExtras(
    screenGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF0E1520), AnchorColors.HarborBackground, Color(0xFF0A1218)),
    ),
    heroGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF1A3D5C), Color(0xFF152535), Color(0xFF1A2E40)),
    ),
    accentGlow = Color(0x338AB4E8),
    cardBorder = NightHarborTokens.rule,
    focusRing = Brush.sweepGradient(
        colors = listOf(AnchorColors.HarborPrimary, AnchorColors.HarborGrowth, AnchorColors.HarborPrimary),
    ),
    groveGradient = Brush.verticalGradient(
        listOf(AnchorColors.HarborBackground, AnchorColors.HarborSurface, AnchorColors.HarborDock.copy(alpha = 0.6f)),
    ),
    focusGradient = Brush.verticalGradient(
        listOf(AnchorColors.HarborBackground, AnchorColors.HarborSurface, AnchorColors.HarborSkyNight),
    ),
    planGradient = Brush.verticalGradient(
        listOf(Color(0xFF0A1420), AnchorColors.HarborBackground, Color(0xFF101828)),
    ),
)

private val AnchorExtrasLight = AnchorExtras(
    screenGradient = Brush.verticalGradient(
        colors = listOf(AnchorColors.MorningCard, AnchorColors.MorningCanvas, AnchorColors.MorningContainer),
    ),
    heroGradient = Brush.linearGradient(
        colors = listOf(MorningHarborTokens.primarySoft, AnchorColors.MorningContainer, Color(0xFFF7E3C8)),
    ),
    accentGlow = MorningHarborTokens.beacon.copy(alpha = 0.20f),
    cardBorder = MorningHarborTokens.rule,
    focusRing = Brush.sweepGradient(
        colors = listOf(AnchorColors.MorningPrimary, AnchorColors.MorningSage, AnchorColors.MorningPrimary),
    ),
    groveGradient = Brush.verticalGradient(
        listOf(AnchorColors.MorningCard, AnchorColors.MorningCanvas, AnchorColors.MorningContainer),
    ),
    focusGradient = Brush.verticalGradient(
        listOf(AnchorColors.MorningCard, AnchorColors.MorningCanvas, MorningHarborTokens.primarySoft),
    ),
    planGradient = Brush.verticalGradient(
        listOf(AnchorColors.MorningCard, AnchorColors.MorningCanvas, AnchorColors.MorningContainer),
    ),
)

val LocalAnchorExtras = staticCompositionLocalOf { AnchorExtrasDark }

@Composable
fun AnchorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reducedMotion: Boolean = rememberReducedMotion(),
    content: @Composable () -> Unit,
) {
    val extras = if (darkTheme) AnchorExtrasDark else AnchorExtrasLight
    CompositionLocalProvider(LocalAnchorExtras provides extras) {
        SharedAnchorTheme(
            darkTheme = darkTheme,
            displayFont = NewsreaderFontFamily,
            bodyFont = AtkinsonFontFamily,
            reducedMotion = reducedMotion,
            content = content,
        )
    }
}
