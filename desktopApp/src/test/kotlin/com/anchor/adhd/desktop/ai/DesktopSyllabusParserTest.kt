package com.anchor.adhd.desktop.ai

import com.anchor.adhd.desktop.db.SyllabusItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.Assume.assumeTrue
import org.junit.rules.TemporaryFolder
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DesktopSyllabusParserTest {
    @get:Rule
    val folder = TemporaryFolder()

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
    fun extractsAndParsesGeneratedMultiPagePdf() {
        val file = folder.newFile("sample-syllabus.pdf")
        PDDocument().use { document ->
            for (lines in listOf(
                listOf("CS 101: Sample Computing", "Spring 2028", "Exam 1 (20%) - Feb 10"),
                listOf("Exam 2 (30%) - Mar 12", "Final Exam (50%) - May 15"),
            )) {
                val page = PDPage()
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 14f)
                    stream.setLeading(22f)
                    stream.newLineAtOffset(50f, 740f)
                    lines.forEach { stream.showText(it); stream.newLine() }
                    stream.endText()
                }
            }
            document.save(file)
        }
        val parsed = DesktopSyllabusParser.parseSyllabus(DesktopSyllabusParser.extractTextFromFile(file))
        assertEquals("CS101", parsed.courseCode)
        assertEquals(3, parsed.deliverables.size)
        val exam = parsed.deliverables.first { it.title.contains("Exam 2") }
        assertEquals("Mar 12", exam.dueDateText)
        assertEquals(30, exam.weightPercent)
        assertTrue(exam.prepSteps.isNotEmpty())
    }

    @Test
    fun extractsGeneratedImageOnlyPdfUsingWindowsOcr() {
        assumeTrue("Windows OCR is platform-specific", com.sun.jna.Platform.isWindows())
        val file = folder.newFile("sample-scanned-syllabus.pdf")
        val image = BufferedImage(1836, 2376, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, image.width, image.height)
            graphics.color = Color.BLACK
            graphics.font = Font("Arial", Font.PLAIN, 44)
            listOf("CS 101: Sample Computing", "Spring 2028", "Final Exam (50%) - May 15")
                .forEachIndexed { index, line -> graphics.drawString(line, 150, 180 + index * 100) }
        } finally {
            graphics.dispose()
        }
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.drawImage(LosslessFactory.createFromImage(document, image), 0f, 0f, 612f, 792f)
            }
            document.save(file)
        }
        val text = DesktopSyllabusParser.extractTextFromFile(file)
        assertTrue("Generated scan should be extracted", text.contains("Final Exam", ignoreCase = true))
        val parsed = DesktopSyllabusParser.parseSyllabus(text)
        assertEquals("CS101", parsed.courseCode)
        assertTrue(parsed.deliverables.any { it.title.contains("Final Exam") && it.weightPercent == 50 })
    }

    @Test
    fun extractsGeneratedDocxAssignmentTable() {
        val file = folder.newFile("sample-syllabus.docx")
        fun paragraph(text: String) = "<w:p><w:r><w:t>$text</w:t></w:r></w:p>"
        fun row(cells: List<String>) = "<w:tr>" + cells.joinToString("") { "<w:tc>${paragraph(it)}</w:tc>" } + "</w:tr>"
        val rows = listOf(row(listOf("Date", "Assignment", "Weight"))) + (1..19).map {
            row(listOf("2/$it", "Homework $it", "5%"))
        }
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
            ${paragraph("CS 101: Sample Computing")}${paragraph("Spring 2028")}
            <w:tbl>${rows.joinToString("")}</w:tbl></w:body></w:document>
        """.trimIndent()
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(xml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        val parsed = DesktopSyllabusParser.parseSyllabus(DesktopSyllabusParser.extractTextFromFile(file))
        assertEquals("CS101", parsed.courseCode)
        assertEquals(19, parsed.deliverables.size)
        val homework = parsed.deliverables.first { it.title == "Homework 19" }
        assertEquals("2/19", homework.dueDateText)
        assertEquals(5, homework.weightPercent)
        assertTrue(parsed.deliverables.all { it.dueDateMillis > 0 && it.prepSteps.isNotEmpty() })
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
