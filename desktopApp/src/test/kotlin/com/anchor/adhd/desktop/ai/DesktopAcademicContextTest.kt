package com.anchor.adhd.desktop.ai

import com.anchor.adhd.desktop.db.DesktopCourse
import com.anchor.adhd.desktop.db.DesktopSyllabusItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DesktopAcademicContextTest {
    private val today = LocalDate.of(2026, 10, 1)
    private val zone = ZoneId.of("America/New_York")
    private val courses = listOf(DesktopCourse(1, "CHIN 103", "Elementary Chinese"), DesktopCourse(2, "PSYC 344", "Cognitive Psychology"))

    private fun item(id: Long, code: String, title: String, date: LocalDate?) = DesktopSyllabusItem(
        id = id,
        courseCode = code,
        title = title,
        dueDateText = date?.toString() ?: "TBA",
        dueDateMillis = date?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: 0,
        prepSteps = listOf("Review the assigned vocabulary", "Complete the written exercises"),
    )

    @After
    fun disconnectTestRecords() {
        DesktopAiEngine.connectSyllabus(MutableStateFlow(emptyList()), MutableStateFlow(emptyList()))
    }

    @Test
    fun selectsPendingCourseworkAndRecognizesCompactCourseCodes() {
        val due = item(1, "CHIN 103", "Written assignment", today.plusDays(1))
        val other = item(2, "PSYC 344", "Midterm", today.plusDays(2))
        val complete = due.copy(id = 3, title = "Completed assignment", isCompleted = true)
        val stale = due.copy(id = 4, dueDateMillis = today.minusDays(31).atStartOfDay(zone).toInstant().toEpochMilli())
        val selected = DesktopAcademicContext.selectItems(listOf(other, complete, stale, due), courses, "complete chin103 written assignments", today, zone)
        assertEquals(listOf(due), selected)
        val sameTitleOtherCourse = other.copy(title = due.title)
        assertEquals(listOf(due), DesktopAcademicContext.selectItems(listOf(sameTitleOtherCourse, due), courses, "CHIN 103 Written assignment", today, zone))
        assertTrue(DesktopAcademicContext.selectItems(listOf(due, other), courses, "CS 105 homework", today, zone).isEmpty())
    }

    @Test
    fun usesExactAssignmentAndIncludesPrepWithoutOtherCourses() {
        val due = item(1, "CHIN 103", "Lesson 4 translation", today.plusDays(1))
        val later = item(2, "CHIN 103", "Oral exam", today.plusDays(7))
        val selected = DesktopAcademicContext.selectItems(listOf(later, due), courses, "CHIN 103: Lesson 4 translation", today, zone)
        val description = DesktopAcademicContext.describe(selected, today, zone)
        assertTrue(description.contains("due tomorrow"))
        assertTrue(description.contains("Review the assigned vocabulary"))
        assertFalse(description.contains("Oral exam"))
    }

    @Test
    fun showsUnknownDatesWithoutInventingDeadlinesAndBoundsLongRecords() {
        val undated = item(1, "CHIN 103", "Undated homework", null)
        val text = DesktopAcademicContext.describe(listOf(undated), today, zone)
        assertTrue(text.contains("date unconfirmed: TBA"))
        assertFalse(text.contains("due today"))
        val longItems = (1L..20L).map { undated.copy(id = it, title = "x".repeat(2000), prepSteps = listOf("y".repeat(2000))) }
        assertTrue(DesktopAcademicContext.describe(longItems, today, zone).length <= 1800)
    }

    @Test
    fun deadlineQuestionsRespectCalendarWindows() {
        val thisWeek = item(1, "CHIN 103", "Thursday homework", today)
        val nextWeek = item(2, "PSYC 344", "Monday exam", today.plusDays(4))
        val thisAnswer = DesktopAcademicContext.deadlineAnswer(listOf(thisWeek, nextWeek), "What's due this week?", today, zone)
        assertTrue(thisAnswer.contains("Thursday homework"))
        assertFalse(thisAnswer.contains("Monday exam"))
        val nextAnswer = DesktopAcademicContext.deadlineAnswer(listOf(thisWeek, nextWeek), "What's due next week?", today, zone)
        assertFalse(nextAnswer.contains("Thursday homework"))
        assertTrue(nextAnswer.contains("Monday exam"))
        assertTrue(DesktopAcademicContext.isDeadlineQuestion("What's coming up for CHIN 103?"))
        assertFalse(DesktopAcademicContext.isDeadlineQuestion("Help me draft my essay"))
    }

    @Test
    fun engineReadsCurrentSyllabusRecordsEvenWithoutModel() = runTest {
        val liveDate = LocalDate.now().plusDays(1)
        val items = MutableStateFlow(listOf(item(1, "CHIN 103", "Written exercises", liveDate)))
        DesktopAiEngine.connectSyllabus(items, MutableStateFlow(courses))
        assertTrue(DesktopAiEngine.academicContextFor("CHIN 103 homework").contains("Written exercises"))
        val answer = DesktopAiEngine.chatTurnAsync("", "What's upcoming for CHIN 103?")
        assertTrue(answer.contains("Written exercises"))
        items.value = listOf(items.value.first().copy(isCompleted = true))
        assertFalse(DesktopAiEngine.academicContextFor("CHIN 103 homework").contains("Written exercises"))
        val empty = DesktopAiEngine.chatTurnAsync("", "What's upcoming for CHIN 103?")
        assertTrue(empty.contains("don't have matching pending deadlines"))
    }
}
