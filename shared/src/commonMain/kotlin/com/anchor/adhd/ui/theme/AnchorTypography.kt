package com.anchor.adhd.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Shared typography roles. Display = Nunito (warm harbor rope, optimistic),
 * body/UI = Atkinson Hyperlegible (designed for low vision, maximal
 * b/d/p/q and I/l/1 differentiation for overwhelmed readers).
 *
 * Both platforms pass their loaded families in; roles and weights stay
 * identical so Android and desktop read as one Anchor.
 *
 * Rules: never use Light for critical info (timer uses ExtraBold with
 * tabular figures), never track out caps (see [AnchorVoice]).
 */
fun anchorTypography(display: FontFamily, body: FontFamily, base: Typography = Typography()) = base.copy(
    displayLarge = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 54.sp,
        lineHeight = 60.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    displaySmall = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(fontFamily = body, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = body, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = body, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = body, fontSize = 11.sp, lineHeight = 16.sp)
)
