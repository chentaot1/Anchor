package com.anchor.adhd.desktop.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/**
 * Same pairing as Android: Nunito display + Atkinson Hyperlegible body.
 * Files live in `desktopApp/src/main/resources/font/`, mirrored from
 * `app/src/main/res/font/`. Roles are defined once in shared
 * `com.anchor.adhd.ui.theme.anchorTypography`.
 */
val NunitoFontFamily = FontFamily(
    Font(resource = "font/nunito_variable.ttf", weight = FontWeight.Normal, style = FontStyle.Normal),
    Font(resource = "font/nunito_variable.ttf", weight = FontWeight.Medium, style = FontStyle.Normal),
    Font(resource = "font/nunito_variable.ttf", weight = FontWeight.SemiBold, style = FontStyle.Normal),
    Font(resource = "font/nunito_variable.ttf", weight = FontWeight.Bold, style = FontStyle.Normal),
    Font(resource = "font/nunito_variable.ttf", weight = FontWeight.ExtraBold, style = FontStyle.Normal),
    Font(resource = "font/nunito_italic.ttf", weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(resource = "font/nunito_italic.ttf", weight = FontWeight.Bold, style = FontStyle.Italic)
)

val AtkinsonFontFamily = FontFamily(
    Font(resource = "font/atkinson_hyperlegible_regular.ttf", weight = FontWeight.Normal, style = FontStyle.Normal),
    Font(resource = "font/atkinson_hyperlegible_italic.ttf", weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(resource = "font/atkinson_hyperlegible_bold.ttf", weight = FontWeight.Bold, style = FontStyle.Normal),
    Font(resource = "font/atkinson_hyperlegible_bold_italic.ttf", weight = FontWeight.Bold, style = FontStyle.Italic)
)
