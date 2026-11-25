@file:OptIn(ExperimentalLayoutApi::class)

package com.anchor.adhd.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anchor.adhd.ui.components.AnchorChip
import com.anchor.adhd.ui.components.AnchorHairline
import com.anchor.adhd.ui.components.AnchorQuietButton
import com.anchor.adhd.ui.components.AnchorWell
import com.anchor.adhd.ui.components.AnchorWellTone
import com.anchor.adhd.ui.components.HarborCtaButton
import com.anchor.adhd.ui.components.HarborEmptyState
import com.anchor.adhd.ui.theme.AnchorSpacing
import com.anchor.adhd.ui.theme.LocalAnchorTokens
import com.anchor.adhd.ui.vm.AnchorViewModel

@Composable
fun ChatScreen(vm: AnchorViewModel, modifier: Modifier = Modifier) {
    val tokens = LocalAnchorTokens.current
    val messages by vm.chatMessages.collectAsStateWithLifecycle()
    val busy by vm.chatBusy.collectAsStateWithLifecycle()
    val lastTask by vm.lastChatTaskTitle.collectAsStateWithLifecycle()
    val panic by vm.chatSheetPanic.collectAsStateWithLifecycle()
    val undoCount by vm.undoCount.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var panicInput by rememberSaveable { mutableStateOf("") }
    val chips = remember(lastTask, undoCount, panic) {
        vm.chatSuggestionChips().filter { !panic || it != "Think about this" }
    }
    val listState = rememberLazyListState()

    // Autoscroll to newest (reverseLayout anchors to bottom; index 0 is newest / typing).
    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty() || busy) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            ChatInputBar(
                input = input,
                onInput = { input = it },
                onSend = {
                    if (input.isNotBlank()) {
                        vm.sendChat(input)
                        input = ""
                    }
                },
                enabled = !busy,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (panic) {
                OutlinedTextField(
                    value = panicInput,
                    onValueChange = { panicInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AnchorSpacing.screenHorizontal, vertical = 8.dp),
                    placeholder = { Text("Dump — commas or new lines", color = tokens.muted) },
                    minLines = 2,
                    shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = tokens.captain,
                        unfocusedBorderColor = tokens.fieldEdge,
                        focusedTextColor = tokens.ink,
                        unfocusedTextColor = tokens.ink,
                        focusedContainerColor = tokens.surface,
                        unfocusedContainerColor = tokens.surface,
                        cursorColor = tokens.captain,
                    ),
                )
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AnchorSpacing.screenHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HarborCtaButton(
                        onClick = {
                            if (panicInput.isNotBlank()) {
                                vm.panicDump(panicInput)
                                panicInput = ""
                            }
                        },
                        enabled = panicInput.isNotBlank(),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Add to today", style = MaterialTheme.typography.labelLarge)
                    }
                    AnchorChip(
                        label = "Think about this",
                        selected = false,
                        onClick = { vm.setThinkMode() },
                        captainAccent = true,
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }

            SuggestionChips(chips = chips, onPick = { vm.sendChat(it) }, enabled = !busy)

            if (messages.isEmpty() && !busy) {
                ChatEmptyState(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = AnchorSpacing.screenHorizontal),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(
                        horizontal = AnchorSpacing.screenHorizontal,
                        vertical = 10.dp,
                    ),
                ) {
                    if (busy) {
                        item(key = "typing") { TypingIndicator() }
                    }
                    items(messages, key = { it.id }) { msg ->
                        ChatBubble(
                            msg = msg,
                            onAction = { vm.actOnChatAction(it) },
                            onUndo = { vm.undoChat() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatEmptyState(modifier: Modifier = Modifier) {
    val tokens = LocalAnchorTokens.current
    val captainTextColor = if (tokens.isDark) tokens.captain else tokens.captainInk

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        HarborEmptyState(
            title = "Ask Harbor Captain",
            message = "Dump what's in your head. I'll add it, remind you, break it down, or start a focus session.",
        )
        AnchorWell(
            tone = AnchorWellTone.Captain,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Try asking",
                style = MaterialTheme.typography.labelLarge,
                color = captainTextColor,
            )
            Text(
                text = "“buy milk, call mom”\n“what's today?”\n“remind me at 5 to email”\n“break down my essay”",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.ink2,
            )
        }
    }
}

@Composable
private fun TypingIndicator(modifier: Modifier = Modifier) {
    val tokens = LocalAnchorTokens.current
    val captainTextColor = if (tokens.isDark) tokens.captain else tokens.captainInk

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.Start,
    ) {
        AnchorWell(
            tone = AnchorWellTone.Captain,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            modifier = Modifier.widthIn(max = 220.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = captainTextColor,
                )
                Text(
                    text = "Thinking…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = captainTextColor,
                )
            }
        }
    }
}

@Composable
private fun SuggestionChips(chips: List<String>, onPick: (String) -> Unit, enabled: Boolean) {
    if (chips.isEmpty()) return
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AnchorSpacing.screenHorizontal, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            AnchorChip(
                label = chip,
                selected = false,
                onClick = { onPick(chip) },
                captainAccent = true,
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun ChatBubble(
    msg: ChatMessageUi,
    onAction: (ChatAction) -> Unit,
    onUndo: () -> Unit,
) {
    val tokens = LocalAnchorTokens.current
    val captainTextColor = if (tokens.isDark) tokens.captain else tokens.captainInk
    val captainButtonContent = if (tokens.isDark) tokens.captainInk else tokens.surface

    if (msg.fromUser) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AnchorWell(
                tone = AnchorWellTone.Neutral,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.ink,
                )
            }
            Text(
                text = formatTime(msg.timestampMillis),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = tokens.muted,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AnchorWell(
                tone = AnchorWellTone.Captain,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Text(
                    text = "Harbor Captain",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = captainTextColor,
                )
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.ink,
                )
                msg.action?.let { action ->
                    if (action is ChatAction.StartFocus) {
                        HarborCtaButton(
                            onClick = { onAction(action) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(
                                text = actionLabel(action),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    } else {
                        Button(
                            onClick = { onAction(action) },
                            shape = RoundedCornerShape(AnchorSpacing.radiusButton),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = tokens.captain,
                                contentColor = captainButtonContent,
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 0.dp,
                                focusedElevation = 0.dp,
                                hoveredElevation = 0.dp,
                                disabledElevation = 0.dp,
                            ),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(
                                text = actionLabel(action),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
                if (msg.undoable) {
                    AnchorQuietButton(
                        label = "Undo",
                        onClick = onUndo,
                        outlined = false,
                        tint = captainTextColor,
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            Text(
                text = formatTime(msg.timestampMillis),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = tokens.muted,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    return java.time.Instant.ofEpochMilli(millis).atZone(zone)
        .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
}

private fun actionLabel(action: ChatAction): String = when (action) {
    is ChatAction.ApplyBreakdown -> "Add to today"
    is ChatAction.ApplyBrainDump -> "Add tasks"
    is ChatAction.ApplyTriage -> "Apply order"
    is ChatAction.ApplyReplan -> "Apply replan"
    is ChatAction.StartFocus -> "Start focus"
}

@Composable
private fun ChatInputBar(
    input: String,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    val tokens = LocalAnchorTokens.current
    val captainButtonContent = if (tokens.isDark) tokens.captainInk else tokens.surface
    val canSend = enabled && input.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .background(tokens.paper2),
    ) {
        AnchorHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask Captain or add a task…", color = tokens.muted) },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                shape = RoundedCornerShape(AnchorSpacing.radiusChip),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = tokens.captain,
                    unfocusedBorderColor = tokens.fieldEdge,
                    focusedTextColor = tokens.ink,
                    unfocusedTextColor = tokens.ink,
                    focusedContainerColor = tokens.surface,
                    unfocusedContainerColor = tokens.surface,
                    cursorColor = tokens.captain,
                ),
            )
            Button(
                onClick = onSend,
                enabled = canSend,
                shape = RoundedCornerShape(AnchorSpacing.radiusButton),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.captain,
                    contentColor = captainButtonContent,
                    disabledContainerColor = tokens.captain.copy(alpha = 0.45f),
                    disabledContentColor = captainButtonContent.copy(alpha = 0.45f),
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                    disabledElevation = 0.dp,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
