package com.anchor.adhd.desktop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anchor.adhd.desktop.theme.AnchorColors
import com.anchor.adhd.desktop.theme.AnchorSpacing

enum class DesktopScreen {
    GROVE,
    FOCUS,
    BLOCKER,
    PLAN,
    SYLLABUS,
    AI,
    SETTINGS,
}

data class NavItem(
    val screen: DesktopScreen,
    val label: String,
    val icon: ImageVector,
)

val DESKTOP_NAV_ITEMS =
    listOf(
        NavItem(DesktopScreen.GROVE, "Grove (Home)", Icons.Default.Home),
        NavItem(DesktopScreen.FOCUS, "Focus Canvas", Icons.Default.Timer),
        NavItem(DesktopScreen.BLOCKER, "App Blocker", Icons.Default.Shield),
        NavItem(DesktopScreen.PLAN, "Milestones", Icons.Default.Checklist),
        NavItem(DesktopScreen.SYLLABUS, "Syllabus", Icons.Default.School),
        NavItem(DesktopScreen.AI, "AI Tools", Icons.Default.AutoAwesome),
        NavItem(DesktopScreen.SETTINGS, "Settings", Icons.Default.Settings),
    )

/**
 * Windows 11 Maritime Navigation Sidebar.
 */
@Composable
fun DesktopNavigationSidebar(
    currentScreen: DesktopScreen,
    onScreenSelected: (DesktopScreen) -> Unit,
    isFocusActive: Boolean,
    remainingSeconds: Int,
    onOpenAirlock: () -> Unit,
    onToggleFloatingIsland: () -> Unit = {},
    isFloatingIslandActive: Boolean = false,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    hasUpdateBadge: Boolean = false,
    isStandingShieldActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sidebar_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "active_pulse",
    )

    Surface(
        modifier =
            modifier
                .width(240.dp)
                .fillMaxHeight(),
        color = AnchorColors.HarborDock,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                // Logo & Header
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AnchorColors.HarborBackground),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Anchor,
                            contentDescription = null,
                            tint = AnchorColors.HarborPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Anchor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = "Windows 11 • ADHD",
                            style = MaterialTheme.typography.labelSmall,
                            color = AnchorColors.HarborPrimary.copy(alpha = 0.8f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Navigation Items List
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (item in DESKTOP_NAV_ITEMS) {
                        val isSelected = currentScreen == item.screen
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(AnchorSpacing.radiusChip))
                                    .clickable { onScreenSelected(item.screen) },
                            color = if (isSelected) AnchorColors.HarborPrimary.copy(alpha = 0.18f) else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.dp, AnchorColors.HarborPrimary.copy(alpha = 0.4f)) else null,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = if (isSelected) AnchorColors.HarborPrimary else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                )
                                if (item.screen == DesktopScreen.BLOCKER && (isFocusActive || isStandingShieldActive)) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(AnchorColors.HarborGrowth),
                                    )
                                } else if (item.screen == DesktopScreen.SETTINGS && hasUpdateBadge) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(AnchorColors.HarborAction),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Airlock Quick Entry Button
                Button(
                    onClick = onOpenAirlock,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = AnchorColors.HarborAi.copy(alpha = 0.25f),
                            contentColor = AnchorColors.HarborAi,
                        ),
                    border = BorderStroke(1.dp, AnchorColors.HarborAi.copy(alpha = 0.5f)),
                ) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Airlock (Triage)", fontWeight = FontWeight.Bold)
                }
            }

            // Bottom Section: Pinned to bottom of the sidebar
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Utility Dual Selector Pills: Floating Island & Fullscreen
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Floating Island Micro-HUD Pill
                    Surface(
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(AnchorSpacing.radiusPill))
                                .clickable { onToggleFloatingIsland() },
                        color =
                            if (isFloatingIslandActive) {
                                AnchorColors.HarborGrowth.copy(alpha = 0.25f)
                            } else {
                                Color.White.copy(alpha = 0.05f)
                            },
                        border =
                            BorderStroke(
                                1.dp,
                                if (isFloatingIslandActive) AnchorColors.HarborFoliage.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f),
                            ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInFull,
                                contentDescription = null,
                                tint = if (isFloatingIslandActive) AnchorColors.HarborFoliage else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isFloatingIslandActive) "Island On" else "Island",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isFloatingIslandActive) AnchorColors.HarborFoliage else Color.White.copy(alpha = 0.8f),
                            )
                        }
                    }

                    // Fullscreen Immersion Mode Toggle Pill (F11)
                    Surface(
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(AnchorSpacing.radiusPill))
                                .clickable { onToggleFullscreen() },
                        color = if (isFullscreen) AnchorColors.HarborPrimary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                        border =
                            BorderStroke(
                                1.dp,
                                if (isFullscreen) AnchorColors.HarborPrimary.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f),
                            ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = null,
                                tint = if (isFullscreen) AnchorColors.HarborPrimary else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isFullscreen) "Exit F11" else "Full F11",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isFullscreen) AnchorColors.HarborPrimary else Color.White.copy(alpha = 0.8f),
                            )
                        }
                    }
                }

                // Bottom Active Status Card
                if (isFocusActive) {
                    val mins = remainingSeconds / 60
                    val secs = remainingSeconds % 60
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AnchorSpacing.radiusCard))
                                .clickable { onScreenSelected(DesktopScreen.FOCUS) },
                        color = Color(0xFF0F2B1D),
                        border = BorderStroke(1.dp, AnchorColors.HarborGrowth.copy(alpha = 0.5f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(AnchorColors.HarborGrowth.copy(alpha = pulseAlpha)),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Focus In Progress",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AnchorColors.HarborFoliage,
                                )
                                Text(
                                    text = String.format("%02d:%02d remaining", mins, secs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
