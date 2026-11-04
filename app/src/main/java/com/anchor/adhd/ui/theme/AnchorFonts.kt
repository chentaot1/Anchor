package com.anchor.adhd.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.anchor.adhd.R

/**
 * Nunito (display) + Atkinson Hyperlegible (body). Loaded once here,
 * roles defined once in shared [anchorTypography].
 */
val NunitoFontFamily = FontFamily(
    Font(R.font.nunito_variable, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.nunito_variable, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.nunito_variable, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.nunito_variable, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.nunito_variable, FontWeight.ExtraBold, FontStyle.Normal),
    Font(R.font.nunito_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.nunito_italic, FontWeight.Bold, FontStyle.Italic)
)

val AtkinsonFontFamily = FontFamily(
    Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.atkinson_hyperlegible_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.atkinson_hyperlegible_bold_italic, FontWeight.Bold, FontStyle.Italic)
)

/** Back-compat aliases while call sites migrate to the new names. */
val DisplayFontFamily: FontFamily get() = NunitoFontFamily
val BodyFontFamily: FontFamily get() = AtkinsonFontFamily
