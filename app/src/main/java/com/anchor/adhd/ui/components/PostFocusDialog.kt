package com.anchor.adhd.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anchor.adhd.data.model.FocusEndTag
import com.anchor.adhd.data.model.PostFocusSummary
import com.anchor.adhd.ui.copy.AppCopy

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostFocusDialog(
    summary: PostFocusSummary,
    onDismiss: () -> Unit,
    onConfirm: (FocusEndTag?, Boolean, Int) -> Unit,
    onShrinkNext: () -> Unit,
    onParkSomeday: () -> Unit,
    onStartTen: () -> Unit
) {
    var actual by remember(summary.sessionId) { mutableIntStateOf(summary.actualMinutes) }
    var markDone by remember(summary.sessionId) { mutableStateOf(false) }
    var tag by remember(summary.sessionId) { mutableStateOf<FocusEndTag?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Session logged") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                summary.taskTitle?.let {
                    Text("Working on: $it")
                }
                    Text("Planned ${summary.plannedMinutes} min")
                    if (summary.blockedAttempts > 0) {
                        Text("${summary.blockedAttempts} blocked attempt${if (summary.blockedAttempts == 1) "" else "s"} this session")
                    }
                OutlinedTextField(
                    value = actual.toString(),
                    onValueChange = { v -> v.toIntOrNull()?.let { actual = it.coerceAtLeast(1) } },
                    label = { Text("Actual minutes") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("What happened?", style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusEndTag.entries.filter { it != FocusEndTag.NONE }.forEach { t ->
                        FilterChip(
                            selected = tag == t,
                            onClick = { tag = if (tag == t) null else t },
                            label = { Text(t.name.lowercase().replaceFirstChar { c -> c.uppercase() }) }
                        )
                    }
                }
                FilterChip(
                    selected = markDone,
                    onClick = { markDone = !markDone },
                    label = { Text("Mark task done") }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(tag, markDone, actual) }) { Text("Save") }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onShrinkNext) { Text("Shrink next (10 min)") }
                TextButton(onClick = onParkSomeday) { Text("Park Someday") }
                TextButton(onClick = onStartTen) { Text("Start 10 min") }
                TextButton(onClick = onDismiss) { Text(AppCopy.DIALOG_CLOSE) }
            }
        }
    )
}

@Composable
fun ResetTimerDialog(
    remainingSeconds: Int,
    onDone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Reset") },
        text = {
            val mins = remainingSeconds / 60
            val secs = remainingSeconds % 60
            Text("Silent break · %d:%02d left".format(mins, secs))
        },
        confirmButton = {
            Button(onClick = onDone, enabled = remainingSeconds <= 0) { Text("Done") }
        }
    )
}
