package com.anchor.adhd.ui.grow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anchor.adhd.data.model.HabitEntity
import com.anchor.adhd.data.repository.HabitProgress
import com.anchor.adhd.data.repository.HabitRepository
import com.anchor.adhd.ui.components.AnchorCard
import com.anchor.adhd.ui.components.ItemSpacing

@Composable
fun HabitsSection(
    habits: List<HabitProgress>,
    activeCount: Int,
    onToggle: (Long) -> Unit,
    onAdd: () -> Unit,
    onEdit: (HabitEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
        if (habits.isEmpty()) {
            Text(
                "Add a habit to track daily wins — no streak shame, just progress.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            habits.forEach { progress ->
                HabitRow(
                    progress = progress,
                    onToggle = { onToggle(progress.habit.id) },
                    onEdit = { onEdit(progress.habit) }
                )
            }
        }
        val atLimit = activeCount >= HabitRepository.MAX_ACTIVE_HABITS
        FilledTonalButton(
            onClick = onAdd,
            enabled = !atLimit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (atLimit) "Habit limit reached (${HabitRepository.MAX_ACTIVE_HABITS})"
                else "Add habit"
            )
        }
    }
}

@Composable
private fun HabitRow(
    progress: HabitProgress,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    val habit = progress.habit
    AnchorCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(checked = progress.completedToday, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(habit.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${progress.weekProgress.completed} of ${progress.weekProgress.expected} this week",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${progress.score.score}% · 30-day",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            FilledTonalButton(onClick = onEdit) { Text("Edit") }
        }
        HabitHeatmap(days = progress.heatmap, modifier = Modifier.padding(top = 8.dp))
    }
}
