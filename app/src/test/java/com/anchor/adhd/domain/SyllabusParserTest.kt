package com.anchor.adhd.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SyllabusParserTest {
    @Test
    fun parseSyllabus_extractsCourseNameSemesterAndDeliverablesFromLines() {
        val raw = """
            CS 101: Intro to Computer Science
            Fall 2026
            Midterm Exam - Oct 15 (20%)
            Final Project Proposal - Nov 02 (15%)
            Homework 1 - Sep 18 (5%)
        """.trimIndent()

        val parsed = SyllabusParser.parseSyllabus(raw, referenceDate = LocalDate.of(2026, 9, 1))
        assertTrue(parsed.courseName.contains("CS 101"))
        assertEquals("Fall 2026", parsed.semester)
        assertEquals(3, parsed.deliverables.size)

        val midterm = parsed.deliverables.first { it.title.contains("Midterm", ignoreCase = true) }
        assertEquals(LocalDate.of(2026, 10, 15), midterm.dueDate)
        assertEquals(20, midterm.weightPercent)
        assertEquals(SyllabusItemType.EXAM, midterm.itemType)
        assertEquals(4, midterm.prepSteps.size)
    }

    @Test
    fun parseSyllabus_parsesScheduleTableRowsWithWeightsAndItemTypes() {
        val raw = """
            BIO 204 Cellular Biology — Spring 2026
            Date | Deliverable | Weight
            2026-03-12 | Lab Report 1 | 10%
            2026-04-20 | Final Exam | 35%
        """.trimIndent()

        val parsed = SyllabusParser.parseSyllabus(raw, referenceDate = LocalDate.of(2026, 2, 1))
        assertEquals(2, parsed.deliverables.size)
        assertEquals(LocalDate.of(2026, 3, 12), parsed.deliverables[0].dueDate)
        assertEquals(10, parsed.deliverables[0].weightPercent)
        assertEquals(SyllabusItemType.PROJECT, parsed.deliverables[0].itemType)
        assertEquals(LocalDate.of(2026, 4, 20), parsed.deliverables[1].dueDate)
        assertEquals(35, parsed.deliverables[1].weightPercent)
        assertEquals(SyllabusItemType.EXAM, parsed.deliverables[1].itemType)
    }

    @Test
    fun parseSyllabus_prefersSemesterYearOverEarlierPolicyYear() {
        val raw = """
            Academic Integrity Policy revised 2021.
            Course: PHYS 301 — Fall 2026
            Midterm Exam - Oct 22 (25%)
        """.trimIndent()

        val parsed = SyllabusParser.parseSyllabus(raw, referenceDate = LocalDate.of(2026, 9, 1))
        assertEquals("Fall 2026", parsed.semester)
        assertEquals(1, parsed.deliverables.size)
        assertEquals(LocalDate.of(2026, 10, 22), parsed.deliverables.first().dueDate)
    }

    @Test
    fun generateBackwardChainedPrepSteps_buildsExamProjectAndHomeworkMilestones() {
        val examDue = LocalDate.of(2026, 10, 20)
        val examSteps = SyllabusParser.generateBackwardChainedPrepSteps("Midterm Exam", examDue, SyllabusItemType.EXAM)
        assertEquals(4, examSteps.size)
        assertTrue(examSteps.first().contains(examDue.minusDays(7).toString()))

        val projectSteps = SyllabusParser.generateBackwardChainedPrepSteps("Final Paper", examDue, SyllabusItemType.PROJECT)
        assertEquals(4, projectSteps.size)
        assertTrue(projectSteps.first().contains(examDue.minusDays(10).toString()))

        val hwSteps = SyllabusParser.generateBackwardChainedPrepSteps("Problem Set 3", examDue, SyllabusItemType.HOMEWORK)
        assertEquals(3, hwSteps.size)
        assertTrue(hwSteps.first().contains(examDue.minusDays(3).toString()))
    }

    @Test
    fun parseSyllabus_fallbackWhenEmptyFalse_returnsEmptyForOrdinaryChat() {
        val raw = """
            I need to buy groceries
            and also clean the kitchen sink
            before dinner tonight
        """.trimIndent()

        val parsed = SyllabusParser.parseSyllabus(raw, fallbackWhenEmpty = false)
        assertTrue(parsed.deliverables.isEmpty())
    }

    @Test
    fun parseSyllabus_handlesSeptAbbreviationExplicitYearsPointAndDecimalWeightsAndIgnoresHyphenatedRanges() {
        val raw = """
            PSYC 210: Cognitive Psychology
            Fall 2026
            Read Chapters 3-4 before lecture
            Write a 5-7 page essay on memory consolidation
            10-12 minute presentation overview
            Quiz 1 — Sept. 14, 2026 (15 pts)
            Research Paper — 10/15/2026 (12.5%)
            Final Exam — 12-10-2026 (30 points)
        """.trimIndent()

        val parsed = SyllabusParser.parseSyllabus(raw, referenceDate = LocalDate.of(2026, 9, 1))
        assertEquals(3, parsed.deliverables.size)

        val quiz = parsed.deliverables.first { it.title.contains("Quiz 1") }
        assertEquals(LocalDate.of(2026, 9, 14), quiz.dueDate)
        assertEquals(15, quiz.weightPercent)

        val paper = parsed.deliverables.first { it.title.contains("Research Paper") }
        assertEquals(LocalDate.of(2026, 10, 15), paper.dueDate)
        assertEquals(13, paper.weightPercent)

        val exam = parsed.deliverables.first { it.title.contains("Final Exam") }
        assertEquals(LocalDate.of(2026, 12, 10), exam.dueDate)
        assertEquals(30, exam.weightPercent)
    }

    @Test
    fun explicitYearsOverrideSemesterAndDecimalCategoryWeightsAreRounded() {
        val parsed = SyllabusParser.parseSyllabus("""
            ENG 210 Literature
            Fall 2025
            Exam 1: 12.5%
            Assignment schedule
            Date | Assignment | Weight
            Sept. 14, 2026 | Quiz 1 | 15 pts
            Oct 15, 2026 | Essay | 20 points
            2026-10-20 | Project | 12.5%
            10-25-2026 | Presentation | 25%
            11/15/2026 | Final Exam | 30%
        """.trimIndent())
        assertEquals(5, parsed.deliverables.size)
        parsed.deliverables.forEach { item ->
            assertEquals(2026, java.time.Instant.ofEpochMilli(item.dueDateMillis).atZone(java.time.ZoneId.systemDefault()).year)
        }
        assertEquals(13, parsed.deliverables.first { it.title.contains("Project") }.weightPercent)
        val category = SyllabusParser.parseSyllabus("""
            ENG 210 Literature
            Fall 2026
            Exam 1: 12.5%
            Course Schedule
            Sept 14
            Exam 1
        """.trimIndent())
        assertEquals(13, category.deliverables.first { it.title.contains("Exam 1") }.weightPercent)
        assertEquals("Essay", SyllabusParser.cleanDeliverableTitle("Essay 12.5%"))
        val ranges = SyllabusParser.parseSyllabus("""
            ENG 210 Literature
            Fall 2026
            Essay 5-7 page essay 20%
            Presentation 10-12 minute presentation 15%
            Read Chapters 3-4 10%
            Quiz 1 due 10-15
        """.trimIndent())
        assertEquals("10/15", ranges.deliverables.first { it.title.contains("Quiz 1") }.dueDateText)
        ranges.deliverables.filter { !it.title.contains("Quiz 1") }.forEach { item ->
            assertEquals(0L, item.dueDateMillis)
        }
    }
}
