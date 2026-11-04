package com.anchor.adhd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.anchor.adhd.ui.theme.LocalAnchorExtras

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
    TabItem(AnchorTabs.GROVE, "Grove", Icons.Outlined.Park, Icons.Filled.Park),
    TabItem(AnchorTabs.PLAN, "Plan", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    TabItem(AnchorTabs.FOCUS, "Focus", Icons.Outlined.Timer, Icons.Filled.Timer),
    TabItem(AnchorTabs.AI, "AI", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    TabItem(AnchorTabs.MORE, "More", Icons.Outlined.Apps, Icons.Filled.Apps)
)

@Composable
fun AnchorShell(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    planBadgeCount: Int = 0,
    tabBackground: @Composable (Int) -> Brush = { LocalAnchorExtras.current.screenGradient },
    content: @Composable (PaddingValues) -> Unit
) {
    val bg = tabBackground(selectedTab)
    Box(modifier = modifier.fillMaxSize().background(bg)) {
        Scaffold(
            containerColor = Color.Transparent,
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
}

@Composable
fun AnchorBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    planBadgeCount: Int = 0
) {
    // Transparent container lets the per-tab harbor gradient flow behind the
    // bar; the M3 active-indicator pill carries selection state.
    NavigationBar(modifier = modifier, containerColor = Color.Transparent) {
        ANCHOR_TAB_ITEMS.forEach { tab ->
            val selected = selectedTab == tab.index
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab.index) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (tab.index == AnchorTabs.PLAN && planBadgeCount > 0) {
                                Badge { Text("$planBadgeCount") }
                            }
                        }
                    ) {
                        Icon(
                            if (selected) tab.selectedIcon else tab.icon,
                            contentDescription = null
                        )
                    }
                },
                label = { Text(tab.label) }
            )
        }
    }
}
