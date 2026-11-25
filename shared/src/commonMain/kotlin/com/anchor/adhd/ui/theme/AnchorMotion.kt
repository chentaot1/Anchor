package com.anchor.adhd.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf

val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * Unified state-only motion system. Motion explains state (press, switch,
 * sheet open, rising focus tide), never decorates.
 *
 * All transitions honor [LocalReducedMotion] provided by [SharedAnchorTheme].
 */
object AnchorMotion {
    const val MICRO_MS = 120
    const val SHORT_MS = 220
    const val SHEET_MS = 320
    const val TIDE_MS = 10000
    const val PRESS_SCALE = 0.985f

    // Backward-compatible durations kept until legacy screens complete migration.
    const val INSTANT_MS = 100
    const val QUICK_MS = 200
    const val STANDARD_MS = 300
    const val SLOW_MS = 500
    const val BREATH_MS = 2400

    val EnterEasing: Easing = FastOutSlowInEasing
    val ExitEasing: Easing = LinearEasing
    val EmphasizedEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    fun <T> microFade() = tween<T>(MICRO_MS, easing = EnterEasing)
    fun <T> shortState() = tween<T>(SHORT_MS, easing = EnterEasing)
    fun <T> sheetTransition() = tween<T>(SHEET_MS, easing = EmphasizedEasing)

    fun <T> quickFade() = tween<T>(QUICK_MS, easing = EnterEasing)
    fun <T> standardSlide() = tween<T>(STANDARD_MS, easing = EnterEasing)
    fun <T> slowGlow() = tween<T>(SLOW_MS, easing = EmphasizedEasing)
    fun <T> breath() = tween<T>(BREATH_MS, easing = LinearEasing)

    fun pressScale() = spring<Float>(stiffness = 600f, dampingRatio = 0.8f)
}

/**
 * Shared voice rules. Tracked-out caps ("N O W", "MOMENTUM SPARKS") are
 * harder to read under stress; use sentence case everywhere.
 */
object AnchorVoice {
    const val NOW_LABEL = "Now"
    const val NOW_ACTIVE_LABEL = "Focus active"
    const val MAX_TRACKING_SP = 0.5f
}
