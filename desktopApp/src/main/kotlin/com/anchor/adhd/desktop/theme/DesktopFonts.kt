package com.anchor.adhd.desktop.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

val NewsreaderFontFamily =
    FontFamily(
        Font(resource = "font/newsreader_medium.ttf", weight = FontWeight.Normal, style = FontStyle.Normal),
        Font(resource = "font/newsreader_medium.ttf", weight = FontWeight.Medium, style = FontStyle.Normal),
        Font(resource = "font/newsreader_semibold.ttf", weight = FontWeight.SemiBold, style = FontStyle.Normal),
        Font(resource = "font/newsreader_semibold.ttf", weight = FontWeight.Bold, style = FontStyle.Normal),
        Font(resource = "font/newsreader_semibold.ttf", weight = FontWeight.ExtraBold, style = FontStyle.Normal),
    )

val AtkinsonFontFamily =
    FontFamily(
        Font(resource = "font/atkinson_hyperlegible_regular.ttf", weight = FontWeight.Normal, style = FontStyle.Normal),
        Font(resource = "font/atkinson_hyperlegible_italic.ttf", weight = FontWeight.Normal, style = FontStyle.Italic),
        Font(resource = "font/atkinson_hyperlegible_bold.ttf", weight = FontWeight.Bold, style = FontStyle.Normal),
        Font(resource = "font/atkinson_hyperlegible_bold_italic.ttf", weight = FontWeight.Bold, style = FontStyle.Italic),
    )
