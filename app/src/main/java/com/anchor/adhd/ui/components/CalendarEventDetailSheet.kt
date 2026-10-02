package com.anchor.adhd.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anchor.adhd.data.model.CalendarEventEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarEventDetailSheet(
    event: CalendarEventEntity,
    onDismiss: () -> Unit,
    onEditInCalendar: () -> Unit,
    onCopyToAnchor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val zone = ZoneId.systemDefault()
    val formatter = DateTimeFormatter.ofPattern("EEE, MMM d - h:mm a")
    val start = Instant.ofEpochMilli(event.startMillis).atZone(zone).format(formatter)
    val end = Instant.ofEpochMilli(event.endMillis).atZone(zone).format(formatter)
    val sourceLabel = if (event.source == "device") "Device calendar" else "Imported calendar"

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(event.title, style = MaterialTheme.typography.titleLarge)
            Text(sourceLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("$start\n$end", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Calendar events stay linked to your device. Edit the original, or copy it into Anchor for task controls, focus, and rescheduling.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onEditInCalendar, modifier = Modifier.fillMaxWidth()) {
                Text("Edit in device calendar")
            }
            OutlinedButton(onClick = onCopyToAnchor, modifier = Modifier.fillMaxWidth()) {
                Text("Copy as editable Anchor task")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
        }
    }
}
