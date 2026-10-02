package com.anchor.adhd.desktop.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.anchor.adhd.desktop.ai.DesktopAiEngine
import com.anchor.adhd.desktop.ai.LlamaServerStatus
import com.anchor.adhd.desktop.theme.AnchorColors
import com.anchor.adhd.desktop.theme.AnchorSpacing
import kotlinx.coroutines.launch

/**
 * Airlock Brain-Dump & Triage Dialog for Windows 11.
 * Decomposes chaotic thoughts into calm, tailored 5-minute physical micro-steps
 * powered by on-device Claude-Opus-Fable5 (Q8_0) with instant heuristic fallbacks.
 */
@Composable
fun DesktopAirlockDialog(
    onDismiss: () -> Unit,
    onAnchorMicroStep: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var rawText by remember { mutableStateOf("") }
    val generatedSteps = remember { mutableStateListOf<String>() }
    var isRefiningByAi by remember { mutableStateOf(false) }

    val engineStatus by DesktopAiEngine.engineStatus.collectAsState()
    val isModelReady = engineStatus is LlamaServerStatus.Ready

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier =
                Modifier
                    .width(540.dp)
                    .clip(RoundedCornerShape(AnchorSpacing.radiusScene))
                    .border(1.dp, AnchorColors.HarborAi.copy(alpha = 0.4f), RoundedCornerShape(AnchorSpacing.radiusScene)),
            shape = RoundedCornerShape(AnchorSpacing.radiusScene),
            color = AnchorColors.HarborDock,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier =
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(AnchorColors.HarborMist),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = AnchorColors.HarborAi,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Airlock Brain-Dump",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // AI engine indicator pill
                                Surface(
                                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                    color = if (isModelReady) AnchorColors.HarborAi.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                                    border =
                                        BorderStroke(
                                            1.dp,
                                            if (isModelReady) AnchorColors.HarborAi.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.2f),
                                        ),
                                ) {
                                    Text(
                                        text = if (isModelReady) "Claude-Opus AI" else "Heuristic",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isModelReady) AnchorColors.HarborAi else Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(
                                text = "Unload racing thoughts into physical micro-steps",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f),
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Input area
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    placeholder = {
                        Text(
                            "What is overwhelming you right now? e.g., 'Finish research paper' or paste a long brain dump...",
                            color = Color.White.copy(alpha = 0.4f),
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                    shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AnchorColors.HarborAi,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = AnchorColors.HarborBackground.copy(alpha = 0.8f),
                            unfocusedContainerColor = AnchorColors.HarborBackground.copy(alpha = 0.8f),
                        ),
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Primary: Decompose into 5-min steps
                    Button(
                        onClick = {
                            val trimmed = rawText.trim()
                            if (trimmed.isNotBlank()) {
                                // Isolate primary task if user entered a multi-task brain dump
                                val parseResult =
                                    com.anchor.adhd.domain.AirlockHeuristic
                                        .parse(trimmed)
                                val targetTask = parseResult.primaryTask.ifBlank { trimmed }

                                // 1. Instant zero-latency heuristic display
                                val instant = DesktopAiEngine.generateMicroSteps(targetTask)
                                generatedSteps.clear()
                                generatedSteps.addAll(instant)

                                // 2. Async AI refinement if Claude-Opus-Fable5 is ready
                                if (isModelReady || DesktopAiEngine.isModelReady()) {
                                    isRefiningByAi = true
                                    scope.launch {
                                        val aiSteps = DesktopAiEngine.generateMicroStepsAsync(targetTask)
                                        if (aiSteps.isNotEmpty()) {
                                            generatedSteps.clear()
                                            generatedSteps.addAll(aiSteps)
                                        }
                                        isRefiningByAi = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = AnchorColors.HarborAi,
                                contentColor = Color.White,
                            ),
                    ) {
                        if (isRefiningByAi) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refining...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Decompose (5-Min Steps)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // Secondary: Parse Brain Dump into separate tasks
                    OutlinedButton(
                        onClick = {
                            val trimmed = rawText.trim()
                            if (trimmed.isNotBlank()) {
                                val instant = DesktopAiEngine.parseBrainDump(trimmed)
                                generatedSteps.clear()
                                generatedSteps.addAll(instant)

                                if (isModelReady || DesktopAiEngine.isModelReady()) {
                                    isRefiningByAi = true
                                    scope.launch {
                                        val parsed = DesktopAiEngine.parseBrainDumpAsync(trimmed)
                                        if (parsed.isNotEmpty()) {
                                            generatedSteps.clear()
                                            generatedSteps.addAll(parsed)
                                        }
                                        isRefiningByAi = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                        border = BorderStroke(1.dp, AnchorColors.HarborPrimary.copy(alpha = 0.5f)),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                            contentDescription = null,
                            tint = AnchorColors.HarborPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Extract Tasks", fontSize = 13.sp, color = AnchorColors.HarborPrimary)
                    }
                }

                // Generated Steps List
                if (generatedSteps.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "CHOOSE YOUR ANCHOR MICRO-STEP",
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.2.sp,
                            color = AnchorColors.HarborAction,
                            fontWeight = FontWeight.Bold,
                        )
                        if (isRefiningByAi) {
                            Text(
                                text = "Claude-Opus is refining...",
                                style = MaterialTheme.typography.labelSmall,
                                color = AnchorColors.HarborAi,
                                fontSize = 10.sp,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(generatedSteps) { step ->
                            Surface(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(AnchorSpacing.radiusCard))
                                        .clickable {
                                            onAnchorMicroStep(step)
                                            onDismiss()
                                        },
                                shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                                color = AnchorColors.HarborBackground.copy(alpha = 0.8f),
                                border = BorderStroke(1.dp, AnchorColors.HarborPrimary.copy(alpha = 0.25f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = AnchorColors.HarborPrimary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = step,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
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
