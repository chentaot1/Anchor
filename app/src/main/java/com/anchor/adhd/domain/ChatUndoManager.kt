package com.anchor.adhd.domain

import com.anchor.adhd.data.model.InboxState

enum class ChatUndoSource {
    PANIC,
    CHAT,
    SHARE,
    APPLY,
}

/**
 * Holds reversible chat/panic/share/apply operations for a rolling 12h window
 * (not wall-clock midnight). Persist via [ChatUndoOpEntity]; this class is the in-memory stack.
 */
sealed class ChatUndoOp {
    data class CreatedTasks(val taskIds: List<Long>) : ChatUndoOp()
    data class CompletionChanged(val taskIds: List<Long>, val wasCompleted: Boolean) : ChatUndoOp()
    data class InboxMoved(val taskId: Long, val fromState: InboxState) : ChatUndoOp()
    data class ScheduleChanged(
        val taskId: Long,
        val previousStartMillis: Long?,
        val previousDuration: Int,
    ) : ChatUndoOp()
}

data class ChatUndoEntry(
    val dbId: Long = 0,
    val op: ChatUndoOp,
    val source: ChatUndoSource = ChatUndoSource.CHAT,
    val atMillis: Long,
)

class ChatUndoManager(
    private val windowMs: Long = TWELVE_HOURS,
) {
    private val stack = ArrayDeque<ChatUndoEntry>()

    fun push(
        op: ChatUndoOp,
        nowMillis: Long = System.currentTimeMillis(),
        source: ChatUndoSource = ChatUndoSource.CHAT,
        dbId: Long = 0,
    ) {
        prune(nowMillis)
        stack.addLast(ChatUndoEntry(dbId = dbId, op = op, source = source, atMillis = nowMillis))
    }

    /** Returns the most recent non-expired op, and consumes it. */
    fun pop(nowMillis: Long = System.currentTimeMillis()): ChatUndoOp? = popEntry(nowMillis)?.op

    fun popEntry(nowMillis: Long = System.currentTimeMillis()): ChatUndoEntry? {
        prune(nowMillis)
        return stack.removeLastOrNull()
    }

    fun canUndo(nowMillis: Long = System.currentTimeMillis()): Boolean {
        prune(nowMillis)
        return stack.isNotEmpty()
    }

    fun snapshot(nowMillis: Long = System.currentTimeMillis()): List<ChatUndoEntry> {
        prune(nowMillis)
        return stack.toList()
    }

    fun restore(entries: List<ChatUndoEntry>, nowMillis: Long = System.currentTimeMillis()) {
        stack.clear()
        entries.sortedBy { it.atMillis }.forEach { e ->
            push(e.op, e.atMillis, e.source, e.dbId)
        }
        prune(nowMillis)
    }

    private fun prune(now: Long) {
        while (stack.isNotEmpty() && now - stack.first().atMillis > windowMs) {
            stack.removeFirst()
        }
    }

    fun clear() = stack.clear()

    companion object {
        const val TWELVE_HOURS = 12 * 60 * 60 * 1000L
    }
}

object ChatUndoCodec {
    fun kind(op: ChatUndoOp): String = when (op) {
        is ChatUndoOp.CreatedTasks -> "created"
        is ChatUndoOp.CompletionChanged -> "completion"
        is ChatUndoOp.InboxMoved -> "inbox"
        is ChatUndoOp.ScheduleChanged -> "schedule"
    }

    fun payload(op: ChatUndoOp): String = when (op) {
        is ChatUndoOp.CreatedTasks -> op.taskIds.joinToString(",")
        is ChatUndoOp.CompletionChanged -> "${op.wasCompleted}|${op.taskIds.joinToString(",")}"
        is ChatUndoOp.InboxMoved -> "${op.taskId}|${op.fromState.name}"
        is ChatUndoOp.ScheduleChanged -> "${op.taskId}|${op.previousStartMillis ?: ""}|${op.previousDuration}"
    }

    fun decode(kind: String, payload: String): ChatUndoOp? = when (kind) {
        "created" -> ChatUndoOp.CreatedTasks(parseIds(payload))
        "completion" -> {
            val parts = payload.split("|", limit = 2)
            val was = parts.getOrNull(0)?.toBooleanStrictOrNull() ?: false
            ChatUndoOp.CompletionChanged(parseIds(parts.getOrNull(1).orEmpty()), was)
        }
        "inbox" -> {
            val parts = payload.split("|", limit = 2)
            val id = parts.getOrNull(0)?.toLongOrNull() ?: return null
            val state = runCatching { InboxState.valueOf(parts.getOrNull(1) ?: "TODAY") }.getOrDefault(InboxState.TODAY)
            ChatUndoOp.InboxMoved(id, state)
        }
        "schedule" -> {
            val parts = payload.split("|")
            val id = parts.getOrNull(0)?.toLongOrNull() ?: return null
            val start = parts.getOrNull(1)?.toLongOrNull()
            val duration = parts.getOrNull(2)?.toIntOrNull() ?: 20
            ChatUndoOp.ScheduleChanged(id, start, duration)
        }
        else -> null
    }

    private fun parseIds(raw: String): List<Long> =
        raw.split(",").mapNotNull { it.trim().toLongOrNull() }
}

/**
 * Reverses a chat operation against the repository. Batch create-undo deletes only
 * still-inbox / unedited items (skips completed, moved, or scheduled).
 */
object ChatUndoApplier {
    suspend fun undo(
        op: ChatUndoOp,
        repository: com.anchor.adhd.data.repository.PlanRepository,
    ): String? = when (op) {
        is ChatUndoOp.CreatedTasks -> {
            var removed = 0
            op.taskIds.forEach { id ->
                val task = repository.getTask(id) ?: return@forEach
                if (!isUneditedInbox(task)) return@forEach
                repository.getChildren(id).forEach { child ->
                    if (isUneditedInbox(child) || child.parentTaskId == id) {
                        if (!child.isCompleted) repository.deleteTask(child.id)
                    }
                }
                repository.deleteTask(id)
                removed++
            }
            when {
                removed == 0 -> "Nothing to undo — those were already changed"
                removed == 1 -> "Undid — removed the task just added"
                else -> "Undid — removed $removed tasks just added"
            }
        }
        is ChatUndoOp.CompletionChanged -> {
            if (op.wasCompleted) {
                repository.completeTaskCascade(op.taskIds.first())
            } else {
                op.taskIds.forEach { repository.unCompleteTaskCascade(it) }
            }
            if (op.wasCompleted) "Undid — marked complete again" else "Undid — restored as not done"
        }
        is ChatUndoOp.InboxMoved -> {
            repository.moveInbox(op.taskId, op.fromState)
            "Undid — moved back"
        }
        is ChatUndoOp.ScheduleChanged -> {
            val task = repository.getTask(op.taskId) ?: return "Undid — that task is gone"
            repository.updateTask(
                task.copy(
                    scheduledStartMillis = op.previousStartMillis,
                    durationMinutes = op.previousDuration,
                )
            )
            "Undid — restored the previous time"
        }
    }

    private fun isUneditedInbox(task: com.anchor.adhd.data.model.TaskEntity): Boolean =
        !task.isCompleted &&
            task.inboxState == InboxState.TODAY &&
            task.scheduledStartMillis == null
}
