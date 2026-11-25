package com.anchor.adhd.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.adhd.desktop.db.DesktopTask
import com.anchor.adhd.ui.components.AnchorBeaconPlate
import com.anchor.adhd.ui.components.AnchorQuietButton
import com.anchor.adhd.ui.components.HarborCtaButton
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens

/**
 * Committed Deep-Harbor Next Action focal plate for desktop Home.
 * Adapts across Morning Harbor (Light) and Night Harbor (Dark) via [LocalAnchorTokens]
 * with zero glows, sheens, or infinite loops.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesktopNowCard(
    currentTask: DesktopTask?,
    isFocusActive: Boolean,
    remainingSeconds: Int,
    durationMinutes: Int,
    onStartFocus: () -> Unit,
    onOpenDurationPicker: () -> Unit,
    isWorkPhase: Boolean = true,
    hasOpenFocusSession: Boolean = false,
    onBreakDownWithCaptain: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAnchorTokens.current
    val safeRemainingSeconds = remainingSeconds.coerceAtLeast(0)
    val formattedRemaining = String.format("%02d:%02d", safeRemainingSeconds / 60, safeRemainingSeconds % 60)
    val showProgress = isFocusActive || hasOpenFocusSession || !isWorkPhase
    val progress =
        if (showProgress) {
            val totalSecs = (durationMinutes * 60).coerceAtLeast(1)
            ((totalSecs - safeRemainingSeconds).toFloat() / totalSecs).coerceIn(0f, 1f)
        } else {
            0f
        }
    val statusLabel =
        when {
            isFocusActive && isWorkPhase -> "Focus active"
            isFocusActive && !isWorkPhase -> "Break active"
            hasOpenFocusSession || !isWorkPhase -> if (isWorkPhase) "Focus paused" else "Break paused"
            else -> "Next action"
        }
    val statusAccent = if (!isWorkPhase) tokens.sage else tokens.beaconPlateCta
    val firstStep =
        currentTask
            ?.notes
            ?.lineSequence()
            ?.map(String::trim)
            ?.firstOrNull { it.isNotEmpty() }

    AnchorBeaconPlate(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusAccent),
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = statusAccent,
                    maxLines = 1,
                )
            }

            Surface(
                shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                color = tokens.beaconPlatePill,
                border = BorderStroke(1.dp, tokens.beaconPlateBorder),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(AnchorSpacing.radiusChip))
                        .clickable(role = Role.Button, onClickLabel = "Choose focus duration") {
                            onOpenDurationPicker()
                        },
            ) {
                Row(
                    modifier = Modifier.heightIn(min = 32.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Timer,
                        contentDescription = null,
                        tint = tokens.beaconPlateInk2,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "${durationMinutes}m",
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum",
                            ),
                        color = tokens.beaconPlateInk,
                        maxLines = 1,
                    )
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = tokens.beaconPlateInk2,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }

        Text(
            text = currentTask?.title ?: "Select or create a task to begin",
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontSize = 19.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Bold,
                ),
            color = tokens.beaconPlateInk,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        val supportingLine =
            when {
                firstStep != null -> firstStep
                currentTask == null -> "Pick a task from Today's plan or capture a thought to anchor."
                else -> null
            }
        if (supportingLine != null) {
            Text(
                text = supportingLine,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.beaconPlateInk2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (showProgress) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isWorkPhase) "Focus tide" else "Break tide",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.beaconPlateInk2,
                )
                Text(
                    text = formattedRemaining,
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontFeatureSettings = "tnum",
                        ),
                    color = tokens.beaconPlateInk,
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(AnchorSpacing.radiusPill)),
                color = statusAccent,
                trackColor = tokens.beaconPlatePill,
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HarborCtaButton(
                onClick = onStartFocus,
                onBeaconPlate = true,
                modifier = Modifier.align(Alignment.CenterVertically).heightIn(min = 40.dp),
            ) {
                Icon(
                    imageVector = if (isFocusActive) Icons.Outlined.Timer else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text =
                        when {
                            isFocusActive -> if (isWorkPhase) "Open focus timer" else "Open break timer"
                            hasOpenFocusSession || !isWorkPhase ->
                                if (isWorkPhase) "Resume focus ($formattedRemaining)" else "Resume break ($formattedRemaining)"
                            else -> "Start $durationMinutes min focus"
                        },
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (onBreakDownWithCaptain != null) {
                AnchorQuietButton(
                    label = if (currentTask != null) "Break down with Captain" else "Ask Captain",
                    onClick = onBreakDownWithCaptain,
                    outlined = false,
                    tint = tokens.beaconPlateCaptain,
                    modifier = Modifier.align(Alignment.CenterVertically),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }
}
