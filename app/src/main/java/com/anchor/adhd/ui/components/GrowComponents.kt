package com.anchor.adhd.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WeeklyMirrorCard(
    focusSessions: Int,
    focusMinutes: Int,
    tasksCompleted: Int,
    checkInsLogged: Int,
    replanQueue: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("This week", style = MaterialTheme.typography.titleMedium)
        StatRow("Focus sessions", "$focusSessions")
        StatRow("Focus minutes", "$focusMinutes")
        StatRow("Tasks completed", "$tasksCompleted")
        StatRow("Check-ins", "$checkInsLogged")
        StatRow("Replan queue", "$replanQueue")
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
