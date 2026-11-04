package com.anchor.adhd.ui.theme

import androidx.compose.material3.Typography

/**
 * Typography roles live in shared [anchorTypography] (single source for
 * Android + desktop). Display = Nunito, body = Atkinson Hyperlegible.
 */
fun anchorTypography(base: Typography = Typography()) =
    anchorTypography(display = NunitoFontFamily, body = AtkinsonFontFamily, base = base)

/** Back-compat aliases while call sites migrate. */
val OutfitFontFamily get() = NunitoFontFamily
val PlusJakartaFontFamily get() = AtkinsonFontFamily
