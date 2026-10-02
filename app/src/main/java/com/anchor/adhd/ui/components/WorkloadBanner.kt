package com.anchor.adhd.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anchor.adhd.data.model.WorkloadStatus
import com.anchor.adhd.domain.WorkloadResult

@Composable
fun WorkloadBanner(
    workload: WorkloadResult,
    estimateErrorPercent: Int? = null,
    modifier: Modifier = Modifier
) {
    val (container, onContainer) = when (workload.status) {
        WorkloadStatus.OK -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        WorkloadStatus.WARNING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        WorkloadStatus.OVER -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    val hours = workload.effectiveLoad / 60
    val mins = workload.effectiveLoad % 60
    val capHours = workload.capacityMinutes / 60
    val capMins = workload.capacityMinutes % 60
    val timeLabel = if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
    val capLabel = if (capMins > 0) "${capHours}h ${capMins}m" else "${capHours}h"
    val statusLabel = when (workload.status) {
        WorkloadStatus.OK -> "Planned $timeLabel of $capLabel capacity"
        WorkloadStatus.WARNING -> "Getting full: $timeLabel of $capLabel"
        WorkloadStatus.OVER -> "Over capacity: $timeLabel of $capLabel"
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = container,
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = buildString {
                append(statusLabel)
                if (estimateErrorPercent != null && estimateErrorPercent > 20) {
                    append(" · Estimates off by ~$estimateErrorPercent%")
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = onContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}
