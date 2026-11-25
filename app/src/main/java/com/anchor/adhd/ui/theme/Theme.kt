package com.anchor.adhd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

@Composable
fun AnchorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reducedMotion: Boolean = rememberReducedMotion(),
    content: @Composable () -> Unit,
) {
    SharedAnchorTheme(
        darkTheme = darkTheme,
        displayFont = NewsreaderFontFamily,
        bodyFont = AtkinsonFontFamily,
        reducedMotion = reducedMotion,
        content = content,
    )
}