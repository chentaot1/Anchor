package com.anchor.adhd.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontFamily

@Composable
fun SharedAnchorTheme(
    darkTheme: Boolean,
    displayFont: FontFamily,
    bodyFont: FontFamily,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val tokens = if (darkTheme) NightHarborTokens else MorningHarborTokens
    val scheme = if (darkTheme) anchorDarkColorScheme() else anchorLightColorScheme()
    CompositionLocalProvider(
        LocalAnchorTokens provides tokens,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = anchorTypography(display = displayFont, body = bodyFont),
            shapes = AnchorShapes,
        ) {
            CompositionLocalProvider(LocalContentColor provides tokens.ink) {
                content()
            }
        }
    }
}
