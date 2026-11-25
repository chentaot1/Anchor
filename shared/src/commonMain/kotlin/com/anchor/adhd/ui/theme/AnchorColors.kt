package com.anchor.adhd.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for Anchor color aliases, delegating to
 * [MorningHarborTokens] and [NightHarborTokens].
 *
 * The trio is blue + amber + starboard teal: blue carries information, amber
 * carries the ONE next action per screen, and starboard teal carries Captain AI.
 * Sage carries growth and completion; clay rose carries no-shame errors.
 *
 * Both Android (`com.anchor.adhd.ui.theme`) and desktop
 * (`com.anchor.adhd.desktop.theme`) build their Material3 schemes from
 * [AnchorTokens]. Do not fork hex codes per platform.
 */
object AnchorColors {
    // Night Harbor (dark theme base). Lifted off pure black to reduce eye strain.
    val HarborBackground: Color get() = NightHarborTokens.paper
    val HarborSurface: Color get() = NightHarborTokens.surface
    val HarborSurfaceContainer: Color get() = NightHarborTokens.paper3
    val HarborPrimary: Color get() = NightHarborTokens.primary
    val HarborAction: Color get() = NightHarborTokens.beacon
    val HarborAi: Color get() = NightHarborTokens.captain
    val HarborPilotInk: Color get() = NightHarborTokens.captainInk
    val HarborGrowth = Color(0xFF3E8E67)
    val HarborFoliage: Color get() = NightHarborTokens.sage
    val HarborFoliageDark = Color(0xFF52B788)
    val HarborDock: Color get() = NightHarborTokens.paper2
    val HarborMist: Color get() = NightHarborTokens.captain.copy(alpha = 0.20f)
    val HarborSkyNight: Color get() = NightHarborTokens.beaconPlate
    val HarborWater = Color(0x597EB8FF)
    val HarborBeaconAmber: Color get() = NightHarborTokens.beacon

    // Morning Harbor (light theme base). Warm paper, never stark white.
    val MorningCanvas: Color get() = MorningHarborTokens.paper
    val MorningCard: Color get() = MorningHarborTokens.surface
    val MorningContainer: Color get() = MorningHarborTokens.paper2
    val MorningInk: Color get() = MorningHarborTokens.ink
    val MorningMuted: Color get() = MorningHarborTokens.muted
    val MorningPrimary: Color get() = MorningHarborTokens.primary
    val MorningBeacon: Color get() = MorningHarborTokens.beacon
    val MorningBeaconInk: Color get() = MorningHarborTokens.beaconInk
    val MorningSage: Color get() = MorningHarborTokens.sage
    val MorningAi: Color get() = MorningHarborTokens.captain

    // No-shame semantics. Errors are clay rose, never harsh alarm red.
    val ClayRose: Color get() = MorningHarborTokens.clayRose
    val ClayRoseContainer: Color get() = MorningHarborTokens.clayRoseSoft
}
