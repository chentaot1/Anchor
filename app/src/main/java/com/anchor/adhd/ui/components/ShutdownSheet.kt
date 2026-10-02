package com.anchor.adhd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Brush
import com.anchor.adhd.ui.theme.AnchorColors
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anchor.adhd.data.model.TaskEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShutdownSheet(
    unresolvedTasks: List<TaskEntity>,
    tomorrowPreview: String?,
    onDone: (TaskEntity) -> Unit,
    onDeferTomorrow: (TaskEntity) -> Unit,
    onSomeday: (TaskEntity) -> Unit,
    onComplete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            AnchorColors.HarborAi.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {
        Column(
            Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Shutdown", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Capture open loops. Tomorrow can hold the rest.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (tomorrowPreview != null) {
                Text("Tomorrow starts with: $tomorrowPreview", style = MaterialTheme.typography.bodySmall)
            }
            unresolvedTasks.forEach { task ->
                Text(task.title, style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onDone(task) }) { Text("Done") }
                    TextButton(onClick = { onDeferTomorrow(task) }) { Text("Tomorrow") }
                    TextButton(onClick = { onSomeday(task) }) { Text("Someday") }
                }
            }
            if (unresolvedTasks.isEmpty()) {
                Text("Nothing left open for today.", style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
                Text("Shutdown complete")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Leave for now")
            }
        }
        }
    }
}
