package com.anchor.adhd.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.anchor.adhd.desktop.ai.DesktopAirlockMode
import com.anchor.adhd.desktop.ai.DesktopAirlockSession
import com.anchor.adhd.desktop.ai.DesktopAirlockState
import com.anchor.adhd.desktop.theme.AnchorColors
import com.anchor.adhd.desktop.theme.AnchorSpacing
import androidx.compose.foundation.shape.RoundedCornerShape

/** Preview first, then acknowledge a complete batch before closing the app-owned draft. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun DesktopAirlockDialog(state: DesktopAirlockState, session: DesktopAirlockSession) {
    val scroll = rememberScrollState()
    Dialog(onDismissRequest = session::dismiss) {
        BoxWithConstraints {
            val heightLimit = if (constraints.hasBoundedHeight) (maxHeight - 32.dp).coerceAtLeast(200.dp) else 600.dp
            Surface(
                modifier = Modifier.widthIn(max = 540.dp).fillMaxWidth().heightIn(max = heightLimit),
                shape = RoundedCornerShape(AnchorSpacing.radiusScene),
                color = AnchorColors.HarborDock,
                border = BorderStroke(1.dp, AnchorColors.HarborAi.copy(alpha = 0.4f)),
            ) {
                Box {
                    Column(Modifier.verticalScroll(scroll).padding(start = 24.dp, top = 24.dp, end = 32.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AnchorColors.HarborAi)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Airlock Brain-Dump", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Review thoughts before saving them", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                            }
                            IconButton(onClick = session::dismiss, enabled = !state.isSaving) {
                                Icon(Icons.Default.Close, contentDescription = "Close Airlock", tint = Color.White.copy(alpha = 0.7f))
                            }
                        }
                        OutlinedTextField(
                            value = state.draft,
                            onValueChange = session::edit,
                            readOnly = state.isSaving,
                            label = { Text("Brain dump") },
                            placeholder = { Text("What is overwhelming you? Enter one task or several thoughts.") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 5,
                            shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AnchorColors.HarborAi,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                            ),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { session.generate(DesktopAirlockMode.STEPS) },
                                enabled = !state.isSaving && state.draft.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = AnchorColors.HarborAi, contentColor = Color.White),
                            ) { Text("Decompose (5-Min Steps)") }
                            OutlinedButton(
                                onClick = { session.generate(DesktopAirlockMode.TASKS) },
                                enabled = !state.isSaving && state.draft.isNotBlank(),
                            ) { Text("Extract Tasks", color = AnchorColors.HarborPrimary) }
                        }
                        if (state.isSaving || state.isRefining) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(Modifier.size(18.dp), color = AnchorColors.HarborAi, strokeWidth = 2.dp)
                                Text(if (state.isSaving) "Saving tasks..." else "Refining preview...", color = Color.White)
                            }
                        }
                        state.feedback?.let { message ->
                            Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                color = if (state.isError) AnchorColors.HarborAction else AnchorColors.HarborPrimary,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        state.preview?.let { preview ->
                            Text(if (preview.mode == DesktopAirlockMode.STEPS) "CHOOSE A 5-MINUTE ANCHOR" else "CHOOSE A TASK FOR NOW",
                                color = AnchorColors.HarborAction, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text("Preview for: ${preview.input}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                            Text("Choose an item to set NOW and save the whole list, or save all to your inbox. Nothing is saved until you choose.",
                                color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                            preview.items.forEachIndexed { index, step ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable(enabled = !state.isSaving, role = Role.Button) {
                                        session.savePreview(preview.key, index)
                                    },
                                    shape = RoundedCornerShape(AnchorSpacing.radiusCard),
                                    color = AnchorColors.HarborBackground.copy(alpha = 0.8f),
                                    border = BorderStroke(1.dp, AnchorColors.HarborPrimary.copy(alpha = 0.25f)),
                                ) {
                                    Row(Modifier.heightIn(min = 44.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AnchorColors.HarborPrimary, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text(step, color = Color.White, modifier = Modifier.weight(1f))
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (preview.mode == DesktopAirlockMode.STEPS) "5m" else "15m", color = AnchorColors.HarborPrimary)
                                    }
                                }
                            }
                            if (preview.secondaryTasks.isNotEmpty()) {
                                Text("OTHER THOUGHTS TO SAVE (15m EACH)", color = AnchorColors.HarborPrimary, style = MaterialTheme.typography.labelLarge)
                                preview.secondaryTasks.forEach { Text(it, color = Color.White) }
                            }
                            Button(onClick = { session.savePreview(preview.key) }, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                                Text("Save All to Inbox (${preview.items.size + preview.secondaryTasks.size})")
                            }
                        }
                    }
                    if (scroll.maxValue > 0) {
                        Box(Modifier.matchParentSize()) {
                            VerticalScrollbar(
                                adapter = rememberScrollbarAdapter(scroll),
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = 12.dp, bottom = 12.dp, end = 4.dp).width(8.dp),
                                style = defaultScrollbarStyle().copy(
                                    unhoverColor = Color.White.copy(alpha = 0.35f),
                                    hoverColor = AnchorColors.HarborPrimary,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
