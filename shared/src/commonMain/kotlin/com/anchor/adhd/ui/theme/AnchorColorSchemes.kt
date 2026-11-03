package com.anchor.adhd.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Shared Material3 schemes. Android and desktop wrappers must delegate here
 * instead of forking hex codes, so both apps stay one Anchor.
 */
fun anchorDarkColorScheme() = darkColorScheme(
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
    surface = AnchorColors.HarborSurface,
    onSurface = Color(0xFFE8EDF3),
    surfaceVariant = AnchorColors.HarborSurfaceContainer,
    onSurfaceVariant = Color(0xFFA8B4C0),
    outline = Color(0xFF3A4D62),
    outlineVariant = Color(0xFF243040),
    error = Color(0xFFE8AFAF),
    onError = Color(0xFF4A1111),
    errorContainer = Color(0xFF5C2323),
    onErrorContainer = Color(0xFFF6D9D9)
)

/**
 * Morning Harbor: warm paper canvas for daytime student use.
 * Dark-only caused glare and eye strain; light is the default.
 */
fun anchorLightColorScheme() = lightColorScheme(
    primary = AnchorColors.MorningPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4E7F2),
    onPrimaryContainer = Color(0xFF0E2E40),
    secondary = AnchorColors.MorningAi,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2DBFA),
    onSecondaryContainer = Color(0xFF241A4D),
    tertiary = AnchorColors.MorningBeacon,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7E3C8),
    onTertiaryContainer = AnchorColors.MorningBeaconInk,
    background = AnchorColors.MorningCanvas,
    onBackground = AnchorColors.MorningInk,
    surface = AnchorColors.MorningCard,
    onSurface = AnchorColors.MorningInk,
    surfaceVariant = AnchorColors.MorningContainer,
    onSurfaceVariant = AnchorColors.MorningMuted,
    outline = Color(0xFFB9C4D2),
    outlineVariant = Color(0xFFDCD5C2),
    error = AnchorColors.ClayRose,
    onError = Color.White,
    errorContainer = AnchorColors.ClayRoseContainer,
    onErrorContainer = Color(0xFF4A1F1F)
)
