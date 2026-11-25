package com.anchor.adhd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.anchor.adhd.data.model.CalendarEventEntity
import com.anchor.adhd.data.model.TaskEntity
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens
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
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAnchorTokens.current
    val zone = remember { ZoneId.systemDefault() }
    val totalHours = DAY_END_HOUR - DAY_START_HOUR + 1
    val height = (totalHours * HOUR_HEIGHT_DP).dp
    val formatter = remember { DateTimeFormatter.ofPattern("h a", Locale.US) }
    val dayStartMillis = date.atTime(DAY_START_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
    val nowMinutesFromStart = ((nowMillis - dayStartMillis) / 60_000).toInt()
    val nowLineDp = (nowMinutesFromStart / 60f * HOUR_HEIGHT_DP).coerceIn(0f, (totalHours * HOUR_HEIGHT_DP).toFloat())

    val allDayTasks = scheduled.filter { it.scheduledStartMillis == null }
    val dueTodayList = scheduled.filter { task ->
        task.dueAtMillis != null && !task.isCompleted && run {
            val dueLocalDate = Instant.ofEpochMilli(task.dueAtMillis).atZone(zone).toLocalDate()
            dueLocalDate == date
        }
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Drag blocks to reschedule in 15 min steps",
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = tokens.muted,
        )

        if (allDayTasks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AnchorSectionHeader(
                    title = "All-day tasks",
                    trailingLabel = "${allDayTasks.size}",
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    allDayTasks.forEach { task ->
                        AnchorChip(
                            label = task.title,
                            selected = task.id == spotlightTaskId,
                            onClick = { onTaskClick(task) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            leadingIcon = if (task.dueAtMillis != null) {
                                {
                                    Icon(
                                        imageVector = Icons.Outlined.Flag,
                                        contentDescription = "Has due date",
                                        tint = tokens.clayRose,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.width(52.dp)) {
                for (h in DAY_START_HOUR..DAY_END_HOUR) {
                    Box(Modifier.height(HOUR_HEIGHT_DP.dp).padding(end = 8.dp)) {
                        Text(
                            text = date.atTime(h, 0).format(formatter),
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = tokens.muted,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            val gridShape = RoundedCornerShape(AnchorSpacing.radiusWell)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .clip(gridShape)
                    .background(tokens.paper2),
            ) {
                for (h in DAY_START_HOUR..DAY_END_HOUR) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .offset(y = ((h - DAY_START_HOUR) * HOUR_HEIGHT_DP).dp)
                            .background(tokens.rule),
                    )
                }

                calendarEvents.forEach { event ->
                    val isNear = isWithinActiveWindow(event.startMillis, nowMillis)
                    TimelineBlock(
                        label = event.title,
                        startMillis = event.startMillis,
                        endMillis = event.endMillis,
                        containerColor = if (isNear) tokens.paper3 else tokens.surface,
                        borderColor = tokens.rule,
                        contentColor = if (isNear) tokens.ink else tokens.ink2,
                        leadingIcon = Icons.Outlined.Event,
                        leadingIconTint = tokens.muted,
                        trailingIcon = null,
                        trailingIconTint = tokens.muted,
                        zone = zone,
                        highlighted = false,
                        draggable = false,
                        onClick = { onCalendarEventClick(event) },
                        onDragEnd = {},
                        onLockedDragAttempt = {},
                    )
                }

                scheduled.forEach { task ->
                    val start = task.scheduledStartMillis ?: return@forEach
                    val end = start + task.durationMinutes * 60_000L
                    val isLocked = lockedTaskId != null && task.id == lockedTaskId
                    val isSpotlight = spotlightTaskId != null && task.id == spotlightTaskId
                    val isNear = isSpotlight || isWithinActiveWindow(start, nowMillis)

                    val containerColor = when {
                        isSpotlight -> tokens.beaconPlate
                        isNear -> tokens.primarySoft
                        else -> tokens.surface
                    }
                    val borderColor = when {
                        isSpotlight -> tokens.beaconPlateCta
                        isNear -> tokens.primary
                        else -> tokens.rule
                    }
                    val contentColor = when {
                        isSpotlight -> tokens.beaconPlateInk
                        isNear -> tokens.ink
                        else -> tokens.ink2
                    }
                    val statusIcon = when {
                        isLocked -> Icons.Outlined.Lock
                        task.dueAtMillis != null -> Icons.Outlined.Flag
                        else -> null
                    }
                    val statusTint = when {
                        isLocked && isSpotlight -> tokens.beaconPlateCta
                        isLocked -> tokens.muted
                        task.dueAtMillis != null && isSpotlight -> tokens.beaconPlateCta
                        else -> tokens.clayRose
                    }

                    TimelineBlock(
                        label = task.title,
                        startMillis = start,
                        endMillis = end,
                        containerColor = containerColor,
                        borderColor = borderColor,
                        contentColor = contentColor,
                        leadingIcon = null,
                        leadingIconTint = contentColor,
                        trailingIcon = statusIcon,
                        trailingIconTint = statusTint,
                        zone = zone,
                        highlighted = isSpotlight,
                        draggable = !isLocked,
                        onClick = { onTaskClick(task) },
                        onDragEnd = { deltaMinutes ->
                            val dayEndMillis = date.atTime(DAY_END_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
                            val maxStart = (dayEndMillis - task.durationMinutes * 60_000L).coerceAtLeast(dayStartMillis)
                            val snapped = (deltaMinutes / 15f).roundToInt() * 15
                            val calculatedStart = start + snapped * 60_000L
                            val newStart = calculatedStart.coerceIn(dayStartMillis, maxStart)
                            onTaskReschedule(task, newStart)
                        },
                        onLockedDragAttempt = onLockedDragAttempt,
                    )
                }

                if (nowMinutesFromStart in 0..(totalHours * 60)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (nowLineDp - 3f).coerceAtLeast(0f).dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(tokens.beacon),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(2.dp)
                                .background(tokens.beacon),
                        )
                    }
                }
            }
        }

        if (dueTodayList.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AnchorSectionHeader(
                    title = "Due today",
                    trailingLabel = "${dueTodayList.size}",
                )
                dueTodayList.forEachIndexed { index, task ->
                    AnchorListRow(
                        title = task.title,
                        trailingText = "${task.durationMinutes} min",
                        onClick = { onTaskClick(task) },
                        minHeight = 48.dp,
                        showDivider = index < dueTodayList.lastIndex,
                        leading = {
                            Icon(
                                imageVector = Icons.Outlined.Flag,
                                contentDescription = null,
                                tint = tokens.clayRose,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(72.dp))
    }
}

private fun isWithinActiveWindow(startMillis: Long, nowMillis: Long): Boolean {
    val diffMinutes = abs(startMillis - nowMillis) / 60_000.0
    return diffMinutes <= 60.0
}

@Composable
private fun TimelineBlock(
    label: String,
    startMillis: Long,
    endMillis: Long,
    containerColor: Color,
    borderColor: Color,
    contentColor: Color,
    leadingIcon: ImageVector?,
    leadingIconTint: Color,
    trailingIcon: ImageVector?,
    trailingIconTint: Color,
    zone: ZoneId,
    highlighted: Boolean,
    draggable: Boolean,
    onClick: () -> Unit,
    onDragEnd: (deltaMinutes: Int) -> Unit,
    onLockedDragAttempt: () -> Unit,
) {
    val dayStart = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
        .atTime(DAY_START_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
    val topMinutes = ((startMillis - dayStart) / 60_000).toInt().coerceAtLeast(0)
    val durationMinutes = ((endMillis - startMillis) / 60_000).toInt().coerceAtLeast(15)
    val topDp = topMinutes / 60f * HOUR_HEIGHT_DP
    val heightDp = (durationMinutes / 60f * HOUR_HEIGHT_DP).coerceAtLeast(28f)

    val density = LocalDensity.current
    val hourHeightPx = with(density) { HOUR_HEIGHT_DP.dp.toPx() }
    var dragOffsetPx by remember(startMillis) { mutableFloatStateOf(0f) }
    val blockShape = RoundedCornerShape(AnchorSpacing.radiusChip)

    Row(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            // Raw tap gestures are invisible to screen readers; expose the
            // same action through semantics (drag rescheduling stays
            // touch-only, with the detail sheet as the keyboard/AT path).
            .semantics(mergeDescendants = true) {
                onClick(label = "Open details", action = { onClick(); true })
            }
            .offset {
                IntOffset(0, topDp.dp.roundToPx() + dragOffsetPx.roundToInt())
            }
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(blockShape)
            .background(containerColor, blockShape)
            .border(
                width = if (highlighted) 1.5.dp else 1.dp,
                color = borderColor,
                shape = blockShape,
            )
            .then(
                if (draggable) {
                    Modifier.pointerInput(startMillis) {
                        detectTapGestures(onTap = { onClick() })
                    }
                } else {
                    Modifier.pointerInput(startMillis) {
                        detectTapGestures(
                            onTap = { onClick() },
                            onLongPress = { onLockedDragAttempt() },
                        )
                    }
                },
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
                            onDragCancel = { dragOffsetPx = 0f },
                        ) { change, dragAmount ->
                            change.consume()
                            dragOffsetPx += dragAmount
                        }
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = leadingIconTint,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
            ),
            color = contentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = trailingIconTint,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
