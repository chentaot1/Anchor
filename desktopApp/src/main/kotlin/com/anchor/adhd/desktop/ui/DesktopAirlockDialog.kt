package com.anchor.adhd.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.anchor.adhd.desktop.ai.DesktopAirlockMode
import com.anchor.adhd.desktop.ai.DesktopAirlockSession
import com.anchor.adhd.desktop.ai.DesktopAirlockState
import com.anchor.adhd.ui.components.AnchorHairline
import com.anchor.adhd.ui.components.AnchorListRow
import com.anchor.adhd.ui.components.AnchorQuietButton
import com.anchor.adhd.ui.components.AnchorSectionHeader
import com.anchor.adhd.ui.components.HarborCtaButton
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens

/** Preview first, then acknowledge a complete batch before closing the app-owned draft. */
@Composable
internal fun DesktopAirlockDialog(
    state: DesktopAirlockState,
    session: DesktopAirlockSession,
) {
    Dialog(onDismissRequest = session::dismiss) {
        DesktopAirlockDialogSurface(
            state = state,
            session = session,
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun DesktopAirlockDialogSurface(
    state: DesktopAirlockState,
    session: DesktopAirlockSession,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAnchorTokens.current
    val captainTextColor = if (tokens.isDark) tokens.captain else tokens.captainInk
    val captainButtonContent = if (tokens.isDark) tokens.captainInk else tokens.surface
    val scroll = rememberScrollState()

    BoxWithConstraints(modifier = modifier) {
        val heightLimit = if (constraints.hasBoundedHeight) (maxHeight - 32.dp).coerceAtLeast(200.dp) else 600.dp
        val compactHeight = heightLimit < 440.dp

        Surface(
            modifier =
                Modifier
                    .widthIn(max = 540.dp)
                    .fillMaxWidth()
                    .heightIn(max = heightLimit),
            shape = RoundedCornerShape(AnchorSpacing.radiusSheet),
            color = tokens.surface,
            contentColor = tokens.ink,
            border = BorderStroke(1.dp, tokens.rule),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Box {
                Column(
                    modifier =
                        Modifier
                            .verticalScroll(scroll)
                            .padding(start = 24.dp, top = 22.dp, end = 32.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(38.dp)
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
                        Spacer(Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "The Airlock",
                                style = MaterialTheme.typography.titleLarge,
                                color = tokens.ink,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                text = "Dump the chaos and review thoughts before saving.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.muted,
                            )
                        }
                        IconButton(onClick = session::dismiss, enabled = !state.isSaving) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Airlock",
                                tint = tokens.muted,
                            )
                        }
                    }

                    // Step 1: Brain dump
                    OutlinedTextField(
                        value = state.draft,
                        onValueChange = session::edit,
                        readOnly = state.isSaving,
                        label = { Text("Brain dump") },
                        placeholder = {
                            Text(
                                text = "What is overwhelming you? Enter one task or several thoughts.",
                                color = tokens.muted,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = if (compactHeight) 2 else 3,
                        maxLines = if (compactHeight) 3 else 5,
                        shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = tokens.captain,
                                unfocusedBorderColor = tokens.fieldEdge,
                                focusedTextColor = tokens.ink,
                                unfocusedTextColor = tokens.ink,
                                focusedContainerColor = tokens.paper,
                                unfocusedContainerColor = tokens.paper,
                                focusedLabelColor = tokens.captain,
                                unfocusedLabelColor = tokens.muted,
                                cursorColor = tokens.captain,
                            ),
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { session.generate(DesktopAirlockMode.STEPS) },
                            enabled = !state.isSaving && state.draft.isNotBlank(),
                            shape = RoundedCornerShape(AnchorSpacing.radiusButton),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = tokens.captain,
                                    contentColor = captainButtonContent,
                                    disabledContainerColor = tokens.captain.copy(alpha = 0.45f),
                                    disabledContentColor = captainButtonContent.copy(alpha = 0.45f),
                                ),
                            elevation =
                                ButtonDefaults.buttonElevation(
                                    defaultElevation = 0.dp,
                                    pressedElevation = 0.dp,
                                    focusedElevation = 0.dp,
                                    hoveredElevation = 0.dp,
                                    disabledElevation = 0.dp,
                                ),
                            modifier = Modifier.heightIn(min = 40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Decompose (5-min steps)", style = MaterialTheme.typography.labelLarge)
                        }
                        AnchorQuietButton(
                            label = "Extract tasks",
                            onClick = { session.generate(DesktopAirlockMode.TASKS) },
                            outlined = true,
                            tint = tokens.ink,
                            enabled = !state.isSaving && state.draft.isNotBlank(),
                        )
                    }

                    if (state.isSaving || state.isRefining) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = tokens.captain,
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = if (state.isSaving) "Saving tasks…" else "Refining preview…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.ink,
                            )
                        }
                    }

                    state.feedback?.let { message ->
                        Text(
                            text = message,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            color = if (state.isError) tokens.clayRose else tokens.sage,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    // Step 2 & 3: Preview and choose next action
                    state.preview?.let { preview ->
                        AnchorHairline()

                        AnchorSectionHeader(
                            title =
                                if (preview.mode == DesktopAirlockMode.STEPS) {
                                    "Choose a 5-minute anchor"
                                } else {
                                    "Choose a task for now"
                                },
                            trailingLabel = "${preview.items.size} ready",
                        )

                        Text(
                            text = "Preview for: ${preview.input}",
                            color = tokens.muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text =
                                "Choose an item to set as next action and save the whole list, " +
                                    "or save all to your inbox. Nothing is saved until you choose.",
                            color = tokens.ink2,
                            style = MaterialTheme.typography.bodySmall,
                        )

                        Column(modifier = Modifier.fillMaxWidth()) {
                            preview.items.forEachIndexed { index, step ->
                                AnchorListRow(
                                    title = step,
                                    trailingText = if (preview.mode == DesktopAirlockMode.STEPS) "5m" else "15m",
                                    onClick =
                                        if (!state.isSaving) {
                                            { session.savePreview(preview.key, index) }
                                        } else {
                                            null
                                        },
                                    minHeight = 44.dp,
                                    showDivider = index < preview.items.lastIndex,
                                    leading = {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = tokens.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                )
                            }
                        }

                        if (preview.secondaryTasks.isNotEmpty()) {
                            AnchorSectionHeader(
                                title = "Other thoughts to save",
                                trailingLabel = "15m each",
                            )
                            Column(modifier = Modifier.fillMaxWidth()) {
                                preview.secondaryTasks.forEachIndexed { idx, secondary ->
                                    AnchorListRow(
                                        title = secondary,
                                        minHeight = 40.dp,
                                        showDivider = idx < preview.secondaryTasks.lastIndex,
                                    )
                                }
                            }
                        }

                        HarborCtaButton(
                            onClick = { session.savePreview(preview.key) },
                            enabled = !state.isSaving,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                        ) {
                            Text(
                                text = "Save all to inbox (${preview.items.size + preview.secondaryTasks.size})",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }

                if (scroll.maxValue > 0) {
                    Box(Modifier.matchParentSize()) {
                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(scroll),
                            modifier =
                                Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .padding(top = 18.dp, bottom = 18.dp, end = 6.dp)
                                    .width(8.dp),
                            style =
                                defaultScrollbarStyle().copy(
                                    unhoverColor = tokens.rule,
                                    hoverColor = tokens.primary,
                                ),
                        )
                    }
                }
            }
        }
    }
}
