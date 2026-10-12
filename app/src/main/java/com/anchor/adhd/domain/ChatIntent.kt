package com.anchor.adhd.domain

/**
 * Result of routing free text in Anchor Chat, before execution.
 * Wave 1 implements the auto-safe + read verbs; others are detected for later waves.
 */
sealed class ChatIntent {
    data class CaptureTasks(val titles: List<String>, val raw: String) : ChatIntent()
    data object WhatsToday : ChatIntent()
    data class Remind(val title: String, val atMillis: Long?, val raw: String) : ChatIntent()
    data class MarkDone(val query: String) : ChatIntent()
    data class MoveSomeday(val query: String) : ChatIntent()

    data class Breakdown(val query: String) : ChatIntent()
    data class Schedule(val query: String) : ChatIntent()
    data class Triage(val raw: String) : ChatIntent()
    data class Replan(val raw: String) : ChatIntent()
    data class StartFocus(val query: String, val minutes: Int) : ChatIntent()
    data class Snooze(val query: String) : ChatIntent()
    data class BrainDump(val text: String) : ChatIntent()
    data class PickOne(val minutes: Int?) : ChatIntent()

    data class Clarify(val question: String, val pendingTitle: String? = null) : ChatIntent()
    data class Chat(val text: String) : ChatIntent()
}

object ChatIntentRouter {
    /**
     * Phrase-boundary routing (offline, no model). Ambiguous prose returns [ChatIntent.Chat]
     * so a later classifier can see it — never silent capture.
     */
    fun route(text: String, nowMillis: Long = System.currentTimeMillis()): ChatIntent {
        val t = text.trim()
        if (t.isEmpty()) return ChatIntent.Chat(t)

        // whats_today
        if (matchesAny(t, "what's on my plate", "what is on my plate", "what's today", "what is today",
                "what's next", "what is next", "what should i do", "what do i have", "today's plan", "plans today")) {
            return ChatIntent.WhatsToday
        }

        // remind
        if (isRemindPhrase(t)) {
            val (title, atMillis) = scanRemind(t, nowMillis)
            return ChatIntent.Remind(title, atMillis, t)
        }

        // mark done — "complete" as a word, not the prefix of "completed"
        if (matchesAny(t, "mark done", "mark as done", "i'm done", "i am done", "complete") &&
            !hasTargetDate(t, nowMillis)) {
            return ChatIntent.MarkDone(stripVerb(t, listOf("mark done", "mark as done", "complete")))
        }

        // move to someday — not a bare "later" / "someday" buried in prose
        if (isMoveSomeday(t)) {
            return ChatIntent.MoveSomeday(stripVerb(t, listOf("move to someday", "move to later", "to someday")))
        }

        // breakdown
        if (matchesAny(t, "break down", "breakdown", "break it down")) {
            return ChatIntent.Breakdown(stripVerb(t, listOf("break down", "breakdown")))
        }

        // brain dump
        if (matchesAny(t, "brain dump", "braindump", "dump this")) {
            val dump = t.replaceFirst(
                Regex("""^(brain\s*dump:?|dump this)\s*""", RegexOption.IGNORE_CASE),
                ""
            ).trim()
            return if (dump.isBlank()) {
                ChatIntent.Clarify("Paste the dump — errands, thoughts, anything. I'll turn it into tasks.")
            } else {
                ChatIntent.BrainDump(dump)
            }
        }

        // schedule — explicit times, or a task-like request explicitly targeting tomorrow
        if ((hasTime(t, nowMillis) && looksLikeSchedule(t)) ||
            (hasTargetDate(t, nowMillis) && looksLikeFutureTask(t))) {
            return ChatIntent.Schedule(t)
        }

        // triage
        if (matchesAny(t, "triage", "order my", "what first", "prioritize", "prioritise")) {
            return ChatIntent.Triage(t)
        }

        // replan
        if (matchesAny(t, "replan", "i missed", "missed everything", "derailed", "fall behind")) {
            return ChatIntent.Replan(t)
        }

        // start focus
        if (matchesAny(t, "start focus", "start a focus", "start timer", "focus on", "pomodoro")) {
            return ChatIntent.StartFocus(t, extractMinutes(t) ?: 20)
        }

        // snooze / defer — "later today I have class" is not a snooze
        if (isSnoozeCommand(t)) {
            return ChatIntent.Snooze(t)
        }

        // pick one / I have N minutes — no model if the picker can decide
        if (isPickOne(t)) {
            return ChatIntent.PickOne(extractMinutes(t))
        }

        // capture: delimiter lists and short imperatives only. Everything else is Chat/Clarify.
        val titles = splitCapture(t)
        if (titles.size >= 2) {
            return ChatIntent.CaptureTasks(titles, t)
        }
        if (titles.size == 1 && looksLikeImperativeCapture(t)) {
            return ChatIntent.CaptureTasks(titles, t)
        }
        if (titles.size == 1 && isAmbiguousShort(titles.first(), t) && !isChatFiller(t)) {
            return ChatIntent.Clarify(
                "Got it — should I add \"${titles.first()}\" to Today, or did you want something else?",
                pendingTitle = titles.first()
            )
        }
        return ChatIntent.Chat(t)
    }

    /**
     * True when a [ChatIntent.Chat] turn may call the on-device classifier.
     * Greetings, thanks, and empty text must not wake the local model.
     */
    fun shouldWakeModel(text: String): Boolean {
        val t = normalizeWords(text)
        if (t.isEmpty()) return false
        return t !in ACK_OR_GREETING
    }

    private fun isPickOne(t: String): Boolean {
        if (matchesAny(t, "pick one", "pick something", "choose one", "i have 30 minutes", "i have half an hour")) {
            return true
        }
        return Regex("""\bi have \d{1,3}\s*(min|minute|minutes)\b""", RegexOption.IGNORE_CASE).containsMatchIn(t)
    }

    private fun isAmbiguousShort(title: String, raw: String): Boolean {
        val clean = title.lowercase().replace(Regex("[.,!?]"), "").trim()
        val single = clean.split(" ", ",").filter { it.isNotBlank() }.size <= 2
        return single && !looksLikeImperativeCapture(raw)
    }

    private fun looksLikeImperativeCapture(t: String): Boolean {
        return Regex(
            """^(please\s+)?(buy|call|email|text|write|finish|clean|make|get|pick|schedule|add|put|set|complete|do|send|read|study|pay|start|remember)\b""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(t.trim())
    }

    private fun stripVerb(t: String, verbs: List<String>): String {
        var out = t
        for (v in verbs) {
            out = out.replaceFirst(Regex(Regex.escape(v), RegexOption.IGNORE_CASE), "").trim()
        }
        return out.ifBlank { t }
    }

    private fun matchesAny(t: String, vararg phrases: String): Boolean {
        val lower = t.lowercase()
        return phrases.any { phrase ->
            val p = phrase.trim().lowercase()
            if (p.isEmpty()) return@any false
            Regex("""\b${Regex.escape(p)}\b""").containsMatchIn(lower)
        }
    }

    private fun isRemindPhrase(t: String): Boolean {
        val lower = t.lowercase()
        return lower.startsWith("remind") || lower.startsWith("reminder") || matchesAny(
            t, "remind me", "nudge me", "ping me"
        )
    }

    private fun isMoveSomeday(t: String): Boolean {
        if (matchesAny(t, "move to someday", "move to later", "to someday")) return true
        val clean = normalizeWords(t)
        return clean == "someday" || clean.startsWith("someday ")
    }

    private fun isSnoozeCommand(t: String): Boolean {
        if (matchesAny(t, "snooze", "push to night", "remind again", "defer")) return true
        if (!matchesAny(t, "later today")) return false
        val rest = normalizeWords(
            t.replace(Regex("""\blater today\b""", RegexOption.IGNORE_CASE), "")
        )
        return rest.isEmpty() || rest in setOf("please", "that", "it", "this")
    }

    private fun isChatFiller(text: String): Boolean {
        val t = normalizeWords(text)
        if (t.isEmpty() || t in ACK_OR_GREETING || t in CHAT_FILLER) return true
        return Regex("""\b(help|overwhelmed|stuck|stressed)\b""").containsMatchIn(t)
    }

    private fun hasTime(t: String, now: Long): Boolean = scanTime(t, now) != null

    private fun looksLikeSchedule(t: String): Boolean {
        val lower = t.lowercase()
        return scanTime(t, 0L) != null && (Regex("""\b(at|by|put|set|schedule)\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(lower) || scanTime(t, 0L) != null)
    }

    private fun looksLikeFutureTask(t: String): Boolean {
        if (looksLikeImperativeCapture(t)) return true
        return Regex(
            """^(please\s+)?i\s+(need|have)\s+to\b|^(please\s+)?(need|have)\s+to\b""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(t.trim())
    }

    private val ACK_OR_GREETING = setOf(
        "ok", "okay", "k", "kk", "thanks", "thank you", "thx", "ty",
        "hi", "hey", "hello", "yo", "cool", "got it", "np",
    )

    private val CHAT_FILLER = setOf(
        "help", "help me", "overwhelmed", "stuck", "i'm stuck", "im stuck", "stressed", "idk",
    )

    private fun normalizeWords(text: String): String =
        text.lowercase().replace(Regex("[.,!?]"), "").trim()
}

/** Splits a capture string on commas, semicolons, newlines, and "and" between items. */
fun splitCapture(text: String): List<String> {
    val parts = text
        .split(Regex("""\s*[,;\n]\s*|(?<=\w)\s+and\s+(?=\w)"""))
        .map { it.trim().replace(Regex("""^\d+[\.\)]\s*"""), "") }
        .filter { it.isNotBlank() }
    return parts.ifEmpty { listOf(text.trim()) }
}

/** Line/comma split for brain dump when the on-device model is not loaded. */
fun parseBrainDumpWithoutModel(text: String): List<String> =
    splitCapture(text).take(8)

/**
 * Returns the reminder time (epoch millis) parsed from text, or null if none found.
 * Supports "at 3", "3pm", "at 17:00", "in 30 min", "at 5pm".
 */
fun scanTime(text: String, nowMillis: Long): Long? {
    val lower = text.lowercase()
    val zone = java.time.ZoneId.systemDefault()
    val explicitDate = targetDateFor(lower, nowMillis, zone)

    // "in N min/hours"
    val inLater = Regex("""in (\d+(?:\.\d+)?)\s*(min|minute|minutes|hr|hour|hours)""").find(lower)
    if (inLater != null) {
        val amount = inLater.groupValues[1].toDoubleOrNull() ?: return null
        val unit = inLater.groupValues[2]
        val ms = if (unit.startsWith("h")) (amount * 3_600_000) else (amount * 60_000)
        return (nowMillis + ms).toLong()
    }

    // "at 17:00" / "17:00" (with optional am/pm)
    val m24 = Regex("""(?:at\s+|by\s+)?(\d{1,2}):(\d{2})\s*(am|pm)?""").find(lower)
    if (m24 != null) {
        val clock = resolveClock(m24.groupValues[1], m24.groupValues[2], m24.groupValues[3])
            ?: return null
        return atTime(clock.first, clock.second, nowMillis, zone, explicitDate)
    }

    // "at 5pm" / "5pm" / bare "at 3" (plain hour). Only treat as a bare hour when it has
    // an explicit am/pm suffix OR an "at"/"by" preposition, so we don't misparse a number
    // that's just part of an item (e.g. "buy 3 shirts").
    val m12 = Regex("""(?:at\s+|by\s+)(\d{1,2})(am|pm)?""").find(lower)
        ?: Regex("""(\d{1,2})(am|pm)""").find(lower)
    if (m12 != null) {
        var h = m12.groupValues[1].toInt()
        val suffix = m12.groupValues[2].lowercase()
        if (suffix == "pm" && h < 12) h += 12
        if (suffix == "am" && h == 12) h = 0
        if (h in 0..23) return atTime(h, 0, nowMillis, zone, explicitDate)
    }
    return null
}

/** Resolves a clock string to 24h hour + minute, or null if it isn't a valid time. */
private fun resolveClock(hourStr: String, minuteStr: String, suffix: String): Pair<Int, Int>? {
    val h = hourStr.toIntOrNull() ?: return null
    val m = minuteStr.toIntOrNull() ?: return null
    val adjusted = when (suffix.lowercase()) {
        "am" -> if (h == 12) 0 else h
        "pm" -> if (h in 1..11) h + 12 else if (h == 12) 12 else return null
        else -> h
    }
    return if (adjusted in 0..23 && m in 0..59) Pair(adjusted, m) else null
}

private fun atTime(
    hour: Int,
    minute: Int,
    nowMillis: Long,
    zone: java.time.ZoneId,
    targetDate: java.time.LocalDate? = null
): Long {
    val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val date = targetDate ?: today
    val target = date.atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
    // If the requested time has already passed today, roll forward to the next
    // calendar date in the current zone (important across daylight-saving changes).
    if (targetDate == null && target <= nowMillis) {
        return today.plusDays(1).atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }
    return target
}

private fun extractMinutes(text: String): Int? {
    val m = Regex("""(\d{1,3})\s*(min|minute|minutes)""").find(text.lowercase())
    return m?.groupValues?.get(1)?.toIntOrNull()
}

/** Parses reminder text into a title + optional time. Title = text minus any time/verb phrases. */
fun scanRemind(text: String, nowMillis: Long): Pair<String, Long?> {
    val at = scheduleTargetMillis(text, nowMillis)
    val lower = text.lowercase()
    var title = text
    for (v in listOf("remind me to ", "remind me about ", "remind me to", "remind me", "reminder: ", "reminder ")) {
        if (lower.startsWith(v)) { title = text.substring(v.length).trim(); break }
    }
    // Strip any inline time phrase from the title (not just trailing), then a leading "to ".
    title = stripSchedulingMarkers(title)
        .replaceFirst(Regex("""^to\s+""", RegexOption.IGNORE_CASE), "")
        .trim()
    return title.ifBlank { "Reminder" } to at
}

/** Returns true when a message contains an explicit calendar date. */
fun hasTargetDate(
    text: String,
    nowMillis: Long,
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
): Boolean = targetDateFor(text, nowMillis, zone) != null

/** Resolves relative dates, weekdays, numeric dates, and month/day dates. */
fun targetDateFor(
    text: String,
    nowMillis: Long,
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
): java.time.LocalDate? {
    val base = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val lower = text.lowercase()

    if (Regex("""\bday after tomorrow\b""").containsMatchIn(lower)) return base.plusDays(2)
    if (Regex("""\b(?:tomorrow|tmrw)\b""").containsMatchIn(lower)) return base.plusDays(1)
    if (Regex("""\btoday\b""").containsMatchIn(lower)) return base
    if (Regex("""\b(?:next week|in a week)\b""").containsMatchIn(lower)) return base.plusWeeks(1)

    Regex("""\bin\s+(\d{1,2})\s+weeks?\b""").find(lower)?.let {
        return base.plusWeeks(it.groupValues[1].toLong())
    }

    Regex("""\bin\s+(\d{1,2})\s+days?\b""").find(lower)?.let {
        return base.plusDays(it.groupValues[1].toLong())
    }

    Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\b""").find(lower)?.let {
        return runCatching {
            java.time.LocalDate.of(
                it.groupValues[1].toInt(),
                it.groupValues[2].toInt(),
                it.groupValues[3].toInt()
            )
        }.getOrNull()
    }

    Regex("""\b(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b""").find(lower)?.let {
        val explicitYear = it.groupValues[3].isNotBlank()
        val rawYear = it.groupValues[3].toIntOrNull()
        val year = when {
            rawYear == null -> base.year
            rawYear < 100 -> 2000 + rawYear
            else -> rawYear
        }
        val parsed = runCatching {
            java.time.LocalDate.of(year, it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }.getOrNull()
        if (parsed != null) return if (!explicitYear && parsed.isBefore(base)) parsed.plusYears(1) else parsed
    }

    val monthNames = mapOf(
        "january" to 1, "february" to 2, "march" to 3, "april" to 4,
        "may" to 5, "june" to 6, "july" to 7, "august" to 8,
        "september" to 9, "october" to 10, "november" to 11, "december" to 12,
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "sept" to 9, "oct" to 10,
        "nov" to 11, "dec" to 12
    )
    Regex("""\b([a-z]+)\s+(\d{1,2})(?:,?\s+(\d{4}))?\b""").find(lower)?.let {
        val month = monthNames[it.groupValues[1]] ?: return@let
        val explicitYear = it.groupValues[3].isNotBlank()
        val parsed = runCatching {
            java.time.LocalDate.of(it.groupValues[3].toIntOrNull() ?: base.year, month, it.groupValues[2].toInt())
        }.getOrNull()
        if (parsed != null) return if (!explicitYear && parsed.isBefore(base)) parsed.plusYears(1) else parsed
    }

    val weekday = Regex("""\b(next\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""")
        .find(lower)
    if (weekday != null) {
        val desired = java.time.DayOfWeek.valueOf(weekday.groupValues[2].uppercase())
        var delta = (desired.value - base.dayOfWeek.value + 7) % 7
        if (weekday.groupValues[1].isNotBlank() && delta == 0) delta = 7
        return base.plusDays(delta.toLong())
    }
    return null
}

fun mentionsTomorrow(text: String): Boolean =
    Regex("""\b(?:tomorrow|tmrw)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)

fun tomorrowStartMillis(nowMillis: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long =
    targetDateFor("tomorrow", nowMillis, zone)!!
        .atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

fun scheduleTargetMillis(text: String, nowMillis: Long): Long? =
    scanTime(text, nowMillis) ?: targetDateFor(text, nowMillis)?.atTime(9, 0)
        ?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

fun stripSchedulingMarkers(text: String): String = text
    .replace(Regex("""(?:at|by)\s+\d{1,2}(?::\d{2})?\s*(?:am|pm)?""", RegexOption.IGNORE_CASE), "")
    .replace(Regex("""\bin\s+\d+(?:\.\d+)?\s*(?:min|minute|minutes|hr|hour|hours)\b""", RegexOption.IGNORE_CASE), "")
    .replace(Regex("""\bfor\s+\d{1,3}\s*(?:min|minute|minutes|hr|hour|hours)\b""", RegexOption.IGNORE_CASE), "")
    .replace(Regex("""\b(?:day after tomorrow|tomorrow|tmrw|today)\b""", RegexOption.IGNORE_CASE), "")
    .replace(Regex("""\b(?:next week|in a week)\b""", RegexOption.IGNORE_CASE), "")
    .replace(Regex("""\bin\s+\d{1,2}\s+weeks?\b""", RegexOption.IGNORE_CASE), "")
    .replace(Regex("""\bin\s+\d{1,2}\s+days?\b""", RegexOption.IGNORE_CASE), "")
    .replace(Regex("""\b\d{4}-\d{1,2}-\d{1,2}\b"""), "")
    .replace(Regex("""\b\d{1,2}/\d{1,2}(?:/\d{2,4})?\b"""), "")
    .replace(Regex("""\b(?:next\s+)?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", RegexOption.IGNORE_CASE), "")
    .replace(Regex("""\b(?:january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec)\s+\d{1,2}(?:,?\s+\d{4})?\b""", RegexOption.IGNORE_CASE), "")
    .replaceFirst(Regex("""^(please\s+)?(?:put|set|schedule|add)\s+""", RegexOption.IGNORE_CASE), "")
    .replace(Regex("""\s+(?:on|by|for)\s*$""", RegexOption.IGNORE_CASE), "")
    .trim()

/**
 * Target time for a snooze/defer request. Uses an explicit time if present,
 * else 8pm for "night/evening", else +30 minutes.
 */
fun snoozeTargetMillis(query: String, nowMillis: Long): Long {
    scanTime(query, nowMillis)?.let { return it }
    val lower = query.lowercase()
    if (lower.contains("night") || lower.contains("evening")) {
        val zone = java.time.ZoneId.systemDefault()
        val local = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone)
        var target = local.withHour(20).withMinute(0).withSecond(0).withNano(0)
        if (!target.toInstant().isAfter(local.toInstant())) target = target.plusDays(1)
        return target.toInstant().toEpochMilli()
    }
    return nowMillis + 30 * 60_000L
}
