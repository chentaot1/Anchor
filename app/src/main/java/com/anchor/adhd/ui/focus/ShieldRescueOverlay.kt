package com.anchor.adhd.ui.focus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.components.AnchorFocalPlate
import com.anchor.adhd.ui.components.AnchorQuietButton
import com.anchor.adhd.ui.components.AnchorWell
import com.anchor.adhd.ui.components.HarborCtaButton
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Calm rescue overlay displayed over blocked apps — bridges back to focus with a single CTA.
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
    onSpendLeisure: (() -> Unit)? = null,
) {
  BackHandler { onReturnToFocus() }

    val tokens = LocalAnchorTokens.current
    val haptic = LocalHapticFeedback.current
    var holdProgress by remember { mutableFloatStateOf(0f) }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(tokens.paper)
                .padding(AnchorSpacing.screenHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnchorFocalPlate {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = tokens.primary,
                        modifier = Modifier.size(40.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "You drifted for a moment",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = protectionReason ?: "We're still in deep focus on",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                val displayTask = taskTitle?.trim()?.takeIf { it.isNotBlank() } ?: "your focus target"
                Text(
                    text = displayTask,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = tokens.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text =
                        if (protectionReason == null) {
                            "$remainingMinutes minutes left"
                        } else {
                            "$bankedMinutes minutes of leisure banked"
                        },
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = tokens.primary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(24.dp))

                HarborCtaButton(
                    onClick = onReturnToFocus,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Return to focus", style = MaterialTheme.typography.labelLarge)
                }

                if (onSpendLeisure != null && bankedMinutes >= 15) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AnchorQuietButton(
                        label = "Use 15 minutes of earned leisure",
                        onClick = onSpendLeisure,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    )
                }

                if (emergencyPassAllowed) {
                    Spacer(modifier = Modifier.height(20.dp))
                    AnchorWell(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = tokens.muted,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Emergency 60s pass",
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.muted,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { holdProgress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = tokens.captain,
                            trackColor = tokens.rule,
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(AnchorSpacing.radiusChip))
                                    .pointerInput(blockedPackage) {
                                        coroutineScope {
                                            val gestureScope = this
                                            awaitEachGesture {
                                                awaitFirstDown(requireUnconsumed = false)
                                                var completed = false
                                                val holdJob =
                                                    gestureScope.launch {
                                                        for (step in 1..70) {
                                                            delay(100)
                                                            holdProgress = step / 70f
                                                            if (step % 10 == 0 && step < 70) {
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            }
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
                                                    if (!completed) holdProgress = 0f
                                                }
                                            }
                                        }
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text =
                                    if (holdProgress <= 0f) {
                                        "Hold for 7s to unlock temporarily"
                                    } else {
                                        "Holding… ${(7 - (holdProgress * 7)).toInt() + 1}s"
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (holdProgress > 0f) tokens.captain else tokens.muted,
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Emergency passes cannot bypass this protection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.clayRose,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}