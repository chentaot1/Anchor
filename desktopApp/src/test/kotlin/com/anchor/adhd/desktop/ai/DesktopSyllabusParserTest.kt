package com.anchor.adhd.desktop.ai

import com.anchor.adhd.desktop.db.SyllabusItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSyllabusParserTest {
    @Test
    fun testParseStandardSyllabusText() {
        val sampleSyllabus =
            """
            CS 101: Introduction to Computer Science
            Fall 2026 - Prof. Turing

            Course Schedule and Grading Policy:
            - Midterm Exam (25%) - Oct 24
            - Term Project Final Submission (30%) - Nov 20
            - Problem Set 1 (5%) - Sep 15
            - Reading Chapter 4 Discussion (5%) - Oct 02
            - Final Exam (35%) - Dec 15
            """.trimIndent()

        val parsed = DesktopSyllabusParser.parseSyllabus(sampleSyllabus)

        assertEquals("CS101", parsed.courseCode)
        assertTrue(parsed.courseName.contains("Computer Science") || parsed.courseName.contains("Introduction"))
        assertTrue("Should extract at least 4 deliverables", parsed.deliverables.size >= 4)

        // Verify Midterm Exam
        val midterm = parsed.deliverables.find { it.type == SyllabusItemType.EXAM && it.title.contains("Midterm", ignoreCase = true) }
        assertNotNull("Midterm exam must be extracted", midterm)
        assertEquals(25, midterm!!.weightPercent)
        assertTrue("Must generate backward-chained prep steps", midterm.prepSteps.isNotEmpty())
        assertTrue("Prep steps must contain starter win", midterm.prepSteps.any { it.contains("starter", ignoreCase = true) })

        // Verify Term Project
        val project = parsed.deliverables.find { it.type == SyllabusItemType.PROJECT }
        assertNotNull("Term Project must be extracted", project)
        assertEquals(30, project!!.weightPercent)
        assertTrue("Project must generate backward-chained steps", project.prepSteps.size >= 3)
    }

    @Test
    fun testParseSyllabusWithDifferentDateFormats() {
        val sample =
            """
            BIO 204: Cellular Biology
            1. Quiz 1 - 10/12 (10%)
            2. Research Paper - 11/18 (20%)
            3. Lab Report 3 - Oct 30 (15%)
            """.trimIndent()

        val parsed = DesktopSyllabusParser.parseSyllabus(sample)
        assertEquals("BIO204", parsed.courseCode)
        assertTrue(parsed.deliverables.size >= 3)

        val quiz = parsed.deliverables.find { it.title.contains("Quiz", ignoreCase = true) }
        assertNotNull(quiz)
        assertEquals(10, quiz!!.weightPercent)
    }

    @Test
    fun testBackwardChainingLogic() {
        val examSteps = DesktopSyllabusParser.generateBackwardChainedPrepSteps(SyllabusItemType.EXAM, "Final Exam")
        assertTrue(examSteps.any { it.contains("active recall", ignoreCase = true) || it.contains("practice", ignoreCase = true) })

        val projectSteps = DesktopSyllabusParser.generateBackwardChainedPrepSteps(SyllabusItemType.PROJECT, "Term Paper")
        assertTrue(projectSteps.any { it.contains("outline", ignoreCase = true) })
        assertTrue(projectSteps.any { it.contains("rough draft", ignoreCase = true) })
    }

    @Test
    fun testSmartRouterSyllabusAction() {
        val rSyllabus = DesktopSmartRouter.routeQuick("syllabus")
        assertTrue(rSyllabus is SmartRouteResult.SyllabusAction)

        val rImport = DesktopSmartRouter.routeQuick("import syllabus")
        assertTrue(rImport is SmartRouteResult.SyllabusAction)

        val rUpload = DesktopSmartRouter.routeQuick("upload syllabus")
        assertTrue(rUpload is SmartRouteResult.SyllabusAction)
    }

    @Test
    fun testInspectPsyc344LecPdf() {
        val file = java.io.File("C:\\Users\\johnb\\Downloads\\psyc 344 lec.pdf")
        if (!file.exists()) {
            println("File does not exist: ${file.absolutePath}")
            return
        }

        val rawText = DesktopSyllabusParser.extractTextFromFile(file)
        assertTrue("Extracted text must be substantial via OCR", rawText.length > 500)

        val parsed = DesktopSyllabusParser.parseSyllabus(rawText)
        println("=== PARSED COURSE CODE: ${parsed.courseCode} ===")
        println("=== PARSED COURSE NAME: ${parsed.courseName} ===")
        println("=== PARSED DELIVERABLES COUNT: ${parsed.deliverables.size} ===")
        parsed.deliverables.forEachIndexed { i, d ->
            println("[$i] ${d.title} | ${d.type} | Due: ${d.dueDateText} (${d.dueDateMillis}) | Weight: ${d.weightPercent}%")
            d.prepSteps.forEach { step -> println("     -> Step: $step") }
        }

        assertEquals("PSYC344", parsed.courseCode)
        assertTrue("Course name must match Research Methods", parsed.courseName.contains("Research Methods", ignoreCase = true))

        val exam1 = parsed.deliverables.find { it.title.contains("Exam 1", ignoreCase = true) && !it.title.contains("Review", ignoreCase = true) }
        assertNotNull("Exam 1 must be parsed", exam1)
        assertTrue("Exam 1 must be in September", exam1!!.dueDateText.contains("Sep 14"))

        val exam2 = parsed.deliverables.find { it.title.contains("Exam 2", ignoreCase = true) && !it.title.contains("Review", ignoreCase = true) }
        assertNotNull("Exam 2 must be parsed", exam2)
        assertTrue("Exam 2 must be in October", exam2!!.dueDateText.contains("Oct 26"))

        val exam3 = parsed.deliverables.find { it.title.contains("Exam 3", ignoreCase = true) && !it.title.contains("Review", ignoreCase = true) }
        assertNotNull("Exam 3 must be parsed", exam3)
        assertTrue("Exam 3 must be in December", exam3!!.dueDateText.contains("Dec 2"))
    }

    @Test
    fun testInspectPsyc344DisPdf() {
        val file = java.io.File("C:\\Users\\johnb\\Downloads\\psyc 344 dis.pdf")
        if (!file.exists()) {
            println("File does not exist: ${file.absolutePath}")
            return
        }

        println(">>> Inspecting psyc 344 dis.pdf")
        val rawText = DesktopSyllabusParser.extractTextFromFile(file)
        println("=== RAW TEXT LENGTH: ${rawText.length} ===")
        java.io.File("build/psyc_344_dis_raw.txt").writeText(rawText)
        println("Saved raw text to build/psyc_344_dis_raw.txt")

        val parsed = DesktopSyllabusParser.parseSyllabus(rawText)
        println("=== PARSED COURSE CODE: ${parsed.courseCode} ===")
        println("=== PARSED COURSE NAME: ${parsed.courseName} ===")
        println("=== PARSED DELIVERABLES COUNT: ${parsed.deliverables.size} ===")
        parsed.deliverables.forEachIndexed { i, d ->
            println("[$i] ${d.title} | ${d.type} | Due: ${d.dueDateText} (${d.dueDateMillis}) | Weight: ${d.weightPercent}%")
            d.prepSteps.forEach { step -> println("     -> Step: $step") }
        }

        assertEquals("PSYC344", parsed.courseCode)
        assertTrue("Course name must match Research Methods", parsed.courseName.contains("Research Methods", ignoreCase = true))
        assertTrue("Course name should indicate Lab/Discussion", parsed.courseName.contains("Lab", ignoreCase = true))

        // Verify Paper 1 final draft is extracted
        val paper1 =
            parsed.deliverables.find {
                it.title.contains("Paper 1", ignoreCase = true) &&
                    (it.title.contains("final", ignoreCase = true) || it.title.contains("graded", ignoreCase = true) || it.weightPercent == 20)
            }
        assertNotNull("Paper 1 final submission must be extracted", paper1)
        assertEquals("Paper 1 must have 20% weight", 20, paper1!!.weightPercent)
        assertTrue("Paper 1 must be due in October", paper1.dueDateText.contains("Oct 9"))

        // Verify Paper 2 final draft is extracted
        val paper2 =
            parsed.deliverables.find {
                it.title.contains("Paper 2", ignoreCase = true) &&
                    (it.title.contains("final", ignoreCase = true) || it.title.contains("graded", ignoreCase = true) || it.weightPercent == 30)
            }
        assertNotNull("Paper 2 final submission must be extracted", paper2)
        assertEquals("Paper 2 must have 30% weight", 30, paper2!!.weightPercent)
        assertTrue("Paper 2 must be due in November", paper2.dueDateText.contains("Nov 24") || paper2.dueDateText.contains("Nov 20"))

        // Verify CITI training certificate
        val citi = parsed.deliverables.find { it.title.contains("CITI", ignoreCase = true) }
        assertNotNull("CITI training must be extracted", citi)
        assertTrue("CITI training must be in August", citi!!.dueDateText.contains("Aug 28"))

        // Verify no bogus phantom TBD items
        val tbdCount = parsed.deliverables.count { it.dueDateText == "TBD" }
        assertEquals("Should have 0 phantom TBD items", 0, tbdCount)
    }

    @Test
    fun testInspectEnvi101Docx() {
        val file = java.io.File("C:\\Users\\johnb\\Downloads\\ENVI101Fall2026Syllabus_11AM.docx")
        if (!file.exists()) {
            println("File does not exist: ${file.absolutePath}")
            return
        }

        println(">>> Inspecting ENVI101Fall2026Syllabus_11AM.docx via extractTextFromFile")
        val text = DesktopSyllabusParser.extractTextFromFile(file)
        assertTrue("Extracted text must be substantial", text.length > 500)

        val parsed = DesktopSyllabusParser.parseSyllabus(text)
        println("Course Code: '${parsed.courseCode}'")
        println("Course Name: '${parsed.courseName}'")
        println("Deliverables count: ${parsed.deliverables.size}")
        parsed.deliverables.forEachIndexed { i, d ->
            println("[$i] ${d.title} | ${d.type} | due: '${d.dueDateText}' | weight: ${d.weightPercent}%")
        }

        assertEquals("ENVI101", parsed.courseCode)
        assertTrue(
            "Course name must match Humans and the Ecological Environment",
            parsed.courseName.contains("Humans and the Ecological Environment", ignoreCase = true),
        )
        assertEquals("Must extract all 19 deliverables from assignment table", 19, parsed.deliverables.size)

        // Verify Exam 1
        val exam1 = parsed.deliverables.find { it.title.contains("Exam 1", ignoreCase = true) }
        assertNotNull("Exam 1 must be present", exam1)
        assertEquals(15, exam1!!.weightPercent)
        assertEquals("9/18", exam1.dueDateText)

        // Verify Exam 2
        val exam2 = parsed.deliverables.find { it.title.contains("Exam 2", ignoreCase = true) }
        assertNotNull("Exam 2 must be present", exam2)
        assertEquals(15, exam2!!.weightPercent)
        assertEquals("10/26", exam2.dueDateText)

        // Verify Exam 3
        val exam3 = parsed.deliverables.find { it.title.contains("Exam 3", ignoreCase = true) }
        assertNotNull("Exam 3 must be present", exam3)
        assertEquals(15, exam3!!.weightPercent)
        assertEquals("11/18", exam3.dueDateText)

        // Verify Cumulative Final
        val finalExam = parsed.deliverables.find { it.title.contains("Final", ignoreCase = true) }
        assertNotNull("Cumulative Final must be present", finalExam)
        assertEquals(15, finalExam!!.weightPercent)
        assertEquals("12/14", finalExam.dueDateText)

        // Verify Field Trips (4% each)
        val fieldTrips = parsed.deliverables.filter { it.title.contains("Field Trip", ignoreCase = true) }
        assertEquals(4, fieldTrips.size)
        assertTrue("Field trips must be 4% each", fieldTrips.all { it.weightPercent == 4 })

        // Verify News Article Responses (3% each)
        val newsResponses = parsed.deliverables.filter { it.title.contains("News Article", ignoreCase = true) }
        assertEquals(4, newsResponses.size)
        assertTrue("News Article Responses must be 3% each", newsResponses.all { it.weightPercent == 3 })

        // Verify Career Exploration Assignment (3%)
        val career = parsed.deliverables.find { it.title.contains("Career Exploration", ignoreCase = true) }
        assertNotNull("Career Exploration Assignment must be present", career)
        assertEquals(3, career!!.weightPercent)
        assertEquals("10/23", career.dueDateText)

        // Verify zero phantom TBD items
        assertEquals("Must have 0 phantom TBD items", 0, parsed.deliverables.count { it.dueDateText == "TBD" })
    }

    @Test
    fun testDayFirstAndHyphenDatesAndYearDetection() {
        val syllabus =
            """
            HIST 201: World History
            Spring 2027 - Prof. Davis

            Deliverables:
            - Midterm Essay (20%) - 14 Feb
            - Primary Source Analysis (15%) - 2027-03-22
            - Research Term Paper (30%) - 18th April
            - Final Exam (35%) - 05-12
            """.trimIndent()

        val parsed = DesktopSyllabusParser.parseSyllabus(syllabus)
        assertEquals("HIST201", parsed.courseCode)
        assertTrue(parsed.courseName.contains("World History"))
        assertEquals(4, parsed.deliverables.size)

        val midterm = parsed.deliverables.find { it.title.contains("Midterm", ignoreCase = true) }
        assertNotNull(midterm)
        assertEquals("Feb 14", midterm!!.dueDateText)
        assertEquals(20, midterm.weightPercent)

        val analysis = parsed.deliverables.find { it.title.contains("Primary Source", ignoreCase = true) }
        assertNotNull(analysis)
        assertEquals("3/22", analysis!!.dueDateText)
        assertEquals(15, analysis.weightPercent)

        val termPaper = parsed.deliverables.find { it.title.contains("Term Paper", ignoreCase = true) }
        assertNotNull(termPaper)
        assertEquals("Apr 18", termPaper!!.dueDateText)
        assertEquals(30, termPaper.weightPercent)

        val finalExam = parsed.deliverables.find { it.title.contains("Final Exam", ignoreCase = true) }
        assertNotNull(finalExam)
        assertEquals("5/12", finalExam!!.dueDateText)
        assertEquals(35, finalExam.weightPercent)
    }

    @Test
    fun testSemesterYearPrioritizedOverRoomNumber() {
        val syllabus =
            """
            CHEM 102: General Chemistry II
            Office: Science Building Room 2010
            Term: Fall 2026

            Deliverables:
            - Midterm Exam (25%) - Oct 15
            """.trimIndent()

        val parsed = DesktopSyllabusParser.parseSyllabus(syllabus)
        assertEquals("CHEM102", parsed.courseCode)
        val midterm = parsed.deliverables.firstOrNull()
        assertNotNull(midterm)
        val parsedYear =
            java.time.Instant
                .ofEpochMilli(midterm!!.dueDateMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .year
        assertEquals("Should prioritize Fall 2026 over Room 2010", 2026, parsedYear)
    }

    @Test
    fun testSeptAbbreviationPointAndDecimalWeightsAndExplicitYears() {
        val syllabus =
            """
            ENG 210: Modern Literature
            Fall 2026

            Assignments:
            - Reading Response 1 (15 pts) - Sept. 14
            - Short Essay (12.5%) - 28 Sept
            - Midterm Paper (20 points) - 10/15/2027
            - Final Presentation (25%) - 12-10-2027
            """.trimIndent()

        val parsed = DesktopSyllabusParser.parseSyllabus(syllabus)
        assertEquals("ENG210", parsed.courseCode)
        assertEquals(4, parsed.deliverables.size)

        val resp1 = parsed.deliverables.find { it.title.contains("Reading Response 1", ignoreCase = true) }
        assertNotNull(resp1)
        assertEquals("Sep 14", resp1!!.dueDateText)
        assertEquals(15, resp1.weightPercent)

        val essay = parsed.deliverables.find { it.title.contains("Short Essay", ignoreCase = true) }
        assertNotNull(essay)
        assertEquals("Sep 28", essay!!.dueDateText)
        assertEquals(13, essay.weightPercent)

        val midterm = parsed.deliverables.find { it.title.contains("Midterm Paper", ignoreCase = true) }
        assertNotNull(midterm)
        assertEquals("10/15", midterm!!.dueDateText)
        assertEquals(20, midterm.weightPercent)
        val midtermYear =
            java.time.Instant
                .ofEpochMilli(midterm.dueDateMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .year
        assertEquals("Should honor explicit year 2027 in 10/15/2027", 2027, midtermYear)

        val finalPres = parsed.deliverables.find { it.title.contains("Final Presentation", ignoreCase = true) }
        assertNotNull(finalPres)
        assertEquals("12/10", finalPres!!.dueDateText)
        val finalYear =
            java.time.Instant
                .ofEpochMilli(finalPres.dueDateMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .year
        assertEquals("Should honor explicit year 2027 in 12-10-2027", 2027, finalYear)
    }

    @Test
    fun testHyphenatedPageChapterAndMinuteRangesAreNotParsedAsDates() {
        val syllabus =
            """
            SOC 105: Intro to Sociology
            Fall 2026

            Deliverables:
            - Write a 5-7 page essay (20%) - Oct 20
            - Deliver a 10-12 minute presentation (15%) - Nov 12
            - Read Chapters 3-4 before discussion
            """.trimIndent()

        val parsed = DesktopSyllabusParser.parseSyllabus(syllabus)
        assertEquals("SOC105", parsed.courseCode)

        val essay = parsed.deliverables.find { it.title.contains("essay", ignoreCase = true) }
        assertNotNull(essay)
        assertEquals("Should parse Oct 20 rather than 5-7", "Oct 20", essay!!.dueDateText)
        assertTrue("Title should keep 5-7 page range", essay.title.contains("5-7 page", ignoreCase = true))

        val presentation = parsed.deliverables.find { it.title.contains("presentation", ignoreCase = true) }
        assertNotNull(presentation)
        assertEquals("Should parse Nov 12 rather than 10-12", "Nov 12", presentation!!.dueDateText)
        assertTrue("Title should keep 10-12 minute range", presentation.title.contains("10-12 minute", ignoreCase = true))

        val chaptersFalsePositive = parsed.deliverables.find { it.dueDateText == "3/4" }
        assertEquals("Read Chapters 3-4 without a date/weight should not be parsed as due on 3/4", null, chaptersFalsePositive)
    }

    @Test
    fun explicitYearsOverrideSemesterAndDecimalCategoryWeightsAreRounded() {
        val parsed = DesktopSyllabusParser.parseSyllabus("""
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
        val category = DesktopSyllabusParser.parseSyllabus("""
            ENG 210 Literature
            Fall 2026
            Exam 1: 12.5%
            Course Schedule
            Sept 14
            Exam 1
        """.trimIndent())
        assertEquals(13, category.deliverables.first { it.title.contains("Exam 1") }.weightPercent)
        assertEquals("Essay", DesktopSyllabusParser.cleanDeliverableTitle("Essay 12.5%"))
        val ranges = DesktopSyllabusParser.parseSyllabus("""
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
