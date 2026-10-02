package com.anchor.adhd.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.google.android.filament.Engine
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader

/** One Filament engine for the whole app — avoids GL teardown crashes when tabs change. */
val LocalAnchorEngine = staticCompositionLocalOf<Engine?> { null }
val LocalAnchorModelLoader = staticCompositionLocalOf<ModelLoader?> { null }

@Composable
fun Anchor3DProvider(enabled: Boolean = true, content: @Composable () -> Unit) {
    if (!enabled) {
        CompositionLocalProvider(
            LocalAnchorEngine provides null,
            LocalAnchorModelLoader provides null,
            content = content
        )
        return
    }
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    CompositionLocalProvider(
        LocalAnchorEngine provides engine,
        LocalAnchorModelLoader provides modelLoader,
        content = content
    )
}
