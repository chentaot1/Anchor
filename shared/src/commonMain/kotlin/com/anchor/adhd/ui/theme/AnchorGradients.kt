package com.anchor.adhd.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Premium gradient language. Rules:
 * - The beacon gradient is reserved for the ONE next action per screen.
 * - Blue gradients carry information/hero surfaces; pilot teal carries AI.
 * - Never put body text on a gradient — dark beacon ink on beacon,
 *   white on blue, deep pilot ink on teal. Same stops in both themes so Android and
 *   desktop render identical heroes.
 */
object AnchorGradients {
    /** Blue + orange + pilot-teal trio, stated plainly. */
    val HarborBlue = Color(0xFF5EA8EE)
    val HarborOrange = Color(0xFFFF9E4F)
    val HarborPilot = Color(0xFF6FE3E0)

    /** Hero next-action CTA: warm orange pour, dark beacon ink text. */
    fun beaconCta(): Brush = Brush.linearGradient(
        colors = listOf(Color(0xFFFF9E4F), Color(0xFFFFC084))
    )

    /** Deep harbor hero surface behind beacon content (dark surfaces). */
    fun harborHero(): Brush = Brush.verticalGradient(
        colors = listOf(Color(0xFF16294A), Color(0xFF0E1624))
    )

    /** Soft blue information glow for focus/progress surfaces. */
    fun blueGlow(): Brush = Brush.radialGradient(
        colors = listOf(Color(0x335EA8EE), Color.Transparent)
    )

    /** Pilot-teal AI surface wash. */
    fun aiWash(): Brush = Brush.linearGradient(
        colors = listOf(Color(0x406FE3E0), Color(0x146FE3E0))
    )
}
