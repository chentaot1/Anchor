package com.anchor.adhd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anchor.adhd.data.model.WorkloadStatus
import com.anchor.adhd.domain.WorkloadResult
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens

@Composable
fun WorkloadBanner(
    workload: WorkloadResult,
    estimateErrorPercent: Int? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAnchorTokens.current
    val containerColor = when (workload.status) {
        WorkloadStatus.OK, WorkloadStatus.WARNING -> tokens.paper2
        WorkloadStatus.OVER -> tokens.clayRoseSoft
    }
    val accentColor = when (workload.status) {
        WorkloadStatus.OK -> tokens.sage
        WorkloadStatus.WARNING -> tokens.beacon
        WorkloadStatus.OVER -> tokens.clayRose
    }
    val trackColor = when (workload.status) {
        WorkloadStatus.OK, WorkloadStatus.WARNING -> tokens.paper3
        WorkloadStatus.OVER -> tokens.clayRose.copy(alpha = 0.22f)
    }
    val subtitleColor = when (workload.status) {
        WorkloadStatus.OK, WorkloadStatus.WARNING -> tokens.muted
        WorkloadStatus.OVER -> tokens.clayRose
    }

    val loadLabel = formatWorkloadDuration(workload.effectiveLoad)
    val capacityLabel = formatWorkloadDuration(workload.capacityMinutes)
    val statusHeadline = when (workload.status) {
        WorkloadStatus.OK -> "Planned $loadLabel of $capacityLabel capacity"
        WorkloadStatus.WARNING -> "Getting full: $loadLabel of $capacityLabel"
        WorkloadStatus.OVER -> "Over capacity: $loadLabel of $capacityLabel"
    }
    val progress = when {
        workload.effectiveLoad <= 0 -> 0f
        workload.capacityMinutes <= 0 -> 1f
        else -> (workload.effectiveLoad.toFloat() / workload.capacityMinutes.toFloat()).coerceIn(0f, 1f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        color = containerColor,
        contentColor = tokens.ink,
        shape = RoundedCornerShape(AnchorSpacing.radiusWell),
        border = null,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                )
                Text(
                    text = statusHeadline,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = tokens.ink,
                    modifier = Modifier.weight(1f),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(AnchorSpacing.radiusPill))
                    .background(trackColor),
            ) {
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(6.dp)
                            .clip(RoundedCornerShape(AnchorSpacing.radiusPill))
                            .background(accentColor),
                    )
                }
            }

            if (estimateErrorPercent != null && estimateErrorPercent > 20) {
                Text(
                    text = "Recent estimates ran about $estimateErrorPercent% off",
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = subtitleColor,
                )
            }
        }
    }
}

private fun formatWorkloadDuration(totalMinutes: Int): String {
    val safe = totalMinutes.coerceAtLeast(0)
    val hours = safe / 60
    val minutes = safe % 60
    return when {
        hours == 0 -> "${minutes}m"
        minutes == 0 -> "${hours}h"
        else -> "${hours}h ${minutes}m"
    }
}
