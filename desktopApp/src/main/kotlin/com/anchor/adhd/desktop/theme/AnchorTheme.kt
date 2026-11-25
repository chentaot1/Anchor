package com.anchor.adhd.desktop.theme

import androidx.compose.runtime.Composable
import com.anchor.adhd.ui.theme.SharedAnchorTheme
import com.anchor.adhd.ui.theme.AnchorColors as SharedColors
import com.anchor.adhd.ui.theme.AnchorSpacing as SharedSpacing

/**
 * Desktop theme delegates to shared (`com.anchor.adhd.ui.theme`) so Windows
 * and Android render one Anchor. Do not fork hex codes, fonts, or motion
 * here — change shared and both apps follow.
 */
object AnchorColors {
    val HarborBackground get() = SharedColors.HarborBackground
    val HarborPrimary get() = SharedColors.HarborPrimary
    val HarborAction get() = SharedColors.HarborAction
    val HarborAi get() = SharedColors.HarborAi
    val HarborPilotInk get() = SharedColors.HarborPilotInk
    val HarborGrowth get() = SharedColors.HarborGrowth
    val HarborFoliage get() = SharedColors.HarborFoliage
    val HarborFoliageDark get() = SharedColors.HarborFoliageDark
    val HarborDock get() = SharedColors.HarborDock
    val HarborMist get() = SharedColors.HarborMist
    val HarborSkyNight get() = SharedColors.HarborSkyNight
    val HarborWater get() = SharedColors.HarborWater
    val HarborBeaconAmber get() = SharedColors.HarborBeaconAmber
}

object AnchorSpacing {
    val screenHorizontal get() = SharedSpacing.screenHorizontalDesktop
    val screenVertical get() = SharedSpacing.screenVertical
    val sectionGap get() = SharedSpacing.sectionGap
    val itemGap get() = SharedSpacing.itemGap
    val radiusPlate get() = SharedSpacing.radiusPlate
    val radiusWell get() = SharedSpacing.radiusWell
    val radiusButton get() = SharedSpacing.radiusButton
    val radiusScene get() = SharedSpacing.radiusScene
    val radiusCard get() = SharedSpacing.radiusCard
    val radiusChip get() = SharedSpacing.radiusChip
    val radiusSheet get() = SharedSpacing.radiusSheet
    val radiusPill get() = SharedSpacing.radiusPill
    val elevationFloating get() = SharedSpacing.elevationFloating
}

@Composable
fun AnchorTheme(
    darkTheme: Boolean = true,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    SharedAnchorTheme(
        darkTheme = darkTheme,
        displayFont = NewsreaderFontFamily,
        bodyFont = AtkinsonFontFamily,
        reducedMotion = reducedMotion,
        content = content,
    )
}