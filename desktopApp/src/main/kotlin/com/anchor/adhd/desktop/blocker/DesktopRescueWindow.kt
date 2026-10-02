package com.anchor.adhd.desktop.blocker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.adhd.desktop.theme.AnchorColors
import com.anchor.adhd.desktop.theme.AnchorSpacing
import kotlinx.coroutines.delay

/**
 * Fullscreen or floating always-on-top Gentle Rescue Window for Windows 11.
 * Appears whenever an active distraction (Discord, YouTube, Reddit, etc.) is detected during a focus session.
 */
@Composable
fun DesktopRescueOverlay(
    detection: BlockerDetection,
    taskTitle: String,
    remainingMinutes: Int,
    onReturnToFocus: () -> Unit,
    onEmergencyPass: (String) -> Unit,
    onQuickThoughtScribe: (String) -> Unit = {},
    onQuickPass: ((String) -> Unit)? = null,
    quickPassesRemaining: Int = 5,
    onCloseTab: (() -> Unit)? = null,
    onSwitchTabGrace: (() -> Unit)? = null,
    timeBankStatus: TimeBankStatus? = null,
    onUnlockWithTimeBank: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var isHolding by remember { mutableStateOf(false) }
    var thoughtText by remember { mutableStateOf("") }

    val prevApp =
        detection.previousWorkContext
            ?.executableName
            ?.removeSuffix(".exe")
            ?.replaceFirstChar { it.uppercase() }
    val prevTitle = detection.previousWorkContext?.windowTitle?.take(45)

    LaunchedEffect(isHolding) {
        if (isHolding) {
            val startTime = System.currentTimeMillis()
            val requiredHoldMillis = 7000L
            while (isHolding) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / requiredHoldMillis).coerceIn(0f, 1f)
                holdProgress = progress
                if (progress >= 1f) {
                    holdProgress = 0f
                    isHolding = false
                    onEmergencyPass(detection.appOrSiteName)
                    break
                }
                delay(16)
            }
        } else {
            holdProgress = 0f
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(AnchorColors.HarborBackground.copy(alpha = 0.95f))
                .padding(AnchorSpacing.screenHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .width(490.dp)
                    .clip(RoundedCornerShape(AnchorSpacing.radiusScene))
                    .background(AnchorColors.HarborDock.copy(alpha = 0.95f))
                    .border(
                        1.dp,
                        AnchorColors.HarborMist,
                        RoundedCornerShape(AnchorSpacing.radiusScene),
                    ).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Calm Shield Icon
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(AnchorColors.HarborMist),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = AnchorColors.HarborPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val isLecture = detection.reason.contains("Lecture Shield Active", ignoreCase = true)
            val isCurfew = detection.reason.contains("Night Curfew", ignoreCase = true)
            val isQuota = detection.reason.contains("Daily Allowance Reached", ignoreCase = true)
            val isScope = detection.reason.contains("Out-of-Scope", ignoreCase = true)

            val headline = when {
                isLecture -> "⚓ Lecture Shield Active"
                isCurfew -> "🌙 Night Curfew Engaged"
                isQuota -> "⏳ Daily Quota Exhausted"
                isScope -> "🧭 Focus Scope Guard"
                else -> "Take a breath..."
            }

            // Gentle Header & Context Bridge
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                color = if (isLecture) Color(0xFF3B1818) else Color.White.copy(alpha = 0.06f),
                border = BorderStroke(1.dp, if (isLecture) AnchorColors.HarborAction else Color.White.copy(alpha = 0.12f)),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = detection.reason,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isLecture) AnchorColors.HarborAction else AnchorColors.HarborPrimary,
                        textAlign = TextAlign.Center,
                    )
                    if (detection.isWebDistraction) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Preserves your other browser tabs • Only closes the distracting tab",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.55f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            if (prevApp != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                    color = Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "You opened ${detection.appOrSiteName}, but you were just working in:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$prevApp — \"$prevTitle\"",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = AnchorColors.HarborPrimary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Anchored Task Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                color = AnchorColors.HarborBackground.copy(alpha = 0.8f),
                border = BorderStroke(1.dp, AnchorColors.HarborPrimary.copy(alpha = 0.25f)),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "CURRENT ANCHOR",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp,
                        color = AnchorColors.HarborAction,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = taskTitle.ifBlank { "Deep Work" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (remainingMinutes > 0) "$remainingMinutes min remaining" else "Standing Shield Active (No Timer)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons: Specialized for Web Distraction (preserve tabs) vs App Distraction
            if (detection.isWebDistraction) {
                // 1. Primary Action: Close only the distracting tab
                Button(
                    onClick = {
                        if (onCloseTab != null) {
                            onCloseTab()
                        } else {
                            onReturnToFocus()
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = AnchorColors.HarborPrimary,
                            contentColor = Color(0xFF002A4A),
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Close Blocked Tab (Keeps Other Tabs Open)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // 2. Secondary Action: 15s Grace Window to manually switch tabs
                if (onSwitchTabGrace != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onSwitchTabGrace,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                        border = BorderStroke(1.dp, AnchorColors.HarborPrimary.copy(alpha = 0.5f)),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = AnchorColors.HarborPrimary,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Switch Tab Manually (15s Grace Window)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // 3. Tertiary: Minimize entire browser
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onReturnToFocus() }
                            .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Or minimize entire browser",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                // Desktop App Distraction: Standard minimize & resume
                val returnLabel =
                    if (prevApp != null) {
                        "⚡ Return to $prevApp & Resume"
                    } else if (remainingMinutes > 0) {
                        "Return to Focus"
                    } else {
                        "Return to Task & Dismiss"
                    }
                Button(
                    onClick = onReturnToFocus,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = AnchorColors.HarborPrimary,
                            contentColor = Color(0xFF002A4A),
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = returnLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Scaled Time-Bank (Earned Leisure Break from study)
            if (timeBankStatus != null && !isLecture) {
                if (timeBankStatus.availableBankedMinutes >= 30) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                        color = Color(0xFF133324),
                        border = BorderStroke(1.dp, AnchorColors.HarborGrowth),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = AnchorColors.HarborFoliage,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Earned Leisure Available: ${timeBankStatus.availableBankedMinutes}m Banked!",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onUnlockWithTimeBank?.invoke(30) },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AnchorColors.HarborGrowth,
                                    contentColor = Color(0xFF002A16),
                                ),
                            ) {
                                Text("Unlock for 30m (Spend 30m Banked)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                        color = Color.White.copy(alpha = 0.04f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Time-Bank Progress (Tier ${timeBankStatus.currentTier}):",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.85f),
                                )
                                Text(
                                    text = "${timeBankStatus.minutesIntoCurrentTier} / ${timeBankStatus.minutesRequiredForNextTier}m studied",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AnchorColors.HarborPrimary,
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { timeBankStatus.nextTierProgress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = AnchorColors.HarborPrimary,
                                trackColor = Color.White.copy(alpha = 0.1f),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            // Quick Thought Scribe (Dopamine dump: record thought to inbox without opening distraction)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                color = Color.White.copy(alpha = 0.04f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Had a sudden thought? Dump it here to stay focused:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = thoughtText,
                            onValueChange = { thoughtText = it },
                            placeholder = {
                                Text(
                                    "e.g. Look up bus times, reply to email later...",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.35f),
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AnchorColors.HarborPrimary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                ),
                            singleLine = true,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (thoughtText.isNotBlank()) {
                                    onQuickThoughtScribe(thoughtText)
                                    thoughtText = ""
                                }
                            },
                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = AnchorColors.HarborAction,
                                    contentColor = Color.White,
                                ),
                        ) {
                            Text("Scribe", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            var quickDropPhrase by remember { mutableStateOf("") }
            val expectedPhrase = "Just dropping a file and returning to work"
            val isPhraseMatching = quickDropPhrase.trim().equals(expectedPhrase, ignoreCase = true)

            // Quick File Drop (2m utility trip, e.g. for Discord)
            if (onQuickPass != null && !isLecture) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                    color = Color.White.copy(alpha = 0.04f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Quick File Drop (2m pass):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                            Text(
                                text = "$quickPassesRemaining of 5 left today",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (quickPassesRemaining > 0) AnchorColors.HarborPrimary else Color.Red.copy(alpha = 0.7f),
                            )
                        }

                        if (quickPassesRemaining > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Type to unlock: \"$expectedPhrase\"",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.45f),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = quickDropPhrase,
                                    onValueChange = { quickDropPhrase = it },
                                    placeholder = {
                                        Text(
                                            "Type confirmation phrase...",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.35f),
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                    colors =
                                        OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = AnchorColors.HarborPrimary,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                        ),
                                    singleLine = true,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (isPhraseMatching) {
                                            onQuickPass(detection.appOrSiteName)
                                            quickDropPhrase = ""
                                        }
                                    },
                                    enabled = isPhraseMatching,
                                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = AnchorColors.HarborPrimary,
                                            contentColor = Color(0xFF002A4A),
                                        ),
                                ) {
                                    Text("Pass 2m", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "All 5 quick passes used for today. Resets at 4:00 AM.",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 7-Second Emergency Pass to disarm impulsivity
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (isLecture) "Lecture Shield is strictly active during class." else "Need urgent access? Hold for 7 seconds:",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(AnchorSpacing.radiusPill))
                            .background(if (isHolding) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f))
                            .border(
                                1.dp,
                                if (isHolding) AnchorColors.HarborAction else Color.White.copy(alpha = 0.15f),
                                RoundedCornerShape(AnchorSpacing.radiusPill),
                            ).pointerInput(detection.appOrSiteName) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    isHolding = true
                                    try {
                                        waitForUpOrCancellation()
                                    } finally {
                                        isHolding = false
                                    }
                                }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    if (holdProgress > 0f) {
                        LinearProgressIndicator(
                            progress = { holdProgress },
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(AnchorSpacing.radiusPill)),
                            color = AnchorColors.HarborAction.copy(alpha = 0.6f),
                            trackColor = Color.Transparent,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (isHolding) AnchorColors.HarborAction else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (holdProgress > 0f) "Hold to unlock: ${(holdProgress * 100).toInt()}%" else "Hold for Emergency Pass",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
