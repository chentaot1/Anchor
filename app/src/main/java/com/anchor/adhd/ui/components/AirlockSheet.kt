package com.anchor.adhd.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens
import com.anchor.adhd.ui.vm.AirlockState
import com.anchor.adhd.ui.vm.AnchorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirlockSheet(
    vm: AnchorViewModel,
    onDismiss: () -> Unit,
    onDropAnchor: (durationMinutes: Int) -> Unit,
    modifier: Modifier = Modifier,
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
        modifier = modifier,
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
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAnchorTokens.current
    val captainTextColor = if (tokens.isDark) tokens.captain else tokens.captainInk
    var showCustomDurationDialog by remember { mutableStateOf(false) }

    HarborModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AnchorSpacing.screenHorizontal, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(tokens.captainSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Anchor,
                            contentDescription = null,
                            tint = captainTextColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "The Airlock",
                            style = MaterialTheme.typography.titleLarge,
                            color = tokens.ink,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = "Dump the chaos. We isolate one step.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.muted,
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = tokens.muted,
                    )
                }
            }

            if (!state.isReadyForFocus) {
                // Step 1: Messy brain dump ingestion
                Text(
                    text = "Step 1 of 3 — Empty your head",
                    style = MaterialTheme.typography.labelLarge,
                    color = tokens.captain,
                )

                OutlinedTextField(
                    value = state.rawInput,
                    onValueChange = onInputChange,
                    placeholder = {
                        Text(
                            text = "Paste or type everything that's overwhelming you right now…\n\n" +
                                "e.g., finish chem lab, email professor about extension and then buy groceries\npack backpack",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.muted,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp, max = 240.dp),
                    shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = tokens.captain,
                        unfocusedBorderColor = tokens.fieldEdge,
                        focusedTextColor = tokens.ink,
                        unfocusedTextColor = tokens.ink,
                        focusedContainerColor = tokens.paper,
                        unfocusedContainerColor = tokens.paper,
                        cursorColor = tokens.captain,
                    ),
                )

                HarborCtaButton(
                    onClick = { onSequence(state.rawInput) },
                    enabled = state.rawInput.isNotBlank() && !state.isSequencing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    if (state.isSequencing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = tokens.beaconInk,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sequencing…",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sequence task",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            } else {
                // Step 2: Sequenced single focus target & somatic starter (flat hierarchy, zero nested cards)
                Surface(
                    color = tokens.sageSoft,
                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = tokens.sage,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = if (state.secondaryTaskTitles.isNotEmpty()) {
                                "Saved ${state.secondaryTaskTitles.size} tasks safely to Inbox"
                            } else {
                                "Single focus target isolated"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum",
                            ),
                            color = tokens.sage,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Step 2 of 3 — Right now, only one thing matters",
                        style = MaterialTheme.typography.labelLarge,
                        color = tokens.primary,
                    )
                    Text(
                        text = state.primaryTaskTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        color = tokens.ink,
                    )
                }

                AnchorWell(
                    tone = AnchorWellTone.Captain,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = captainTextColor,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "First physical step (10 seconds)",
                            style = MaterialTheme.typography.labelLarge,
                            color = captainTextColor,
                        )
                    }

                    Text(
                        text = state.somaticStarter,
                        style = MaterialTheme.typography.bodyLarge,
                        color = tokens.ink,
                    )

                    if (state.isRefiningWithAi) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = captainTextColor,
                            )
                            Text(
                                text = "Refining with Harbor Captain…",
                                style = MaterialTheme.typography.labelSmall,
                                color = captainTextColor,
                            )
                        }
                    }
                }

                AnchorHairline()

                // Step 3: Duration selector & Drop anchor
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Step 3 of 3 — Focus duration",
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.ink,
                    )

                    val standardDurations = listOf(10, 20, 30, 45)
                    val isCustomDuration = state.selectedWorkMinutes !in standardDurations
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        standardDurations.forEach { duration ->
                            val selected = state.selectedWorkMinutes == duration
                            AnchorChip(
                                label = "${duration}m",
                                selected = selected,
                                onClick = { onWorkMinutesChange(duration) },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Timer,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                } else {
                                    null
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                        AnchorChip(
                            label = if (isCustomDuration) "${state.selectedWorkMinutes}m (custom)" else "Custom…",
                            selected = isCustomDuration,
                            onClick = { showCustomDurationDialog = true },
                            leadingIcon = if (isCustomDuration) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            } else {
                                null
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }

                if (showCustomDurationDialog) {
                    CustomDurationDialog(
                        initialMinutes = state.selectedWorkMinutes,
                        onDismiss = { showCustomDurationDialog = false },
                        onConfirm = { customMins ->
                            onWorkMinutesChange(customMins)
                            showCustomDurationDialog = false
                        },
                    )
                }

                HarborCtaButton(
                    onClick = { onDropAnchor(state.selectedWorkMinutes) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Anchor,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Drop anchor (${state.selectedWorkMinutes} min)",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                AnchorQuietButton(
                    label = "Edit brain dump",
                    onClick = onEditDump,
                    outlined = false,
                    tint = tokens.muted,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .heightIn(min = 48.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
