package com.anchor.adhd.ui.grow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anchor.adhd.data.model.HabitAutoSource
import com.anchor.adhd.data.model.HabitEntity
import com.anchor.adhd.data.model.HabitScheduleType
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitEditorSheet(
    existing: HabitEntity?,
    onSave: (
        name: String,
        scheduleType: HabitScheduleType,
        scheduleDays: String,
        targetPerWeek: Int,
        gracePerWeek: Int,
        autoSource: HabitAutoSource,
        autoThreshold: Int
    ) -> Unit,
    onArchive: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var scheduleType by remember(existing) { mutableStateOf(existing?.scheduleType ?: HabitScheduleType.DAILY) }
    var selectedDays by remember(existing) {
        mutableStateOf(
            existing?.scheduleDays?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.toSet()
                ?: emptySet()
        )
    }
    var targetPerWeek by remember(existing) { mutableIntStateOf(existing?.targetPerWeek ?: 3) }
    var gracePerWeek by remember(existing) { mutableIntStateOf(existing?.gracePerWeek ?: 1) }
    var autoSource by remember(existing) { mutableStateOf(existing?.autoSource ?: HabitAutoSource.NONE) }
    var autoThreshold by remember(existing) { mutableIntStateOf(existing?.autoThreshold ?: 5000) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (existing == null) "New habit" else "Edit habit",
                style = MaterialTheme.typography.headlineSmall
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text("Schedule", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HabitScheduleType.entries.forEach { type ->
                    FilterChip(
                        selected = scheduleType == type,
                        onClick = { scheduleType = type },
                        label = { Text(scheduleLabel(type)) }
                    )
                }
            }
            if (scheduleType == HabitScheduleType.SPECIFIC_DAYS) {
                Text("Pick days", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayOfWeek.entries.forEach { dow ->
                        val value = dow.value
                        FilterChip(
                            selected = value in selectedDays,
                            onClick = {
                                selectedDays = if (value in selectedDays) selectedDays - value else selectedDays + value
                            },
                            label = { Text(dow.name.take(3).lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
            if (scheduleType == HabitScheduleType.X_PER_WEEK) {
                Text("Target per week: $targetPerWeek", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..7).forEach { n ->
                        FilterChip(
                            selected = targetPerWeek == n,
                            onClick = { targetPerWeek = n },
                            label = { Text("$n×") }
                        )
                    }
                }
            }
            Text("Grace days per week: $gracePerWeek", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..3).forEach { n ->
                    FilterChip(
                        selected = gracePerWeek == n,
                        onClick = { gracePerWeek = n },
                        label = { Text("$n") }
                    )
                }
            }
            Text("Auto-complete from Samsung Health", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HabitAutoSource.entries.forEach { source ->
                    FilterChip(
                        selected = autoSource == source,
                        onClick = {
                            autoSource = source
                            when (source) {
                                HabitAutoSource.STEPS -> {
                                    if (autoThreshold !in listOf(3000, 5000, 7500, 10000)) {
                                        autoThreshold = 5000
                                    }
                                }
                                HabitAutoSource.EXERCISE -> {
                                    if (autoThreshold !in listOf(15, 30, 45, 60)) {
                                        autoThreshold = 30
                                    }
                                }
                                HabitAutoSource.NONE -> Unit
                            }
                        },
                        label = {
                            Text(
                                when (source) {
                                    HabitAutoSource.NONE -> "Manual"
                                    HabitAutoSource.STEPS -> "Steps"
                                    HabitAutoSource.EXERCISE -> "Exercise"
                                }
                            )
                        }
                    )
                }
            }
            if (autoSource != HabitAutoSource.NONE) {
                Text("Threshold: $autoThreshold", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val options = if (autoSource == HabitAutoSource.STEPS) {
                        listOf(3000, 5000, 7500, 10000)
                    } else {
                        listOf(15, 30, 45, 60)
                    }
                    options.forEach { n ->
                        FilterChip(
                            selected = autoThreshold == n,
                            onClick = { autoThreshold = n },
                            label = { Text("$n") }
                        )
                    }
                }
            }
            Button(
                onClick = {
                    val days = selectedDays.sorted().joinToString(",")
                    onSave(name, scheduleType, days, targetPerWeek, gracePerWeek, autoSource, autoThreshold)
                },
                enabled = name.isNotBlank() && (scheduleType != HabitScheduleType.SPECIFIC_DAYS || selectedDays.isNotEmpty()),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
            if (existing != null && onArchive != null) {
                OutlinedButton(onClick = onArchive, modifier = Modifier.fillMaxWidth()) {
                    Text("Archive habit")
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
}

private fun scheduleLabel(type: HabitScheduleType): String = when (type) {
    HabitScheduleType.DAILY -> "Daily"
    HabitScheduleType.WEEKDAYS -> "Weekdays"
    HabitScheduleType.SPECIFIC_DAYS -> "Pick days"
    HabitScheduleType.X_PER_WEEK -> "N per week"
}
