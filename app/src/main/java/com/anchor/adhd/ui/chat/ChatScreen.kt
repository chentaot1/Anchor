@file:OptIn(ExperimentalLayoutApi::class)

package com.anchor.adhd.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.anchor.adhd.ui.theme.AnchorColors
import com.anchor.adhd.ui.vm.AnchorViewModel

@Composable
fun ChatScreen(vm: AnchorViewModel, modifier: Modifier = Modifier) {
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
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
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
                enabled = !busy
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (panic) {
                OutlinedTextField(
                    value = panicInput,
                    onValueChange = { panicInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Dump — commas or new lines") },
                    minLines = 2
                )
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (panicInput.isNotBlank()) {
                                vm.panicDump(panicInput)
                                panicInput = ""
                            }
                        },
                        enabled = panicInput.isNotBlank()
                    ) { Text("Add to Today") }
                    AssistChip(onClick = { vm.setThinkMode() }, label = { Text("Think about this") })
                }
            }
            SuggestionChips(chips = chips, onPick = { vm.sendChat(it) }, enabled = !busy)
            if (messages.isEmpty() && !busy) {
                ChatEmptyState(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp
                    )
                ) {
                    if (busy) {
                        item(key = "typing") { TypingIndicator() }
                    }
                    items(messages, key = { it.id }) { msg ->
                        ChatBubble(msg, onAction = { vm.actOnChatAction(it) }, onUndo = { vm.undoChat() })
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Hey — I'm Anchor.",
            style = MaterialTheme.typography.headlineSmall,
            color = AnchorColors.HarborAi
        )
        Text(
            "Dump what's in your head. I'll add it, remind you, break it down, or start a focus.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            "Try: “buy milk, call mom”\n“what's today?”\n“remind me at 5 to email”\n“break down my essay”",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun TypingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            Modifier
                .background(AnchorColors.HarborAi.copy(alpha = 0.18f), RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text("Thinking…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SuggestionChips(chips: List<String>, onPick: (String) -> Unit, enabled: Boolean) {
    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        chips.forEach { chip ->
            AssistChip(
                onClick = { onPick(chip) },
                enabled = enabled,
                label = { Text(chip) }
            )
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessageUi, onAction: (ChatAction) -> Unit, onUndo: () -> Unit) {
    if (msg.fromUser) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .background(AnchorColors.HarborAction.copy(alpha = 0.25f), RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(msg.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                formatTime(msg.timestampMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, end = 4.dp)
            )
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Column(
                Modifier.widthIn(max = 320.dp)
            ) {
                Box(
                    Modifier
                        .background(AnchorColors.HarborAi.copy(alpha = 0.18f), RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(msg.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                msg.action?.let { action ->
                    Button(
                        onClick = { onAction(action) },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(actionLabel(action))
                    }
                }
                if (msg.undoable) {
                    TextButton(
                        onClick = onUndo,
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Text("Undo")
                    }
                }
                Text(
                    formatTime(msg.timestampMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp)
                )
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    return java.time.Instant.ofEpochMilli(millis).atZone(zone)
        .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
}

private fun actionLabel(action: ChatAction): String = when (action) {
    is ChatAction.ApplyBreakdown -> "Add to Today"
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
    enabled: Boolean
) {
    Row(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ask or add a task…") },
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            shape = RoundedCornerShape(24.dp)
        )
        FilledTonalButton(onClick = onSend, enabled = enabled && input.isNotBlank()) {
            Icon(Icons.Default.Send, contentDescription = "Send")
        }
    }
}
