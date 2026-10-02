package com.anchor.adhd.ui.grow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.components.AnchorCard
import com.anchor.adhd.ui.components.ExpandableSection
import com.anchor.adhd.ui.components.ScreenPadding
import com.anchor.adhd.ui.components.SectionHeader
import com.anchor.adhd.ui.components.SectionSpacing
import com.anchor.adhd.ui.components.WeeklyMirrorCard
import com.anchor.adhd.ui.vm.AnchorViewModel

@Composable
fun HabitsScreen(vm: AnchorViewModel, modifier: Modifier = Modifier) {
    val weekly by vm.weeklySummary.collectAsState()
    val habits by vm.habitsWithProgress.collectAsState()
    val habitCount by vm.activeHabitCount.collectAsState()
    val context = LocalContext.current
    val activity = context as android.app.Activity
    val healthConnected by vm.samsungHealthConnected.collectAsState()

    var showHabitEditor by remember { mutableStateOf(false) }
    var editingHabitId by remember { mutableLongStateOf(-1L) }
    val editingHabit = habits.find { it.habit.id == editingHabitId }?.habit

    LazyColumn(
        modifier.fillMaxSize().padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing)
    ) {
        item {
            AnchorCard {
                ExpandableSection(
                    title = "Habits",
                    subtitle = "${habitCount} active",
                    initiallyExpanded = true
                ) {
                    HabitsSection(
                        habits = habits,
                        activeCount = habitCount,
                        onToggle = { vm.toggleHabitCompletion(it) },
                        onAdd = {
                            editingHabitId = -1L
                            showHabitEditor = true
                        },
                        onEdit = { habit ->
                            editingHabitId = habit.id
                            showHabitEditor = true
                        }
                    )
                }
            }
        }

        item {
            AnchorCard {
                SectionHeader(
                    "Samsung Health",
                    if (healthConnected) {
                        "Connected — step/exercise auto-rules can sync"
                    } else {
                        "Set step/exercise auto-rules on habits, then connect"
                    }
                )
                OutlinedButton(
                    onClick = { vm.connectSamsungHealth(activity) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect Samsung Health")
                }
                OutlinedButton(
                    onClick = { vm.syncSamsungHealth() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sync habits now")
                }
            }
        }

        item {
            AnchorCard {
                ExpandableSection(
                    title = "This week",
                    subtitle = "${weekly.focusSessions} focus sessions",
                    initiallyExpanded = false
                ) {
                    WeeklyMirrorCard(
                        focusSessions = weekly.focusSessions,
                        focusMinutes = weekly.focusMinutes,
                        tasksCompleted = weekly.tasksCompleted,
                        checkInsLogged = weekly.checkInsLogged,
                        replanQueue = weekly.replanQueueSize
                    )
                }
            }
        }
    }

    if (showHabitEditor) {
        HabitEditorSheet(
            existing = editingHabit,
            onSave = { name, scheduleType, scheduleDays, targetPerWeek, gracePerWeek, autoSource, autoThreshold ->
                if (editingHabit == null) {
                    vm.createHabit(name, scheduleType, scheduleDays, targetPerWeek, gracePerWeek, autoSource, autoThreshold)
                } else {
                    vm.updateHabit(
                        editingHabit.copy(
                            name = name,
                            scheduleType = scheduleType,
                            scheduleDays = scheduleDays,
                            targetPerWeek = targetPerWeek,
                            gracePerWeek = gracePerWeek,
                            autoSource = autoSource,
                            autoThreshold = autoThreshold
                        )
                    )
                }
                showHabitEditor = false
                editingHabitId = -1L
            },
            onArchive = editingHabit?.let { habit ->
                {
                    vm.archiveHabit(habit.id)
                    showHabitEditor = false
                    editingHabitId = -1L
                }
            },
            onDismiss = {
                showHabitEditor = false
                editingHabitId = -1L
            }
        )
    }
}