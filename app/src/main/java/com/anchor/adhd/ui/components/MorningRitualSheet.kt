package com.anchor.adhd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.theme.AnchorColors
import com.anchor.adhd.data.model.EnergyLevel
import com.anchor.adhd.data.model.ReplanItemEntity
import com.anchor.adhd.data.model.TaskEntity
import com.anchor.adhd.domain.WorkloadResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorningRitualSheet(
    workload: WorkloadResult,
    estimateErrorPercent: Int? = null,
    replanItems: List<Pair<ReplanItemEntity, TaskEntity?>>,
    onEnergySelected: (EnergyLevel) -> Unit,
    onReplanReschedule: (Long, Long, Int) -> Unit,
    onReplanSomeday: (Long, Long) -> Unit,
    onReplanDismiss: (Long) -> Unit,
    onRunMorningRoutine: () -> Unit,
    onComplete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var step by remember { mutableIntStateOf(0) }
    val activeReplan = replanItems.mapNotNull { (item, task) -> task?.let { item to it } }
    val totalSteps = if (activeReplan.isEmpty()) 3 else 4
    val effectiveStep = if (step == 1 && activeReplan.isEmpty()) 2 else step
    val progressIndex = if (activeReplan.isEmpty() && effectiveStep >= 2) effectiveStep - 1 else effectiveStep

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val sunriseAlpha = 0.35f + (progressIndex + 1) / totalSteps.toFloat() * 0.45f
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            AnchorColors.HarborAction.copy(alpha = sunriseAlpha),
                            Color.Transparent
                        )
                    )
                )
        ) {
        Column(
            Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Morning check-in", style = MaterialTheme.typography.headlineSmall)
            LinearProgressIndicator(progress = { (progressIndex + 1) / totalSteps.toFloat() }, modifier = Modifier.fillMaxWidth())

            when (effectiveStep) {
                0 -> {
                    Text("How's your energy?", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EnergyLevel.entries.forEach { level ->
                            OutlinedButton(onClick = {
                                onEnergySelected(level)
                                step = if (activeReplan.isEmpty()) 2 else 1
                            }) { Text(level.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        }
                    }
                    TextButton(onClick = { step = if (activeReplan.isEmpty()) 2 else 1 }) { Text("Skip") }
                }
                1 -> {
                    Text("Missed blocks", style = MaterialTheme.typography.titleMedium)
                    activeReplan.forEach { (item, t) ->
                        Text(t.title, style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onReplanReschedule(item.id, t.id, t.durationMinutes) }) { Text("Reschedule") }
                            TextButton(onClick = { onReplanSomeday(item.id, t.id) }) { Text("Someday") }
                            TextButton(onClick = { onReplanDismiss(item.id) }) { Text("Dismiss") }
                        }
                    }
                    Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) { Text("Next") }
                }
                2 -> {
                    WorkloadBanner(workload = workload, estimateErrorPercent = estimateErrorPercent)
                    Text(
                        "Review what's on your plate before the day runs away.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { step = 3 }, modifier = Modifier.fillMaxWidth()) { Text("Next") }
                }
                else -> {
                    Text("Optional: run your morning routine?", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = onRunMorningRoutine, modifier = Modifier.fillMaxWidth()) {
                        Text("Run morning routine")
                    }
                    Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
                        Text("Start the day")
                    }
                }
            }
        }
        }
    }
}
