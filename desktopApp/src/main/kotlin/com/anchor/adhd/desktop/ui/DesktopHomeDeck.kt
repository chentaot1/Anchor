package com.anchor.adhd.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.adhd.desktop.ai.ResetType
import com.anchor.adhd.desktop.db.DesktopFunLink
import com.anchor.adhd.desktop.db.DesktopTask
import com.anchor.adhd.desktop.theme.AnchorColors
import com.anchor.adhd.desktop.theme.AnchorSpacing

/**
 * Evidence-Based ADHD Desktop Command Deck (Two-Column Bento Layout).
 *
 * Implements clinical ADHD principles by Dr. Russell Barkley & Dr. Thomas Brown:
 * 1. Point of Performance Action Center (Left Column ~58% width):
 *    - The NOW Beacon Station with externalized time (tide progress).
 *    - Low-friction Momentum Starters (3m Spark, 15m Sprint, 25m Focus, 50m Flow).
 *    - Instant Thought Capture Bar (mental exhaust valve for impulse offloading).
 *
 * 2. Externalized Working Memory & Dopamine Reinforcement (Right Column ~42% width):
 *    - Today's Momentum Hub (focus minutes today, deflections deflected, streak).
 *    - "Up Next" Rule of 3 Station (visible working memory without cognitive overload).
 *    - Brain Breaks (Trojan dopamine reset links).
 */
@Composable
fun DesktopHomeDeck(
    currentTask: DesktopTask?,
    tasks: List<DesktopTask>,
    funLinks: List<DesktopFunLink>,
    isFocusActive: Boolean,
    remainingSeconds: Int,
    durationMinutes: Int,
    todayFocusMinutes: Int,
    deflectedCount: Int,
    streakDays: Int,
    onStartFocus: () -> Unit,
    onOpenDurationPicker: () -> Unit,
    onLaunchMomentumPreset: (Int) -> Unit,
    onCaptureThought: (String) -> Unit,
    onMakeTaskNow: (Long) -> Unit,
    onToggleTaskCompleted: (Long) -> Unit,
    onNavigateToPlan: () -> Unit,
    onStartFocusWithTask: (taskTitle: String, minutes: Int) -> Unit = { _, _ -> },
    onSaveTasksToInbox: (List<String>) -> Unit = {},
    onApplyBlockerRule: (target: String, enable: Boolean) -> Unit = { _, _ -> },
    onResetData: ((ResetType) -> Unit)? = null,
    onNavigateToSyllabus: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var thoughtInput by remember { mutableStateOf("") }
    val leftScrollState = rememberScrollState()
    val rightScrollState = rememberScrollState()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Master Universal Smart AI Omni-Bar
        DesktopSmartOmniBar(
            onStartFocusWithTask = onStartFocusWithTask,
            onSaveTasksToInbox = onSaveTasksToInbox,
            onApplyBlockerRule = onApplyBlockerRule,
            onResetData = onResetData,
            onNavigateToSyllabus = onNavigateToSyllabus,
        )

        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // ==========================================
            // LEFT COLUMN: POINT OF PERFORMANCE ACTION CENTER (~58%)
            // ==========================================
            Column(
                modifier =
                    Modifier
                        .weight(1.15f)
                        .fillMaxSize()
                        .verticalScroll(leftScrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1. The NOW Beacon Card
                DesktopNowCard(
                    currentTask = currentTask,
                    isFocusActive = isFocusActive,
                    remainingSeconds = remainingSeconds,
                    durationMinutes = durationMinutes,
                    onStartFocus = onStartFocus,
                    onOpenDurationPicker = onOpenDurationPicker,
                )

                // 2. Low-Friction Momentum Starters (Activation Energy Reducers)
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AnchorSpacing.radiusCard))
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.12f),
                                RoundedCornerShape(AnchorSpacing.radiusCard),
                            ),
                    shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                    color = AnchorColors.HarborDock.copy(alpha = 0.85f),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = AnchorColors.HarborFoliage,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "MOMENTUM SPARKS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp,
                                    color = AnchorColors.HarborFoliage,
                                )
                            }
                            Text(
                                text = "1-click low-friction start",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MomentumPresetChip(
                                label = "3m Spark",
                                subtitle = "Micro-start",
                                color = AnchorColors.HarborGrowth,
                                accent = AnchorColors.HarborFoliage,
                                modifier = Modifier.weight(1f),
                                onClick = { onLaunchMomentumPreset(3) },
                            )

                            MomentumPresetChip(
                                label = "15m Sprint",
                                subtitle = "Quick blitz",
                                color = AnchorColors.HarborBeaconAmber,
                                accent = Color(0xFFFFD580),
                                modifier = Modifier.weight(1f),
                                onClick = { onLaunchMomentumPreset(15) },
                            )

                            MomentumPresetChip(
                                label = "25m Focus",
                                subtitle = "Pomodoro",
                                color = AnchorColors.HarborPrimary,
                                accent = AnchorColors.HarborAction,
                                modifier = Modifier.weight(1f),
                                onClick = { onLaunchMomentumPreset(25) },
                            )

                            MomentumPresetChip(
                                label = "50m Flow",
                                subtitle = "Deep dive",
                                color = AnchorColors.HarborAi,
                                accent = Color(0xFFC084FC),
                                modifier = Modifier.weight(1f),
                                onClick = { onLaunchMomentumPreset(50) },
                            )
                        }
                    }
                }

                // 3. Instant Thought Capture (Mental Exhaust Valve)
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AnchorSpacing.radiusCard))
                            .border(
                                1.dp,
                                AnchorColors.HarborAi.copy(alpha = 0.35f),
                                RoundedCornerShape(AnchorSpacing.radiusCard),
                            ),
                    shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                    color = AnchorColors.HarborDock.copy(alpha = 0.85f),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = AnchorColors.HarborAi,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "INSTANT THOUGHT CAPTURE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp,
                                    color = AnchorColors.HarborAi,
                                )
                            }
                            Text(
                                text = "Offload impulses without derailing",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = thoughtInput,
                                onValueChange = { thoughtInput = it },
                                placeholder = {
                                    Text(
                                        text = "Got an impulse? Dump it here & press Enter...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.4f),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors =
                                    OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AnchorColors.HarborAi,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                    ),
                                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions =
                                    KeyboardActions(onDone = {
                                        if (thoughtInput.isNotBlank()) {
                                            onCaptureThought(thoughtInput.trim())
                                            thoughtInput = ""
                                        }
                                    }),
                            )

                            Button(
                                onClick = {
                                    if (thoughtInput.isNotBlank()) {
                                        onCaptureThought(thoughtInput.trim())
                                        thoughtInput = ""
                                    }
                                },
                                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = AnchorColors.HarborAi,
                                        contentColor = Color(0xFF1E0E3E),
                                    ),
                                modifier = Modifier.height(48.dp),
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Capture", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ==========================================
            // RIGHT COLUMN: EXTERNALIZED WORKING MEMORY (~42%)
            // ==========================================
            Column(
                modifier =
                    Modifier
                        .weight(0.85f)
                        .fillMaxSize()
                        .verticalScroll(rightScrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1. Today's Momentum Hub (Immediate Dopamine Feedback)
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AnchorSpacing.radiusCard))
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.12f),
                                RoundedCornerShape(AnchorSpacing.radiusCard),
                            ),
                    shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                    color = AnchorColors.HarborDock.copy(alpha = 0.85f),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "TODAY'S MOMENTUM",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                                color = AnchorColors.HarborAction,
                            )
                            Text(
                                text = "Daily feedback loop",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.45f),
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DopamineStatTile(
                                icon = Icons.Default.Timer,
                                value = "${todayFocusMinutes}m",
                                label = "Focused Today",
                                tint = AnchorColors.HarborPrimary,
                                modifier = Modifier.weight(1f),
                            )

                            DopamineStatTile(
                                icon = Icons.Default.Shield,
                                value = "$deflectedCount",
                                label = "Deflected",
                                tint = AnchorColors.HarborGrowth,
                                modifier = Modifier.weight(1f),
                            )

                            DopamineStatTile(
                                icon = Icons.Default.Whatshot,
                                value = "${streakDays}d",
                                label = "Day Streak",
                                tint = AnchorColors.HarborBeaconAmber,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // 2. Up Next: The Rule of 3 (External Working Memory)
                val upcomingTasks = tasks.filter { !it.isCompleted && it.id != currentTask?.id }.take(3)
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AnchorSpacing.radiusCard))
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.12f),
                                RoundedCornerShape(AnchorSpacing.radiusCard),
                            ),
                    shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                    color = AnchorColors.HarborDock.copy(alpha = 0.85f),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "UP NEXT (ON DECK)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp,
                                    color = Color.White.copy(alpha = 0.9f),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = AnchorColors.HarborPrimary.copy(alpha = 0.2f),
                                ) {
                                    Text(
                                        text = "${upcomingTasks.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AnchorColors.HarborPrimary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onNavigateToPlan() },
                            ) {
                                Text(
                                    text = "Milestones",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AnchorColors.HarborPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = AnchorColors.HarborPrimary,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (upcomingTasks.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                                color = Color.White.copy(alpha = 0.04f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = "All clear on deck! ⚓",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.8f),
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Capture a thought or add milestones anytime.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (task in upcomingTasks) {
                                    Surface(
                                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                                        color = Color.White.copy(alpha = 0.05f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            IconButton(
                                                onClick = { onToggleTaskCompleted(task.id) },
                                                modifier = Modifier.size(24.dp),
                                            ) {
                                                Icon(
                                                    imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                    contentDescription = null,
                                                    tint =
                                                        if (task.isCompleted) {
                                                            AnchorColors.HarborGrowth
                                                        } else {
                                                            Color.White.copy(
                                                                alpha = 0.4f,
                                                            )
                                                        },
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = task.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                )
                                                Text(
                                                    text = "${task.durationMinutes}m duration",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White.copy(alpha = 0.5f),
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            // 1-Click Promote to NOW
                                            Surface(
                                                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                                color = AnchorColors.HarborPrimary.copy(alpha = 0.18f),
                                                border = BorderStroke(1.dp, AnchorColors.HarborPrimary.copy(alpha = 0.4f)),
                                                modifier = Modifier.clickable { onMakeTaskNow(task.id) },
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Bolt,
                                                        contentDescription = null,
                                                        tint = AnchorColors.HarborPrimary,
                                                        modifier = Modifier.size(12.dp),
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = "Make NOW",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = AnchorColors.HarborPrimary,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Brain Breaks (Trojan Dopamine Resets)
                DesktopFunLinks(links = funLinks)
            }
        }
    }
}

@Composable
private fun MomentumPresetChip(
    label: String,
    subtitle: String,
    color: Color,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .clip(RoundedCornerShape(AnchorSpacing.radiusChip))
                .clickable { onClick() },
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun DopamineStatTile(
    icon: ImageVector,
    value: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .clip(RoundedCornerShape(AnchorSpacing.radiusChip)),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
            )
        }
    }
}
