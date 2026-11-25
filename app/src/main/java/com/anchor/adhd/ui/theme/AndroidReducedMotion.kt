package com.anchor.adhd.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** True when system animator / transition scales are disabled (reduced motion). */
@Composable
fun rememberReducedMotion(): Boolean {
    val view = LocalView.current
    return remember(view) {
        val resolver = view.context.contentResolver
        val animator = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val transition = Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        animator == 0f || transition == 0f
    }
}
