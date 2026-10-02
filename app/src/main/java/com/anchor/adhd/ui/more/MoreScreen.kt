package com.anchor.adhd.ui.more

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.MoreRoute
import com.anchor.adhd.ui.ai.AiScreen
import com.anchor.adhd.ui.plan.PlanScreen
import com.anchor.adhd.ui.components.AnchorCard
import com.anchor.adhd.ui.grow.HabitsScreen
import com.anchor.adhd.ui.grow.StrategiesScreen
import com.anchor.adhd.ui.settings.SettingsScreen
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.vm.AnchorViewModel
import com.anchor.adhd.data.AnchorContainer


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    vm: AnchorViewModel,
    container: AnchorContainer,
    route: MoreRoute,
    onRouteChange: (MoreRoute) -> Unit,
    modifier: Modifier = Modifier,
    planTab: Int = 0,
    onPlanTabSelected: (Int) -> Unit = {},
    showPlanList: Boolean = false,
    onTogglePlanList: () -> Unit = {}
) {
    when (route) {
        MoreRoute.Hub -> MoreHub(onRouteChange, modifier)
        MoreRoute.Forest -> ForestGalleryScreen(vm, onBack = { onRouteChange(MoreRoute.Hub) }, modifier)
        MoreRoute.Grow, MoreRoute.Strategies -> Column(modifier) {
            TopAppBar(
                title = { Text("Strategies") },
                navigationIcon = {
                    IconButton(onClick = { onRouteChange(MoreRoute.Hub) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
            StrategiesScreen(vm, Modifier.weight(1f))
        }
        MoreRoute.Habits -> Column(modifier) {
            TopAppBar(
                title = { Text("Habits") },
                navigationIcon = {
                    IconButton(onClick = { onRouteChange(MoreRoute.Hub) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
            HabitsScreen(vm, Modifier.weight(1f))
        }
        MoreRoute.Ai -> AiScreen(vm, modifier)
        MoreRoute.Plan -> Column(modifier.fillMaxSize()) {
            BackHandler { onRouteChange(MoreRoute.Hub) }
            TopAppBar(
                title = { Text("Plan") },
                navigationIcon = {
                    IconButton(onClick = { onRouteChange(MoreRoute.Hub) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
            PlanScreen(vm, planTab, onPlanTabSelected, Modifier.weight(1f),
                timelineFirst = planTab == 0, showListView = showPlanList, onToggleListView = onTogglePlanList)
        }
        MoreRoute.Settings -> SettingsScreen(vm, container, onBack = { onRouteChange(MoreRoute.Hub) })
        MoreRoute.Blocker -> com.anchor.adhd.ui.focus.AppBlockerScreen(container, onBack = { onRouteChange(MoreRoute.Hub) })
    }
}

@Composable
private fun MoreHub(onRouteChange: (MoreRoute) -> Unit, modifier: Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AnchorSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(AnchorSpacing.itemGap)
    ) {
        Text(
            "More",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = AnchorSpacing.itemGap)
        )
        HubCard("Plan", "Inbox, timeline & assignments", Icons.Default.CalendarMonth) { onRouteChange(MoreRoute.Plan) }
        HubCard("Forest", "Every planted session", Icons.Default.Forest) { onRouteChange(MoreRoute.Forest) }
        HubCard("App Blocker", "Allowances, shields & earned leisure", Icons.Default.Shield) { onRouteChange(MoreRoute.Blocker) }
        HubCard("Habits", "Heatmap & tracking", Icons.Default.CalendarMonth) { onRouteChange(MoreRoute.Habits) }
        HubCard("Strategies", "In-the-moment tools", Icons.Default.Lightbulb) { onRouteChange(MoreRoute.Strategies) }
        HubCard("Settings", "Focus, planning, data", Icons.Default.Settings) { onRouteChange(MoreRoute.Settings) }
    }
}

@Composable
private fun HubCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    AnchorCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
