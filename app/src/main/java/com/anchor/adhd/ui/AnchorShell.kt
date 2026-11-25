package com.anchor.adhd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.components.AnchorHairline
import com.anchor.adhd.ui.theme.LocalAnchorTokens

/** Top-level destinations. Aligned with MainActivity.TAB_* so intents map 1:1. */
object AnchorTabs {
    const val GROVE = 0
    const val PLAN = 1
    const val FOCUS = 2
    const val AI = 3
    const val MORE = 4
}

enum class MoreRoute { Hub, Grow, Ai, Settings, Habits, Strategies, Blocker, Plan }

data class TabItem(
    val index: Int,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

val ANCHOR_TAB_ITEMS = listOf(
    TabItem(AnchorTabs.GROVE, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    TabItem(AnchorTabs.PLAN, "Plan", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    TabItem(AnchorTabs.FOCUS, "Focus", Icons.Outlined.Timer, Icons.Filled.Timer),
    TabItem(AnchorTabs.AI, "Captain", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    TabItem(AnchorTabs.MORE, "More", Icons.Outlined.Apps, Icons.Filled.Apps)
)

@Composable
fun AnchorShell(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    planBadgeCount: Int = 0,
    content: @Composable (PaddingValues) -> Unit
) {
    val tokens = LocalAnchorTokens.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = tokens.paper,
        contentColor = tokens.ink,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AnchorBottomBar(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                planBadgeCount = planBadgeCount
            )
        }
    ) { padding ->
        content(padding)
    }
}

@Composable
fun AnchorBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    planBadgeCount: Int = 0
) {
    val tokens = LocalAnchorTokens.current
    Column(modifier = modifier) {
        AnchorHairline()
        NavigationBar(
            containerColor = tokens.paper2,
            contentColor = tokens.ink,
            tonalElevation = 0.dp
        ) {
            ANCHOR_TAB_ITEMS.forEach { tab ->
                val selected = selectedTab == tab.index
                val isCaptain = tab.index == AnchorTabs.AI
                val itemColors = if (isCaptain) {
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = tokens.captain,
                        selectedTextColor = tokens.captain,
                        indicatorColor = tokens.captainSoft,
                        unselectedIconColor = tokens.ink2,
                        unselectedTextColor = tokens.ink2
                    )
                } else {
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = tokens.primary,
                        selectedTextColor = tokens.primary,
                        indicatorColor = tokens.primarySoft,
                        unselectedIconColor = tokens.ink2,
                        unselectedTextColor = tokens.ink2
                    )
                }
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTabSelected(tab.index) },
                    colors = itemColors,
                    icon = {
                        BadgedBox(
                            badge = {
                                if (tab.index == AnchorTabs.PLAN && planBadgeCount > 0) {
                                    Badge(
                                        containerColor = tokens.beacon,
                                        contentColor = tokens.beaconInk
                                    ) {
                                        Text(
                                            text = "$planBadgeCount",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFeatureSettings = "tnum"
                                            )
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.icon,
                                contentDescription = null
                            )
                        }
                    },
                    label = {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}
