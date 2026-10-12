package com.anchor.adhd.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anchor.adhd.data.model.RoutineEntity
import com.anchor.adhd.data.model.RoutineStepEntity
import kotlinx.coroutines.delay

@Composable
fun RoutineRunnerSheet(
    routine: RoutineEntity,
    steps: List<RoutineStepEntity>,
    onStartFocus: () -> Unit,
    onDismiss: () -> Unit
) {
    var stepIndex by remember { mutableIntStateOf(0) }
    var secondsLeft by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }

    val step = steps.getOrNull(stepIndex)
    val progress = if (steps.isEmpty()) 0f else (stepIndex + if (running) 0.5f else 0f) / steps.size

    LaunchedEffect(running, stepIndex) {
        if (!running || step == null) return@LaunchedEffect
        secondsLeft = step.durationMinutes * 60
        while (secondsLeft > 0 && running) {
            delay(1000)
            secondsLeft--
        }
        if (running && secondsLeft <= 0) {
            if (stepIndex < steps.lastIndex) {
                stepIndex++
            } else {
                running = false
                stepIndex = steps.size
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(routine.name, style = MaterialTheme.typography.titleLarge)
        routine.ifThen?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())

        if (stepIndex >= steps.size && steps.isNotEmpty() && !running) {
            Text("Routine complete.", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onStartFocus, modifier = Modifier.fillMaxWidth()) {
                Text("Start focus timer")
            }
        } else if (step == null) {
            Text("No steps in this routine.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Step ${stepIndex + 1} of ${steps.size}", style = MaterialTheme.typography.labelMedium)
            Text(step.title, style = MaterialTheme.typography.titleMedium)
            Text(
                if (running) "%d:%02d".format(secondsLeft / 60, secondsLeft % 60)
                else "${step.durationMinutes} min",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!running) {
                    Button(onClick = { running = true }, modifier = Modifier.weight(1f)) {
                        Text("Start step")
                    }
                } else {
                    OutlinedButton(onClick = {
                        running = false
                        if (stepIndex < steps.lastIndex) stepIndex++ else stepIndex = steps.size
                    }, modifier = Modifier.weight(1f)) {
                        Text("Skip")
                    }
                }
            }
        }

        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Close")
        }
    }
}
