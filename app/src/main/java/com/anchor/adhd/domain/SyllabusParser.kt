package com.anchor.adhd.domain

import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.util.Locale

enum class SyllabusItemType {
    EXAM,
    PROJECT,
    HOMEWORK,
    READING
}

data class ParsedDeliverable(
    val title: String,
    val type: SyllabusItemType,
    val dueDateText: String,
    val dueDateMillis: Long,
    val weightPercent: Int,
    val prepSteps: List<String>,
    val dueDate: LocalDate = if (dueDateMillis > 0L) {
        Instant.ofEpochMilli(dueDateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    } else {
        LocalDate.now()
    }
) {
    val itemType: SyllabusItemType get() = type
}

data class ParsedSyllabus(
    val courseCode: String,
    val courseName: String,
    val deliverables: List<ParsedDeliverable>,
    val semester: String = ""
)

/**
 * On-Device Syllabus Ingestion & Backward-Chaining Engine for Android,
 * matching DesktopSyllabusParser text/table parsing, semester-qualified year priority,
 * and backward-chained preparation step generation.
 */
object SyllabusParser {
    private data class ParsedDateMatch(
        val text: String,
        val millis: Long,
        val localDate: LocalDate
    )

    fun parseSyllabus(
        rawText: String,
        fallbackWhenEmpty: Boolean = true,
        zone: ZoneId = ZoneId.systemDefault(),
        referenceDate: LocalDate = LocalDate.now(zone)
    ): ParsedSyllabus {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }

        // 1. Detect Course Code & Name
        var detectedCode = "COURSE 101"
        var detectedName = "Semester Course"

        val courseCodeRegex = Regex("""\b([A-Z]{2,5}\s*[-_]?\s*\d{3,4}[A-Z]?)\b""", RegexOption.IGNORE_CASE)
        val excludedPrefixes = setOf(
            "FALL", "SPRING", "WINTER", "SUMMER", "TERM", "YEAR", "WEEK", "PAGE",
            "ROOM", "BLDG", "DATE", "CHAP", "HALL", "SECTION", "SEC", "POST", "INFO"
        )

        var codeLineIndex = -1
        outer@ for ((idx, line) in lines.take(100).withIndex()) {
            val lower = line.lowercase(Locale.ROOT)
            if (lower.contains("email") || lower.contains("subject") || lower.contains("prerequisite") || lower.contains("contact")) {
                continue
            }

            for (match in courseCodeRegex.findAll(line)) {
                val candidatePrefix = match.groupValues[1].takeWhile { it.isLetter() }.uppercase(Locale.ROOT)
                if (candidatePrefix in excludedPrefixes) continue

                detectedCode = match.groupValues[1]
                    .uppercase(Locale.ROOT)
                    .replace(" ", "")
                    .replace("-", " ")
                // Ensure a space between letters and digits (e.g. "CS101" -> "CS 101")
                detectedCode = detectedCode.replace(Regex("""^([A-Z]+)(\d+)"""), "$1 $2")
                codeLineIndex = idx

                val cleanLine = line
                    .replace(match.value, "")
                    .replace(Regex("""(?i)\b(fall|spring|winter|summer|\d{4})\b"""), "")
                    .trim(':', '-', '–', '—', ' ', '|', '(', ')')

                val isJustSection =
                    cleanLine.matches(Regex("""(?i)^(?:section|sec|lecture|lab|dis|discussion)\s*[a-z0-9]*$""")) ||
                        cleanLine.startsWith("Section", ignoreCase = true)

                if (cleanLine.length in 8..60 && !isJustSection) {
                    detectedName = cleanLine
                }
                break@outer
            }
        }

        if (detectedName == "Semester Course" && codeLineIndex > 0) {
            for (i in (codeLineIndex - 1) downTo maxOf(0, codeLineIndex - 2)) {
                val candidate = lines[i].trim(':', '-', '–', '—', ' ', '|')
                val lowerCandidate = candidate.lowercase(Locale.ROOT)
                if (lowerCandidate.startsWith("table ") ||
                    lowerCandidate.contains("instructor:") ||
                    lowerCandidate.contains("office:") ||
                    lowerCandidate.contains("email:") ||
                    lowerCandidate.contains("ta information")
                ) {
                    continue
                }
                if (candidate.length in 4..60 && !candidate.contains("syllabus", ignoreCase = true)) {
                    detectedName = candidate
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { word -> word.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase(Locale.ROOT) } }
                    break
                }
            }
        }

        if (detectedName == "Semester Course") {
            for (line in lines.take(20)) {
                val clean = line.replace(Regex("""(?i)\b(syllabus|fall|spring|winter|summer|\d{4})\b"""), "").trim(':', '-', '–', '—', ' ', '|')
                val lowerClean = clean.lowercase(Locale.ROOT)
                if (clean.length in 4..60 &&
                    !lowerClean.startsWith("table ") &&
                    !lowerClean.contains("team") &&
                    !lowerClean.contains("instructor") &&
                    !lowerClean.contains("professor") &&
                    !lowerClean.contains("office")
                ) {
                    detectedName = clean
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { word -> word.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase(Locale.ROOT) } }
                    break
                }
            }
        }

        val formattedCourseName = when {
            detectedCode != "COURSE 101" && !detectedName.contains(detectedCode, ignoreCase = true) ->
                if (detectedName == "Semester Course") detectedCode else "$detectedCode: $detectedName"
            else -> detectedName
        }

        // 2. Extract Deliverables & Deadlines
        val items = mutableListOf<ParsedDeliverable>()
        val semesterYearRegex = Regex("""\b(Fall|Spring|Summer|Winter)\s+(20\d{2})\b""", RegexOption.IGNORE_CASE)
        val yearRegex = Regex("""\b(20\d{2})\b""")
        val headerLines = lines.take(30)
        val semesterMatch = headerLines.firstNotNullOfOrNull { semesterYearRegex.find(it) }
            ?: lines.firstNotNullOfOrNull { semesterYearRegex.find(it) }
        val detectedYear = semesterMatch?.groupValues?.get(2)?.toIntOrNull()
            ?: headerLines.firstNotNullOfOrNull { line -> yearRegex.find(line)?.groupValues?.get(1)?.toIntOrNull() }
            ?: referenceDate.year
        val detectedSemester = semesterMatch?.let { m ->
            val season = m.groupValues[1].lowercase(Locale.ROOT).replaceFirstChar { it.uppercase(Locale.ROOT) }
            "$season ${m.groupValues[2]}"
        } ?: "Fall $detectedYear"

        val dateMonthRegex = Regex(
            """\b(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\.?\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(20\d{2}|\d{2}))?\b""",
            RegexOption.IGNORE_CASE
        )
        val dayMonthRegex = Regex(
            """\b(\d{1,2})(?:st|nd|rd|th)?\s+(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\.?(?:,?\s+(20\d{2}|\d{2}))?\b""",
            RegexOption.IGNORE_CASE
        )
        val isoDateRegex = Regex("""\b(20\d{2})-(\d{1,2})-(\d{1,2})\b""")
        val slashDateRegex = Regex("""\b(\d{1,2})/(\d{1,2})(?:/(20\d{2}|\d{2}))?\b""")
        val usHyphenYearDateRegex = Regex("""\b(\d{1,2})-(\d{1,2})-(20\d{2}|\d{2})\b""")
        val shortHyphenDateRegex = Regex("""(?<!\d-)(\b\d{1,2})-(\d{1,2})\b(?!-\d)""")
        val rangePrefixRegex = Regex("""(?i)\b(?:ch(?:ap(?:ter)?s?)?|pages?|pgs?|pp|p|weeks?|wks?|parts?|sec(?:tion)?s?|problems?|questions?|exercises?|units?|modules?|labs?|slides?|items?|steps?|grades?|levels?)\.?\s*$""")
        val rangeSuffixRegex = Regex("""(?i)^\s*(?:pages?|pgs?|pp|mins?|minutes?|hrs?|hours?|secs?|seconds?|words?|slides?|chapters?|problems?|questions?|points?|pts?|days?|weeks?|sources?|citations?|references?|paragraphs?|items?)\b""")
        val weightRegex = Regex("""(?i)(?:\(\s*)?(\d+(?:\.\d+)?)\s*(?:%|points|pts)(?:\s*\))?""")

        fun resolveYear(rawYear: String?): Int {
            if (rawYear.isNullOrBlank()) return detectedYear
            val y = rawYear.toIntOrNull() ?: return detectedYear
            return if (y < 100) 2000 + y else y
        }

        fun parseDateFromText(text: String): ParsedDateMatch? {
            val isoMatch = isoDateRegex.find(text)
            if (isoMatch != null) {
                val year = isoMatch.groupValues[1].toIntOrNull() ?: detectedYear
                val m = isoMatch.groupValues[2].toIntOrNull()?.coerceIn(1, 12) ?: 10
                val month = Month.of(m)
                val maxDay = month.length(java.time.Year.isLeap(year.toLong()))
                val day = isoMatch.groupValues[3].toIntOrNull()?.coerceIn(1, maxDay) ?: 15
                val localDate = LocalDate.of(year, month, day)
                return ParsedDateMatch("$m/$day", localDate.atStartOfDay(zone).toInstant().toEpochMilli(), localDate)
            }

            val dateMonthMatch = dateMonthRegex.find(text)
            if (dateMonthMatch != null) {
                val rawMonth = dateMonthMatch.groupValues[1]
                val monthStr = rawMonth.take(3).lowercase(Locale.ROOT)
                val month = parseMonth(monthStr)
                val year = resolveYear(dateMonthMatch.groupValues.getOrNull(3))
                val maxDay = month.length(java.time.Year.isLeap(year.toLong()))
                val day = (dateMonthMatch.groupValues[2].toIntOrNull() ?: 1).coerceIn(1, maxDay)
                val localDate = LocalDate.of(year, month, day)
                val monthLabel = month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                return ParsedDateMatch("$monthLabel $day", localDate.atStartOfDay(zone).toInstant().toEpochMilli(), localDate)
            }

            val dayMonthMatch = dayMonthRegex.find(text)
            if (dayMonthMatch != null) {
                val rawMonth = dayMonthMatch.groupValues[2]
                val monthStr = rawMonth.take(3).lowercase(Locale.ROOT)
                val month = parseMonth(monthStr)
                val year = resolveYear(dayMonthMatch.groupValues.getOrNull(3))
                val maxDay = month.length(java.time.Year.isLeap(year.toLong()))
                val day = (dayMonthMatch.groupValues[1].toIntOrNull() ?: 1).coerceIn(1, maxDay)
                val localDate = LocalDate.of(year, month, day)
                val monthLabel = month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                return ParsedDateMatch("$monthLabel $day", localDate.atStartOfDay(zone).toInstant().toEpochMilli(), localDate)
            }

            val slashMatch = slashDateRegex.find(text)
            if (slashMatch != null) {
                val m = slashMatch.groupValues[1].toIntOrNull()?.coerceIn(1, 12) ?: 10
                val year = resolveYear(slashMatch.groupValues.getOrNull(3))
                val month = Month.of(m)
                val maxDay = month.length(java.time.Year.isLeap(year.toLong()))
                val rawD = slashMatch.groupValues[2].toIntOrNull()?.coerceIn(1, maxDay) ?: 15
                val localDate = LocalDate.of(year, month, rawD)
                return ParsedDateMatch("$m/$rawD", localDate.atStartOfDay(zone).toInstant().toEpochMilli(), localDate)
            }

            val hyphenYearMatch = usHyphenYearDateRegex.find(text)
            if (hyphenYearMatch != null) {
                val m = hyphenYearMatch.groupValues[1].toIntOrNull()?.coerceIn(1, 12) ?: 10
                val year = resolveYear(hyphenYearMatch.groupValues.getOrNull(3))
                val month = Month.of(m)
                val maxDay = month.length(java.time.Year.isLeap(year.toLong()))
                val rawD = hyphenYearMatch.groupValues[2].toIntOrNull()?.coerceIn(1, maxDay) ?: 15
                val localDate = LocalDate.of(year, month, rawD)
                return ParsedDateMatch("$m/$rawD", localDate.atStartOfDay(zone).toInstant().toEpochMilli(), localDate)
            }

            for (match in shortHyphenDateRegex.findAll(text)) {
                if (rangePrefixRegex.containsMatchIn(text.substring(0, match.range.first)) ||
                    rangeSuffixRegex.containsMatchIn(text.substring(match.range.last + 1))) continue
                val m = match.groupValues[1].toIntOrNull() ?: continue
                val d = match.groupValues[2].toIntOrNull() ?: continue
                if (m !in 1..12 || d !in 1..31) continue
                val month = Month.of(m)
                val day = d.coerceAtMost(month.length(java.time.Year.isLeap(detectedYear.toLong())))
                val localDate = LocalDate.of(detectedYear, month, day)
                return ParsedDateMatch("$m/$day", localDate.atStartOfDay(zone).toInstant().toEpochMilli(), localDate)
            }
            return null
        }

        val boilerplateKeywords = listOf(
            "email", "http://", "https://", "office hours", "zoom", "phone", "607-",
            "teaching team", "please refer to", "academic integrity", "accommodations",
            "cheating", "plagiarism", "disability", "counseling", "prerequisite",
            "credit hours", "grade grubbing", "undue personal", "campus help",
            "walk-in", "tutoring", "advising", "dean of students", "textbook requirement",
            "access code", "student services", "description & course objectives",
            "care team", "12.5 hours", "grading scale", "have questions", "subject line",
            "within 48 hours", "time & location", "lecture hall", "prerequisites",
            "by the end of this course", "spss", "apa format", "course policies",
            "makeup exams", "grade negotiation", "its helpdesk", "eli tutoring",
            "the speaking center", "libraries", "seek (support", "care team:",
            "page break", "date topic readings", "reading requirement", "points each"
        )

        val policyPhrases = listOf(
            "students may choose", "if you miss", "replace the missing", "lowest exam grade",
            "alternative exam date", "otherwise, you will need", "grade grubbing",
            "can schedule", "grading scale", "total exams", "points each", "there will be",
            "lecture exams"
        )

        val noClassKeywords = listOf(
            "no classes", "labor day", "fall break", "thanksgiving break",
            "yom kippur", "spring break", "winter break", "holiday"
        )

        // 2a. Extract Grading Category Weights
        val gradingWeights = mutableMapOf<String, Int>()
        val gradingWeightRegex = Regex(
            """(?i)\b(Paper\s*\d+|Exam\s*\d+|Final\s*Exam|Midterm|Quiz\s*\d+|Project\s*\d+|Homework\s*\d+|Assignment\s*\d+|Engagement|CITI)\b[^\d\n]*?(\d+(?:\.\d+)?)\s*(?:points|%|pts)"""
        )
        for (line in lines) {
            for (m in gradingWeightRegex.findAll(line)) {
                val cat = m.groupValues[1].lowercase(Locale.ROOT).replace(Regex("""\s+"""), " ").trim()
                val w = m.groupValues[2].toFloatOrNull()?.let { Math.round(it) } ?: 0
                if (w in 1..100) {
                    gradingWeights[cat] = w
                }
            }
        }

        // 2b-0. Explicit Assignment Schedule Table Parsing
        val assignTableIdx = lines.indexOfFirst { line ->
            val l = line.lowercase(Locale.ROOT)
            (
                l.contains("schedule of assignments") ||
                    l.contains("course exams and assignments") ||
                    l.contains("schedule of exams and assignments") ||
                    l.contains("assignment schedule") ||
                    l.contains("assignments and grading")
                ) ||
                (
                    l.contains("|") &&
                        (l.contains("due date") || l.contains("deadline") || l.contains("date")) &&
                        (l.contains("assignment") || l.contains("exam") || l.contains("deliverable") || l.contains("item")) &&
                        (l.contains("percentage") || l.contains("weight") || l.contains("grade") || l.contains("points"))
                    )
        }

        if (assignTableIdx >= 0) {
            val tableLines = lines.drop(assignTableIdx)
            for (line in tableLines) {
                val l = line.lowercase(Locale.ROOT)
                if (line.isBlank() ||
                    (!line.contains("|") && parseDateFromText(line) == null) ||
                    l.startsWith("table ") ||
                    l.startsWith("grading scale") ||
                    l.startsWith("course policies")
                ) {
                    if (items.isNotEmpty()) break
                }
                if (!line.contains("|")) continue

                val cells = line.split("|").map { it.trim() }.filter { it.isNotBlank() }
                if (cells.size < 2) continue

                if (cells.any { c ->
                        c.equals("Due date", ignoreCase = true) ||
                            c.equals("Date", ignoreCase = true) ||
                            c.equals("Deliverable", ignoreCase = true) ||
                            c.equals("Assignment/Exam", ignoreCase = true) ||
                            c.contains("Percentage of grade", ignoreCase = true)
                    }
                ) {
                    continue
                }

                val dateCell = cells.firstOrNull { parseDateFromText(it) != null } ?: continue
                val parsedDate = parseDateFromText(dateCell) ?: continue

                val weightFloat = cells
                    .mapNotNull { cell ->
                        val m = Regex("""(?i)^\s*\(?\s*(\d+(?:\.\d+)?)\s*(?:%|points|pts)\s*\)?\s*$""").find(cell)
                        m?.groupValues?.get(1)?.toFloatOrNull()
                    }.firstOrNull() ?: 0f
                val weight = Math.round(weightFloat).coerceIn(0, 100)

                val titleCandidate = cells.firstOrNull {
                    it != dateCell && !it.matches(Regex("""(?i)^\s*\(?\s*\d+(?:\.\d+)?\s*(?:%|points|pts)\s*\)?\s*$"""))
                } ?: continue
                val clean = cleanDeliverableTitle(titleCandidate)
                if (clean.length < 3) continue

                val type = determineItemType(clean)
                val finalWeight = if (weight > 0) weight else determineWeight(clean, type, gradingWeights)

                items.add(
                    ParsedDeliverable(
                        title = clean,
                        type = type,
                        dueDateText = parsedDate.text,
                        dueDateMillis = parsedDate.millis,
                        weightPercent = finalWeight,
                        prepSteps = generateBackwardChainedPrepSteps(type, clean),
                        dueDate = parsedDate.localDate
                    )
                )
            }
        }

        // 2b. Multi-Column Schedule Table Parsing
        if (items.isEmpty()) {
            val scheduleHeaderRegex =
                Regex("""(?i)^\s*(?:Table\s*\d+:?\s*)?(?:(?:COURSE|CLASS|TENTATIVE|LAB|LECTURE|WEEKLY)\s+)*(?:SCHEDULE|CALENDAR|TIMELINE)(?:\s*[-–—].*)?\s*$""")
            val scheduleIdx = lines.indexOfLast { scheduleHeaderRegex.matches(it.trim()) }
            val scheduleEndRegex =
                Regex("""(?i)^\s*(?:Important\s+Notes|Course\s+Policies|Campus\s+Help|Grade\s+Negotiation)""")

            if (scheduleIdx >= 0) {
                val scheduleLines = lines.drop(scheduleIdx + 1)
                val blocks = mutableListOf< Pair<ParsedDateMatch, MutableList<String>> >()

                for (line in scheduleLines) {
                    if (scheduleEndRegex.containsMatchIn(line.trim())) break
                    val parsedDate = parseDateFromText(line)
                    if (parsedDate != null) {
                        blocks.add(parsedDate to mutableListOf(line))
                    } else if (blocks.isNotEmpty()) {
                        blocks.last().second.add(line)
                    }
                }

                val deadlineRegex = Regex(
                    """(?i)(?:Hard\s+Deadlines?|Suggested\s+Deadline|Deadline):\s*([^\n\r]+?)(?=(?:Hard\s+Deadlines?|Suggested\s+Deadline|Deadline|\bCh\.|\bIn-Class|\z))"""
                )

                for ((dateMatch, blockLines) in blocks) {
                    val blockText = blockLines.joinToString(" ")
                    val lower = blockText.lowercase(Locale.ROOT)
                    if (noClassKeywords.any { lower.contains(it) } &&
                        !lower.contains("deadline") &&
                        !lower.contains("exam") &&
                        !lower.contains("submit")
                    ) {
                        continue
                    }

                    val dlMatches = deadlineRegex
                        .findAll(blockText)
                        .map { it.groupValues[1].trim() }
                        .filter { it.length > 5 }
                        .toList()
                    if (dlMatches.isNotEmpty()) {
                        for (rawDl in dlMatches) {
                            val clean = cleanDeliverableTitle(rawDl)
                            if (clean.length < 3) continue
                            val type = determineItemType(clean)
                            val weight = determineWeight(clean, type, gradingWeights)
                            items.add(
                                ParsedDeliverable(
                                    title = clean,
                                    type = type,
                                    dueDateText = dateMatch.text,
                                    dueDateMillis = dateMatch.millis,
                                    weightPercent = weight,
                                    prepSteps = generateBackwardChainedPrepSteps(type, clean),
                                    dueDate = dateMatch.localDate
                                )
                            )
                        }
                    } else if (lower.contains("exam") ||
                        lower.contains("review") ||
                        lower.contains("activity:") ||
                        lower.contains("evaluation") ||
                        lower.contains("revision")
                    ) {
                        val clean = cleanDeliverableTitle(blockText)
                        if (clean.length >= 3) {
                            val type = determineItemType(clean)
                            val weight = determineWeight(clean, type, gradingWeights)
                            items.add(
                                ParsedDeliverable(
                                    title = clean,
                                    type = type,
                                    dueDateText = dateMatch.text,
                                    dueDateMillis = dateMatch.millis,
                                    weightPercent = weight,
                                    prepSteps = generateBackwardChainedPrepSteps(type, clean),
                                    dueDate = dateMatch.localDate
                                )
                            )
                        }
                    }
                }
            }
        }

        // 2c. Fallback Line-by-Line Parsing
        if (items.isEmpty()) {
            for (line in lines) {
                val lower = line.lowercase(Locale.ROOT)
                if (boilerplateKeywords.any { lower.contains(it) }) continue
                if (noClassKeywords.any { lower.contains(it) }) continue

                val parsedDate = parseDateFromText(line)
                val weekMatch = Regex("""\bWeek\s*(\d{1,2})\b""", RegexOption.IGNORE_CASE).find(line)
                val hasDate = parsedDate != null || weekMatch != null || lower.contains("tba") || lower.contains("tbd")
                val hasWeight = weightRegex.find(line) != null

                if (!hasDate && (policyPhrases.any { lower.contains(it) } || line.length > 100)) continue

                val isBulletOrNumbered = Regex("""^\s*(?:[-*•]|\d+\.)\s+""").containsMatchIn(line)
                val hasActionableKeyword =
                    lower.contains("exam") ||
                        lower.contains("midterm") ||
                        lower.contains("final") ||
                        lower.contains("quiz") ||
                        lower.contains("test") ||
                        lower.contains("paper") ||
                        lower.contains("project") ||
                        lower.contains("essay") ||
                        lower.contains("presentation") ||
                        lower.contains("assignment") ||
                        lower.contains("homework") ||
                        lower.contains("problem set") ||
                        lower.contains("ps ") ||
                        lower.contains("lab") ||
                        lower.contains("data collection") ||
                        lower.contains("review") ||
                        lower.contains("analysis") ||
                        lower.contains("case study") ||
                        lower.contains("reflection") ||
                        lower.contains("response") ||
                        lower.contains("exercise") ||
                        lower.contains("critique") ||
                        lower.contains("journal") ||
                        lower.contains("submission") ||
                        lower.contains("field trip") ||
                        (isBulletOrNumbered && (hasDate || hasWeight))

                val isExplicitMajorItem = Regex(
                    """(?i)\b(exam\s*\d+|final\s*exam|midterm|quiz\s*\d+|paper\s*\d+|project\s*\d+|hw\s*\d+|assignment\s*\d+)\b"""
                ).containsMatchIn(line)
                if (!isExplicitMajorItem && !(hasActionableKeyword && (hasDate || hasWeight))) {
                    continue
                }

                val type = determineItemType(line)
                var dueDateText = "TBD"
                var dueDateMillis = 0L
                var dueLocalDate = referenceDate

                if (parsedDate != null) {
                    dueDateText = parsedDate.text
                    dueDateMillis = parsedDate.millis
                    dueLocalDate = parsedDate.localDate
                } else if (weekMatch != null) {
                    dueDateText = "Week ${weekMatch.groupValues[1]}"
                } else if (lower.contains("tba") || lower.contains("tbd")) {
                    dueDateText = "TBA"
                }

                var weight = weightRegex.find(line)?.groupValues?.get(1)?.toFloatOrNull()?.let { Math.round(it) }?.coerceIn(0, 100) ?: 0
                if (weight == 0) {
                    weight = determineWeight(line, type, gradingWeights)
                }

                var cleanTitle = cleanDeliverableTitle(line)
                if (cleanTitle.isBlank() || cleanTitle.length < 3) {
                    cleanTitle = when (type) {
                        SyllabusItemType.EXAM -> "Course Exam"
                        SyllabusItemType.PROJECT -> "Term Project"
                        SyllabusItemType.HOMEWORK -> "Homework Assignment"
                        SyllabusItemType.READING -> "Weekly Reading"
                    }
                }

                val prepSteps = generateBackwardChainedPrepSteps(type, cleanTitle)
                items.add(
                    ParsedDeliverable(
                        title = cleanTitle,
                        type = type,
                        dueDateText = dueDateText,
                        dueDateMillis = dueDateMillis,
                        weightPercent = weight,
                        prepSteps = prepSteps,
                        dueDate = dueLocalDate
                    )
                )
            }
        }

        val distinctItems = items.distinctBy { it.title.lowercase(Locale.ROOT) }

        return ParsedSyllabus(
            courseCode = detectedCode,
            courseName = formattedCourseName,
            deliverables = if (distinctItems.isNotEmpty() || !fallbackWhenEmpty) {
                distinctItems
            } else {
                val midDate = referenceDate.plusDays(14)
                val endDate = referenceDate.plusDays(35)
                listOf(
                    ParsedDeliverable(
                        title = "Course Midterm Exam",
                        type = SyllabusItemType.EXAM,
                        dueDateText = "Mid-Semester",
                        dueDateMillis = midDate.atStartOfDay(zone).toInstant().toEpochMilli(),
                        weightPercent = 25,
                        prepSteps = generateBackwardChainedPrepSteps(SyllabusItemType.EXAM, "Course Midterm Exam"),
                        dueDate = midDate
                    ),
                    ParsedDeliverable(
                        title = "Final Deliverable / Term Project",
                        type = SyllabusItemType.PROJECT,
                        dueDateText = "End of Term",
                        dueDateMillis = endDate.atStartOfDay(zone).toInstant().toEpochMilli(),
                        weightPercent = 30,
                        prepSteps = generateBackwardChainedPrepSteps(SyllabusItemType.PROJECT, "Final Deliverable"),
                        dueDate = endDate
                    )
                )
            },
            semester = detectedSemester
        )
    }

    fun generateBackwardChainedPrepSteps(
        type: SyllabusItemType,
        title: String
    ): List<String> = when (type) {
        SyllabusItemType.EXAM -> listOf(
            "Gather lecture slides & review guide (15m starter)",
            "Active recall: solve 5 high-yield practice problems (25m focus)",
            "Review formulas & weak conceptual areas (25m focus)",
            "Quick calm warm-up review (15m prior to exam)"
        )
        SyllabusItemType.PROJECT -> listOf(
            "Topic selection & write 3 core research questions (15m starter)",
            "Draft outline & bullet-point thesis arguments (25m focus)",
            "Write rough draft section 1 & 2 (25m focus)",
            "Write remaining sections & conclusions (25m focus)",
            "Proofread, check formatting & citations (15m wrap-up)"
        )
        SyllabusItemType.HOMEWORK -> listOf(
            "Read assignment instructions & setup workspace (10m starter)",
            "Work on first half of questions (25m focus)",
            "Complete remaining questions & review submission (20m wrap-up)"
        )
        SyllabusItemType.READING -> listOf(
            "Skim chapter summary, headings & bold vocabulary (10m starter)",
            "Read primary sections & highlight 3 key takeaways (20m focus)"
        )
    }

    fun generateBackwardChainedPrepSteps(
        title: String,
        dueDate: LocalDate,
        type: SyllabusItemType
    ): List<String> = when (type) {
        SyllabusItemType.EXAM -> listOf(
            "Gather lecture slides & review guide for $title (${dueDate.minusDays(7)})",
            "Active recall: solve 5 practice problems for $title (${dueDate.minusDays(5)})",
            "Review formulas & weak areas for $title (${dueDate.minusDays(3)})",
            "Quick calm warm-up review for $title (${dueDate.minusDays(1)})"
        )
        SyllabusItemType.PROJECT -> listOf(
            "Topic selection & outline for $title (${dueDate.minusDays(10)})",
            "Draft core sections for $title (${dueDate.minusDays(7)})",
            "Complete remaining draft sections for $title (${dueDate.minusDays(3)})",
            "Proofread, check formatting & submit $title (${dueDate.minusDays(1)})"
        )
        SyllabusItemType.HOMEWORK -> listOf(
            "Read instructions & setup workspace for $title (${dueDate.minusDays(3)})",
            "Complete first half of $title (${dueDate.minusDays(2)})",
            "Finish & submit $title (${dueDate.minusDays(1)})"
        )
        SyllabusItemType.READING -> listOf(
            "Skim chapter headings & summary for $title (${dueDate.minusDays(2)})",
            "Read primary sections & take notes for $title (${dueDate.minusDays(1)})"
        )
    }

    fun parseMonth(monthStr: String): Month = when (monthStr.lowercase(Locale.ROOT).take(3)) {
        "jan" -> Month.JANUARY
        "feb" -> Month.FEBRUARY
        "mar" -> Month.MARCH
        "apr" -> Month.APRIL
        "may" -> Month.MAY
        "jun" -> Month.JUNE
        "jul" -> Month.JULY
        "aug" -> Month.AUGUST
        "sep" -> Month.SEPTEMBER
        "oct" -> Month.OCTOBER
        "nov" -> Month.NOVEMBER
        "dec" -> Month.DECEMBER
        else -> Month.OCTOBER
    }

    fun cleanDeliverableTitle(raw: String): String {
        val stripBoilerplate = Regex(
            """(?i)\b(?:using Google Docs|\(?\d+,\s*\d+\)?|How to Write.*|Research Methods.*|---\s*PAGE BREAK\s*---|before class on.*|\?)\b"""
        )
        val stripDates = Regex(
            """(?i)\b(?:(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\.?\s+\d{1,2}(?:st|nd|rd|th)?(?:,?\s+(?:20\d{2}|\d{2}))?|\d{1,2}(?:st|nd|rd|th)?\s+(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\.?(?:,?\s+(?:20\d{2}|\d{2}))?|20\d{2}-\d{1,2}-\d{1,2}|\d{1,2}-\d{1,2}-(?:20\d{2}|\d{2})|\d{1,2}/\d{1,2}(?:/(?:20\d{2}|\d{2}))?)\b"""
        )
        val stripWeights = Regex(
            """(?i)\(\s*\d+(?:\.\d+)?\s*(?:%|points|pts)\s*\)|\b\d+(?:\.\d+)?\s*(?:%|points|pts)(?!\w)"""
        )
        val stripShortHyphenDate = Regex("""(?i)(?:[-–—:]|\bdue|\bon)\s*\b\d{1,2}-\d{1,2}\b(?!\s*(?:pages?|pgs?|pp|mins?|minutes?|hrs?|hours?|secs?|seconds?|words?|slides?|chapters?|problems?|questions?|points?|pts?)\b)""")
        var clean = raw
            .replace(Regex("""(?i)\b(Mon|Tue|Wed|Thu|Fri|Sat|Sun)\.?,?\s*"""), "")
            .replace(stripBoilerplate, "")
            .replace(stripWeights, "")
            .replace(stripDates, "")
            .replace(stripShortHyphenDate, "")
            .replace(Regex("""(?i)\bCh\.?\s*\d+(?:-\d+)?\s*\([A-Za-z\s]+\)"""), "")
            .replace(Regex("""(?i)\bTBA\b|\bTBD\b"""), "")
            .replace(Regex("""^[0-9\-\*\•\.\)]+\s*"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim(':', '-', '–', '—', ' ', '|', ',', ';', '*', '?')

        if (clean.length > 55) {
            clean = clean.take(52) + "..."
        }
        val lowerClean = clean.lowercase(Locale.ROOT)
        if (lowerClean.endsWith(" of") ||
            lowerClean.endsWith(" the") ||
            lowerClean.endsWith(" to") ||
            lowerClean.endsWith(" on") ||
            lowerClean.endsWith(" for")
        ) {
            return ""
        }
        return clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }

    fun determineItemType(title: String): SyllabusItemType {
        val lower = title.lowercase(Locale.ROOT)
        return when {
            lower.contains("paper") ||
                lower.contains("project") ||
                lower.contains("essay") ||
                lower.contains("presentation") ||
                lower.contains("thesis") ||
                lower.contains("lab report") ||
                lower.contains("report") ||
                lower.contains("data collection") ||
                lower.contains("analysis") ||
                lower.contains("case study") ||
                lower.contains("critique") ||
                lower.contains("portfolio") ||
                lower.contains("draft") -> SyllabusItemType.PROJECT
            lower.contains("exam") ||
                lower.contains("midterm") ||
                lower.contains("quiz") ||
                lower.contains("test") ||
                lower.contains("final exam") ||
                (
                    lower.contains("final") &&
                        !lower.contains("paper") &&
                        !lower.contains("project") &&
                        !lower.contains("draft") &&
                        !lower.contains("submission")
                    ) -> SyllabusItemType.EXAM
            lower.contains("reading") || lower.contains("chapter") || lower.contains("discussion") -> SyllabusItemType.READING
            else -> SyllabusItemType.HOMEWORK
        }
    }

    fun determineWeight(
        title: String,
        type: SyllabusItemType,
        gradingWeights: Map<String, Int>
    ): Int {
        val lower = title.lowercase(Locale.ROOT)
        if (lower.contains("draft") && !lower.contains("final")) {
            return 0
        }
        for ((cat, w) in gradingWeights) {
            if (lower.contains(cat) && (lower.contains("final") || !lower.contains("draft"))) {
                return w
            }
        }
        if (lower.contains("paper 1") && (lower.contains("final") || lower.contains("graded"))) {
            return gradingWeights["paper 1"] ?: 20
        }
        if (lower.contains("paper 2") && (lower.contains("final") || lower.contains("graded"))) {
            return gradingWeights["paper 2"] ?: 30
        }
        if (lower.contains("citi")) return gradingWeights["citi"] ?: 3
        if (lower.contains("group evaluation") || lower.contains("wrap-up")) return gradingWeights["engagement"] ?: 5
        if (type == SyllabusItemType.EXAM && (lower.contains("exam ") || lower.contains("midterm") || lower.contains("final"))) {
            return 15
        }
        return 0
    }
}
