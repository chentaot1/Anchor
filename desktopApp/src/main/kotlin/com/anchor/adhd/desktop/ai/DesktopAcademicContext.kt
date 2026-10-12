package com.anchor.adhd.desktop.ai

import com.anchor.adhd.desktop.db.DesktopCourse
import com.anchor.adhd.desktop.db.DesktopSyllabusItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A bounded, current view of the same records shown on the syllabus page. */
object DesktopAcademicContext {
    fun selectItems(
        items: List<DesktopSyllabusItem>,
        courses: List<DesktopCourse>,
        query: String,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        limit: Int = 6,
    ): List<DesktopSyllabusItem> {
        val codes = (courses.map { it.code } + items.map { it.courseCode }).distinct()
        val matchedCodes = codes.filter { code ->
            val parts = Regex("[A-Za-z]+|[0-9]+").findAll(code).map { Regex.escape(it.value) }.toList()
            parts.isNotEmpty() && Regex("\\b${parts.joinToString("\\s*")}\\b", RegexOption.IGNORE_CASE).containsMatchIn(query)
        }
        val matchingNames = courses.filter { it.name.isNotBlank() && query.contains(it.name, ignoreCase = true) }.map { it.code }
        val requestedCodes = Regex("\\b[A-Za-z]{2,6}\\s*[0-9]{2,4}\\b").findAll(query).map { it.value }.toList()
        val relevantCodes = (matchedCodes + matchingNames + requestedCodes).map { it.replace(Regex("\\s+"), "").lowercase() }.toSet()
        val pending = items.filter {
            !it.isCompleted && (relevantCodes.isEmpty() || it.courseCode.replace(Regex("\\s+"), "").lowercase() in relevantCodes)
        }
        val titleMatches = pending.filter { it.title.isNotBlank() && query.contains(it.title, ignoreCase = true) }
        val relevant = when {
            titleMatches.isNotEmpty() -> titleMatches
            else -> pending
        }
        return relevant
            .filter { it.dueDateMillis <= 0 || !dueDate(it, zone)!!.isBefore(today.minusDays(30)) }
            .sortedWith(compareBy<DesktopSyllabusItem> { if (it.dueDateMillis > 0) it.dueDateMillis else Long.MAX_VALUE }.thenBy { it.id })
            .take(limit)
    }

    private fun dueDate(item: DesktopSyllabusItem, zone: ZoneId): LocalDate? =
        item.dueDateMillis.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

    private fun compact(value: String, limit: Int): String = value.replace(Regex("\\s+"), " ").trim().take(limit)

    fun describe(
        items: List<DesktopSyllabusItem>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        includePrep: Boolean = true,
    ): String = buildString {
        appendLine("Saved syllabus items as of $today (incomplete; nearest deadlines and recent overdue items):")
        if (items.isEmpty()) append("No matching pending deadlines are saved. Ask the user for the syllabus or assignment instructions.")
        for (item in items) {
            val date = dueDate(item, zone)
            val days = date?.let { ChronoUnit.DAYS.between(today, it) }
            val status = when {
                days == null -> "date unconfirmed: ${compact(item.dueDateText, 50).ifBlank { "not saved" }}"
                days < 0 -> "$date; ${-days} days overdue"
                days == 0L -> "$date; due today"
                days == 1L -> "$date; due tomorrow"
                else -> "$date; due in $days days"
            }
            val savedLabel = compact(item.dueDateText, 50)
            val dateLabel = if (date != null && savedLabel.isNotBlank() && savedLabel != date.toString()) "; saved date label: $savedLabel" else ""
            val line = "- ${compact(item.courseCode, 30)}: ${compact(item.title, 140)} [${item.itemType}; $status$dateLabel]"
            val prep = if (includePrep) item.prepSteps.take(2).joinToString("; ") { compact(it, 80) } else ""
            val entry = line + if (prep.isNotBlank()) "\n  Saved prep: $prep\n" else "\n"
            if (length + entry.length > 1800) break
            append(entry)
        }
    }.trim()

    fun isDeadlineQuestion(query: String): Boolean = Regex(
        """^(?:what|which|when|show|list|anything|do i)\b.*\b(?:due|deadlines?|upcoming|coming up|assignments?|exams?)\b""",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(query.trim())

    fun deadlineAnswer(
        items: List<DesktopSyllabusItem>,
        query: String,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val lower = query.lowercase()
        val range = when {
            "tomorrow" in lower -> today.plusDays(1)..today.plusDays(1)
            "today" in lower -> today..today
            "next week" in lower -> {
                val monday = today.plusDays((8 - today.dayOfWeek.value).toLong())
                monday..monday.plusDays(6)
            }
            "this week" in lower -> today..today.plusDays((7 - today.dayOfWeek.value).toLong())
            else -> null
        }
        val dated = items.filter { item ->
            val date = dueDate(item, zone)
            range == null || (date != null && date in range)
        }
        if (dated.isEmpty()) return "I don't have matching pending deadlines saved${if (range != null) " for $range" else ""}. Check the syllabus page or add the assignment details."
        return describe(dated.take(6), today, zone, includePrep = false) + "\n\nThese are the dates saved on your syllabus page."
    }
}
