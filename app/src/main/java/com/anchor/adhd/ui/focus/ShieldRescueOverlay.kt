package com.anchor.adhd.ui.focus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp
import com.anchor.adhd.ui.theme.AnchorColors
import com.anchor.adhd.ui.theme.AnchorSpacing

/**
 * Serene harbor-mist rescue overlay displayed over blocked apps.
 * Catches drifting attention without harsh alarms, providing a direct bridge back to focus
 * and a 7-second continuous hold emergency pass to defeat impulsivity.
 */
@Composable
fun ShieldRescueOverlay(
    blockedPackage: String,
    taskTitle: String?,
    remainingMinutes: Int,
    onReturnToFocus: () -> Unit,
    onEmergencyPass: (String) -> Unit,
    modifier: Modifier = Modifier,
    protectionReason: String? = null,
    emergencyPassAllowed: Boolean = true,
    bankedMinutes: Int = 0,
    onSpendLeisure: (() -> Unit)? = null
) {
    // Back navigation trap: pressing back routes directly to focus
    BackHandler {
        onReturnToFocus()
    }

    val haptic = LocalHapticFeedback.current
    var holdProgress by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AnchorColors.HarborBackground.copy(alpha = 0.95f))
            .padding(AnchorSpacing.screenHorizontal),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(AnchorSpacing.radiusScene))
                .background(AnchorColors.HarborDock.copy(alpha = 0.9f))
                .border(
                    1.dp,
                    AnchorColors.HarborMist,
                    RoundedCornerShape(AnchorSpacing.radiusScene)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Calm Shield Icon Header
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AnchorColors.HarborMist),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = AnchorColors.HarborPrimary,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Hey — you drifted for a second.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AnchorColors.HarborAction,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = protectionReason ?: "We're still in Deep Focus on",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            val displayTask = taskTitle?.trim()?.takeIf { it.isNotBlank() } ?: "your focus target"
            Text(
                text = displayTask,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (protectionReason == null) "$remainingMinutes minutes left" else "$bankedMinutes minutes of leisure banked",
                style = MaterialTheme.typography.titleMedium,
                color = AnchorColors.HarborPrimary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Primary CTA: Return to Focus
            Button(
                onClick = onReturnToFocus,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AnchorColors.HarborAction,
                    contentColor = Color.Black
                )
            ) {
                Icon(
                    Icons.Default.Anchor,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Return to Focus",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(22.dp))
            if (onSpendLeisure != null && bankedMinutes >= 15) {
                Button(onClick = onSpendLeisure, modifier = Modifier.fillMaxWidth()) { Text("Use 15 minutes of earned leisure") }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 7-Second Friction Lock (Emergency Pass)
            if (emergencyPassAllowed) {
            Surface(
                shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                color = AnchorColors.HarborSkyNight.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, AnchorColors.HarborMist),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Emergency 60s Pass",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { holdProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = AnchorColors.HarborAi,
                        trackColor = AnchorColors.HarborDock
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Gesture target box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (holdProgress > 0f) AnchorColors.HarborAi.copy(alpha = 0.22f)
                                else AnchorColors.HarborDock.copy(alpha = 0.5f)
                            )
                            .border(
                                1.dp,
                                if (holdProgress > 0f) AnchorColors.HarborAi else AnchorColors.HarborMist,
                                RoundedCornerShape(10.dp)
                            )
                            .pointerInput(blockedPackage) {
                                coroutineScope {
                                val gestureScope = this
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    var completed = false
                                    val holdJob = gestureScope.launch {
                                        for (step in 1..70) {
                                            delay(100)
                                            holdProgress = step / 70f
                                            if (step % 10 == 0 && step < 70) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        completed = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onEmergencyPass(blockedPackage)
                                    }
                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Main)
                                            if (event.changes.any { it.isConsumed }) break
                                            if (event.changes.all { !it.pressed }) break

                                            if (completed) break
                                        }
                                    } finally {
                                        holdJob.cancel()
                                        if (!completed) {
                                            holdProgress = 0f
                                        }
                                    }
                                }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (holdProgress <= 0f) "Hold for 7s to unlock temporarily"
                                   else "Holding... ${(7 - (holdProgress * 7)).toInt() + 1}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (holdProgress > 0f) AnchorColors.HarborAi else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            } else {
                Text("Emergency passes cannot bypass this protection.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
