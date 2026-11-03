package com.anchor.adhd.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Unified motion system. One breath per screen: if the beacon breathes,
 * lanterns and ripples stay still. Motion explains state (start, complete,
 * drag, send), never decorates.
 *
 * All one-shot animation must honor reduced motion (see platform
 * `rememberReducedMotion`); ambient loops pause when focus is active.
 */
object AnchorMotion {
    const val INSTANT_MS = 100
    const val QUICK_MS = 200
    const val STANDARD_MS = 300
    const val SLOW_MS = 500

    /** Single ambient breath shared by beacon, lanterns and tide. */
    const val BREATH_MS = 2400

    /** Legacy harbor loops migrate here over time (10s tide, 9s star). */
    const val TIDE_MS = 10000

    val EnterEasing: Easing = FastOutSlowInEasing
    val ExitEasing: Easing = LinearEasing
    val EmphasizedEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

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
