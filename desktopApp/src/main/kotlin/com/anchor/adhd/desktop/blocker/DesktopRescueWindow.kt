package com.anchor.adhd.desktop.blocker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anchor.adhd.desktop.db.DesktopThoughtCaptureState
import com.anchor.adhd.ui.components.AnchorFocalPlate
import com.anchor.adhd.ui.components.AnchorHairline
import com.anchor.adhd.ui.components.AnchorQuietButton
import com.anchor.adhd.ui.components.AnchorSectionHeader
import com.anchor.adhd.ui.components.AnchorWell
import com.anchor.adhd.ui.components.HarborCtaButton
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens
import kotlinx.coroutines.delay

internal fun rescueOverlayHeadline(reason: String): String =
    when {
        reason.contains("Lecture Shield Active", ignoreCase = true) -> "Lecture shield active"
        reason.contains("Night Curfew", ignoreCase = true) -> "Night curfew engaged"
        reason.contains("Daily Allowance Reached", ignoreCase = true) -> "Daily quota exhausted"
        reason.contains("Out-of-Scope", ignoreCase = true) -> "Focus scope guard"
        else -> "Take a breath"
    }

/**
 * Fullscreen or floating always-on-top rescue window for Windows.
 */
@Composable
fun DesktopRescueOverlay(
    detection: BlockerDetection,
    taskTitle: String,
    remainingMinutes: Int,
    onReturnToFocus: () -> Unit,
    onEmergencyPass: (String) -> Unit,
    thoughtCaptureState: DesktopThoughtCaptureState = DesktopThoughtCaptureState(),
    onThoughtDraftChange: (String) -> Unit = {},
    onCaptureThought: () -> Unit = {},
    onQuickPass: ((String) -> Unit)? = null,
    quickPassesRemaining: Int = 5,
    onCloseTab: (() -> Unit)? = null,
    onSwitchTabGrace: (() -> Unit)? = null,
    timeBankStatus: TimeBankStatus? = null,
    onUnlockWithTimeBank: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    DesktopRescueOverlaySurface(
        detection = detection,
        taskTitle = taskTitle,
        remainingMinutes = remainingMinutes,
        onReturnToFocus = onReturnToFocus,
        onEmergencyPass = onEmergencyPass,
        thoughtCaptureState = thoughtCaptureState,
        onThoughtDraftChange = onThoughtDraftChange,
        onCaptureThought = onCaptureThought,
        onQuickPass = onQuickPass,
        quickPassesRemaining = quickPassesRemaining,
        onCloseTab = onCloseTab,
        onSwitchTabGrace = onSwitchTabGrace,
        timeBankStatus = timeBankStatus,
        onUnlockWithTimeBank = onUnlockWithTimeBank,
        modifier = modifier,
    )
}

@Composable
fun DesktopRescueOverlaySurface(
    detection: BlockerDetection,
    taskTitle: String,
    remainingMinutes: Int,
    onReturnToFocus: () -> Unit,
    onEmergencyPass: (String) -> Unit,
    thoughtCaptureState: DesktopThoughtCaptureState = DesktopThoughtCaptureState(),
    onThoughtDraftChange: (String) -> Unit = {},
    onCaptureThought: () -> Unit = {},
    onQuickPass: ((String) -> Unit)? = null,
    quickPassesRemaining: Int = 5,
    onCloseTab: (() -> Unit)? = null,
    onSwitchTabGrace: (() -> Unit)? = null,
    timeBankStatus: TimeBankStatus? = null,
    onUnlockWithTimeBank: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAnchorTokens.current
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var isHolding by remember { mutableStateOf(false) }
    val prevApp =
        detection.previousWorkContext
            ?.executableName
            ?.removeSuffix(".exe")
            ?.replaceFirstChar { it.uppercase() }
    val prevTitle = detection.previousWorkContext?.windowTitle?.take(45)
    val isLecture = detection.reason.contains("Lecture Shield Active", ignoreCase = true)

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

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(tokens.paper)
                .padding(AnchorSpacing.screenHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        val compact = maxWidth < 520.dp
        Column(
            modifier =
                Modifier
                    .widthIn(max = if (compact) maxWidth else 490.dp)
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnchorFocalPlate {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = tokens.primary, modifier = Modifier.size(32.dp))
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = rescueOverlayHeadline(detection.reason),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = tokens.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = detection.reason,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isLecture) tokens.clayRose else tokens.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (detection.isWebDistraction) {
                    Text(
                        text = "Preserves your other browser tabs. Only closes the distracting tab.",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }

                if (prevApp != null) {
                    Spacer(Modifier.height(10.dp))
                    AnchorWell {
                        Text(
                            "You opened ${detection.appOrSiteName}, but you were just working in:",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.muted,
                        )
                        Text(
                            "$prevApp — \"$prevTitle\"",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = tokens.primary,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                AnchorSectionHeader("Current focus")
                Text(
                    text = taskTitle.ifBlank { "Deep work" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = tokens.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text =
                        if (remainingMinutes > 0) {
                            "$remainingMinutes min remaining"
                        } else {
                            "Standing shield active (no timer)"
                        },
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = tokens.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(18.dp))

                if (detection.isWebDistraction) {
                    HarborCtaButton(
                        onClick = {
                            if (onCloseTab != null) onCloseTab() else onReturnToFocus()
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Close blocked tab (keep other tabs)", style = MaterialTheme.typography.labelLarge)
                    }
                    if (onSwitchTabGrace != null) {
                        Spacer(Modifier.height(10.dp))
                        AnchorQuietButton(
                            label = "Switch tab manually (15s grace)",
                            onClick = onSwitchTabGrace,
                            leadingIcon = { Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    AnchorQuietButton(
                        label = "Minimize entire browser",
                        onClick = onReturnToFocus,
                        outlined = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    val returnLabel =
                        if (prevApp != null) {
                            "Return to $prevApp"
                        } else if (remainingMinutes > 0) {
                            "Return to focus"
                        } else {
                            "Return to task and dismiss"
                        }
                    HarborCtaButton(
                        onClick = onReturnToFocus,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(returnLabel, style = MaterialTheme.typography.labelLarge)
                    }
                }

                if (timeBankStatus != null && !isLecture) {
                    Spacer(Modifier.height(14.dp))
                    AnchorHairline()
                    Spacer(Modifier.height(10.dp))
                    if (timeBankStatus.availableBankedMinutes >= 30) {
                        AnchorWell {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = tokens.sage, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Earned leisure: ${timeBankStatus.availableBankedMinutes}m banked",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                    color = tokens.ink,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            HarborCtaButton(
                                onClick = { onUnlockWithTimeBank?.invoke(30) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                            ) {
                                Text("Unlock for 30m (spend 30m banked)", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    } else {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "Time-bank progress (tier ${timeBankStatus.currentTier})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.ink2,
                                )
                                Text(
                                    "${timeBankStatus.minutesIntoCurrentTier} / ${timeBankStatus.minutesRequiredForNextTier}m studied",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                    color = tokens.primary,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { timeBankStatus.nextTierProgress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = tokens.primary,
                                trackColor = tokens.rule,
                            )
                        }
                    }
                }

                val submitThought = {
                    if (thoughtCaptureState.draft.isNotBlank() && !thoughtCaptureState.isSaving) {
                        onCaptureThought()
                    }
                }

                Spacer(Modifier.height(14.dp))
                AnchorWell {
                    Text("Had a sudden thought? Capture it here to stay focused.", style = MaterialTheme.typography.labelSmall, color = tokens.muted)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = thoughtCaptureState.draft,
                            onValueChange = onThoughtDraftChange,
                            readOnly = thoughtCaptureState.isSaving,
                            placeholder = { Text("e.g. Look up bus times later…", color = tokens.muted) },
                            modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                            shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = tokens.fieldEdge,
                                    unfocusedBorderColor = tokens.rule,
                                    focusedTextColor = tokens.ink,
                                    unfocusedTextColor = tokens.ink,
                                    cursorColor = tokens.primary,
                                ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitThought() }),
                        )
                        Spacer(Modifier.width(8.dp))
                        HarborCtaButton(
                            onClick = submitThought,
                            enabled = thoughtCaptureState.draft.isNotBlank() && !thoughtCaptureState.isSaving,
                            modifier = Modifier.heightIn(min = 40.dp),
                        ) {
                            Text(
                                if (thoughtCaptureState.isSaving) "Saving…" else "Scribe",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    thoughtCaptureState.feedback?.let { feedback ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            feedback,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (thoughtCaptureState.isError) tokens.clayRose else tokens.sage,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }

                var quickDropPhrase by remember { mutableStateOf("") }
                val expectedPhrase = "Just dropping a file and returning to work"
                val isPhraseMatching = quickDropPhrase.trim().equals(expectedPhrase, ignoreCase = true)

                if (onQuickPass != null && !isLecture) {
                    Spacer(Modifier.height(14.dp))
                    AnchorWell {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Quick file drop (2m pass)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = tokens.ink)
                            Text(
                                "$quickPassesRemaining of 5 left today",
                                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                color = if (quickPassesRemaining > 0) tokens.primary else tokens.clayRose,
                            )
                        }
                        if (quickPassesRemaining > 0) {
                            Text("Type to unlock: \"$expectedPhrase\"", style = MaterialTheme.typography.labelSmall, color = tokens.muted)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = quickDropPhrase,
                                    onValueChange = { quickDropPhrase = it },
                                    placeholder = { Text("Type confirmation phrase…", color = tokens.muted) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                                    colors =
                                        OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = tokens.fieldEdge,
                                            unfocusedBorderColor = tokens.rule,
                                            focusedTextColor = tokens.ink,
                                            unfocusedTextColor = tokens.ink,
                                        ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions =
                                        KeyboardActions(
                                            onDone = {
                                                if (isPhraseMatching) {
                                                    onQuickPass(detection.appOrSiteName)
                                                    quickDropPhrase = ""
                                                }
                                            },
                                        ),
                                )
                                Spacer(Modifier.width(8.dp))
                                AnchorQuietButton(
                                    label = "Pass 2m",
                                    onClick = {
                                        if (isPhraseMatching) {
                                            onQuickPass(detection.appOrSiteName)
                                            quickDropPhrase = ""
                                        }
                                    },
                                    enabled = isPhraseMatching,
                                    modifier = Modifier.heightIn(min = 40.dp),
                                )
                            }
                        } else {
                            Text("All 5 quick passes used today. Resets at 4:00 AM.", style = MaterialTheme.typography.labelSmall, color = tokens.muted)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text =
                        if (isLecture) {
                            "Lecture shield is active during class (emergency passes disabled)."
                        } else {
                            "Need urgent access? Hold for 7 seconds."
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!isLecture) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 42.dp)
                                .clip(RoundedCornerShape(AnchorSpacing.radiusChip))
                                .background(if (isHolding) tokens.paper3 else tokens.paper2)
                                .border(1.dp, if (isHolding) tokens.beacon else tokens.rule, RoundedCornerShape(AnchorSpacing.radiusChip))
                                .pointerInput(detection.appOrSiteName) {
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
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(AnchorSpacing.radiusChip)),
                                color = tokens.beacon.copy(alpha = 0.6f),
                                trackColor = tokens.rule.copy(alpha = 0.3f),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (isHolding) tokens.beacon else tokens.muted,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text =
                                    if (holdProgress > 0f) {
                                        "Hold to unlock: ${(holdProgress * 100).toInt()}%"
                                    } else {
                                        "Hold for emergency pass"
                                    },
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.ink2,
                            )
                        }
                    }
                }
            }
        }
    }
}