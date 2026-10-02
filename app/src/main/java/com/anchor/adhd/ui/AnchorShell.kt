package com.anchor.adhd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.theme.AnchorColors
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorExtras

object AnchorTabs {
    const val GROVE = 0
    const val AI = 1
    const val FOCUS = 2
    const val MORE = 3
}

enum class MoreRoute { Hub, Grow, Ai, Settings, Forest, Habits, Strategies, Blocker, Plan }

data class TabItem(val index: Int, val label: String, val icon: ImageVector)

val ANCHOR_TAB_ITEMS = listOf(
    TabItem(AnchorTabs.GROVE, "Grove", Icons.Rounded.Park),
    TabItem(AnchorTabs.AI, "AI", Icons.Rounded.AutoAwesome),
    TabItem(AnchorTabs.FOCUS, "Focus", Icons.Rounded.Timer),
    TabItem(AnchorTabs.MORE, "More", Icons.Rounded.Apps)
)

@Composable
fun AnchorShell(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
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
                AnchorBottomBar(selectedTab = selectedTab, onTabSelected = onTabSelected)
            }
        ) { padding ->
            content(padding)
        }
    }
}

@Composable
fun AnchorBottomBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AnchorColors.HarborDock)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = AnchorSpacing.screenHorizontal, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ANCHOR_TAB_ITEMS.forEach { tab ->
                val selected = selectedTab == tab.index
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(AnchorSpacing.radiusChip))
                        .clickable { onTabSelected(tab.index) }
                        .background(if (selected) AnchorColors.HarborAction.copy(alpha = 0.22f) else Color.Transparent)
                        .padding(vertical = 8.dp)
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) AnchorColors.HarborAction else Color(0xFFA8B4C0)
                    )
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) AnchorColors.HarborAction else Color(0xFFA8B4C0)
                    )
                }
            }
        }
    }
}
