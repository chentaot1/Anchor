package com.anchor.adhd.domain

import com.anchor.adhd.data.model.AssignmentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AcademicContextTest {
    private val zone = ZoneId.of("UTC")
    private val baseDate = LocalDate.of(2026, 10, 10)
    private val nowMillis = baseDate.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun dueMillis(daysFromNow: Long): Long =
        baseDate.plusDays(daysFromNow).atTime(23, 59).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun selectAssignments_prioritizesMatchingCourseCodeAndTitle() {
        val csExam = AssignmentEntity(
            id = 1L,
            title = "Midterm Exam",
            course = "CS 101",
            dueAtMillis = dueMillis(10),
            notes = "Prep steps: Review lecture notes | Complete practice problems"
        )
        val histPaper = AssignmentEntity(
            id = 2L,
            title = "Archive Essay",
            course = "HIST 210",
            dueAtMillis = dueMillis(3)
        )

        val selected = AcademicContext.selectAssignments(
            assignments = listOf(histPaper, csExam),
            query = "Help me study for CS101 midterm",
            nowMillis = nowMillis,
            zone = zone
        )
        assertEquals(1L, selected.first().id)
    }

    @Test
    fun selectAssignments_excludesCompletedAssignments() {
        val completed = AssignmentEntity(
            id = 1L,
            title = "Quiz 1",
            course = "BIO 101",
            dueAtMillis = dueMillis(1),
            isCompleted = true
        )
        val active = AssignmentEntity(
            id = 2L,
            title = "Lab Report",
            course = "BIO 101",
            dueAtMillis = dueMillis(4),
            isCompleted = false
        )

        val selected = AcademicContext.selectAssignments(
            assignments = listOf(completed, active),
            query = "BIO 101",
            nowMillis = nowMillis,
            zone = zone
        )
        assertEquals(listOf(active), selected)
    }

    @Test
    fun describe_formatsCourseDueDateWeightAndPrepSteps() {
        val item = AssignmentEntity(
            id = 1L,
            title = "Final Project",
            course = "CS 202",
            dueAtMillis = dueMillis(5),
            notes = "weight 30%; Prep steps: Scaffold repo | Build MVP"
        )
        val text = AcademicContext.describe(listOf(item), zone = zone, nowMillis = nowMillis)
        assertTrue(text.contains("CS 202: Final Project"))
        assertTrue(text.contains("2026-10-15; due in 5 days"))
        assertTrue(text.contains("weight 30%"))
        assertTrue(text.contains("Prep steps: Scaffold repo | Build MVP"))
    }

    @Test
    fun isDeadlineQuestion_detectsDeadlineQueriesAndIgnoresUnrelatedChat() {
        assertTrue(AcademicContext.isDeadlineQuestion("When is the CS 101 midterm due?"))
        assertTrue(AcademicContext.isDeadlineQuestion("What assignments are due this week?"))
        assertTrue(AcademicContext.isDeadlineQuestion("Show upcoming deadlines"))
        assertFalse(AcademicContext.isDeadlineQuestion("Break down write history essay"))
        assertFalse(AcademicContext.isDeadlineQuestion("Start a 25 minute timer"))
    }

    @Test
    fun deadlineAnswer_returnsDeterministicDueSummaryForMatchingCourse() {
        val csExam = AssignmentEntity(
            id = 1L,
            title = "Midterm Exam",
            course = "CS 101",
            dueAtMillis = dueMillis(5),
            notes = "Prep steps: Review lecture notes | Complete practice problems"
        )
        val answer = AcademicContext.deadlineAnswer(
            assignments = listOf(csExam),
            query = "When is my CS 101 midterm due?",
            nowMillis = nowMillis,
            zone = zone
        )
        assertTrue(answer.contains("CS 101"))
        assertTrue(answer.contains("Midterm Exam"))
        assertTrue(answer.contains("2026-10-15"))
        assertTrue(answer.contains("Review lecture notes"))
    }
}
