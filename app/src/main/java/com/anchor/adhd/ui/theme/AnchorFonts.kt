package com.anchor.adhd.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.anchor.adhd.R

val NewsreaderFontFamily = FontFamily(
    Font(R.font.newsreader_medium, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.newsreader_medium, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.newsreader_semibold, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.newsreader_semibold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.newsreader_semibold, FontWeight.ExtraBold, FontStyle.Normal),
)

val AtkinsonFontFamily = FontFamily(
    Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.atkinson_hyperlegible_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.atkinson_hyperlegible_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

val DisplayFontFamily: FontFamily get() = NewsreaderFontFamily
val BodyFontFamily: FontFamily get() = AtkinsonFontFamily
