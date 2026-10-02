package com.anchor.adhd.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.adhd.ui.theme.AnchorColors
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.vm.AirlockState
import com.anchor.adhd.ui.vm.AnchorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirlockSheet(
    vm: AnchorViewModel,
    onDismiss: () -> Unit,
    onDropAnchor: (durationMinutes: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by vm.airlockState.collectAsState()

    AirlockSheetContent(
        state = state,
        onInputChange = { vm.updateAirlockInput(it) },
        onSequence = { vm.runAirlockSequencer(it) },
        onWorkMinutesChange = { vm.setAirlockWorkMinutes(it) },
        onDropAnchor = onDropAnchor,
        onEditDump = { vm.editAirlockDump() },
        onDismiss = onDismiss,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AirlockSheetContent(
    state: AirlockState,
    onInputChange: (String) -> Unit,
    onSequence: (String) -> Unit,
    onWorkMinutesChange: (Int) -> Unit,
    onDropAnchor: (Int) -> Unit,
    onEditDump: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCustomDurationDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AnchorColors.HarborBackground,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AnchorSpacing.screenHorizontal, vertical = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(AnchorColors.HarborMist),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Anchor,
                            contentDescription = null,
                            tint = AnchorColors.HarborPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "The Airlock",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = AnchorColors.HarborPrimary
                        )
                        Text(
                            text = "Dump the chaos. We isolate one step.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (!state.isReadyForFocus) {
                // Ingestion Mode: Messy Brain Dump
                OutlinedTextField(
                    value = state.rawInput,
                    onValueChange = onInputChange,
                    placeholder = {
                        Text(
                            "Paste or type everything that's overwhelming you right now...\n\n" +
                                "e.g., finish chem lab, email professor about extension and then buy groceries\npack backpack",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp, max = 240.dp),
                    shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AnchorColors.HarborPrimary,
                        unfocusedBorderColor = AnchorColors.HarborMist,
                        focusedContainerColor = AnchorColors.HarborDock.copy(alpha = 0.5f),
                        unfocusedContainerColor = AnchorColors.HarborDock.copy(alpha = 0.3f),
                        cursorColor = AnchorColors.HarborPrimary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onSequence(state.rawInput) },
                    enabled = state.rawInput.isNotBlank() && !state.isSequencing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AnchorColors.HarborAction,
                        contentColor = Color.Black
                    )
                ) {
                    if (state.isSequencing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Sequencing...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Sequence Task",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // Sequenced De-escalation Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AnchorSpacing.radiusCard))
                        .background(AnchorColors.HarborDock)
                        .border(
                            1.dp,
                            AnchorColors.HarborAction.copy(alpha = 0.5f),
                            RoundedCornerShape(AnchorSpacing.radiusCard)
                        )
                        .padding(18.dp)
                ) {
                    // Top Safe Storage Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.secondaryTaskTitles.isNotEmpty()) {
                            Surface(
                                color = AnchorColors.HarborGrowth.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                border = BorderStroke(1.dp, AnchorColors.HarborFoliage.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = AnchorColors.HarborFoliage,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        "Saved ${state.secondaryTaskTitles.size} tasks safely to Inbox",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AnchorColors.HarborFoliage
                                    )
                                }
                            }
                        } else {
                            Surface(
                                color = AnchorColors.HarborGrowth.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                border = BorderStroke(1.dp, AnchorColors.HarborFoliage.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = AnchorColors.HarborFoliage,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        "Single focus target isolated",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AnchorColors.HarborFoliage
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "RIGHT NOW, ONLY ONE THING MATTERS",
                        style = MaterialTheme.typography.labelSmall,
                        color = AnchorColors.HarborAction,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = state.primaryTaskTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Somatic Micro-Step Box
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(900, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "ai_pulse"
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AnchorColors.HarborSkyNight.copy(alpha = 0.8f),
                        border = BorderStroke(
                            1.dp,
                            if (state.isRefiningWithAi) AnchorColors.HarborAi.copy(alpha = pulseAlpha)
                            else AnchorColors.HarborMist
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = AnchorColors.HarborPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "FIRST PHYSICAL STEP (10 SECONDS)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AnchorColors.HarborPrimary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = state.somaticStarter,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (state.isRefiningWithAi) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.alpha(pulseAlpha)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 2.dp,
                                        color = AnchorColors.HarborAi
                                    )
                                    Text(
                                        "Refining with on-device AI...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AnchorColors.HarborAi
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Duration Selector Chips
                Text(
                    text = "Focus Duration",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                val standardDurations = listOf(10, 20, 30, 45)
                val isCustomDuration = state.selectedWorkMinutes !in standardDurations
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    standardDurations.forEach { duration ->
                        val selected = state.selectedWorkMinutes == duration
                        FilterChip(
                            selected = selected,
                            onClick = { onWorkMinutesChange(duration) },
                            label = { Text("${duration}m") },
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        Icons.Default.Timer,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AnchorColors.HarborPrimary.copy(alpha = 0.25f),
                                selectedLabelColor = AnchorColors.HarborPrimary
                            )
                        )
                    }
                    // Custom Duration Chip
                    FilterChip(
                        selected = isCustomDuration,
                        onClick = { showCustomDurationDialog = true },
                        label = { Text(if (isCustomDuration) "${state.selectedWorkMinutes}m ✎" else "Custom...") },
                        leadingIcon = if (isCustomDuration) {
                            {
                                Icon(
                                    Icons.Default.Timer,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AnchorColors.HarborPrimary.copy(alpha = 0.25f),
                            selectedLabelColor = AnchorColors.HarborPrimary
                        )
                    )
                }

                if (showCustomDurationDialog) {
                    CustomDurationDialog(
                        initialMinutes = state.selectedWorkMinutes,
                        onDismiss = { showCustomDurationDialog = false },
                        onConfirm = { customMins ->
                            onWorkMinutesChange(customMins)
                            showCustomDurationDialog = false
                        }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Big Primary CTA: DROP ANCHOR
                Button(
                    onClick = { onDropAnchor(state.selectedWorkMinutes) },
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
                        "DROP ANCHOR (${state.selectedWorkMinutes} MIN)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onEditDump,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        "Edit brain dump",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
