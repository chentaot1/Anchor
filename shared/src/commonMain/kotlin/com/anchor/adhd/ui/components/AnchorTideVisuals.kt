package com.anchor.adhd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens

data class WeeklyTideDay(
    val shortLabel: String,
    val minutes: Int,
    val isToday: Boolean,
)

fun computeTideBarFractions(
    days: List<WeeklyTideDay>,
    minScaleMinutes: Int = 60,
): List<Float> {
    if (days.isEmpty()) return emptyList()
    val floor = minScaleMinutes.coerceAtLeast(1)
    val peak = maxOf(days.maxOf { it.minutes.coerceAtLeast(0) }, floor)
    return days.map { day ->
        (day.minutes.coerceAtLeast(0).toFloat() / peak.toFloat()).coerceIn(0f, 1f)
    }
}

fun computeTodayTideFraction(
    todayMinutes: Int,
    goalMinutes: Int,
): Float {
    val safeToday = todayMinutes.coerceAtLeast(0)
    val safeGoal = goalMinutes.coerceAtLeast(1)
    return (safeToday.toFloat() / safeGoal.toFloat()).coerceIn(0f, 1f)
}

fun formatTideDuration(totalMinutes: Int): String {
    val safe = totalMinutes.coerceAtLeast(0)
    val hours = safe / 60
    val minutes = safe % 60
    return when {
        hours == 0 -> "${minutes}m"
        minutes == 0 -> "${hours}h"
        else -> "${hours}h ${minutes}m"
    }
}

/**
 * Real-data 7-day focus-minute rhythm card for desktop Home. Highlights today in
 * amber beacon and prior days in harbor primary over neutral paper3 slots.
 */
@Composable
fun AnchorWeeklyTide(
    days: List<WeeklyTideDay>,
    stageLabel: String,
    totalMinutes: Int,
    todayMinutes: Int,
    deflectedCount: Int,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAnchorTokens.current
    val fractions = computeTideBarFractions(days)
    val barMaxHeight = 72.dp
    val barShape = RoundedCornerShape(6.dp)

    AnchorWell(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Weekly tide",
                style = MaterialTheme.typography.titleSmall,
                color = tokens.ink,
            )
            Text(
                text = stageLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = tokens.sage,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = formatTideDuration(totalMinutes),
            style = MaterialTheme.typography.displaySmall,
            color = tokens.ink,
        )

        if (days.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        val breakdown = days.joinToString(", ") { day ->
                            "${day.shortLabel} ${formatTideDuration(day.minutes)}"
                        }
                        contentDescription =
                            "Weekly tide: ${formatTideDuration(totalMinutes)} total, $stageLabel. $breakdown"
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEachIndexed { index, day ->
                    val fraction = fractions.getOrElse(index) { 0f }
                    val barColor = if (day.isToday) tokens.beacon else tokens.primary

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(barMaxHeight)
                                .clip(barShape)
                                .background(tokens.paper3),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            if (fraction > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height((barMaxHeight * fraction).coerceAtLeast(6.dp))
                                        .clip(barShape)
                                        .background(barColor),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = day.shortLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                                fontFeatureSettings = "tnum",
                            ),
                            color = if (day.isToday) tokens.ink else tokens.muted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        AnchorHairline()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Today ${formatTideDuration(todayMinutes)}",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = tokens.ink2,
            )
            Text(
                text = "${deflectedCount.coerceAtLeast(0)} deflected",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = tokens.muted,
            )
        }
    }
}

/**
 * Compact horizontal daily tide progress strip for mobile Home.
 */
@Composable
fun AnchorTodayTideStrip(
    todayMinutes: Int,
    goalMinutes: Int,
    stageLabel: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAnchorTokens.current
    val safeToday = todayMinutes.coerceAtLeast(0)
    val safeGoal = goalMinutes.coerceAtLeast(1)
    val fraction = computeTodayTideFraction(safeToday, safeGoal)
    val pillShape = RoundedCornerShape(AnchorSpacing.radiusPill)

    AnchorWell(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription =
                "Today's tide: ${formatTideDuration(safeToday)} of ${formatTideDuration(safeGoal)}, $stageLabel"
        },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Today's tide",
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.ink,
                )
                Text(
                    text = stageLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = tokens.sage,
                    maxLines = 1,
                )
            }
            Text(
                text = "${formatTideDuration(safeToday)} / ${formatTideDuration(safeGoal)}",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = tokens.ink2,
                maxLines = 1,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(pillShape)
                .background(tokens.paper3),
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(pillShape)
                        .background(if (fraction >= 1f) tokens.sage else tokens.primary),
                )
            }
        }
    }
}
