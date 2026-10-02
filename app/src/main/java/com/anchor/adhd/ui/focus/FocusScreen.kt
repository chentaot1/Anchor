package com.anchor.adhd.ui.focus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anchor.adhd.domain.PlantCatalog
import com.anchor.adhd.service.FocusBlockService
import com.anchor.adhd.service.FocusTimerState
import com.anchor.adhd.ui.components.CustomDurationDialog
import com.anchor.adhd.ui.components.RaindropAnimation
import com.anchor.adhd.ui.copy.AppCopy
import com.anchor.adhd.ui.theme.AnchorColors
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.vm.AnchorViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusScreen(vm: AnchorViewModel, modifier: Modifier = Modifier) {
    val timer by vm.timerState.collectAsState()
    val now by vm.nowTask.collectAsState()
    val focusDurations by vm.focusDurations.collectAsState()
    val weekly by vm.weeklySummary.collectAsState()
    val blockedAttempts by vm.blockedAttempts.collectAsState()
    val plantingSpecies = remember(weekly.focusSessions) {
        PlantCatalog.defaultPlantedSpecies(weekly.focusSessions)
    }
    val active = timer.phase != FocusTimerState.Phase.IDLE
    val (workMin, breakMin) = focusDurations
    var pickerMinutes by rememberSaveable(workMin) { mutableIntStateOf(workMin) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var showRaindrop by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val progress = if (active && timer.totalWorkSeconds > 0) {
        1f - timer.remainingSeconds.toFloat() / timer.totalWorkSeconds
    } else 0f

    val remainingSecs = if (active) timer.remainingSeconds.toLong() else (pickerMinutes * 60L)
    val currentTaskTitle = if (active) {
        timer.taskTitle ?: now.task?.title
    } else {
        now.task?.title ?: "Ready to anchor"
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (active) "Focus in progress" else "Focus", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.openAppBlocker() }) {
                    Icon(Icons.Outlined.Shield, contentDescription = "App protection")
                }
            }
            Box(Modifier.fillMaxWidth().height(310.dp)) {
                FocusCanvas(
                    progress = progress,
                    species = plantingSpecies,
                    isWorkPhase = timer.phase == FocusTimerState.Phase.WORK || !active,
                    remainingSeconds = remainingSecs,
                    taskTitle = null,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AnchorSpacing.screenHorizontal, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(currentTaskTitle.orEmpty(), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(
                    if (FocusBlockService.isEnabled()) "App protection on · $blockedAttempts blocked" else "Set up app protection",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { vm.openAppBlocker() }.padding(8.dp)
                )
                if (active) {
                    Button(
                        onClick = {
                            showRaindrop = true
                            vm.requestEndFocus(true)
                            scope.launch {
                                delay(900)
                                showRaindrop = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AnchorColors.HarborAction,
                            contentColor = AnchorColors.HarborBackground
                        )
                    ) {
                        Text(
                            "Complete session",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                    TextButton(onClick = { vm.requestEndFocus(timer.phase != FocusTimerState.Phase.WORK) }) {
                        Text(AppCopy.FOCUS_END_EARLY, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val standardOptions = listOf(10, 20, 30, 45)
                    val isCustom = pickerMinutes !in standardOptions
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        standardOptions.forEach { mins ->
                            val selected = pickerMinutes == mins
                            FilterChip(
                                selected = selected,
                                onClick = { pickerMinutes = mins },
                                label = {
                                    Text(
                                        text = "$mins min",
                                        maxLines = 1,
                                        textAlign = TextAlign.Center
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AnchorColors.HarborAction,
                                    selectedLabelColor = AnchorColors.HarborBackground,
                                    containerColor = AnchorColors.HarborDock,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = null
                            )
                        }
                        // Custom Pill: opens CustomDurationDialog
                        FilterChip(
                            selected = isCustom,
                            onClick = { showCustomDialog = true },
                            label = {
                                Text(
                                    text = if (isCustom) "$pickerMinutes min · Custom" else "Custom",
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AnchorColors.HarborAction,
                                selectedLabelColor = AnchorColors.HarborBackground,
                                containerColor = AnchorColors.HarborDock,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = null
                        )
                    }
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.startFocus(pickerMinutes, breakMin, now.task?.id)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AnchorColors.HarborAction,
                            contentColor = AnchorColors.HarborBackground
                        )
                    ) {
                        Text(
                            "Start focus · $pickerMinutes min",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }

        if (showCustomDialog) {
            CustomDurationDialog(
                initialMinutes = pickerMinutes,
                onDismiss = { showCustomDialog = false },
                onConfirm = { customMins ->
                    pickerMinutes = customMins
                    showCustomDialog = false
                }
            )
        }

        RaindropAnimation(active = showRaindrop, modifier = Modifier.fillMaxSize())
    }
}

