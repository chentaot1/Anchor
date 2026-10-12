package com.anchor.adhd.domain

import com.anchor.adhd.data.model.AssignmentEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Bounded, deterministic academic context and deadline query responder for Android,
 * mirroring DesktopAcademicContext for AssignmentEntity records.
 */
object AcademicContext {
    private val STOP_WORDS = setOf(
        "the", "and", "for", "with", "from", "that", "this", "what", "when", "which",
        "show", "list", "due", "deadline", "deadlines", "upcoming", "coming", "assignment",
        "assignments", "exam", "exams", "break", "down", "breakdown", "help", "start",
        "work", "today", "tomorrow", "week", "next"
    )

    fun selectAssignments(
        assignments: List<AssignmentEntity>,
        query: String,
        nowMillis: Long = System.currentTimeMillis(),
        limit: Int = 6,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<AssignmentEntity> {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val rawCourses = assignments.map { it.course }.filter { it.isNotBlank() }.distinct()

        val matchedCodes = rawCourses.filter { course ->
            val codeToken = extractCourseCode(course) ?: course
            val parts = Regex("[A-Za-z]+|[0-9]+").findAll(codeToken).map { Regex.escape(it.value) }.toList()
            (parts.isNotEmpty() && Regex("\\b${parts.joinToString("\\s*")}\\b", RegexOption.IGNORE_CASE).containsMatchIn(query)) ||
                query.contains(course, ignoreCase = true)
        }.map { normalizeCode(extractCourseCode(it) ?: it) }

        val requestedCodes = Regex("\\b[A-Za-z]{2,6}\\s*[-_]?\\s*[0-9]{2,4}[A-Za-z]?\\b")
            .findAll(query)
            .map { normalizeCode(it.value) }
            .filter { code ->
                val prefix = code.takeWhile { it.isLetter() }
                prefix !in setOf("fall", "spring", "summer", "winter", "week", "page", "room", "chap")
            }
            .toList()

        val relevantCodes = (matchedCodes + requestedCodes).toSet()

        val pending = assignments.filter { item ->
            if (item.isCompleted) return@filter false
            if (relevantCodes.isEmpty()) return@filter true
            val itemNorm = normalizeCode(extractCourseCode(item.course) ?: item.course)
            itemNorm in relevantCodes || relevantCodes.any { item.course.replace(Regex("\\s+"), "").lowercase().contains(it) }
        }

        val titleMatches = pending.filter { item ->
            item.title.isNotBlank() && (
                query.contains(item.title, ignoreCase = true) ||
                    item.title.contains(query.trim(), ignoreCase = true) ||
                    hasKeywordOverlap(item.title, query)
                )
        }

        val relevant = if (titleMatches.isNotEmpty()) titleMatches else pending

        return relevant
            .filter { item ->
                val d = dueDate(item, zone)
                d == null || !d.isBefore(today.minusDays(30))
            }
            .sortedWith(
                compareBy<AssignmentEntity> { if (it.dueAtMillis > 0) it.dueAtMillis else Long.MAX_VALUE }
                    .thenBy { it.id }
            )
            .take(limit)
    }

    fun describe(
        assignments: List<AssignmentEntity>,
        zone: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis()
    ): String = buildString {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        appendLine("Saved syllabus items as of $today (incomplete; nearest deadlines and recent overdue items):")
        if (assignments.isEmpty()) {
            append("No matching pending deadlines are saved. Ask the user for the syllabus or assignment instructions.")
        }
        for (item in assignments) {
            val date = dueDate(item, zone)
            val days = date?.let { ChronoUnit.DAYS.between(today, it) }
            val status = when {
                days == null -> "date unconfirmed"
                days < 0 -> "$date; ${-days} days overdue"
                days == 0L -> "$date; due today"
                days == 1L -> "$date; due tomorrow"
                else -> "$date; due in $days days"
            }
            val courseLabel = compact(item.course, 30).ifBlank { "COURSE" }
            val line = "- $courseLabel: ${compact(item.title, 140)} [$status]"
            val notes = compact(item.notes, 160)
            val entry = line + if (notes.isNotBlank()) "\n  Saved prep: $notes\n" else "\n"
            if (length + entry.length > 1800) break
            append(entry)
        }
    }.trim()

    fun isDeadlineQuestion(query: String): Boolean = Regex(
        """^(?:what(?:'s|\s+is|\s+are)?|which|when(?:'s|\s+is|\s+are)?|show|list|anything|do i)\b.*\b(?:due|deadlines?|upcoming|coming up|assignments?|exams?|midterms?|finals?)\b""",
        RegexOption.IGNORE_CASE
    ).containsMatchIn(query.trim())

    fun deadlineAnswer(
        assignments: List<AssignmentEntity>,
        query: String,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
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
        val selected = selectAssignments(assignments, query, nowMillis = nowMillis, limit = Int.MAX_VALUE, zone = zone)
        val dated = selected.filter { item ->
            val date = dueDate(item, zone)
            range == null || (date != null && date in range)
        }
        if (dated.isEmpty()) {
            return "I don't have matching pending deadlines saved${if (range != null) " for $range" else ""}. Check Plan → Assignments or paste your syllabus here."
        }
        return describe(dated.take(6), zone = zone, nowMillis = nowMillis) +
            "\n\nThese are the dates saved on your Assignments page."
    }

    private fun dueDate(item: AssignmentEntity, zone: ZoneId): LocalDate? =
        item.dueAtMillis.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

    private fun extractCourseCode(text: String): String? =
        Regex("\\b([A-Za-z]{2,6}\\s*[-_]?\\s*[0-9]{2,4}[A-Za-z]?)\\b").find(text)?.groupValues?.get(1)

    private fun normalizeCode(code: String): String =
        code.replace(Regex("[\\s\\-_]+"), "").lowercase()

    private fun hasKeywordOverlap(title: String, query: String): Boolean {
        val titleWords = Regex("[A-Za-z0-9]{3,}").findAll(title.lowercase())
            .map { it.value }
            .filter { it !in STOP_WORDS }
            .toSet()
        if (titleWords.isEmpty()) return false
        val queryWords = Regex("[A-Za-z0-9]{3,}").findAll(query.lowercase())
            .map { it.value }
            .filter { it !in STOP_WORDS }
            .toSet()
        return titleWords.intersect(queryWords).isNotEmpty()
    }

    private fun compact(value: String, limit: Int): String =
        value.replace(Regex("\\s+"), " ").trim().take(limit)
}
