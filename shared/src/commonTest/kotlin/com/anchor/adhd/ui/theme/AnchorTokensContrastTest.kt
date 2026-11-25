package com.anchor.adhd.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnchorTokensContrastTest {
    private fun relLuminance(c: Color): Double {
        fun ch(v: Float): Double = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    private fun contrast(fg: Color, bg: Color): Double {
        val l1 = relLuminance(fg)
        val l2 = relLuminance(bg)
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun morningAndNightTokensMeetWcagAndAvoidPureAbsolutes() {
        listOf(MorningHarborTokens, NightHarborTokens).forEach { t ->
            assertFalse(t.paper == Color.Black || t.paper == Color.White)
            assertFalse(t.ink == Color.Black || t.ink == Color.White)
            assertTrue(contrast(t.ink, t.paper) >= 10.0, "ink/paper low in dark=${t.isDark}")
            assertTrue(contrast(t.muted, t.paper) >= 4.5, "muted/paper low in dark=${t.isDark}")
            assertTrue(contrast(t.muted, t.paper2) >= 4.5, "muted/paper2 low in dark=${t.isDark}")
            assertTrue(contrast(t.beaconInk, t.beacon) >= 4.5, "beaconInk/beacon low in dark=${t.isDark}")
            assertTrue(contrast(t.captain, t.paper) >= 4.5, "captain/paper low in dark=${t.isDark}")
            assertTrue(contrast(t.clayRose, t.paper) >= 4.5, "clayRose/paper low in dark=${t.isDark}")
            assertTrue(contrast(t.clayRose, t.paper2) >= 4.5, "clayRose/paper2 low in dark=${t.isDark}")
            assertTrue(contrast(t.fieldEdge, t.paper) >= 3.0, "fieldEdge/paper low in dark=${t.isDark}")
            assertTrue(contrast(t.beaconPlateInk, t.beaconPlate) >= 10.0, "beaconPlateInk contrast")
            assertTrue(contrast(t.beaconInk, t.beaconPlateCta) >= 7.0, "beaconPlateCta contrast")
        }
        assertTrue(relLuminance(NightHarborTokens.paper2) > relLuminance(NightHarborTokens.paper))
        assertTrue(relLuminance(NightHarborTokens.paper3) > relLuminance(NightHarborTokens.paper2))
    }

    @Test
    fun colorSchemesMapDirectlyFromHarborTokens() {
        val light = anchorLightColorScheme()
        assertEquals(MorningHarborTokens.paper, light.background)
        assertEquals(MorningHarborTokens.surface, light.surface)
        assertEquals(MorningHarborTokens.paper2, light.surfaceVariant)
        assertEquals(MorningHarborTokens.paper3, light.surfaceContainerHigh)
        assertEquals(MorningHarborTokens.ink, light.onSurface)
        assertEquals(MorningHarborTokens.muted, light.onSurfaceVariant)
        assertEquals(MorningHarborTokens.fieldEdge, light.outline)
        assertEquals(MorningHarborTokens.rule, light.outlineVariant)
        assertEquals(MorningHarborTokens.primary, light.primary)
        assertEquals(MorningHarborTokens.primarySoft, light.primaryContainer)
        assertEquals(MorningHarborTokens.captain, light.secondary)
        assertEquals(MorningHarborTokens.captainSoft, light.secondaryContainer)
        assertEquals(MorningHarborTokens.beacon, light.tertiary)
        assertEquals(MorningHarborTokens.beaconInk, light.onTertiary)
        assertEquals(MorningHarborTokens.clayRose, light.error)
        assertEquals(MorningHarborTokens.clayRoseSoft, light.errorContainer)
        assertEquals(MorningHarborTokens.beaconPlate, light.inverseSurface)
        assertEquals(MorningHarborTokens.beaconPlateInk, light.inverseOnSurface)
        assertEquals(NightHarborTokens.primary, light.inversePrimary)
        assertEquals(MorningHarborTokens.paper3, light.surfaceDim)
        assertEquals(MorningHarborTokens.surface, light.surfaceBright)
        assertEquals(NightHarborTokens.paper, light.scrim)

        val dark = anchorDarkColorScheme()
        assertEquals(NightHarborTokens.paper, dark.background)
        assertEquals(NightHarborTokens.surface, dark.surface)
        assertEquals(NightHarborTokens.paper2, dark.surfaceVariant)
        assertEquals(NightHarborTokens.paper3, dark.surfaceContainerHigh)
        assertEquals(NightHarborTokens.ink, dark.onSurface)
        assertEquals(NightHarborTokens.muted, dark.onSurfaceVariant)
        assertEquals(NightHarborTokens.fieldEdge, dark.outline)
        assertEquals(NightHarborTokens.rule, dark.outlineVariant)
        assertEquals(NightHarborTokens.primary, dark.primary)
        assertEquals(NightHarborTokens.primarySoft, dark.primaryContainer)
        assertEquals(NightHarborTokens.captain, dark.secondary)
        assertEquals(NightHarborTokens.captainSoft, dark.secondaryContainer)
        assertEquals(NightHarborTokens.beacon, dark.tertiary)
        assertEquals(NightHarborTokens.beaconInk, dark.onTertiary)
        assertEquals(NightHarborTokens.clayRose, dark.error)
        assertEquals(NightHarborTokens.clayRoseSoft, dark.errorContainer)
        assertEquals(MorningHarborTokens.paper, dark.inverseSurface)
        assertEquals(MorningHarborTokens.ink, dark.inverseOnSurface)
        assertEquals(MorningHarborTokens.primary, dark.inversePrimary)
        assertEquals(NightHarborTokens.paper, dark.surfaceDim)
        assertEquals(NightHarborTokens.paper3, dark.surfaceBright)
        assertEquals(NightHarborTokens.paper, dark.scrim)

        listOf(light, dark).forEach { scheme ->
            assertTrue(contrast(scheme.onPrimary, scheme.primary) >= 4.5, "onPrimary/primary low")
            assertTrue(contrast(scheme.onSecondary, scheme.secondary) >= 4.5, "onSecondary/secondary low")
            assertTrue(contrast(scheme.onTertiary, scheme.tertiary) >= 4.5, "onTertiary/tertiary low")
            assertTrue(contrast(scheme.onError, scheme.error) >= 4.5, "onError/error low")
            assertTrue(contrast(scheme.inverseOnSurface, scheme.inverseSurface) >= 10.0, "inverseOnSurface/inverseSurface low")
            assertTrue(contrast(scheme.inversePrimary, scheme.inverseSurface) >= 4.5, "inversePrimary/inverseSurface low")

            val allSchemeColors = listOf(
                scheme.primary,
                scheme.onPrimary,
                scheme.primaryContainer,
                scheme.onPrimaryContainer,
                scheme.inversePrimary,
                scheme.secondary,
                scheme.onSecondary,
                scheme.secondaryContainer,
                scheme.onSecondaryContainer,
                scheme.tertiary,
                scheme.onTertiary,
                scheme.tertiaryContainer,
                scheme.onTertiaryContainer,
                scheme.background,
                scheme.onBackground,
                scheme.surface,
                scheme.onSurface,
                scheme.surfaceVariant,
                scheme.onSurfaceVariant,
                scheme.surfaceTint,
                scheme.inverseSurface,
                scheme.inverseOnSurface,
                scheme.error,
                scheme.onError,
                scheme.errorContainer,
                scheme.onErrorContainer,
                scheme.outline,
                scheme.outlineVariant,
                scheme.scrim,
                scheme.surfaceBright,
                scheme.surfaceDim,
                scheme.surfaceContainer,
                scheme.surfaceContainerHigh,
                scheme.surfaceContainerHighest,
                scheme.surfaceContainerLow,
                scheme.surfaceContainerLowest,
            )
            allSchemeColors.forEach { color ->
                assertFalse(color == Color.Black || color == Color.White, "Scheme contains pure black/white: $color")
            }
        }
    }

    @Test
    fun typographyFollowsDesignScaleAndInvariants() {
        val display = FontFamily.Serif
        val body = FontFamily.SansSerif
        val typography = anchorTypography(display = display, body = body)

        assertEquals(display, typography.displayLarge.fontFamily)
        assertEquals(FontWeight.Medium, typography.displayLarge.fontWeight)
        assertEquals(52.sp, typography.displayLarge.fontSize)
        assertEquals("tnum", typography.displayLarge.fontFeatureSettings)

        assertEquals(display, typography.displaySmall.fontFamily)
        assertEquals(FontWeight.Medium, typography.displaySmall.fontWeight)
        assertEquals(28.sp, typography.displaySmall.fontSize)
        assertEquals("tnum", typography.displaySmall.fontFeatureSettings)

        assertEquals(display, typography.headlineMedium.fontFamily)
        assertEquals(FontWeight.Medium, typography.headlineMedium.fontWeight)
        assertEquals(26.sp, typography.headlineMedium.fontSize)

        assertEquals(display, typography.titleLarge.fontFamily)
        assertEquals(FontWeight.Medium, typography.titleLarge.fontWeight)
        assertEquals(22.sp, typography.titleLarge.fontSize)

        assertEquals(body, typography.titleMedium.fontFamily)
        assertEquals(FontWeight.Bold, typography.titleMedium.fontWeight)
        assertEquals(17.sp, typography.titleMedium.fontSize)

        assertEquals(body, typography.titleSmall.fontFamily)
        assertEquals(FontWeight.Bold, typography.titleSmall.fontWeight)
        assertEquals(14.sp, typography.titleSmall.fontSize)
        assertEquals(0.sp, typography.titleSmall.letterSpacing)

        assertEquals(body, typography.bodyLarge.fontFamily)
        assertEquals(16.sp, typography.bodyLarge.fontSize)
        assertEquals(body, typography.bodyMedium.fontFamily)
        assertEquals(14.sp, typography.bodyMedium.fontSize)
        assertEquals(body, typography.bodySmall.fontFamily)
        assertEquals(13.sp, typography.bodySmall.fontSize)

        assertEquals(body, typography.labelLarge.fontFamily)
        assertEquals(FontWeight.Bold, typography.labelLarge.fontWeight)
        assertEquals(14.sp, typography.labelLarge.fontSize)
        assertEquals(0.sp, typography.labelLarge.letterSpacing)

        assertEquals(body, typography.labelMedium.fontFamily)
        assertEquals(12.5.sp, typography.labelMedium.fontSize)
        assertEquals(0.sp, typography.labelMedium.letterSpacing)

        assertEquals(body, typography.labelSmall.fontFamily)
        assertEquals(12.sp, typography.labelSmall.fontSize)
        assertEquals(0.sp, typography.labelSmall.letterSpacing)
    }

    @Test
    fun shapesSpacingAndMotionMatchDesignContract() {
        assertEquals(RoundedCornerShape(8.dp), AnchorShapes.extraSmall)
        assertEquals(RoundedCornerShape(10.dp), AnchorShapes.small)
        assertEquals(RoundedCornerShape(12.dp), AnchorShapes.medium)
        assertEquals(RoundedCornerShape(14.dp), AnchorShapes.large)
        assertEquals(RoundedCornerShape(24.dp), AnchorShapes.extraLarge)

        assertEquals(14.dp, AnchorSpacing.radiusPlate)
        assertEquals(14.dp, AnchorSpacing.radiusWell)
        assertEquals(12.dp, AnchorSpacing.radiusButton)
        assertEquals(10.dp, AnchorSpacing.radiusChip)
        assertEquals(24.dp, AnchorSpacing.radiusSheet)
        assertEquals(999.dp, AnchorSpacing.radiusPill)
        assertEquals(14.dp, AnchorSpacing.radiusCard)

        assertEquals(120, AnchorMotion.MICRO_MS)
        assertEquals(220, AnchorMotion.SHORT_MS)
        assertEquals(320, AnchorMotion.SHEET_MS)
        assertEquals(10000, AnchorMotion.TIDE_MS)
        assertEquals(0.985f, AnchorMotion.PRESS_SCALE)
    }

    @Test
    fun anchorColorsDelegateToHarborTokens() {
        assertEquals(NightHarborTokens.paper, AnchorColors.HarborBackground)
        assertEquals(NightHarborTokens.surface, AnchorColors.HarborSurface)
        assertEquals(NightHarborTokens.paper3, AnchorColors.HarborSurfaceContainer)
        assertEquals(NightHarborTokens.primary, AnchorColors.HarborPrimary)
        assertEquals(NightHarborTokens.beacon, AnchorColors.HarborAction)
        assertEquals(NightHarborTokens.captain, AnchorColors.HarborAi)
        assertEquals(NightHarborTokens.captainInk, AnchorColors.HarborPilotInk)
        assertEquals(NightHarborTokens.sage, AnchorColors.HarborFoliage)
        assertEquals(NightHarborTokens.beacon, AnchorColors.HarborBeaconAmber)

        assertEquals(MorningHarborTokens.paper, AnchorColors.MorningCanvas)
        assertEquals(MorningHarborTokens.surface, AnchorColors.MorningCard)
        assertEquals(MorningHarborTokens.paper2, AnchorColors.MorningContainer)
        assertEquals(MorningHarborTokens.ink, AnchorColors.MorningInk)
        assertEquals(MorningHarborTokens.muted, AnchorColors.MorningMuted)
        assertEquals(MorningHarborTokens.primary, AnchorColors.MorningPrimary)
        assertEquals(MorningHarborTokens.beacon, AnchorColors.MorningBeacon)
        assertEquals(MorningHarborTokens.beaconInk, AnchorColors.MorningBeaconInk)
        assertEquals(MorningHarborTokens.sage, AnchorColors.MorningSage)
        assertEquals(MorningHarborTokens.captain, AnchorColors.MorningAi)
        assertEquals(MorningHarborTokens.clayRose, AnchorColors.ClayRose)
        assertEquals(MorningHarborTokens.clayRoseSoft, AnchorColors.ClayRoseContainer)
    }
}
