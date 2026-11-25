package com.anchor.adhd.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.components.AnchorHairline
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens

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
    val selectedIcon: ImageVector = icon,
)

val DESKTOP_NAV_ITEMS =
    listOf(
        NavItem(DesktopScreen.GROVE, "Home", Icons.Outlined.Home, Icons.Filled.Home),
        NavItem(DesktopScreen.PLAN, "Plan", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
        NavItem(DesktopScreen.FOCUS, "Focus", Icons.Outlined.Timer, Icons.Filled.Timer),
        NavItem(DesktopScreen.BLOCKER, "Blocker", Icons.Outlined.Shield, Icons.Filled.Shield),
        NavItem(DesktopScreen.SYLLABUS, "Syllabus", Icons.Outlined.School, Icons.Filled.School),
        NavItem(DesktopScreen.AI, "Captain", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
        NavItem(DesktopScreen.SETTINGS, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
    )

internal val SIDEBAR_COMPACT_BREAKPOINT = 760.dp
internal val SIDEBAR_EXPANDED_WIDTH = 200.dp
internal val SIDEBAR_COMPACT_WIDTH = 64.dp

internal fun isCompactSidebarWidth(width: Dp): Boolean = width < SIDEBAR_COMPACT_BREAKPOINT

internal fun desktopScreenForShortcutNumber(number: Int): DesktopScreen? = DESKTOP_NAV_ITEMS.getOrNull(number - 1)?.screen

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
    planPendingCount: Int = 0,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAnchorTokens.current
    val itemShape = RoundedCornerShape(AnchorSpacing.radiusChip)
    val sidebarWidth = if (compact) SIDEBAR_COMPACT_WIDTH else SIDEBAR_EXPANDED_WIDTH

    Row(
        modifier =
            modifier
                .width(sidebarWidth)
                .fillMaxHeight()
                .background(tokens.paper2),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp, vertical = if (compact) 8.dp else 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            ) {
                SidebarBrandHeader(compact = compact)

                Spacer(modifier = Modifier.height(if (compact) 6.dp else 12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    DESKTOP_NAV_ITEMS.forEachIndexed { index, item ->
                        val isSelected = currentScreen == item.screen
                        val isCaptain = item.screen == DesktopScreen.AI
                        val hasBlockerIndicator =
                            item.screen == DesktopScreen.BLOCKER && (isFocusActive || isStandingShieldActive)
                        val hasUpdateIndicator = item.screen == DesktopScreen.SETTINGS && hasUpdateBadge
                        val showPlanCount = item.screen == DesktopScreen.PLAN && planPendingCount > 0

                        val iconTint =
                            when {
                                isSelected && isCaptain -> tokens.captain
                                isSelected -> tokens.primary
                                else -> tokens.ink2
                            }
                        val labelColor =
                            when {
                                isSelected && isCaptain -> if (tokens.isDark) tokens.captain else tokens.captainInk
                                isSelected -> tokens.ink
                                else -> tokens.ink2
                            }
                        val containerColor = if (isSelected) tokens.paper3 else Color.Transparent
                        val shortcutNumber = index + 1
                        val tooltipText =
                            if (showPlanCount) {
                                "${item.label}, $planPendingCount pending (Ctrl+$shortcutNumber)"
                            } else {
                                "${item.label} (Ctrl+$shortcutNumber)"
                            }

                        SidebarTooltip(text = tooltipText, enabled = compact) {
                            Surface(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = if (compact) 36.dp else 40.dp)
                                        .clip(itemShape)
                                        .selectable(
                                            selected = isSelected,
                                            role = Role.Tab,
                                            onClick = { onScreenSelected(item.screen) },
                                        ),
                                shape = itemShape,
                                color = containerColor,
                                shadowElevation = 0.dp,
                                tonalElevation = 0.dp,
                            ) {
                                if (compact) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(36.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) item.selectedIcon else item.icon,
                                            contentDescription = item.label,
                                            tint = iconTint,
                                            modifier = Modifier.size(19.dp),
                                        )
                                        val indicatorColor =
                                            when {
                                                hasBlockerIndicator -> tokens.sage
                                                hasUpdateIndicator -> tokens.beacon
                                                showPlanCount -> tokens.primary
                                                else -> null
                                            }
                                        if (indicatorColor != null) {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(top = 5.dp, end = 8.dp)
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(indicatorColor),
                                            )
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) item.selectedIcon else item.icon,
                                            contentDescription = null,
                                            tint = iconTint,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            text = item.label,
                                            style =
                                                MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                ),
                                            color = labelColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (showPlanCount) {
                                            Text(
                                                text = "$planPendingCount",
                                                style =
                                                    MaterialTheme.typography.labelSmall.copy(
                                                        fontFeatureSettings = "tnum",
                                                    ),
                                                color = tokens.muted,
                                                maxLines = 1,
                                            )
                                        } else if (hasBlockerIndicator) {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .size(7.dp)
                                                        .clip(CircleShape)
                                                        .background(tokens.sage),
                                            )
                                        } else if (hasUpdateIndicator) {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .size(7.dp)
                                                        .clip(CircleShape)
                                                        .background(tokens.beacon),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)) {
                if (isFocusActive) {
                    ActiveFocusSidebarStatus(
                        remainingSeconds = remainingSeconds,
                        compact = compact,
                        itemShape = itemShape,
                        onClick = { onScreenSelected(DesktopScreen.FOCUS) },
                    )
                }

                AnchorHairline(modifier = Modifier.padding(vertical = if (compact) 2.dp else 4.dp))

                QuietFooterAction(
                    label = "Airlock reset",
                    shortcut = "Ctrl K",
                    icon = Icons.Outlined.Air,
                    iconTint = tokens.captain,
                    compact = compact,
                    itemShape = itemShape,
                    onClick = onOpenAirlock,
                )

                QuietFooterToggle(
                    label = "Floating Island",
                    shortcut = "Ctrl I",
                    icon = Icons.Outlined.OpenInFull,
                    checked = isFloatingIslandActive,
                    compact = compact,
                    itemShape = itemShape,
                    onToggle = onToggleFloatingIsland,
                )

                QuietFooterToggle(
                    label = if (isFullscreen) "Exit full" else "Fullscreen",
                    shortcut = "F11",
                    icon = if (isFullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                    checked = isFullscreen,
                    compact = compact,
                    itemShape = itemShape,
                    onToggle = onToggleFullscreen,
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(tokens.rule),
        )
    }
}

@Composable
private fun SidebarBrandHeader(compact: Boolean) {
    val tokens = LocalAnchorTokens.current
    if (compact) {
        SidebarTooltip(text = "Anchor", enabled = true) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clearAndSetSemantics { contentDescription = "Anchor" },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "A",
                        style = MaterialTheme.typography.titleLarge,
                        color = tokens.ink,
                        maxLines = 1,
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(tokens.beacon),
                    )
                }
            }
        }
    } else {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Anchor",
                style = MaterialTheme.typography.titleLarge,
                color = tokens.ink,
                maxLines = 1,
            )
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(tokens.beacon),
            )
        }
    }
}

@Composable
private fun ActiveFocusSidebarStatus(
    remainingSeconds: Int,
    compact: Boolean,
    itemShape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    val safeSeconds = remainingSeconds.coerceAtLeast(0)
    val mins = safeSeconds / 60
    val secs = safeSeconds % 60
    val compactMinuteLabel =
        when {
            mins == 0 && safeSeconds > 0 -> "<1m"
            else -> "${mins}m"
        }
    val formattedTime = String.format("%02d:%02d", mins, secs)
    val accessibleLabel = "Focus active, $formattedTime remaining"

    SidebarTooltip(text = "Focus active ($formattedTime)", enabled = compact) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(itemShape)
                    .clickable(role = Role.Button, onClick = onClick)
                    .semantics { contentDescription = accessibleLabel },
            shape = itemShape,
            color = tokens.sageSoft,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            if (compact) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = compactMinuteLabel,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum",
                            ),
                        color = tokens.sage,
                        maxLines = 1,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(tokens.sage),
                    )
                    Column {
                        Text(
                            text = "Focus active",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = tokens.sage,
                            maxLines = 1,
                        )
                        Text(
                            text = "$formattedTime remaining",
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = tokens.ink,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuietFooterAction(
    label: String,
    shortcut: String,
    icon: ImageVector,
    iconTint: Color,
    compact: Boolean,
    itemShape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    SidebarTooltip(text = "$label ($shortcut)", enabled = compact) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (compact) 30.dp else 36.dp)
                    .clip(itemShape)
                    .clickable(role = Role.Button, onClick = onClick),
            shape = itemShape,
            color = Color.Transparent,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            if (compact) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(30.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = iconTint,
                        modifier = Modifier.size(17.dp),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = shortcut,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.muted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuietFooterToggle(
    label: String,
    shortcut: String,
    icon: ImageVector,
    checked: Boolean,
    compact: Boolean,
    itemShape: RoundedCornerShape,
    onToggle: () -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    val contentTint = if (checked) tokens.primary else tokens.ink2
    val textColor = if (checked) tokens.ink else tokens.ink2

    SidebarTooltip(text = "$label ($shortcut)", enabled = compact) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (compact) 30.dp else 36.dp)
                    .clip(itemShape)
                    .toggleable(
                        value = checked,
                        role = Role.Switch,
                        onValueChange = { onToggle() },
                    ),
            shape = itemShape,
            color = if (checked) tokens.paper3 else Color.Transparent,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            if (compact) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(30.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = contentTint,
                        modifier = Modifier.size(17.dp),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentTint,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = shortcut,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.muted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarTooltip(
    text: String,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    val tokens = LocalAnchorTokens.current
    TooltipArea(
        tooltip = {
            Surface(
                shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                color = tokens.surface,
                contentColor = tokens.ink,
                border = BorderStroke(1.dp, tokens.rule),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.ink,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        },
        delayMillis = 250,
        tooltipPlacement =
            TooltipPlacement.ComponentRect(
                anchor = Alignment.CenterEnd,
                alignment = Alignment.CenterEnd,
                offset = DpOffset(x = 8.dp, y = 0.dp),
            ),
        content = content,
    )
}
