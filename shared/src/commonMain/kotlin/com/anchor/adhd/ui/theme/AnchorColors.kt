package com.anchor.adhd.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for Anchor color.
 *
 * The trio is blue + orange + violet: blue carries information, orange
 * carries the ONE next action per screen, violet carries AI. Green carries
 * growth. Neutrals are warm paper / deep harbor night.
 *
 * Both Android (`com.anchor.adhd.ui.theme`) and desktop
 * (`com.anchor.adhd.desktop.theme`, which re-exports these values) build their
 * Material3 schemes from here. Do not fork hex codes per platform.
 *
 * Usage rule (60-30-10): 60% warm neutral, 30% harbor deep, 10% beacon.
 * Amber [HarborBeaconAmber] is reserved for the one next action
 * (NOW card, Start focus). Blue is information, sage is growth,
 * lavender is AI-only. Never show all three saturated on one screen.
 */
object AnchorColors {
    // Night Harbor (dark theme base). Lifted off pure black to reduce eye strain.
    val HarborBackground = Color(0xFF0E1624)
    val HarborSurface = Color(0xFF16202F)
    val HarborSurfaceContainer = Color(0xFF1E2C40)
    val HarborPrimary = Color(0xFF8AB4E8)
    val HarborAction = Color(0xFFFFAB6B)
    val HarborAi = Color(0xFFB8A6F5)
    val HarborGrowth = Color(0xFF3E8E67)
    val HarborFoliage = Color(0xFF7AC9A3)
    val HarborFoliageDark = Color(0xFF52B788)
    val HarborDock = Color(0xFF1A2836)
    val HarborMist = Color(0x33A78BFA)
    val HarborSkyNight = Color(0xFF1B2838)
    val HarborWater = Color(0x597EB8FF)
    val HarborBeaconAmber = Color(0xFFFFB066)

    // Morning Harbor (light theme base). Warm paper, never stark white.
    val MorningCanvas = Color(0xFFF6F1E7)
    val MorningCard = Color(0xFFFFFDF8)
    val MorningContainer = Color(0xFFEDE7D8)
    val MorningInk = Color(0xFF1B2A3A)
    val MorningMuted = Color(0xFF5C6D80)
    val MorningPrimary = Color(0xFF2E6E8E)
    val MorningBeacon = Color(0xFFD97E2B)
    val MorningBeaconInk = Color(0xFF1A1206)
    val MorningSage = Color(0xFF4C7A5B)
    val MorningAi = Color(0xFF6F5CC4)

    // No-shame semantics. Errors are clay rose, never harsh alarm red.
    val ClayRose = Color(0xFFC97F7F)
    val ClayRoseContainer = Color(0xFFF3D9D9)
}
