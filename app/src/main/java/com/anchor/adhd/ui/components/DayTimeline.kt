package com.anchor.adhd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.anchor.adhd.data.model.CalendarEventEntity
import com.anchor.adhd.data.model.TaskEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

const val DAY_START_HOUR = 6
const val DAY_END_HOUR = 23
const val HOUR_HEIGHT_DP = 48

@Composable
fun DayTimeline(
    date: LocalDate,
    scheduled: List<TaskEntity>,
    calendarEvents: List<CalendarEventEntity>,
    onTaskClick: (TaskEntity) -> Unit,
    onCalendarEventClick: (CalendarEventEntity) -> Unit,
    onTaskReschedule: (TaskEntity, Long) -> Unit,
    nowMillis: Long = System.currentTimeMillis(),
    spotlightTaskId: Long? = null,
    lockedTaskId: Long? = null,
    onLockedDragAttempt: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val zone = ZoneId.systemDefault()
    val totalHours = DAY_END_HOUR - DAY_START_HOUR + 1
    val height = (totalHours * HOUR_HEIGHT_DP).dp
    val formatter = DateTimeFormatter.ofPattern("h a", Locale.US)
    val dayStartMillis = date.atTime(DAY_START_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
    val nowMinutesFromStart = ((nowMillis - dayStartMillis) / 60_000).toInt()
    val nowLineDp = (nowMinutesFromStart / 60f * HOUR_HEIGHT_DP).coerceIn(0f, (totalHours * HOUR_HEIGHT_DP).toFloat())

    val allDayTasks = scheduled.filter { it.scheduledStartMillis == null }
    Column(modifier.verticalScroll(rememberScrollState())) {
        Text(
            "Drag blocks to reschedule · snaps to 15 min",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (allDayTasks.isNotEmpty()) {
            Text(
                "All Day Tasks",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                allDayTasks.forEach { task ->
                    Box(
                        Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
                            .clickable { onTaskClick(task) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            task.title + (if (task.dueAtMillis != null) " ⚠️" else ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
        Row {
            Column(Modifier.width(48.dp)) {
                for (h in DAY_START_HOUR..DAY_END_HOUR) {
                    Box(Modifier.height(HOUR_HEIGHT_DP.dp).padding(end = 4.dp)) {
                        Text(
                            date.atTime(h, 0).format(formatter),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                for (h in DAY_START_HOUR..DAY_END_HOUR) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .offset(y = ((h - DAY_START_HOUR) * HOUR_HEIGHT_DP).dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    )
                }
                calendarEvents.forEach { event ->
                    TimelineBlock(
                        label = event.title,
                        startMillis = event.startMillis,
                        endMillis = event.endMillis,
                        color = Color(0xFF4A6278),
                        zone = zone,
                        alpha = blockAlpha(event.startMillis, nowMillis),
                        highlighted = false,
                        draggable = false,
                        onClick = { onCalendarEventClick(event) },
                        onDragEnd = {},
                        onLockedDragAttempt = {}
                    )
                }
                scheduled.forEach { task ->
                    val start = task.scheduledStartMillis ?: return@forEach
                    val end = start + task.durationMinutes * 60_000L
                    val isLocked = lockedTaskId != null && task.id == lockedTaskId
                    val isSpotlight = spotlightTaskId != null && task.id == spotlightTaskId
                    val dueLabel = if (task.dueAtMillis != null) " ⚠️" else ""
                    TimelineBlock(
                        label = task.title + (if (isLocked) " 🔒" else "") + dueLabel,
                        startMillis = start,
                        endMillis = end,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        zone = zone,
                        alpha = if (isSpotlight) 1f else blockAlpha(start, nowMillis),
                        highlighted = isSpotlight,
                        draggable = !isLocked,
                        onClick = { onTaskClick(task) },
                        onDragEnd = { deltaMinutes ->
                            val dayStartMillis = date.atTime(DAY_START_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
                            val dayEndMillis = date.atTime(DAY_END_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
                            val maxStart = (dayEndMillis - task.durationMinutes * 60_000L).coerceAtLeast(dayStartMillis)

                            val snapped = (deltaMinutes / 15f).roundToInt() * 15
                            val calculatedStart = start + snapped * 60_000L
                            val newStart = calculatedStart.coerceIn(dayStartMillis, maxStart)
                            onTaskReschedule(task, newStart)
                        },
                        onLockedDragAttempt = onLockedDragAttempt
                    )
                }
                if (nowMinutesFromStart in 0..(totalHours * 60)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .offset(y = nowLineDp.dp)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.9f))
                    )
                }
            }
        }
        val dueTodayList = scheduled.filter { task ->
            task.dueAtMillis != null && !task.isCompleted && run {
                val dueLocalDate = Instant.ofEpochMilli(task.dueAtMillis).atZone(zone).toLocalDate()
                dueLocalDate == date
            }
        }
        if (dueTodayList.isNotEmpty()) {
            Text(
                "Due Today",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
            dueTodayList.forEach { task ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                        .clickable { onTaskClick(task) }
                        .padding(12.dp)
                ) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

private fun blockAlpha(startMillis: Long, nowMillis: Long): Float {
    val diffMinutes = abs(startMillis - nowMillis) / 60_000.0
    return if (diffMinutes <= 60) 1f else 0.35f
}

@Composable
private fun TimelineBlock(
    label: String,
    startMillis: Long,
    endMillis: Long,
    color: Color,
    zone: ZoneId,
    alpha: Float,
    highlighted: Boolean,
    draggable: Boolean,
    onClick: () -> Unit,
    onDragEnd: (deltaMinutes: Int) -> Unit,
    onLockedDragAttempt: () -> Unit
) {
    val dayStart = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
        .atTime(DAY_START_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
    val topMinutes = ((startMillis - dayStart) / 60_000).toInt().coerceAtLeast(0)
    val durationMinutes = ((endMillis - startMillis) / 60_000).toInt().coerceAtLeast(15)
    val topDp = (topMinutes / 60f * HOUR_HEIGHT_DP)
    val heightDp = (durationMinutes / 60f * HOUR_HEIGHT_DP).coerceAtLeast(24f)

    val density = LocalDensity.current
    val hourHeightPx = with(density) { HOUR_HEIGHT_DP.dp.toPx() }

    var dragOffsetPx by remember(startMillis) { mutableFloatStateOf(0f) }
    val blockColor = color.copy(alpha = color.alpha * alpha)

    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .offset {
                IntOffset(0, topDp.dp.roundToPx() + dragOffsetPx.roundToInt())
            }
            .fillMaxWidth()
            .height(heightDp.dp)
            .then(
                if (highlighted) Modifier.border(2.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.small)
                else Modifier
            )
            .background(blockColor, MaterialTheme.shapes.small)
            .then(
                if (draggable) {
                    Modifier.pointerInput(startMillis) {
                        detectTapGestures(onTap = { onClick() })
                    }
                } else {
                    Modifier.pointerInput(startMillis) {
                        detectTapGestures(
                            onTap = { onClick() },
                            onLongPress = { onLockedDragAttempt() }
                        )
                    }
                }
            )
            .then(
                if (draggable) {
                    Modifier.pointerInput(startMillis) {
                        detectVerticalDragGestures(
                            onDragStart = { dragOffsetPx = 0f },
                            onDragEnd = {
                                val dragMinutes = (dragOffsetPx / hourHeightPx * 60f).roundToInt()
                                onDragEnd(dragMinutes)
                                dragOffsetPx = 0f
                            },
                            onDragCancel = { dragOffsetPx = 0f }
                        ) { change, dragAmount ->
                            change.consume()
                            dragOffsetPx += dragAmount
                        }
                    }
                } else {
                    Modifier
                }
            )
            .padding(6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 2)
    }
}
