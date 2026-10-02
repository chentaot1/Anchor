package com.anchor.adhd.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.anchor.adhd.domain.PlantSpecies
import com.anchor.adhd.ui.theme.AnchorColors
import kotlin.math.cos
import kotlin.math.sin

enum class PlantLod { DOCK, GRID, THUMB }

@Composable
fun PlantIllustration(
    species: PlantSpecies,
    growth: Float,
    modifier: Modifier = Modifier,
    lod: PlantLod = PlantLod.DOCK,
    tint: Color = AnchorColors.HarborFoliage
) {
    val scale = 0.4f + growth.coerceIn(0f, 1f) * 0.6f
    val foliage = speciesFoliage(species, tint)
    val detail = lod == PlantLod.DOCK
    Canvas(
        modifier.semantics { contentDescription = species.displayName }
    ) {
        val cx = size.width / 2f
        val baseY = size.height * 0.94f
        val h = size.height * 0.72f * scale
        when (species) {
            PlantSpecies.PINE, PlantSpecies.CYPRESS -> pine(cx, baseY, h, scale, foliage, species == PlantSpecies.CYPRESS)
            PlantSpecies.LAVENDER, PlantSpecies.IRIS, PlantSpecies.WILDFLOWER -> spikes(cx, baseY, h, scale, foliage, speciesBloom(species), detail)
            PlantSpecies.SUNFLOWER -> sunflower(cx, baseY, h, scale, foliage, detail)
            PlantSpecies.MUSHROOM -> mushroom(cx, baseY, h, scale, foliage)
            PlantSpecies.FERN -> fern(cx, baseY, h, scale, foliage, detail)
            PlantSpecies.MOSS -> moss(cx, baseY, h, scale, foliage)
            PlantSpecies.CLOVER -> clover(cx, baseY, h, scale, foliage)
            PlantSpecies.SEAGRASS -> seagrass(cx, baseY, h, scale, foliage)
            PlantSpecies.BUSH -> bush(cx, baseY, h, scale, foliage)
            PlantSpecies.VINE -> vine(cx, baseY, h, scale, foliage)
            PlantSpecies.WILLOW -> willow(cx, baseY, h, scale, foliage, detail)
            PlantSpecies.BIRCH -> birch(cx, baseY, h, scale, foliage)
            PlantSpecies.PALM -> palm(cx, baseY, h, scale, foliage)
            PlantSpecies.LANTERN_BLOOM -> lantern(cx, baseY, h, scale, foliage)
            PlantSpecies.ANCIENT_OAK -> oak(cx, baseY, h, scale, foliage, wide = true, detail = detail)
            PlantSpecies.BLOSSOM -> blossom(cx, baseY, h, scale, foliage, detail)
            else -> oak(cx, baseY, h, scale, foliage, wide = false, detail = detail)
        }
    }
}

private fun speciesFoliage(species: PlantSpecies, fallback: Color): Color = when (species) {
    PlantSpecies.PINE, PlantSpecies.CYPRESS -> AnchorColors.HarborFoliageDark
    PlantSpecies.LAVENDER, PlantSpecies.BLOSSOM, PlantSpecies.WILDFLOWER -> AnchorColors.HarborAi
    PlantSpecies.IRIS -> AnchorColors.HarborPrimary
    PlantSpecies.SUNFLOWER, PlantSpecies.MUSHROOM, PlantSpecies.LANTERN_BLOOM -> AnchorColors.HarborAction
    PlantSpecies.BIRCH -> fallback
    else -> fallback
}

private fun speciesBloom(species: PlantSpecies): Color = when (species) {
    PlantSpecies.IRIS -> AnchorColors.HarborPrimary
    PlantSpecies.WILDFLOWER -> AnchorColors.HarborAction
    else -> AnchorColors.HarborAi
}

private fun DrawScope.trunk(cx: Float, baseY: Float, height: Float, width: Float, color: Color = AnchorColors.HarborGrowth) {
    drawRoundRect(
        color,
        Offset(cx - width / 2f, baseY - height),
        Size(width, height),
        androidx.compose.ui.geometry.CornerRadius(width / 2f, width / 2f)
    )
}

private fun DrawScope.oak(
    cx: Float,
    baseY: Float,
    h: Float,
    scale: Float,
    foliage: Color,
    wide: Boolean,
    detail: Boolean
) {
    val trunkH = h * if (wide) 0.42f else 0.38f
    val trunkW = size.width * if (wide) 0.11f else 0.09f * scale
    trunk(cx, baseY, trunkH, trunkW.coerceAtLeast(size.width * 0.07f))
    val canopyW = size.width * if (wide) 0.62f else 0.52f * scale
    val canopyH = h * if (wide) 0.55f else 0.48f
    val top = baseY - h * 0.92f
    drawOval(foliage, Offset(cx - canopyW / 2f, top), Size(canopyW, canopyH))
    if (wide) {
        drawOval(foliage.copy(alpha = 0.85f), Offset(cx - canopyW * 0.55f, top + canopyH * 0.28f), Size(canopyW * 0.42f, canopyH * 0.55f))
        drawOval(foliage.copy(alpha = 0.85f), Offset(cx + canopyW * 0.12f, top + canopyH * 0.28f), Size(canopyW * 0.42f, canopyH * 0.55f))
    } else if (detail) {
        drawOval(foliage.copy(alpha = 0.7f), Offset(cx - canopyW * 0.28f, top + canopyH * 0.22f), Size(canopyW * 0.38f, canopyH * 0.4f))
        drawOval(foliage.copy(alpha = 0.7f), Offset(cx + canopyW * 0.02f, top + canopyH * 0.18f), Size(canopyW * 0.34f, canopyH * 0.36f))
    }
}

private fun DrawScope.pine(cx: Float, baseY: Float, h: Float, scale: Float, foliage: Color, cypress: Boolean) {
    trunk(cx, baseY, h * 0.28f, size.width * 0.06f * scale, AnchorColors.HarborGrowth.copy(alpha = 0.85f))
    val layers = if (cypress) 4 else 3
    repeat(layers) { layer ->
        val t = layer / (layers - 1f)
        val layerY = baseY - h * (0.28f + t * 0.58f)
        val halfW = size.width * (0.22f - t * 0.08f) * scale
        val cone = Path().apply {
            moveTo(cx, layerY - h * 0.22f)
            lineTo(cx - halfW, layerY + h * 0.04f)
            quadraticTo(cx, layerY + h * 0.08f, cx + halfW, layerY + h * 0.04f)
            close()
        }
        drawPath(cone, foliage.copy(alpha = 0.95f - t * 0.12f))
    }
}

private fun DrawScope.spikes(
    cx: Float,
    baseY: Float,
    h: Float,
    scale: Float,
    stem: Color,
    bloom: Color,
    detail: Boolean
) {
    val count = if (detail) 5 else 3
    repeat(count) { i ->
        val x = cx + (i - (count - 1) / 2f) * size.width * 0.07f * scale
        val hh = h * (0.72f + (i % 3) * 0.08f)
        drawLine(stem.copy(alpha = 0.85f), Offset(x, baseY), Offset(x, baseY - hh), strokeWidth = 3.5f * scale, cap = StrokeCap.Round)
        drawOval(bloom, Offset(x - 5f * scale, baseY - hh - 10f * scale), Size(10f * scale, 16f * scale))
    }
}

private fun DrawScope.sunflower(cx: Float, baseY: Float, h: Float, scale: Float, foliage: Color, detail: Boolean) {
    drawLine(AnchorColors.HarborGrowth, Offset(cx, baseY), Offset(cx, baseY - h * 0.72f), strokeWidth = 6f * scale, cap = StrokeCap.Round)
    val head = Offset(cx, baseY - h * 0.78f)
    if (detail) {
        repeat(8) { i ->
            val a = i / 8f * Math.PI.toFloat() * 2f
            val r = size.width * 0.09f * scale
            drawCircle(AnchorColors.HarborAction, size.width * 0.035f * scale, Offset(head.x + cos(a) * r, head.y + sin(a) * r))
        }
    }
    drawCircle(Color(0xFF5C3D1A), size.width * 0.055f * scale, head)
    drawCircle(foliage.copy(alpha = 0.5f), size.width * 0.08f * scale, Offset(cx + size.width * 0.08f * scale, baseY - h * 0.4f))
}

private fun DrawScope.mushroom(cx: Float, baseY: Float, h: Float, scale: Float, cap: Color) {
    val stemW = size.width * 0.1f * scale
    drawRoundRect(
        Color(0xFFE8D5B7).copy(alpha = 0.9f),
        Offset(cx - stemW / 2f, baseY - h * 0.42f),
        Size(stemW, h * 0.42f),
        androidx.compose.ui.geometry.CornerRadius(stemW / 2f)
    )
    val capW = size.width * 0.42f * scale
    val capH = h * 0.32f
    drawOval(cap, Offset(cx - capW / 2f, baseY - h * 0.62f), Size(capW, capH))
}

private fun DrawScope.fern(cx: Float, baseY: Float, h: Float, scale: Float, foliage: Color, detail: Boolean) {
    val fronds = if (detail) 7 else 5
    repeat(fronds) { i ->
        val x = cx + (i - (fronds - 1) / 2f) * size.width * 0.075f * scale
        val lean = (i - (fronds - 1) / 2f) * size.width * 0.08f * scale
        val path = Path().apply {
            moveTo(x, baseY)
            cubicTo(x - lean * 0.35f, baseY - h * 0.28f, x + lean * 0.45f, baseY - h * 0.62f, x + lean, baseY - h * (0.58f + (i % 2) * 0.08f))
        }
        drawPath(path, foliage.copy(alpha = 0.9f), style = Stroke(width = 4f * scale, cap = StrokeCap.Round))
        repeat(4) { leaf ->
            val t = (leaf + 1) / 5f
            val lx = x + lean * t
            val ly = baseY - h * (0.16f + t * 0.4f)
            drawOval(
                foliage.copy(alpha = 0.68f),
                Offset(lx - size.width * 0.035f * scale, ly - size.height * 0.02f * scale),
                Size(size.width * 0.07f * scale, size.height * 0.035f * scale)
            )
        }
    }
}

private fun DrawScope.moss(cx: Float, baseY: Float, h: Float, scale: Float, foliage: Color) {
    val patchTop = baseY - h * 0.3f
    val patchWidth = size.width * 0.62f * scale
    val patchHeight = h * 0.24f
    val patch = Path().apply {
        moveTo(cx - patchWidth / 2f, baseY)
        quadraticTo(cx - patchWidth * 0.42f, patchTop, cx - patchWidth * 0.28f, patchTop + patchHeight * 0.15f)
        quadraticTo(cx - patchWidth * 0.12f, patchTop - patchHeight * 0.35f, cx, patchTop + patchHeight * 0.05f)
        quadraticTo(cx + patchWidth * 0.14f, patchTop - patchHeight * 0.28f, cx + patchWidth * 0.28f, patchTop + patchHeight * 0.1f)
        quadraticTo(cx + patchWidth * 0.44f, patchTop - patchHeight * 0.08f, cx + patchWidth / 2f, baseY)
        close()
    }
    drawPath(patch, foliage.copy(alpha = 0.9f))
    repeat(7) { i ->
        val x = cx - patchWidth * 0.38f + i * patchWidth * 0.125f
        drawLine(
            foliage.copy(alpha = 0.48f),
            Offset(x, baseY - patchHeight * 0.1f),
            Offset(x + size.width * 0.018f * scale, patchTop + patchHeight * (0.25f + (i % 3) * 0.12f)),
            strokeWidth = 2f * scale,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.clover(cx: Float, baseY: Float, h: Float, scale: Float, foliage: Color) {
    val leafColor = foliage.copy(alpha = 0.9f)
    repeat(3) { i ->
        val x = cx + (i - 1) * size.width * 0.13f * scale
        val top = baseY - h * (0.42f + (i % 2) * 0.1f)
        drawLine(AnchorColors.HarborGrowth, Offset(cx, baseY), Offset(x, top), strokeWidth = 2.5f * scale, cap = StrokeCap.Round)
        val leaf = Path().apply {
            moveTo(x, top)
            cubicTo(x - size.width * 0.1f * scale, top - h * 0.08f, x - size.width * 0.08f * scale, top - h * 0.2f, x, top - h * 0.12f)
            cubicTo(x + size.width * 0.08f * scale, top - h * 0.2f, x + size.width * 0.1f * scale, top - h * 0.08f, x, top)
            close()
        }
        drawPath(leaf, leafColor)
        drawCircle(leafColor.copy(alpha = 0.82f), size.width * 0.055f * scale, Offset(x - size.width * 0.055f * scale, top - h * 0.1f))
        drawCircle(leafColor.copy(alpha = 0.82f), size.width * 0.055f * scale, Offset(x + size.width * 0.055f * scale, top - h * 0.1f))
    }
}

private fun DrawScope.seagrass(cx: Float, baseY: Float, h: Float, scale: Float, foliage: Color) {
    val blades = 8
    repeat(blades) { i ->
        val x = cx + (i - (blades - 1) / 2f) * size.width * 0.065f * scale
        val lean = (i - (blades - 1) / 2f) * size.width * 0.1f * scale
        val path = Path().apply {
            moveTo(x, baseY)
            cubicTo(x - lean * 0.2f, baseY - h * 0.3f, x + lean * 0.7f, baseY - h * 0.68f, x + lean, baseY - h * (0.52f + (i % 3) * 0.08f))
        }
        drawPath(path, foliage.copy(alpha = 0.8f), style = Stroke(width = 4f * scale, cap = StrokeCap.Round))
    }
}

private fun DrawScope.bush(cx: Float, baseY: Float, h: Float, scale: Float, foliage: Color) {
    val w = size.width * 0.62f * scale
    val top = baseY - h * 0.48f
    val path = Path().apply {
        moveTo(cx - w / 2f, baseY)
        quadraticTo(cx - w * 0.5f, top + h * 0.08f, cx - w * 0.32f, top + h * 0.02f)
        quadraticTo(cx - w * 0.22f, top - h * 0.2f, cx - w * 0.06f, top + h * 0.02f)
        quadraticTo(cx + w * 0.04f, top - h * 0.28f, cx + w * 0.17f, top + h * 0.02f)
        quadraticTo(cx + w * 0.34f, top - h * 0.14f, cx + w * 0.48f, top + h * 0.1f)
        quadraticTo(cx + w * 0.5f, baseY - h * 0.05f, cx + w / 2f, baseY)
        close()
    }
    drawPath(path, foliage.copy(alpha = 0.92f))
    drawLine(AnchorColors.HarborGrowth.copy(alpha = 0.8f), Offset(cx, baseY), Offset(cx, top + h * 0.14f), strokeWidth = 4f * scale, cap = StrokeCap.Round)
    drawLine(AnchorColors.HarborGrowth.copy(alpha = 0.65f), Offset(cx, top + h * 0.2f), Offset(cx - w * 0.2f, top + h * 0.04f), strokeWidth = 2f * scale, cap = StrokeCap.Round)
    drawLine(AnchorColors.HarborGrowth.copy(alpha = 0.65f), Offset(cx, top + h * 0.2f), Offset(cx + w * 0.2f, top + h * 0.08f), strokeWidth = 2f * scale, cap = StrokeCap.Round)
}

private fun DrawScope.vine(cx: Float, baseY: Float, h: Float, scale: Float, foliage: Color) {
    val path = Path().apply {
        moveTo(cx - size.width * 0.18f * scale, baseY)
        cubicTo(
            cx + size.width * 0.22f * scale, baseY - h * 0.35f,
            cx - size.width * 0.2f * scale, baseY - h * 0.7f,
            cx + size.width * 0.08f * scale, baseY - h
        )
    }
    drawPath(path, foliage, style = Stroke(width = 6f * scale, cap = StrokeCap.Round))
}

private fun DrawScope.willow(cx: Float, baseY: Float, h: Float, scale: Float, foliage: Color, detail: Boolean) {
    trunk(cx, baseY, h * 0.55f, size.width * 0.07f * scale)
    val top = baseY - h * 0.78f
    drawOval(foliage.copy(alpha = 0.55f), Offset(cx - size.width * 0.22f * scale, top), Size(size.width * 0.44f * scale, h * 0.22f))
    val strands = if (detail) 7 else 5
    repeat(strands) { i ->
        val x = cx + (i - (strands - 1) / 2f) * size.width * 0.06f * scale
        val path = Path().apply {
            moveTo(x, top + h * 0.08f)
            quadraticTo(x + size.width * 0.04f * scale, baseY - h * 0.25f, x, baseY - h * 0.05f)
        }
        drawPath(path, foliage, style = Stroke(width = 3.5f * scale, cap = StrokeCap.Round))
    }
}

private fun DrawScope.birch(cx: Float, baseY: Float, h: Float, scale: Float, foliage: Color) {
    trunk(cx, baseY, h * 0.62f, size.width * 0.055f * scale, Color(0xFFE8EEF5).copy(alpha = 0.9f))
    drawOval(foliage, Offset(cx - size.width * 0.16f * scale, baseY - h * 0.95f), Size(size.width * 0.32f * scale, h * 0.38f))
}

private fun DrawScope.palm(cx: Float, baseY: Float, h: Float, scale: Float, foliage: Color) {
    trunk(cx, baseY, h * 0.58f, size.width * 0.06f * scale, AnchorColors.HarborGrowth.copy(alpha = 0.7f))
    val origin = Offset(cx, baseY - h * 0.58f)
    repeat(6) { i ->
        val a = (-110f + i * 28f) * (Math.PI.toFloat() / 180f)
        val tip = Offset(origin.x + cos(a) * size.width * 0.22f * scale, origin.y + sin(a) * h * 0.28f)
        drawLine(foliage, origin, tip, strokeWidth = 5f * scale, cap = StrokeCap.Round)
    }
}

private fun DrawScope.lantern(cx: Float, baseY: Float, h: Float, scale: Float, foliage: Color) {
    drawLine(AnchorColors.HarborGrowth, Offset(cx, baseY), Offset(cx, baseY - h * 0.62f), strokeWidth = 4f * scale, cap = StrokeCap.Round)
    drawCircle(AnchorColors.HarborAction.copy(alpha = 0.95f), size.width * 0.1f * scale, Offset(cx, baseY - h * 0.72f))
    drawCircle(foliage.copy(alpha = 0.45f), size.width * 0.06f * scale, Offset(cx + size.width * 0.08f * scale, baseY - h * 0.4f))
}

private fun DrawScope.blossom(cx: Float, baseY: Float, h: Float, scale: Float, foliage: Color, detail: Boolean) {
    trunk(cx, baseY, h * 0.4f, size.width * 0.07f * scale)
    drawOval(foliage, Offset(cx - size.width * 0.2f * scale, baseY - h * 0.88f), Size(size.width * 0.4f * scale, h * 0.42f))
    if (detail) {
        repeat(4) { i ->
            val a = i / 4f * Math.PI.toFloat() * 2f
            drawCircle(AnchorColors.HarborAi, 5f * scale, Offset(cx + cos(a) * size.width * 0.1f * scale, baseY - h * 0.68f + sin(a) * h * 0.08f))
        }
    }
}
