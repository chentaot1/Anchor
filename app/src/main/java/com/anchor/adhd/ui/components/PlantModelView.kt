package com.anchor.adhd.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.anchor.adhd.domain.PlantSpecies
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode

private const val PLACEHOLDER_ASSET = "plants/placeholder.glb"

@Composable
fun PlantModelView(
    species: PlantSpecies,
    growth: Float,
    modifier: Modifier = Modifier,
    autoRotate: Boolean = false
) {
    val engine = LocalAnchorEngine.current
    val modelLoader = LocalAnchorModelLoader.current
    if (engine == null || modelLoader == null) {
        PlantIllustration(species, growth, modifier)
        return
    }

    val context = LocalContext.current
    val assetPath = remember(species) {
        if (assetExists(context, species.modelAssetPath)) species.modelAssetPath else PLACEHOLDER_ASSET
    }
    val scale = 0.35f + growth.coerceIn(0f, 1f) * 0.65f

    if (!assetExists(context, assetPath)) {
        PlantIllustration(species, growth, modifier)
        return
    }

    val childNodes = remember(assetPath, scale, autoRotate) {
        val instance = modelLoader.createModelInstance(assetPath) ?: return@remember emptyList()
        listOf(
            ModelNode(
                modelInstance = instance,
                scaleToUnits = scale.coerceIn(0.2f, 1.2f)
            ).apply {
                position = Position(y = -0.35f)
                if (autoRotate) {
                    rotation = Rotation(y = 18f)
                }
            }
        )
    }

    if (childNodes.isEmpty()) {
        PlantIllustration(species, growth, modifier)
        return
    }

    Box(modifier) {
        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            childNodes = childNodes,
            isOpaque = false
        )
    }
}

private fun assetExists(context: android.content.Context, path: String): Boolean =
    runCatching { context.assets.open(path).close(); true }.getOrDefault(false)
