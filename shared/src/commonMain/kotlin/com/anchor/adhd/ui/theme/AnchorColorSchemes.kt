package com.anchor.adhd.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Shared Material3 schemes built directly from [NightHarborTokens] and
 * [MorningHarborTokens]. Android and desktop wrappers delegate here via
 * [SharedAnchorTheme] so both apps stay one Anchor.
 */
fun anchorDarkColorScheme() = with(NightHarborTokens) {
    darkColorScheme(
        primary = primary,
        onPrimary = paper,
        primaryContainer = primarySoft,
        onPrimaryContainer = ink,
        secondary = captain,
        onSecondary = captainInk,
        secondaryContainer = captainSoft,
        onSecondaryContainer = ink,
        tertiary = beacon,
        onTertiary = beaconInk,
        tertiaryContainer = Color(0xFF422C14),
        onTertiaryContainer = ink,
        background = paper,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = paper2,
        onSurfaceVariant = muted,
        surfaceContainerLowest = paper,
        surfaceContainerLow = surface,
        surfaceContainer = paper2,
        surfaceContainerHigh = paper3,
        surfaceContainerHighest = paper3,
        outline = fieldEdge,
        outlineVariant = rule,
        error = clayRose,
        onError = paper,
        errorContainer = clayRoseSoft,
        onErrorContainer = ink,
    )
}

/**
 * Morning Harbor: warm paper canvas for daytime student use.
 */
fun anchorLightColorScheme() = with(MorningHarborTokens) {
    lightColorScheme(
        primary = primary,
        onPrimary = surface,
        primaryContainer = primarySoft,
        onPrimaryContainer = ink,
        secondary = captain,
        onSecondary = surface,
        secondaryContainer = captainSoft,
        onSecondaryContainer = captainInk,
        tertiary = beacon,
        onTertiary = beaconInk,
        tertiaryContainer = Color(0xFFF7E3C8),
        onTertiaryContainer = beaconInk,
        background = paper,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = paper2,
        onSurfaceVariant = muted,
        surfaceContainerLowest = surface,
        surfaceContainerLow = paper,
        surfaceContainer = paper2,
        surfaceContainerHigh = paper3,
        surfaceContainerHighest = paper3,
        outline = fieldEdge,
        outlineVariant = rule,
        error = clayRose,
        onError = surface,
        errorContainer = clayRoseSoft,
        onErrorContainer = ink,
    )
}
