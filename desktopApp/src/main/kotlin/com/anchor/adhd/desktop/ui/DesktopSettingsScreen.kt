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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.anchor.adhd.desktop.ai.DesktopAiEngine
import com.anchor.adhd.desktop.ai.InferenceBenchmarkResult
import com.anchor.adhd.desktop.ai.LlamaServerStatus
import com.anchor.adhd.desktop.ai.ResetType
import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import com.anchor.adhd.desktop.platform.DesktopAutostartManager
import com.anchor.adhd.desktop.prefs.DesktopUserPreferences
import com.anchor.adhd.desktop.theme.AnchorColors
import com.anchor.adhd.desktop.theme.AnchorSpacing
import com.anchor.adhd.desktop.update.DesktopUpdateManager
import com.anchor.adhd.desktop.update.UpdateState
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.io.File

/**
 * Windows 11 Settings & Storage Management Screen.
 * Includes On-Device AI Engine management (MiniCPM5-1B-Claude-Opus-Fable5-Q8_0).
 */
@Composable
fun DesktopSettingsScreen(
    db: AnchorDesktopDatabase,
    prefs: DesktopUserPreferences,
    durationMinutes: Int,
    onDurationChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val harborState by db.harborState.collectAsState()
    val tasks by db.tasks.collectAsState()
    var sliderValue by remember { mutableFloatStateOf(durationMinutes.toFloat()) }

    val aiEnabled by prefs.aiEnabled.collectAsState(initial = true)
    val aiModelPath by prefs.aiModelPath.collectAsState(initial = prefs.getDefaultModelPath())
    val aiBinaryPath by prefs.aiBinaryPath.collectAsState(initial = prefs.getDefaultBinaryPath())
    val standingShieldEnabled by prefs.standingShieldEnabled.collectAsState(initial = false)
    val autostartOnBoot by prefs.autostartOnBoot.collectAsState(initial = true)
    val startMinimized by prefs.startMinimized.collectAsState(initial = true)
    val engineStatus by DesktopAiEngine.engineStatus.collectAsState()

    var isRunningTest by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<InferenceBenchmarkResult?>(null) }
    var pendingResetAction by remember { mutableStateOf<ResetType?>(null) }
    var resetFeedbackBanner by remember { mutableStateOf<String?>(null) }

    val appDataDir =
        remember {
            val appData = System.getenv("APPDATA") ?: (System.getProperty("user.home") + "/AppData/Roaming")
            File(appData, "Anchor")
        }

    val modelFile = remember(aiModelPath) { File(aiModelPath) }
    val modelExists = remember(modelFile) { modelFile.exists() && modelFile.isFile }
    val discoveredModels = remember(aiModelPath) { prefs.getDiscoveredModels() }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Title
        Column {
            Text(
                text = "Anchor Settings & Windows Storage",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = "Manage focus durations, on-device AI inference, and local persistence",
                style = MaterialTheme.typography.bodySmall,
                color = AnchorColors.HarborPrimary.copy(alpha = 0.8f),
            )
        }

        // On-Device AI Engine Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AnchorSpacing.radiusCard),
            color = AnchorColors.HarborDock,
            border = BorderStroke(1.dp, AnchorColors.HarborAi.copy(alpha = 0.35f)),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header Row with Toggle
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
                                    .background(AnchorColors.HarborAi.copy(alpha = 0.15f)),
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
                            Text(
                                text = "On-Device AI Engine",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                text = if (modelExists) modelFile.nameWithoutExtension else "On-Device GGUF Engine",
                                style = MaterialTheme.typography.bodySmall,
                                color = AnchorColors.HarborAi,
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = aiEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    prefs.setAiEnabled(enabled)
                                    if (enabled) {
                                        DesktopAiEngine.initialize(
                                            scope = scope,
                                            modelPath = aiModelPath,
                                            binaryPath = aiBinaryPath,
                                            enabled = true,
                                        )
                                    } else {
                                        DesktopAiEngine.stop()
                                    }
                                }
                            },
                            colors =
                                SwitchDefaults.colors(
                                    checkedThumbColor = AnchorColors.HarborAi,
                                    checkedTrackColor = AnchorColors.HarborAi.copy(alpha = 0.3f),
                                    uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
                                ),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Status Badge
                    val statusText =
                        when (engineStatus) {
                            is LlamaServerStatus.Ready -> "ACTIVE & READY (Port ${(engineStatus as LlamaServerStatus.Ready).port})"
                            is LlamaServerStatus.Starting -> "STARTING / LOADING WEIGHTS..."
                            is LlamaServerStatus.Error -> "ERROR: ${(engineStatus as LlamaServerStatus.Error).message}"
                            is LlamaServerStatus.Disabled -> "DISABLED (Instant 2ms Heuristic)"
                            LlamaServerStatus.Idle -> "IDLE (Instant 2ms Heuristic)"
                        }
                    val statusColor =
                        when (engineStatus) {
                            is LlamaServerStatus.Ready -> Color(0xFF4CAF50)
                            is LlamaServerStatus.Starting -> AnchorColors.HarborBeaconAmber
                            is LlamaServerStatus.Error -> Color(0xFFE57373)
                            else -> Color.White.copy(alpha = 0.6f)
                        }

                    Surface(
                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                        color = statusColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(statusColor),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                            )
                        }
                    }

                    // Model File Badge
                    val modelSizeText =
                        if (modelExists) {
                            val sizeGb = modelFile.length() / (1024.0 * 1024.0 * 1024.0)
                            if (sizeGb >= 1.0) "%.2f GB Found".format(sizeGb) else "%.0f MB Found".format(modelFile.length() / (1024.0 * 1024.0))
                        } else {
                            "File Missing"
                        }
                    val modelColor = if (modelExists) Color(0xFF4CAF50) else Color(0xFFFFA726)
                    Surface(
                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                        color = modelColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, modelColor.copy(alpha = 0.4f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (modelExists) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = modelColor,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = modelSizeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = modelColor,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // File path display + Browse GGUF button
                Surface(
                    shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                    color = AnchorColors.HarborBackground.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MODEL PATH",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                color = Color.White.copy(alpha = 0.4f),
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = aiModelPath,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        OutlinedButton(
                            onClick = {
                                val dialog =
                                    java.awt.FileDialog(
                                        null as java.awt.Frame?,
                                        "Select On-Device GGUF Model",
                                        java.awt.FileDialog.LOAD,
                                    )
                                dialog.file = "*.gguf"
                                dialog.isVisible = true
                                val dir = dialog.directory
                                val file = dialog.file
                                if (dir != null && file != null) {
                                    val selectedFile = File(dir, file)
                                    if (selectedFile.exists() && selectedFile.isFile) {
                                        scope.launch {
                                            prefs.setAiModelPath(selectedFile.absolutePath)
                                            DesktopAiEngine.initialize(
                                                scope = scope,
                                                modelPath = selectedFile.absolutePath,
                                                binaryPath = aiBinaryPath,
                                                enabled = true,
                                            )
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                            border = BorderStroke(1.dp, AnchorColors.HarborAi.copy(alpha = 0.5f)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = AnchorColors.HarborAi,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Browse GGUF...", color = AnchorColors.HarborAi, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (discoveredModels.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "DETECTED LOCAL MODELS (1-CLICK SWITCH)",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        discoveredModels.take(5).forEach { file ->
                            val isSelected = file.absolutePath.equals(aiModelPath, ignoreCase = true)
                            val sizeGb = file.length() / (1024.0 * 1024.0 * 1024.0)
                            val sizeStr = if (sizeGb >= 1.0) "%.2f GB".format(sizeGb) else "%.0f MB".format(file.length() / (1024.0 * 1024.0))

                            Surface(
                                shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                                color = if (isSelected) AnchorColors.HarborAi.copy(alpha = 0.18f) else AnchorColors.HarborBackground.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, if (isSelected) AnchorColors.HarborAi.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (!isSelected) {
                                        scope.launch {
                                            prefs.setAiModelPath(file.absolutePath)
                                            DesktopAiEngine.initialize(
                                                scope = scope,
                                                modelPath = file.absolutePath,
                                                binaryPath = aiBinaryPath,
                                                enabled = true,
                                            )
                                        }
                                    }
                                },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Storage,
                                            contentDescription = null,
                                            tint = if (isSelected) AnchorColors.HarborAi else Color.White.copy(alpha = 0.4f),
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = file.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
                                                fontSize = 12.sp,
                                            )
                                            Text(
                                                text =
                                                    if (file.name.contains("Ling-3.0", ignoreCase = true)) {
                                                        "Ling 3.0 MoE • Rich Conversational Nuance & Coaching"
                                                    } else if (file.name.contains("Fable5-Thinking-Q8", ignoreCase = true)) {
                                                        "Claude-Opus-Fable5 (Q8_0) • Fast On-Device ADHD Executive Partner"
                                                    } else {
                                                        "Local On-Device GGUF Weights"
                                                    },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) AnchorColors.HarborAi else Color.White.copy(alpha = 0.4f),
                                                fontSize = 10.sp,
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                        color = if (isSelected) AnchorColors.HarborAi.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f),
                                    ) {
                                        Text(
                                            text = if (isSelected) "ACTIVE • $sizeStr" else sizeStr,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            color = if (isSelected) AnchorColors.HarborAi else Color.White.copy(alpha = 0.65f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Row: Test AI Inference
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Zero cloud dependency: 100% private, offline, on-device inference.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.weight(1f),
                    )

                    Button(
                        onClick = {
                            if (!isRunningTest) {
                                isRunningTest = true
                                testResult = null
                                scope.launch {
                                    val res = DesktopAiEngine.testInference()
                                    testResult = res
                                    isRunningTest = false
                                }
                            }
                        },
                        enabled = !isRunningTest && aiEnabled,
                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = AnchorColors.HarborAi,
                                contentColor = Color.White,
                            ),
                    ) {
                        if (isRunningTest) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test AI Inference")
                        }
                    }
                }

                // Test result box if available
                testResult?.let { res ->
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                        color = if (res.success) Color(0xFF003829).copy(alpha = 0.6f) else Color(0xFF380000).copy(alpha = 0.6f),
                        border =
                            BorderStroke(
                                1.dp,
                                if (res.success) Color(0xFF4CAF50).copy(alpha = 0.4f) else Color(0xFFE57373).copy(alpha = 0.4f),
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = if (res.success) "BENCHMARK PASSED" else "TEST FAILED",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (res.success) Color(0xFF81C784) else Color(0xFFE57373),
                                )
                                if (res.success) {
                                    Text(
                                        text = "${"%.1f".format(res.tokensPerSecond)} tok/s • ${res.durationMillis}ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AnchorColors.HarborAction,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (res.success) res.outputText else (res.error ?: "Unknown error"),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }
        }

        // Focus Duration Slider Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AnchorSpacing.radiusCard),
            color = AnchorColors.HarborDock,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Timer, contentDescription = null, tint = AnchorColors.HarborPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Default Focus Duration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                    Text(
                        text = "${sliderValue.toInt()} minutes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AnchorColors.HarborAction,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        val mins = sliderValue.toInt()
                        onDurationChanged(mins)
                        scope.launch { prefs.setFocusWorkMinutes(mins) }
                    },
                    valueRange = 5f..90f,
                    steps = 16,
                    colors =
                        SliderDefaults.colors(
                            thumbColor = AnchorColors.HarborAction,
                            activeTrackColor = AnchorColors.HarborPrimary,
                        ),
                )

                Text(
                    text = "Recommended ADHD focus intervals: 15m, 25m, or 50m.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
        }

        // Standing Shield (Continuous Protection) Setting Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AnchorSpacing.radiusCard),
            color = AnchorColors.HarborDock,
            border =
                BorderStroke(
                    1.dp,
                    if (standingShieldEnabled) AnchorColors.HarborGrowth.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f),
                ),
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (standingShieldEnabled) {
                                        AnchorColors.HarborGrowth.copy(alpha = 0.25f)
                                    } else {
                                        Color.White.copy(alpha = 0.08f)
                                    },
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (standingShieldEnabled) AnchorColors.HarborFoliage else AnchorColors.HarborPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Standing Shield (Continuous Protection)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = "Block distracting apps and websites 24/7 without requiring an active Pomodoro countdown.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = standingShieldEnabled,
                    onCheckedChange = { checked ->
                        scope.launch { prefs.setStandingShieldEnabled(checked) }
                    },
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = AnchorColors.HarborFoliage,
                            checkedTrackColor = AnchorColors.HarborGrowth,
                            uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.15f),
                        ),
                )
            }
        }

        // Windows 11 Autostart & Launch Mode Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AnchorSpacing.radiusCard),
            color = AnchorColors.HarborDock,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
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
                                    .background(AnchorColors.HarborPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Computer,
                                contentDescription = null,
                                tint = AnchorColors.HarborPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Windows Startup & Background Shield",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val isReg = remember(autostartOnBoot) { DesktopAutostartManager.isAutostartRegistered() }
                                Surface(
                                    shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                    color = if (isReg) AnchorColors.HarborGrowth.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                                ) {
                                    Text(
                                        text = if (isReg) "Windows Run: Active" else "Not Registered",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = if (isReg) AnchorColors.HarborFoliage else Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(
                                text = "Keep distraction monitors and lecture shields persistent across system restarts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.65f),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Toggle 1: Autostart on boot
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Launch at Windows Startup",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        Text(
                            text = "Automatically starts Anchor in Windows Task Manager Startup Apps on login.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                    Switch(
                        checked = autostartOnBoot,
                        onCheckedChange = { checked ->
                            scope.launch {
                                prefs.setAutostartOnBoot(checked)
                                DesktopAutostartManager.setAutostart(checked, startMinimized = startMinimized)
                            }
                        },
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = AnchorColors.HarborPrimary,
                                checkedTrackColor = AnchorColors.HarborAction,
                                uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
                            ),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle 2: Start Minimized
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Start Minimized (Background Shield)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        Text(
                            text = "Starts quietly in the Windows taskbar instead of taking over the screen in fullscreen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                    Switch(
                        checked = startMinimized,
                        onCheckedChange = { checked ->
                            scope.launch {
                                prefs.setStartMinimized(checked)
                                if (autostartOnBoot) {
                                    DesktopAutostartManager.setAutostart(true, startMinimized = checked)
                                }
                            }
                        },
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = AnchorColors.HarborPrimary,
                                checkedTrackColor = AnchorColors.HarborAction,
                                uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
                            ),
                    )
                }
            }
        }

        // Windows 11 SQLite Database Storage Card
        var backupStatusMessage by remember { mutableStateOf<String?>(null) }
        var backupStatusIsError by remember { mutableStateOf(false) }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AnchorSpacing.radiusCard),
            color = AnchorColors.HarborDock,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Storage, contentDescription = null, tint = AnchorColors.HarborPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Local Windows 11 Database",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                text = appDataDir.absolutePath,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val dialog =
                                    java.awt.FileDialog(
                                        null as java.awt.Frame?,
                                        "Export Anchor Backup (.json)",
                                        java.awt.FileDialog.SAVE,
                                    )
                                dialog.file = "anchor_backup_${java.time.LocalDate.now()}.json"
                                dialog.isVisible = true
                                val dir = dialog.directory
                                val fileName = dialog.file
                                if (dir != null && fileName != null) {
                                    val targetFile =
                                        File(
                                            dir,
                                            if (fileName.endsWith(".json", ignoreCase = true)) fileName else "$fileName.json",
                                        )
                                    scope.launch {
                                        runCatching {
                                            db.exportDataToFile(targetFile)
                                            backupStatusIsError = false
                                            backupStatusMessage = "Exported backup to ${targetFile.name}"
                                        }.onFailure { err ->
                                            backupStatusIsError = true
                                            backupStatusMessage = "Export failed: ${err.message ?: "Unknown error"}"
                                        }
                                    }
                                }
                            },
                            border = BorderStroke(1.dp, AnchorColors.HarborAction),
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = AnchorColors.HarborAction)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export Backup (.json)", color = AnchorColors.HarborAction)
                        }

                        OutlinedButton(
                            onClick = {
                                val dialog =
                                    java.awt.FileDialog(
                                        null as java.awt.Frame?,
                                        "Restore Anchor Backup (.json)",
                                        java.awt.FileDialog.LOAD,
                                    )
                                dialog.file = "*.json"
                                dialog.isVisible = true
                                val dir = dialog.directory
                                val fileName = dialog.file
                                if (dir != null && fileName != null) {
                                    val sourceFile = File(dir, fileName)
                                    scope.launch {
                                        runCatching {
                                            val jsonContent = sourceFile.readText(Charsets.UTF_8)
                                            val count = db.importDataJson(jsonContent, replaceExisting = true)
                                            backupStatusIsError = false
                                            backupStatusMessage = "Restored $count records from ${sourceFile.name}"
                                        }.onFailure { err ->
                                            backupStatusIsError = true
                                            backupStatusMessage = "Restore failed: ${err.message ?: "Invalid backup JSON"}"
                                        }
                                    }
                                }
                            },
                            border = BorderStroke(1.dp, AnchorColors.HarborPrimary),
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = AnchorColors.HarborPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore Backup (.json)", color = AnchorColors.HarborPrimary)
                        }

                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    if (Desktop.isDesktopSupported()) {
                                        Desktop.getDesktop().open(appDataDir)
                                    }
                                }
                            },
                            border = BorderStroke(1.dp, AnchorColors.HarborPrimary),
                        ) {
                            Icon(imageVector = Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Folder", color = AnchorColors.HarborPrimary)
                        }
                    }
                }

                backupStatusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(12.dp))
                    val bannerColor = if (backupStatusIsError) Color(0xFFE57373) else Color(0xFF81C784)
                    Surface(
                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                        color = bannerColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, bannerColor.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (backupStatusIsError) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = bannerColor,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = bannerColor,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            IconButton(
                                onClick = { backupStatusMessage = null },
                                modifier = Modifier.size(20.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    StatPill("Total Sessions", "${harborState.totalSessions}")
                    StatPill("Focus Minutes", "${harborState.totalMinutes}m")
                    StatPill("Current Streak", "${harborState.streakDays} days")
                    StatPill("Milestones Done", "${tasks.count { it.isCompleted }}/${tasks.size}")
                }
            }
        }

        // Fresh Start & Clean Slate (ADHD Data Reset) Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AnchorSpacing.radiusCard),
            color = AnchorColors.HarborDock,
            border = BorderStroke(1.dp, Color(0xFFFFA000).copy(alpha = 0.35f)),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header Row
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
                                    .background(Color(0xFFFFA000).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = null,
                                tint = Color(0xFFFFA000),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Fresh Start & Clean Slate",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                text = "Clinical ADHD recovery tools: Clear task overload and broken streak guilt with zero shame.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFA000),
                            )
                        }
                    }
                }

                // Success feedback banner if recently reset
                resetFeedbackBanner?.let { msg ->
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                        color = Color(0xFF003829).copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF81C784),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF81C784),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            IconButton(
                                onClick = { resetFeedbackBanner = null },
                                modifier = Modifier.size(20.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Option 1: Clear Task Backlog
                ResetOptionRow(
                    title = "Clear Task Backlog (Inbox Zero)",
                    description = "Wipes lingering completed & overdue tasks and creates a clean 1-task anchor. Prevents avoidance when tasks pile up.",
                    buttonText = "Clear Backlog",
                    buttonColor = Color(0xFFFFA000),
                    onClick = { pendingResetAction = ResetType.CLEAR_TASKS },
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Option 2: Reset Focus History & Streak
                ResetOptionRow(
                    title = "Reset Focus Streak & Sessions",
                    description =
                        "Resets streak to Day 1 and clears session history. " +
                            "Clinical research shows broken streak numbers cause task paralysis; reset anytime.",
                    buttonText = "Reset Streak",
                    buttonColor = Color(0xFF7E57C2),
                    onClick = { pendingResetAction = ResetType.RESET_STREAK },
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Option 3: Factory Reset All Data
                ResetOptionRow(
                    title = "Factory Reset (All Anchor Data)",
                    description = "Complete clean slate: Clears all tasks, sessions, and stats back to initial install. (AI model files are preserved).",
                    buttonText = "Factory Reset",
                    buttonColor = Color(0xFFE53935),
                    onClick = { pendingResetAction = ResetType.FACTORY_RESET },
                )
            }
        }

        // Anchor App Updates & Safe In-App Sync Card
        val updateState by DesktopUpdateManager.updateState.collectAsState()
        var updateLogs by remember { mutableStateOf<List<String>>(emptyList()) }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AnchorSpacing.radiusCard),
            color = AnchorColors.HarborDock,
            border =
                BorderStroke(
                    width = 1.dp,
                    color =
                        when (updateState) {
                            is UpdateState.UpdateAvailable -> AnchorColors.HarborAction
                            is UpdateState.UpdateSuccess -> Color(0xFF4CAF50)
                            is UpdateState.Error -> Color(0xFFE57373)
                            else -> Color.White.copy(alpha = 0.15f)
                        },
                ),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header Row
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
                                    .background(AnchorColors.HarborAction.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = AnchorColors.HarborAction,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Anchor Updates & Safe In-App Sync",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            val envLabel =
                                if (DesktopUpdateManager.isGitEnvironment) {
                                    "Git-Managed (${DesktopUpdateManager.currentBranch})"
                                } else {
                                    "Standalone"
                                }
                            Text(
                                text = "${DesktopUpdateManager.currentVersionTag} • Commit ${DesktopUpdateManager.currentCommit} • $envLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = AnchorColors.HarborAction,
                            )
                        }
                    }

                    // Check for updates button
                    val isChecking = updateState is UpdateState.Checking
                    val isUpdating = updateState is UpdateState.Updating
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                DesktopUpdateManager.checkForUpdates(silent = false)
                            }
                        },
                        enabled = !isChecking && !isUpdating,
                        border = BorderStroke(1.dp, AnchorColors.HarborPrimary),
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = AnchorColors.HarborPrimary,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Checking...", color = AnchorColors.HarborPrimary)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = AnchorColors.HarborPrimary,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Check for Updates", color = AnchorColors.HarborPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // State Details
                when (val state = updateState) {
                    is UpdateState.Idle -> {
                        Text(
                            text = "Anchor automatically checks GitHub for updates. Click \"Check for Updates\" anytime to verify without manual redownloads.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }

                    is UpdateState.Checking -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = AnchorColors.HarborAction,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Checking remote GitHub repository for new commits & releases...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                    }

                    is UpdateState.UpToDate -> {
                        Surface(
                            shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                            color = Color(0xFF003829).copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF81C784),
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Anchor is fully up to date!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF81C784),
                                    )
                                    Text(
                                        text =
                                            "You are on the latest version (${state.currentVersion} • " +
                                                "${state.commitHash}). All features and bugfixes are active.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }

                    is UpdateState.UpdateAvailable -> {
                        Surface(
                            shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                            color = AnchorColors.HarborAction.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, AnchorColors.HarborAction.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = null,
                                            tint = AnchorColors.HarborAction,
                                            modifier = Modifier.size(22.dp),
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "New Update Available! (${state.commitsBehind} new commit${if (state.commitsBehind > 1) "s" else ""})",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                            )
                                            Text(
                                                text = "Latest: ${state.latestCommit}${if (state.latestTag != null) " (${state.latestTag})" else ""}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = AnchorColors.HarborAction,
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                DesktopUpdateManager.performUpdate { log ->
                                                    updateLogs = updateLogs + log
                                                }
                                            }
                                        },
                                        colors =
                                            ButtonDefaults.buttonColors(
                                                containerColor = AnchorColors.HarborAction,
                                                contentColor = AnchorColors.HarborDock,
                                            ),
                                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Update Anchor Now", fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (state.changelog.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Recent Changes:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.7f),
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        color = AnchorColors.HarborBackground.copy(alpha = 0.8f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            for (line in state.changelog.take(5)) {
                                                Text(
                                                    text = "• $line",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    color = Color.White.copy(alpha = 0.85f),
                                                )
                                            }
                                            if (state.changelog.size > 5) {
                                                Text(
                                                    text = "... and ${state.changelog.size - 5} more commits",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White.copy(alpha = 0.5f),
                                                    modifier = Modifier.padding(top = 4.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is UpdateState.Updating -> {
                        Surface(
                            shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                            color = AnchorColors.HarborPrimary.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, AnchorColors.HarborPrimary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.5.dp,
                                        color = AnchorColors.HarborPrimary,
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = state.stage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                }
                                if (state.logs.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            for (log in state.logs.takeLast(4)) {
                                                Text(
                                                    text = log,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    color = AnchorColors.HarborPrimary.copy(alpha = 0.9f),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is UpdateState.UpdateSuccess -> {
                        Surface(
                            shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                            color = Color(0xFF003829).copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, Color(0xFF4CAF50)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF81C784),
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Update Complete!",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF81C784),
                                        )
                                        Text(
                                            text = state.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.85f),
                                        )
                                    }
                                }

                                if (state.requiresRestart) {
                                    Button(
                                        onClick = {
                                            DesktopUpdateManager.restartApplication()
                                        },
                                        colors =
                                            ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF4CAF50),
                                                contentColor = Color.Black,
                                            ),
                                        shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RestartAlt,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Restart Anchor", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    is UpdateState.Error -> {
                        Surface(
                            shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                            color = Color(0xFF380000).copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, Color(0xFFE57373).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFE57373),
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE57373),
                                    )
                                }
                                if (!state.details.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = state.details,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Safe Persistence Reassurance Banner
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = AnchorColors.HarborPrimary.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text =
                            "Zero Data Loss Guarantee: Your focus sessions, streak, milestones, " +
                                "and settings in %APPDATA%\\Anchor are 100% preserved during all updates.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }

    // Confirmation Dialog for Clean Slate / Reset Actions
    pendingResetAction?.let { action ->
        Dialog(onDismissRequest = { pendingResetAction = null }) {
            Surface(
                modifier =
                    Modifier
                        .width(500.dp)
                        .clip(RoundedCornerShape(AnchorSpacing.radiusScene))
                        .border(
                            1.dp,
                            when (action) {
                                ResetType.CLEAR_TASKS -> Color(0xFFFFA000)
                                ResetType.RESET_STREAK -> Color(0xFF7E57C2)
                                ResetType.FACTORY_RESET -> Color(0xFFE53935)
                            }.copy(alpha = 0.6f),
                            RoundedCornerShape(AnchorSpacing.radiusScene),
                        ),
                shape = RoundedCornerShape(AnchorSpacing.radiusScene),
                color = AnchorColors.HarborDock,
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (action) {
                                            ResetType.CLEAR_TASKS -> Color(0xFFFFA000).copy(alpha = 0.2f)
                                            ResetType.RESET_STREAK -> Color(0xFF7E57C2).copy(alpha = 0.2f)
                                            ResetType.FACTORY_RESET -> Color(0xFFE53935).copy(alpha = 0.2f)
                                        },
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint =
                                    when (action) {
                                        ResetType.CLEAR_TASKS -> Color(0xFFFFA000)
                                        ResetType.RESET_STREAK -> Color(0xFF7E57C2)
                                        ResetType.FACTORY_RESET -> Color(0xFFE53935)
                                    },
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text =
                                    when (action) {
                                        ResetType.CLEAR_TASKS -> "Clear Task Backlog?"
                                        ResetType.RESET_STREAK -> "Reset Streak & Sessions?"
                                        ResetType.FACTORY_RESET -> "Factory Reset All Data?"
                                    },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                text = "ADHD Clean Slate • Zero Guilt Guarantee",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text =
                            when (action) {
                                ResetType.CLEAR_TASKS ->
                                    "This will delete all completed and pending tasks in your milestone list and replace them with 1 clean anchor starter task. Your focus minutes, streaks, block rules, and AI models will NOT be affected."
                                ResetType.RESET_STREAK ->
                                    "This will clear past focus session records and restart your streak at Day 1. Use this to release the guilt of a broken streak and rebuild momentum today. Your tasks and settings will NOT be affected."
                                ResetType.FACTORY_RESET ->
                                    "This will wipe all tasks, focus sessions, and reset your streak back to initial out-of-the-box defaults. Your downloaded AI model files in %APPDATA% will remain safe."
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        lineHeight = 20.sp,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        OutlinedButton(
                            onClick = { pendingResetAction = null },
                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        ) {
                            Text("Cancel", color = Color.White)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = {
                                val currentAction = action
                                pendingResetAction = null
                                scope.launch {
                                    when (currentAction) {
                                        ResetType.CLEAR_TASKS -> {
                                            db.clearAllTasks()
                                            resetFeedbackBanner = "Task backlog cleared! Re-anchored with 1 clean starter task."
                                        }
                                        ResetType.RESET_STREAK -> {
                                            db.resetFocusHistoryAndStreak()
                                            resetFeedbackBanner = "Focus history reset! Starting fresh on Day 1."
                                        }
                                        ResetType.FACTORY_RESET -> {
                                            db.resetAllData()
                                            resetFeedbackBanner = "Anchor successfully factory reset to clean slate defaults."
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor =
                                        when (action) {
                                            ResetType.CLEAR_TASKS -> Color(0xFFFFA000)
                                            ResetType.RESET_STREAK -> Color(0xFF7E57C2)
                                            ResetType.FACTORY_RESET -> Color(0xFFE53935)
                                        },
                                    contentColor = Color.White,
                                ),
                        ) {
                            Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                when (action) {
                                    ResetType.CLEAR_TASKS -> "Yes, Clear Tasks"
                                    ResetType.RESET_STREAK -> "Yes, Reset Streak"
                                    ResetType.FACTORY_RESET -> "Yes, Factory Reset"
                                },
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
) {
    Surface(
        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
        color = AnchorColors.HarborBackground.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AnchorColors.HarborPrimary,
            )
        }
    }
}

@Composable
private fun ResetOptionRow(
    title: String,
    description: String,
    buttonText: String,
    buttonColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(AnchorSpacing.radiusCard),
        color = AnchorColors.HarborBackground.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(end = 16.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                )
            }

            OutlinedButton(
                onClick = onClick,
                shape = RoundedCornerShape(AnchorSpacing.radiusPill),
                border = BorderStroke(1.dp, buttonColor.copy(alpha = 0.8f)),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = buttonColor,
                    ),
            ) {
                Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(buttonText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
