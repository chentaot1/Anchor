package com.anchor.adhd.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anchor.adhd.data.model.InboxState
import com.anchor.adhd.data.model.TaskDifficulty
import com.anchor.adhd.data.model.TaskEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailSheet(
    task: TaskEntity,
    children: List<TaskEntity> = emptyList(),
    breakdownPreview: com.anchor.adhd.ai.AiBreakdownResult? = null,
    breakdownLoading: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onBreakDown: () -> Unit,
    onApplyBreakdown: () -> Unit = {},
    onStartFocus: () -> Unit,
    onCompleteChild: (TaskEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val zone = ZoneId.systemDefault()
    val dateFmt = DateTimeFormatter.ofPattern("MMM d")
    val timeFmt = DateTimeFormatter.ofPattern("h:mm a")

    var title by remember(task.id) { mutableStateOf(task.title) }
    var notes by remember(task.id) { mutableStateOf(task.notes) }
    var duration by remember(task.id) { mutableStateOf(task.durationMinutes.toString()) }
    var difficulty by remember(task.id) { mutableStateOf(task.difficulty) }
    var inboxState by remember(task.id) { mutableStateOf(task.inboxState) }
    var scheduledStart by remember(task.id) { mutableStateOf(task.scheduledStartMillis) }
    var dueAt by remember(task.id) { mutableStateOf(task.dueAtMillis) }

    fun formatWhen(millis: Long?): String {
        if (millis == null) return "Not scheduled"
        val dt = Instant.ofEpochMilli(millis).atZone(zone)
        return "${dt.format(dateFmt)} ${dt.format(timeFmt)}"
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(
            Modifier
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Task", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

            if (children.isNotEmpty()) {
                Text("Steps", style = MaterialTheme.typography.labelLarge)
                children.forEach { child ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(checked = child.isCompleted, onCheckedChange = { onCompleteChild(child) })
                        Text(
                            child.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (child.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (breakdownLoading) {
                Text("Breaking it down…", style = MaterialTheme.typography.bodyMedium)
            }
            breakdownPreview?.let { preview ->
                Text("Preview", style = MaterialTheme.typography.labelLarge)
                preview.steps.forEachIndexed { i, step ->
                    Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = onApplyBreakdown, modifier = Modifier.fillMaxWidth()) {
                    Text("Apply steps")
                }
            }

            Text("When (schedule)", style = MaterialTheme.typography.labelLarge)
            Text(formatWhen(scheduledStart), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val today = LocalDate.now(zone)
                    val atNine = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
                    scheduledStart = atNine
                }) { Text("Today 9am") }
                OutlinedButton(onClick = {
                    val tomorrow = LocalDate.now(zone).plusDays(1)
                    scheduledStart = tomorrow.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
                }) { Text("Tomorrow 9am") }
                TextButton(onClick = { scheduledStart = null }) { Text("Clear") }
            }

            Text("Due (deadline)", style = MaterialTheme.typography.labelLarge)
            Text(
                dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).format(dateFmt) } ?: "No deadline",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    dueAt = LocalDate.now(zone).plusDays(1).atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
                }) { Text("Tomorrow") }
                OutlinedButton(onClick = {
                    dueAt = LocalDate.now(zone).plusDays(7).atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
                }) { Text("+1 week") }
                TextButton(onClick = { dueAt = null }) { Text("Clear") }
            }

            OutlinedTextField(
                duration,
                { duration = it.filter { c -> c.isDigit() } },
                label = { Text("Duration (min)") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Difficulty", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskDifficulty.entries.forEach { d ->
                    FilterChip(
                        selected = difficulty == d,
                        onClick = { difficulty = d },
                        label = { Text(d.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            Text("Bucket", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(InboxState.TODAY to "Today", InboxState.SOMEDAY to "Someday", InboxState.WAITING to "Later").forEach { (state, label) ->
                    FilterChip(
                        selected = inboxState == state,
                        onClick = { inboxState = state },
                        label = { Text(label) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBreakDown) { Text("Break down") }
                OutlinedButton(onClick = onStartFocus) { Text("Focus") }
            }

            Button(
                onClick = {
                    onSave(
                        task.copy(
                            title = title.trim(),
                            notes = notes.trim(),
                            durationMinutes = duration.toIntOrNull()?.coerceIn(5, 240) ?: task.durationMinutes,
                            difficulty = difficulty,
                            inboxState = inboxState,
                            scheduledStartMillis = scheduledStart,
                            dueAtMillis = dueAt
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onComplete, modifier = Modifier.weight(1f)) { Text("Complete") }
                TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("Delete") }
            }
        }
    }
}
