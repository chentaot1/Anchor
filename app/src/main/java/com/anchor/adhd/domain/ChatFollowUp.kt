package com.anchor.adhd.domain

/**
 * Session memory helpers for Anchor Chat: resolve "that / it / this" against the last
 * referenced task, and detect yes/no replies to a pending clarify.
 */
object ChatFollowUp {
    private val REF = Regex("""\b(that|it|this|those)\b""", RegexOption.IGNORE_CASE)

    fun looksLikeFollowUp(text: String): Boolean = REF.containsMatchIn(text)

    fun isAffirmative(text: String): Boolean {
        val t = text.trim().lowercase()
        return t in setOf("yes", "yeah", "yep", "y", "ok", "okay", "sure", "do it", "add it", "today", "please")
    }

    fun isNegative(text: String): Boolean {
        val t = text.trim().lowercase()
        return t in setOf("no", "nope", "nah", "cancel", "nevermind", "never mind", "don't", "stop")
    }

    /**
     * Rewrites a follow-up so the keyword router can see a real task title.
     * "break that down" + lastTask "essay" → `break down "essay"`.
     */
    fun resolve(text: String, lastTask: String?): String {
        val quoted = lastTask?.trim()?.trim('"')?.takeIf { it.isNotBlank() } ?: return text
        if (!looksLikeFollowUp(text)) return text
        val lower = text.lowercase()
        return when {
            Regex("""break\s+(that|it|this)\s+down""").containsMatchIn(lower) ->
                "break down \"$quoted\""
            Regex("""(?:mark|complete)\s+(that|it|this)(?:\s+done)?""").containsMatchIn(lower) ->
                "mark done \"$quoted\""
            Regex("""(?:move|someday).*\b(that|it|this)\b|\b(that|it|this)\b.*someday""").containsMatchIn(lower) ->
                "move to someday \"$quoted\""
            Regex("""focus\s+on\s+(that|it|this)""").containsMatchIn(lower) ->
                "focus on \"$quoted\""
            Regex("""\b(snooze|defer)\b.*\b(that|it|this)\b|\b(that|it|this)\b.*\b(snooze|defer)\b""").containsMatchIn(lower) ->
                "snooze \"$quoted\""
            Regex("""remind me (again|about (that|it|this))""").containsMatchIn(lower) ->
                "remind me about \"$quoted\""
            else -> text
        }
    }
}
