package com.anchor.adhd.ui.more

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.anchor.adhd.data.AnchorContainer
import com.anchor.adhd.ui.MoreRoute
import com.anchor.adhd.ui.ai.AiScreen
import com.anchor.adhd.ui.components.AnchorListRow
import com.anchor.adhd.ui.components.AnchorWell
import com.anchor.adhd.ui.grow.HabitsScreen
import com.anchor.adhd.ui.grow.StrategiesScreen
import com.anchor.adhd.ui.plan.PlanScreen
import com.anchor.adhd.ui.settings.SettingsScreen
import com.anchor.adhd.ui.theme.AnchorSpacing
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.vm.AnchorViewModel

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
    onTogglePlanList: () -> Unit = {},
) {
    when (route) {
        MoreRoute.Hub -> MoreHub(onRouteChange, modifier)
        MoreRoute.Grow, MoreRoute.Strategies -> Column(modifier) {
            TopAppBar(
                title = { Text("Strategies") },
                navigationIcon = {
                    IconButton(onClick = { onRouteChange(MoreRoute.Hub) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
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
                },
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
                },
            )
            PlanScreen(
                vm,
                planTab,
                onPlanTabSelected,
                Modifier.weight(1f),
                timelineFirst = planTab == 0,
                showListView = showPlanList,
                onToggleListView = onTogglePlanList,
            )
        }
        MoreRoute.Settings -> SettingsScreen(vm, container, onBack = { onRouteChange(MoreRoute.Hub) })
        MoreRoute.Blocker -> com.anchor.adhd.ui.focus.AppBlockerScreen(container, onBack = { onRouteChange(MoreRoute.Hub) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoreHub(onRouteChange: (MoreRoute) -> Unit, modifier: Modifier) {
    val hubEntries = listOf(
        Triple("App Blocker", "Allowances, shields and earned leisure", MoreRoute.Blocker) to Icons.Default.Shield,
        Triple("Habits", "Heatmap and tracking", MoreRoute.Habits) to Icons.Default.CalendarMonth,
        Triple("Strategies", "In-the-moment tools", MoreRoute.Strategies) to Icons.Default.Lightbulb,
        Triple("Settings", "Focus, planning, data", MoreRoute.Settings) to Icons.Default.Settings,
    )

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AnchorSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(AnchorSpacing.itemGap),
    ) {
        Text(
            "More",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(vertical = AnchorSpacing.itemGap)
                .semantics { heading() },
        )
        AnchorWell(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            hubEntries.forEachIndexed { index, (entry, icon) ->
                val (title, subtitle, route) = entry
                AnchorListRow(
                    title = title,
                    subtitle = subtitle,
                    onClick = { onRouteChange(route) },
                    showDivider = index < hubEntries.lastIndex,
                    leading = {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                )
            }
        }
    }
}