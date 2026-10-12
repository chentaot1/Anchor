package com.anchor.adhd.ui.chat

import com.anchor.adhd.ai.AiBreakdownResult
import com.anchor.adhd.ai.AiBrainDumpResult
import com.anchor.adhd.ai.AiReplanResult
import com.anchor.adhd.ai.AiTriageResult

/** A single message in the Anchor Chat thread (persisted for session memory). */
data class ChatMessageUi(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val action: ChatAction? = null,
    val undoable: Boolean = false,
    val timestampMillis: Long = System.currentTimeMillis(),
)

/**
 * A tappable action card attached to a reply (confirm-flow verbs).
 * `messageId` ties the action to its chat message so the VM can clear/mark
 * the card as applied, preventing duplicate application (idempotency).
 */
sealed class ChatAction {
    data class ApplyBreakdown(val messageId: Long, val parentTitle: String, val result: AiBreakdownResult) : ChatAction()
    data class ApplyBrainDump(val messageId: Long, val result: AiBrainDumpResult) : ChatAction()
    data class ApplyTriage(val messageId: Long, val result: AiTriageResult) : ChatAction()
    data class ApplyReplan(val messageId: Long, val result: AiReplanResult) : ChatAction()
    data class StartFocus(val messageId: Long, val taskId: Long?, val minutes: Int = 20) : ChatAction()
}
