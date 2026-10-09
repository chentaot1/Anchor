package com.anchor.adhd.desktop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.adhd.desktop.db.DesktopTask
import com.anchor.adhd.desktop.theme.AnchorColors
import com.anchor.adhd.desktop.theme.AnchorSpacing

/**
 * Living Beacon NOW Card for Windows 11.
 * Sea-glass surface, ambient amber halo back-glow, tracked N O W badge with beacon dot.
 */
@Composable
fun DesktopNowCard(
    currentTask: DesktopTask?,
    isFocusActive: Boolean,
    remainingSeconds: Int,
    durationMinutes: Int,
    onStartFocus: () -> Unit,
    onOpenDurationPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "now_beacon")
    val beaconPulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "beacon_dot",
    )

    val progress =
        if (isFocusActive) {
            val totalSecs = (durationMinutes * 60).coerceAtLeast(1)
            ((totalSecs - remainingSeconds).toFloat() / totalSecs).coerceIn(0f, 1f)
        } else {
            0f
        }

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AnchorSpacing.radiusCard))
                .border(
                    1.dp,
                    AnchorColors.HarborPrimary.copy(alpha = 0.3f),
                    RoundedCornerShape(AnchorSpacing.radiusCard),
                ),
        shape = RoundedCornerShape(AnchorSpacing.radiusCard),
        color = Color(0xFF141F32).copy(alpha = 0.95f),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            // Header Row: Tracked N O W badge + Duration Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(AnchorColors.HarborBeaconAmber.copy(alpha = beaconPulse)),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "N  O  W",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = AnchorColors.HarborBeaconAmber,
                    )
                }

                // Custom Duration Chip (e.g. 25m ✎)
                Surface(
                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.clickable { onOpenDurationPicker() },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = AnchorColors.HarborPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${durationMinutes}m ✎",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Task Title
            Text(
                text = currentTask?.title ?: "Select or create a task to anchor",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Tide Progress Bar (Active during focus)
            if (isFocusActive) {
                val mins = remainingSeconds / 60
                val secs = remainingSeconds % 60
                val timeStr = String.format("%02d:%02d", mins, secs)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Focus Tide",
                        style = MaterialTheme.typography.bodySmall,
                        color = AnchorColors.HarborPrimary,
                    )
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    color = AnchorColors.HarborPrimary,
                    trackColor = Color.White.copy(alpha = 0.1f),
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Primary Start Button
            Button(
                onClick = onStartFocus,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = if (isFocusActive) AnchorColors.HarborDock else AnchorColors.HarborPrimary,
                        contentColor = if (isFocusActive) Color.White else Color(0xFF002A4A),
                    ),
            ) {
                Icon(
                    imageVector = if (isFocusActive) Icons.Default.Anchor else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isFocusActive) "Focus Active — Open Timer" else "Anchor Now (${durationMinutes}m)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
