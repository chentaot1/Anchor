package com.anchor.adhd.desktop.db

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class DesktopTask(
    val id: Long,
    val title: String,
    val notes: String = "",
    val durationMinutes: Int = 25,
    val difficulty: String = "MEDIUM",
    val isCompleted: Boolean = false,
    val isNextAction: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
)

data class DesktopFocusSession(
    val id: Long,
    val taskTitle: String,
    val durationSeconds: Int,
    val actualSeconds: Int,
    val completed: Boolean,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
)

data class DesktopFunLink(
    val id: Long,
    val name: String,
    val url: String,
    val emoji: String,
    val sortOrder: Int,
    val enabled: Boolean,
)

data class DesktopBlockRule(
    val id: Long,
    val target: String,
    val ruleType: String,
    val enabled: Boolean,
    val scheduleMode: String = "FOCUS_ONLY", // "FOCUS_ONLY", "ALWAYS_24_7", "CLASS_HOURS", "LEISURE_QUOTA", "TRACK_ONLY", "SHARED_POOL"
    val dailyAllowanceMinutes: Int = -1, // -1 = unmetered / relies on scheduleMode; >= 0 is daily quota in minutes
    val lockUntilEpoch: Long = 0L, // 0L = unlocked; timestamp when 24h cooling-off lock expires
    val quotaGroup: String? = null, // e.g. "AI_BROWSER"
)

data class DesktopClassSchedule(
    val id: Long = 0,
    val courseCode: String,
    val courseName: String,
    val sessionType: String = "Lecture", // "Lecture", "Discussion", "Lab"
    val dayOfWeek: Int, // 1 = Monday ... 7 = Sunday (java.time.DayOfWeek.value)
    val startMinuteOfDay: Int, // e.g. 11:00 = 660
    val endMinuteOfDay: Int, // e.g. 12:00 = 720
) {
    fun formatTimeRange(): String {
        fun fmt(min: Int): String {
            val h = min / 60
            val m = min % 60
            val ampm = if (h >= 12) "PM" else "AM"
            val h12 = if (h == 0) 12 else if (h > 12) h - 12 else h
            return if (m == 0) "$h12$ampm" else String.format("%d:%02d%s", h12, m, ampm)
        }
        return "${fmt(startMinuteOfDay)} - ${fmt(endMinuteOfDay)}"
    }

    fun isCurrentlyActive(nowDayOfWeek: Int, nowMinuteOfDay: Int): Boolean {
        return dayOfWeek == nowDayOfWeek && nowMinuteOfDay in startMinuteOfDay until endMinuteOfDay
    }
}

data class DesktopDailyUsage(
    val target: String,
    val usageDate: String, // "YYYY-MM-DD"
    val secondsUsed: Int,
)

data class DailyProtectionStats(
    val logicalDate: String,
    val quickPassesUsed: Int = 0,
    val deflectedCount: Int = 0,
)

enum class SyllabusItemType {
    EXAM, // Midterm, Final, Quiz
    PROJECT, // Term paper, Group project, Presentation, Essay
    HOMEWORK, // Weekly problem sets, Labs, Code assignments
    READING, // Readings, Discussion boards, Textbook chapters
}

data class DesktopCourse(
    val id: Long,
    val code: String,
    val name: String,
    val colorHex: String = "#4E8098",
    val createdAtMillis: Long = System.currentTimeMillis(),
)

data class DesktopSyllabusItem(
    val id: Long,
    val courseCode: String,
    val title: String,
    val itemType: SyllabusItemType = SyllabusItemType.HOMEWORK,
    val dueDateText: String,
    val dueDateMillis: Long = 0L,
    val weightPercent: Int = 0,
    val prepSteps: List<String> = emptyList(),
    val isCompleted: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

data class DesktopHarborState(
    val totalSessions: Int = 0,
    val totalMinutes: Int = 0,
    val streakDays: Int = 1,
    val unlockedBoat: Boolean = false,
    val unlockedWake: Boolean = false,
    val unlockedAurora: Boolean = false,
    val unlockedLanterns: Boolean = false,
    val unlockedLighthouse: Boolean = false,
    val unlockedSweepingBeam: Boolean = false,
    val lastFocusLogicalDate: String = "",
)

/**
 * Windows 11 persistent SQLite database engine.
 * Stores SQLite data at %APPDATA%\Anchor\anchor.db with WAL mode and transaction safety.
 */
class AnchorDesktopDatabase(
    val scope: CoroutineScope,
    private val customDbFile: File? = null,
) {
    private val dbFile: File by lazy {
        customDbFile ?: run {
            val appData = System.getenv("APPDATA") ?: (System.getProperty("user.home") + "/AppData/Roaming")
            val dir = File(appData, "Anchor")
            if (!dir.exists()) dir.mkdirs()
            File(dir, "anchor.db")
        }
    }

    val appDataDirectory: File
        get() = dbFile.parentFile ?: run {
            val appData = System.getenv("APPDATA") ?: (System.getProperty("user.home") + "/AppData/Roaming")
            File(appData, "Anchor")
        }

    var lastAutoBackupFile: File? = null
        private set

    private val connectionUrl: String
        get() = "jdbc:sqlite:${dbFile.absolutePath}"

    private val _tasks = MutableStateFlow<List<DesktopTask>>(emptyList())
    val tasks: StateFlow<List<DesktopTask>> = _tasks.asStateFlow()

    private val _courses = MutableStateFlow<List<DesktopCourse>>(emptyList())
    val courses: StateFlow<List<DesktopCourse>> = _courses.asStateFlow()

    private val _syllabusItems = MutableStateFlow<List<DesktopSyllabusItem>>(emptyList())
    val syllabusItems: StateFlow<List<DesktopSyllabusItem>> = _syllabusItems.asStateFlow()

    private val _funLinks = MutableStateFlow<List<DesktopFunLink>>(emptyList())
    val funLinks: StateFlow<List<DesktopFunLink>> = _funLinks.asStateFlow()

    private val _harborState = MutableStateFlow(DesktopHarborState())
    val harborState: StateFlow<DesktopHarborState> = _harborState.asStateFlow()

    private val _blockRules = MutableStateFlow<List<DesktopBlockRule>>(emptyList())
    val blockRules: StateFlow<List<DesktopBlockRule>> = _blockRules.asStateFlow()

    private val _classSchedules = MutableStateFlow<List<DesktopClassSchedule>>(emptyList())
    val classSchedules: StateFlow<List<DesktopClassSchedule>> = _classSchedules.asStateFlow()

    private val _dailyUsages = MutableStateFlow<Map<String, Int>>(emptyMap())
    val dailyUsages: StateFlow<Map<String, Int>> = _dailyUsages.asStateFlow()

    private val _dailyProtectionStats = MutableStateFlow(DailyProtectionStats(getLogicalDateString()))
    val dailyProtectionStats: StateFlow<DailyProtectionStats> = _dailyProtectionStats.asStateFlow()

    private fun getConnection(): Connection {
        Class.forName("org.sqlite.JDBC")
        val conn = DriverManager.getConnection(connectionUrl)
        try {
            conn.createStatement().use { stmt ->
                stmt.execute("PRAGMA busy_timeout = 5000;")
                stmt.execute("PRAGMA foreign_keys = ON;")
            }
        } catch (_: Exception) {
        }
        return conn
    }

    suspend fun initialize() =
        withContext(Dispatchers.IO) {
            Class.forName("org.sqlite.JDBC")
            getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute("PRAGMA journal_mode = WAL;")
                    stmt.execute("PRAGMA busy_timeout = 5000;")
                    stmt.execute("PRAGMA foreign_keys = ON;")

                    // Metadata key-value store for one-time migrations
                    stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS meta_kv (
                            key TEXT PRIMARY KEY,
                            value TEXT NOT NULL
                        );
                        """.trimIndent(),
                    )

                    // Tasks table
                    stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS tasks (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            title TEXT NOT NULL,
                            notes TEXT DEFAULT '',
                            durationMinutes INTEGER DEFAULT 25,
                            difficulty TEXT DEFAULT 'MEDIUM',
                            isCompleted INTEGER DEFAULT 0,
                            isNextAction INTEGER DEFAULT 0,
                            createdAtMillis INTEGER NOT NULL,
                            sortOrder INTEGER DEFAULT 0
                        );
                        """.trimIndent(),
                    )

                    // Focus sessions table
                    stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS focus_sessions (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            taskTitle TEXT NOT NULL,
                            durationSeconds INTEGER NOT NULL,
                            actualSeconds INTEGER NOT NULL,
                            completed INTEGER DEFAULT 1,
                            startedAtMillis INTEGER NOT NULL,
                            endedAtMillis INTEGER NOT NULL
                        );
                        """.trimIndent(),
                    )

                    // Fun links table
                    stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS fun_links (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            name TEXT NOT NULL,
                            url TEXT NOT NULL UNIQUE,
                            emoji TEXT NOT NULL,
                            sortOrder INTEGER DEFAULT 0,
                            enabled INTEGER DEFAULT 1
                        );
                        """.trimIndent(),
                    )

                    // Block rules table
                    stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS block_rules (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            target TEXT NOT NULL UNIQUE,
                            ruleType TEXT DEFAULT 'APP',
                            enabled INTEGER DEFAULT 1,
                            scheduleMode TEXT DEFAULT 'FOCUS_ONLY',
                            dailyAllowanceMinutes INTEGER DEFAULT -1,
                            lockUntilEpoch INTEGER DEFAULT 0,
                            quotaGroup TEXT DEFAULT NULL
                        );
                        """.trimIndent(),
                    )

                    try {
                        stmt.execute("ALTER TABLE block_rules ADD COLUMN scheduleMode TEXT DEFAULT 'FOCUS_ONLY';")
                    } catch (_: Exception) {
                        // Column already exists
                    }
                    try {
                        stmt.execute("ALTER TABLE block_rules ADD COLUMN dailyAllowanceMinutes INTEGER DEFAULT -1;")
                    } catch (_: Exception) {
                        // Column already exists
                    }
                    try {
                        stmt.execute("ALTER TABLE block_rules ADD COLUMN lockUntilEpoch INTEGER DEFAULT 0;")
                    } catch (_: Exception) {
                        // Column already exists
                    }
                    try {
                        stmt.execute("ALTER TABLE block_rules ADD COLUMN quotaGroup TEXT DEFAULT NULL;")
                    } catch (_: Exception) {
                        // Column already exists
                    }

                    // Migration: Normalize & deduplicate existing WEB rules with https://, www., or trailing slashes
                    try {
                        val rs = stmt.executeQuery("SELECT id, target, ruleType FROM block_rules WHERE ruleType = 'WEB'")
                        val toUpdate = mutableListOf<Pair<Long, String>>()
                        while (rs.next()) {
                            val id = rs.getLong("id")
                            val raw = rs.getString("target")
                            val cleaned = sanitizeTarget(raw, "WEB")
                            if (cleaned != raw && cleaned.isNotBlank()) {
                                toUpdate.add(id to cleaned)
                            }
                        }
                        rs.close()

                        for ((id, cleaned) in toUpdate) {
                            var duplicateExists = false
                            conn.prepareStatement("SELECT id FROM block_rules WHERE target = ? AND id != ?").use { checkStmt ->
                                checkStmt.setString(1, cleaned)
                                checkStmt.setLong(2, id)
                                val checkRs = checkStmt.executeQuery()
                                duplicateExists = checkRs.next()
                                checkRs.close()
                            }
                            if (duplicateExists) {
                                conn.prepareStatement("DELETE FROM block_rules WHERE id = ?").use { delStmt ->
                                    delStmt.setLong(1, id)
                                    delStmt.executeUpdate()
                                }
                            } else {
                                conn.prepareStatement("UPDATE block_rules SET target = ? WHERE id = ?").use { upStmt ->
                                    upStmt.setString(1, cleaned)
                                    upStmt.setLong(2, id)
                                    upStmt.executeUpdate()
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore migration failure
                    }

                    // Class schedules table
                    stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS class_schedules (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            courseCode TEXT NOT NULL,
                            courseName TEXT NOT NULL,
                            sessionType TEXT DEFAULT 'Lecture',
                            dayOfWeek INTEGER NOT NULL,
                            startMinuteOfDay INTEGER NOT NULL,
                            endMinuteOfDay INTEGER NOT NULL
                        );
                        """.trimIndent(),
                    )

                    // Daily app usage table
                    stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS daily_app_usage (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            target TEXT NOT NULL,
                            usageDate TEXT NOT NULL,
                            secondsUsed INTEGER DEFAULT 0,
                            UNIQUE(target, usageDate)
                        );
                        """.trimIndent(),
                    )

                    // Daily protection stats table (quick passes & deflected count tied to 4 AM logical date)
                    stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS daily_protection_stats (
                            logicalDate TEXT PRIMARY KEY,
                            quickPassesUsed INTEGER DEFAULT 0,
                            deflectedCount INTEGER DEFAULT 0
                        );
                        """.trimIndent(),
                    )

                    // Courses table
                    stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS courses (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            code TEXT NOT NULL UNIQUE,
                            name TEXT NOT NULL,
                            colorHex TEXT DEFAULT '#4E8098',
                            createdAtMillis INTEGER NOT NULL
                        );
                        """.trimIndent(),
                    )

                    // Syllabus deliverables & milestones table
                    stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS syllabus_items (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            courseCode TEXT NOT NULL,
                            title TEXT NOT NULL,
                            itemType TEXT DEFAULT 'HOMEWORK',
                            dueDateText TEXT NOT NULL,
                            dueDateMillis INTEGER DEFAULT 0,
                            weightPercent INTEGER DEFAULT 0,
                            prepSteps TEXT DEFAULT '',
                            isCompleted INTEGER DEFAULT 0,
                            createdAtMillis INTEGER NOT NULL
                        );
                        """.trimIndent(),
                    )

                    // Companion / Harbor stats table
                    stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS harbor_stats (
                            id INTEGER PRIMARY KEY,
                            totalSessions INTEGER DEFAULT 0,
                            totalMinutes INTEGER DEFAULT 0,
                            streakDays INTEGER DEFAULT 1,
                            last_focus_logical_date TEXT DEFAULT ''
                        );
                        """.trimIndent(),
                    )

                    try {
                        stmt.execute("ALTER TABLE harbor_stats ADD COLUMN last_focus_logical_date TEXT DEFAULT '';")
                    } catch (_: Exception) {
                        // Column already exists
                    }

                    stmt.execute(
                        """
                        INSERT OR IGNORE INTO harbor_stats (id, totalSessions, totalMinutes, streakDays, last_focus_logical_date)
                        VALUES (1, 0, 0, 1, '');
                        """.trimIndent(),
                    )

                    // Initial seed fun links
                    stmt.execute(
                        "INSERT OR IGNORE INTO fun_links (name, url, emoji, sortOrder, enabled) VALUES ('Wordle', 'https://www.nytimes.com/games/wordle', '🟩', 0, 1);",
                    )
                    stmt.execute(
                        "INSERT OR IGNORE INTO fun_links (name, url, emoji, sortOrder, enabled) VALUES ('Connections', 'https://www.nytimes.com/games/connections', '🟪', 1, 1);",
                    )
                    stmt.execute(
                        "INSERT OR IGNORE INTO fun_links (name, url, emoji, sortOrder, enabled) VALUES ('Chess Puzzles', 'https://lichess.org/training', '♟️', 2, 1);",
                    )
                    stmt.execute(
                        "INSERT OR IGNORE INTO fun_links (name, url, emoji, sortOrder, enabled) VALUES ('Contexto', 'https://contexto.me', '🧩', 3, 1);",
                    )

                    // Initial seed block rules (INSERT OR IGNORE preserves user customizations, allowances, modes, and 24h locks)
                    stmt.execute(
                        "INSERT OR IGNORE INTO block_rules (target, ruleType, enabled, scheduleMode, dailyAllowanceMinutes) VALUES ('discord.exe', 'APP', 1, 'LEISURE_QUOTA', 30);",
                    )
                    stmt.execute(
                        "INSERT OR IGNORE INTO block_rules (target, ruleType, enabled, scheduleMode, dailyAllowanceMinutes) VALUES ('cursor.exe', 'APP', 1, 'LEISURE_QUOTA', 75);",
                    )
                    stmt.execute(
                        "INSERT OR IGNORE INTO block_rules (target, ruleType, enabled, scheduleMode, dailyAllowanceMinutes) VALUES ('antigravity.exe', 'APP', 1, 'LEISURE_QUOTA', 75);",
                    )
                    stmt.execute(
                        "INSERT OR IGNORE INTO block_rules (target, ruleType, enabled, scheduleMode, dailyAllowanceMinutes, quotaGroup) VALUES ('gemini', 'WEB', 1, 'SHARED_POOL', 210, 'AI_BROWSER');",
                    )
                    stmt.execute(
                        "INSERT OR IGNORE INTO block_rules (target, ruleType, enabled, scheduleMode, dailyAllowanceMinutes, quotaGroup) VALUES ('chatgpt', 'WEB', 1, 'SHARED_POOL', 210, 'AI_BROWSER');",
                    )
                    stmt.execute(
                        "INSERT OR IGNORE INTO block_rules (target, ruleType, enabled, scheduleMode, dailyAllowanceMinutes, quotaGroup) VALUES ('claude', 'WEB', 1, 'SHARED_POOL', 210, 'AI_BROWSER');",
                    )
                    stmt.execute(
                        "INSERT OR IGNORE INTO block_rules (target, ruleType, enabled, scheduleMode) VALUES ('spotify.exe', 'APP', 1, 'FOCUS_ONLY');",
                    )
                    stmt.execute(
                        "INSERT OR IGNORE INTO block_rules (target, ruleType, enabled, scheduleMode, dailyAllowanceMinutes) VALUES ('youtube', 'WEB', 1, 'FOCUS_ONLY', 20);",
                    )
                    stmt.execute(
                        "INSERT OR IGNORE INTO block_rules (target, ruleType, enabled, scheduleMode) VALUES ('reddit', 'WEB', 1, 'ALWAYS_24_7');",
                    )
                    stmt.execute(
                        "INSERT OR IGNORE INTO block_rules (target, ruleType, enabled, scheduleMode) VALUES ('twitter / x', 'WEB', 1, 'ALWAYS_24_7');",
                    )

                    // One-time migration gated by meta_kv so user edits/locks survive subsequent initialize() calls
                    val migrationDone =
                        conn.prepareStatement("SELECT value FROM meta_kv WHERE key = 'block_rules_seed_v1';").use { checkStmt ->
                            val rs = checkStmt.executeQuery()
                            rs.next()
                        }
                    if (!migrationDone) {
                        stmt.execute(
                            "UPDATE block_rules SET quotaGroup = 'AI_BROWSER' WHERE LOWER(target) IN ('gemini', 'chatgpt', 'claude') AND quotaGroup IS NULL;",
                        )
                        stmt.execute(
                            "INSERT OR REPLACE INTO meta_kv (key, value) VALUES ('block_rules_seed_v1', '1');",
                        )
                    }

                    // Initial starter task if empty
                    val countRes = stmt.executeQuery("SELECT COUNT(*) FROM tasks;")
                    if (countRes.next() && countRes.getInt(1) == 0) {
                        stmt.execute(
                            """
                            INSERT INTO tasks (title, notes, durationMinutes, difficulty, isCompleted, isNextAction, createdAtMillis, sortOrder)
                            VALUES ('Anchor on your next milestone', 'Welcome to Anchor PC! Press space or click Start to begin.', 25, 'EASY', 0, 1, ${System.currentTimeMillis()}, 0);
                            """.trimIndent(),
                        )
                    }

                    // Real semester courses: ENVI 101, CHIN 103, PSYC 344
                    val nowMillis = System.currentTimeMillis()
                    stmt.execute("INSERT OR IGNORE INTO courses (code, name, colorHex, createdAtMillis) VALUES ('ENVI 101', 'Environmental Studies 101', '#38A169', $nowMillis);")
                    stmt.execute("INSERT OR IGNORE INTO courses (code, name, colorHex, createdAtMillis) VALUES ('CHIN 103', 'Elementary Chinese 103', '#E53E3E', $nowMillis);")
                    stmt.execute("INSERT OR IGNORE INTO courses (code, name, colorHex, createdAtMillis) VALUES ('PSYC 344', 'Psychology 344', '#805AD5', $nowMillis);")

                    // Seed user's actual weekly timetable into class_schedules if empty
                    val scheduleCountRes = stmt.executeQuery("SELECT COUNT(*) FROM class_schedules;")
                    if (scheduleCountRes.next() && scheduleCountRes.getInt(1) == 0) {
                        // ENVI 101: Monday to Thursday, 11:00 AM - 12:00 PM (11:00=660, 12:00=720)
                        for (d in 1..4) {
                            stmt.execute("INSERT INTO class_schedules (courseCode, courseName, sessionType, dayOfWeek, startMinuteOfDay, endMinuteOfDay) VALUES ('ENVI 101', 'Environmental Studies 101', 'Lecture', $d, 660, 720);")
                        }
                        // CHIN 103: Monday to Thursday, 2:45 PM - 3:45 PM (14:45=885, 15:45=945)
                        for (d in 1..4) {
                            stmt.execute("INSERT INTO class_schedules (courseCode, courseName, sessionType, dayOfWeek, startMinuteOfDay, endMinuteOfDay) VALUES ('CHIN 103', 'Elementary Chinese 103', 'Language Class', $d, 885, 945);")
                        }
                        // PSYC 344 Lecture: Monday, Wednesday, 5:30 PM - 6:30 PM (17:30=1050, 18:30=1110)
                        stmt.execute("INSERT INTO class_schedules (courseCode, courseName, sessionType, dayOfWeek, startMinuteOfDay, endMinuteOfDay) VALUES ('PSYC 344', 'Psychology 344', 'Lecture', 1, 1050, 1110);")
                        stmt.execute("INSERT INTO class_schedules (courseCode, courseName, sessionType, dayOfWeek, startMinuteOfDay, endMinuteOfDay) VALUES ('PSYC 344', 'Psychology 344', 'Lecture', 3, 1050, 1110);")

                        // PSYC 344 Discussion: Friday, 1:30 PM - 2:30 PM (13:30=810, 14:30=870)
                        stmt.execute("INSERT INTO class_schedules (courseCode, courseName, sessionType, dayOfWeek, startMinuteOfDay, endMinuteOfDay) VALUES ('PSYC 344', 'Psychology 344', 'Discussion', 5, 810, 870);")
                    }

                    // Initial sample courses and syllabus items if empty
                    val courseCountRes = stmt.executeQuery("SELECT COUNT(*) FROM courses WHERE code NOT IN ('ENVI 101', 'CHIN 103', 'PSYC 344');")
                    if (courseCountRes.next() && courseCountRes.getInt(1) == 0) {
                        val now = System.currentTimeMillis()
                        stmt.execute(
                            """
                            INSERT INTO courses (code, name, colorHex, createdAtMillis)
                            VALUES ('CS 101', 'Intro to Computer Science', '#4E8098', $now);
                            """.trimIndent(),
                        )

                        stmt.execute(
                            """
                            INSERT INTO syllabus_items (courseCode, title, itemType, dueDateText, dueDateMillis, weightPercent, prepSteps, isCompleted, createdAtMillis)
                            VALUES ('CS 101', 'Midterm Exam: Algorithms & Recursion', 'EXAM', 'Oct 24', ${now + 14L * 86400000L}, 25, 'Review Lectures 1-6 study guide|||Solve 3 practice recursion problems|||Flashcards on Big-O complexity', 0, $now);
                            """.trimIndent(),
                        )
                        stmt.execute(
                            """
                            INSERT INTO syllabus_items (courseCode, title, itemType, dueDateText, dueDateMillis, weightPercent, prepSteps, isCompleted, createdAtMillis)
                            VALUES ('CS 101', 'Final Project: Web App Prototype', 'PROJECT', 'Nov 20', ${now + 40L * 86400000L}, 30, 'Brainstorm project idea & outline schema|||Setup repo and basic UI layout|||Implement core database queries|||Test & submit', 0, $now);
                            """.trimIndent(),
                        )
                        stmt.execute(
                            """
                            INSERT INTO syllabus_items (courseCode, title, itemType, dueDateText, dueDateMillis, weightPercent, prepSteps, isCompleted, createdAtMillis)
                            VALUES ('CS 101', 'Problem Set 4: Recursion & Trees', 'HOMEWORK', 'Oct 16', ${now + 6L * 86400000L}, 5, 'Read assignment prompt|||Code problem 1 & 2|||Run test cases', 0, $now);
                            """.trimIndent(),
                        )
                    }
                }
            }
            refreshAll()
        }

    suspend fun refreshAll() =
        withContext(Dispatchers.IO) {
            refreshAllSync()
        }

    internal fun refreshAllSync() {
        refreshTasks()
        refreshCoursesSync()
        refreshSyllabusItemsSync()
        refreshFunLinks()
        refreshHarborState()
        refreshBlockRules()
        refreshClassSchedules()
        refreshDailyUsage()
        refreshDailyProtectionStats()
    }

    private fun refreshTasks() {
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT * FROM tasks ORDER BY isCompleted ASC, isNextAction DESC, sortOrder ASC, id DESC;")
                val list = mutableListOf<DesktopTask>()
                while (rs.next()) {
                    list.add(
                        DesktopTask(
                            id = rs.getLong("id"),
                            title = rs.getString("title"),
                            notes = rs.getString("notes") ?: "",
                            durationMinutes = rs.getInt("durationMinutes"),
                            difficulty = rs.getString("difficulty") ?: "MEDIUM",
                            isCompleted = rs.getInt("isCompleted") == 1,
                            isNextAction = rs.getInt("isNextAction") == 1,
                            createdAtMillis = rs.getLong("createdAtMillis"),
                            sortOrder = rs.getInt("sortOrder"),
                        ),
                    )
                }
                _tasks.value = list
            }
        }
    }

    private fun refreshFunLinks() {
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT * FROM fun_links WHERE enabled = 1 ORDER BY sortOrder ASC;")
                val list = mutableListOf<DesktopFunLink>()
                while (rs.next()) {
                    list.add(
                        DesktopFunLink(
                            id = rs.getLong("id"),
                            name = rs.getString("name"),
                            url = rs.getString("url"),
                            emoji = rs.getString("emoji"),
                            sortOrder = rs.getInt("sortOrder"),
                            enabled = rs.getInt("enabled") == 1,
                        ),
                    )
                }
                _funLinks.value = list
            }
        }
    }

    private fun refreshHarborState() {
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT * FROM harbor_stats WHERE id = 1;")
                if (rs.next()) {
                    val sessions = rs.getInt("totalSessions")
                    val minutes = rs.getInt("totalMinutes")
                    val streak = rs.getInt("streakDays").coerceAtLeast(1)
                    val lastFocusDate = runCatching { rs.getString("last_focus_logical_date") }.getOrNull() ?: ""

                    _harborState.value =
                        DesktopHarborState(
                            totalSessions = sessions,
                            totalMinutes = minutes,
                            streakDays = streak,
                            unlockedBoat = sessions >= 1,
                            unlockedWake = sessions >= 5,
                            unlockedAurora = sessions >= 15,
                            unlockedLanterns = sessions >= 30,
                            unlockedLighthouse = sessions >= 50,
                            unlockedSweepingBeam = sessions >= 100,
                            lastFocusLogicalDate = lastFocusDate,
                        )
                }
            }
        }
    }

    suspend fun insertTask(
        title: String,
        durationMinutes: Int = 25,
    ) = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext
        getConnection().use { conn ->
            conn
                .prepareStatement(
                    """
                    INSERT INTO tasks (title, durationMinutes, createdAtMillis, sortOrder)
                    VALUES (?, ?, ?, 0);
                    """.trimIndent(),
                ).use { stmt ->
                    stmt.setString(1, title.trim())
                    stmt.setInt(2, durationMinutes)
                    stmt.setLong(3, System.currentTimeMillis())
                    stmt.executeUpdate()
                }
        }
        refreshTasks()
    }

    suspend fun toggleTaskCompleted(id: Long) =
        withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                conn.prepareStatement("UPDATE tasks SET isCompleted = ((isCompleted + 1) % 2) WHERE id = ?;").use { stmt ->
                    stmt.setLong(1, id)
                    stmt.executeUpdate()
                }
            }
            refreshTasks()
        }

    suspend fun deleteTask(id: Long) =
        withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                conn.prepareStatement("DELETE FROM tasks WHERE id = ?;").use { stmt ->
                    stmt.setLong(1, id)
                    stmt.executeUpdate()
                }
            }
            refreshTasks()
        }

    suspend fun deleteCompletedTasks() =
        withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeUpdate("DELETE FROM tasks WHERE isCompleted = 1;")
                }
            }
            refreshTasks()
        }

    suspend fun setNextActionTask(id: Long) =
        withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                val minSort =
                    conn.createStatement().use { stmt ->
                        val rs = stmt.executeQuery("SELECT MIN(sortOrder) FROM tasks;")
                        if (rs.next()) rs.getInt(1) else 0
                    }
                conn.createStatement().use { stmt ->
                    stmt.executeUpdate("UPDATE tasks SET isNextAction = 0;")
                }
                conn.prepareStatement("UPDATE tasks SET isNextAction = 1, sortOrder = ? WHERE id = ?;").use { stmt ->
                    stmt.setInt(1, minSort - 1)
                    stmt.setLong(2, id)
                    stmt.executeUpdate()
                }
            }
            refreshTasks()
        }

    suspend fun updateTaskDetails(
        id: Long,
        title: String,
        durationMinutes: Int,
    ) = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext
        getConnection().use { conn ->
            conn.prepareStatement("UPDATE tasks SET title = ?, durationMinutes = ? WHERE id = ?;").use { stmt ->
                stmt.setString(1, title.trim())
                stmt.setInt(2, durationMinutes.coerceIn(1, 240))
                stmt.setLong(3, id)
                stmt.executeUpdate()
            }
        }
        refreshTasks()
    }

    suspend fun getTodayFocusMinutes(): Int =
        withContext(Dispatchers.IO) {
            val startOfLogicalDayMillis =
                getLogicalDate()
                    .atTime(DAILY_RESET_HOUR, 0)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

            var minutes = 0
            try {
                getConnection().use { conn ->
                    conn.prepareStatement("SELECT SUM(actualSeconds) FROM focus_sessions WHERE startedAtMillis >= ?;").use { stmt ->
                        stmt.setLong(1, startOfLogicalDayMillis)
                        val rs = stmt.executeQuery()
                        if (rs.next()) {
                            minutes = rs.getInt(1) / 60
                        }
                    }
                }
            } catch (_: Exception) {
            }
            minutes
        }

    suspend fun makeTaskNow(id: Long) =
        setNextActionTask(id)

    suspend fun upsertAndMakeTaskNow(title: String, durationMinutes: Int): DesktopTask? = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return@withContext null
        val existing = tasks.value.firstOrNull { !it.isCompleted && it.title.equals(cleanTitle, ignoreCase = true) }
        if (existing == null) insertTask(cleanTitle, durationMinutes.coerceIn(1, 240))
        else updateTaskDetails(existing.id, existing.title, durationMinutes)
        val target = tasks.value.firstOrNull { !it.isCompleted && it.title.equals(cleanTitle, ignoreCase = true) }
            ?: return@withContext null
        makeTaskNow(target.id)
        tasks.value.firstOrNull { it.id == target.id }
    }

    suspend fun recordFocusSession(
        durationMinutes: Int,
        taskTitle: String,
        now: LocalDateTime = LocalDateTime.now(),
    ) = recordFocusSession(
        taskTitle = taskTitle,
        durationSeconds = durationMinutes * 60,
        actualSeconds = durationMinutes * 60,
        completed = true,
        now = now,
    )

    suspend fun recordFocusSession(
        taskTitle: String,
        durationSeconds: Int,
        actualSeconds: Int,
        completed: Boolean,
        now: LocalDateTime = LocalDateTime.now(),
    ) = withContext(Dispatchers.IO) {
        val nowMillis =
            now
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        val start = nowMillis - (actualSeconds * 1000L)
        val minutesToAdd = (actualSeconds / 60).coerceAtLeast(1)
        val currentLogicalDate = getLogicalDateString(now)

        getConnection().use { conn ->
            conn
                .prepareStatement(
                    """
                    INSERT INTO focus_sessions (taskTitle, durationSeconds, actualSeconds, completed, startedAtMillis, endedAtMillis)
                    VALUES (?, ?, ?, ?, ?, ?);
                    """.trimIndent(),
                ).use { stmt ->
                    stmt.setString(1, taskTitle)
                    stmt.setInt(2, durationSeconds)
                    stmt.setInt(3, actualSeconds)
                    stmt.setInt(4, if (completed) 1 else 0)
                    stmt.setLong(5, start)
                    stmt.setLong(6, nowMillis)
                    stmt.executeUpdate()
                }

            var currentStreak = 1
            var lastFocusLogicalDate = ""
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT streakDays, last_focus_logical_date FROM harbor_stats WHERE id = 1;")
                if (rs.next()) {
                    currentStreak = rs.getInt("streakDays").coerceAtLeast(1)
                    lastFocusLogicalDate = runCatching { rs.getString("last_focus_logical_date") }.getOrNull()?.trim() ?: ""
                }
            }

            val newStreak =
                if (!completed) {
                    currentStreak
                } else {
                    when {
                        lastFocusLogicalDate.isBlank() -> 1
                        lastFocusLogicalDate == currentLogicalDate -> currentStreak.coerceAtLeast(1)
                        runCatching {
                            LocalDate.parse(lastFocusLogicalDate).plusDays(1) == LocalDate.parse(currentLogicalDate)
                        }.getOrDefault(false) -> currentStreak.coerceAtLeast(1) + 1
                        else -> 1
                    }
                }
            val updatedLogicalDate = if (completed) currentLogicalDate else lastFocusLogicalDate

            conn
                .prepareStatement(
                    """
                    UPDATE harbor_stats 
                    SET totalSessions = totalSessions + 1,
                        totalMinutes = totalMinutes + ?,
                        streakDays = ?,
                        last_focus_logical_date = ?
                    WHERE id = 1;
                    """.trimIndent(),
                ).use { stmt ->
                    stmt.setInt(1, minutesToAdd)
                    stmt.setInt(2, newStreak)
                    stmt.setString(3, updatedLogicalDate)
                    stmt.executeUpdate()
                }
        }
        refreshHarborState()
    }

    private fun refreshBlockRules() {
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT * FROM block_rules ORDER BY ruleType ASC, id ASC;")
                val list = mutableListOf<DesktopBlockRule>()
                while (rs.next()) {
                    val mode = runCatching { rs.getString("scheduleMode") }.getOrNull() ?: "FOCUS_ONLY"
                    val dailyAllowance = runCatching { rs.getInt("dailyAllowanceMinutes") }.getOrNull() ?: -1
                    val lockUntil = runCatching { rs.getLong("lockUntilEpoch") }.getOrNull() ?: 0L
                    val group = runCatching { rs.getString("quotaGroup") }.getOrNull()
                    list.add(
                        DesktopBlockRule(
                            id = rs.getLong("id"),
                            target = rs.getString("target"),
                            ruleType = rs.getString("ruleType") ?: "APP",
                            enabled = rs.getInt("enabled") == 1,
                            scheduleMode = if (mode.isNotBlank()) mode else "FOCUS_ONLY",
                            dailyAllowanceMinutes = dailyAllowance,
                            lockUntilEpoch = lockUntil,
                            quotaGroup = group,
                        ),
                    )
                }
                _blockRules.value = list
            }
        }
    }

    private fun refreshClassSchedules() {
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT * FROM class_schedules ORDER BY dayOfWeek ASC, startMinuteOfDay ASC;")
                val list = mutableListOf<DesktopClassSchedule>()
                while (rs.next()) {
                    list.add(
                        DesktopClassSchedule(
                            id = rs.getLong("id"),
                            courseCode = rs.getString("courseCode"),
                            courseName = rs.getString("courseName") ?: "",
                            sessionType = rs.getString("sessionType") ?: "Lecture",
                            dayOfWeek = rs.getInt("dayOfWeek"),
                            startMinuteOfDay = rs.getInt("startMinuteOfDay"),
                            endMinuteOfDay = rs.getInt("endMinuteOfDay"),
                        ),
                    )
                }
                _classSchedules.value = list
            }
        }
    }

    fun refreshDailyUsage(now: LocalDateTime = LocalDateTime.now()) {
        val today = getLogicalDateString(now)
        val map = mutableMapOf<String, Int>()
        getConnection().use { conn ->
            conn.prepareStatement("SELECT target, secondsUsed FROM daily_app_usage WHERE usageDate = ?;").use { stmt ->
                stmt.setString(1, today)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    map[rs.getString("target").lowercase().trim()] = rs.getInt("secondsUsed")
                }
            }
        }
        _dailyUsages.value = map
    }

    fun refreshDailyProtectionStats(now: LocalDateTime = LocalDateTime.now()): DailyProtectionStats {
        val stats = getProtectionStatsSync(getLogicalDate(now))
        _dailyProtectionStats.value = stats
        return stats
    }

    fun getProtectionStatsSync(logicalDate: LocalDate = getLogicalDate()): DailyProtectionStats {
        val dateStr = logicalDate.toString()
        return try {
            getConnection().use { conn ->
                conn.prepareStatement(
                    "SELECT quickPassesUsed, deflectedCount FROM daily_protection_stats WHERE logicalDate = ?;",
                ).use { stmt ->
                    stmt.setString(1, dateStr)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        DailyProtectionStats(
                            logicalDate = dateStr,
                            quickPassesUsed = rs.getInt("quickPassesUsed").coerceAtLeast(0),
                            deflectedCount = rs.getInt("deflectedCount").coerceAtLeast(0),
                        )
                    } else {
                        DailyProtectionStats(logicalDate = dateStr, quickPassesUsed = 0, deflectedCount = 0)
                    }
                }
            }
        } catch (_: Exception) {
            DailyProtectionStats(logicalDate = dateStr, quickPassesUsed = 0, deflectedCount = 0)
        }
    }

    suspend fun getDailyProtectionStats(now: LocalDateTime = LocalDateTime.now()): DailyProtectionStats =
        withContext(Dispatchers.IO) {
            refreshDailyProtectionStats(now)
        }

    fun persistQuickPassesUsedSync(
        quickPassesUsed: Int,
        logicalDate: LocalDate = getLogicalDate(),
    ) {
        val dateStr = logicalDate.toString()
        val clamped = quickPassesUsed.coerceAtLeast(0)
        try {
            getConnection().use { conn ->
                conn.prepareStatement(
                    """
                    INSERT INTO daily_protection_stats (logicalDate, quickPassesUsed, deflectedCount)
                    VALUES (?, ?, 0)
                    ON CONFLICT(logicalDate) DO UPDATE SET quickPassesUsed = excluded.quickPassesUsed;
                    """.trimIndent(),
                ).use { stmt ->
                    stmt.setString(1, dateStr)
                    stmt.setInt(2, clamped)
                    stmt.executeUpdate()
                }
            }
            _dailyProtectionStats.value = getProtectionStatsSync(logicalDate)
        } catch (_: Exception) {
        }
    }

    fun persistDeflectedCountSync(
        deflectedCount: Int,
        logicalDate: LocalDate = getLogicalDate(),
    ) {
        val dateStr = logicalDate.toString()
        val clamped = deflectedCount.coerceAtLeast(0)
        try {
            getConnection().use { conn ->
                conn.prepareStatement(
                    """
                    INSERT INTO daily_protection_stats (logicalDate, quickPassesUsed, deflectedCount)
                    VALUES (?, 0, ?)
                    ON CONFLICT(logicalDate) DO UPDATE SET deflectedCount = excluded.deflectedCount;
                    """.trimIndent(),
                ).use { stmt ->
                    stmt.setString(1, dateStr)
                    stmt.setInt(2, clamped)
                    stmt.executeUpdate()
                }
            }
            _dailyProtectionStats.value = getProtectionStatsSync(logicalDate)
        } catch (_: Exception) {
        }
    }

    suspend fun recordQuickPassUsed(now: LocalDateTime = LocalDateTime.now()): Int =
        withContext(Dispatchers.IO) {
            val logicalDate = getLogicalDate(now)
            val current = getProtectionStatsSync(logicalDate).quickPassesUsed
            val updated = current + 1
            persistQuickPassesUsedSync(updated, logicalDate)
            updated
        }

    suspend fun recordDeflection(now: LocalDateTime = LocalDateTime.now()): Int =
        withContext(Dispatchers.IO) {
            val logicalDate = getLogicalDate(now)
            val current = getProtectionStatsSync(logicalDate).deflectedCount
            val updated = current + 1
            persistDeflectedCountSync(updated, logicalDate)
            updated
        }

    suspend fun recordAppUsage(target: String, secondsToAdd: Int = 1) = withContext(Dispatchers.IO) {
        if (target.isBlank() || secondsToAdd <= 0) return@withContext
        val clean = target.lowercase().trim()
        val today = getLogicalDateString()
        getConnection().use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO daily_app_usage (target, usageDate, secondsUsed)
                VALUES (?, ?, ?)
                ON CONFLICT(target, usageDate) DO UPDATE SET secondsUsed = secondsUsed + excluded.secondsUsed;
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, clean)
                stmt.setString(2, today)
                stmt.setInt(3, secondsToAdd)
                stmt.executeUpdate()
            }
        }
        refreshDailyUsage()
    }

    suspend fun toggleBlockRule(id: Long): Boolean =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            var allowed = true
            getConnection().use { conn ->
                conn.prepareStatement("SELECT enabled, lockUntilEpoch FROM block_rules WHERE id = ?;").use { stmt ->
                    stmt.setLong(1, id)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        val currentEnabled = rs.getInt("enabled") == 1
                        val lockUntil = rs.getLong("lockUntilEpoch")
                        // If enabled and under 24h cooling lock, disabling is blocked (tightening allowed, loosening blocked)
                        if (currentEnabled && lockUntil > now) {
                            allowed = false
                        } else {
                            val newEnabled = if (currentEnabled) 0 else 1
                            conn.prepareStatement("UPDATE block_rules SET enabled = ? WHERE id = ?;").use { updateStmt ->
                                updateStmt.setInt(1, newEnabled)
                                updateStmt.setLong(2, id)
                                updateStmt.executeUpdate()
                            }
                        }
                    }
                }
            }
            refreshBlockRules()
            allowed
        }

    suspend fun updateBlockRuleScheduleMode(
        id: Long,
        scheduleMode: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        var allowed = true
        getConnection().use { conn ->
            conn.prepareStatement("SELECT scheduleMode, lockUntilEpoch FROM block_rules WHERE id = ?;").use { stmt ->
                stmt.setLong(1, id)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    val currentMode = rs.getString("scheduleMode") ?: "FOCUS_ONLY"
                    val lockUntil = rs.getLong("lockUntilEpoch")
                    // If locked, cannot switch from ALWAYS_24_7 to FOCUS_ONLY (loosening)
                    val isLoosening = currentMode.equals("ALWAYS_24_7", ignoreCase = true) && !scheduleMode.equals("ALWAYS_24_7", ignoreCase = true)
                    if (lockUntil > now && isLoosening) {
                        allowed = false
                    } else {
                        conn.prepareStatement("UPDATE block_rules SET scheduleMode = ? WHERE id = ?;").use { updateStmt ->
                            updateStmt.setString(1, scheduleMode)
                            updateStmt.setLong(2, id)
                            updateStmt.executeUpdate()
                        }
                    }
                }
            }
        }
        refreshBlockRules()
        allowed
    }

    suspend fun updateBlockRuleAllowance(
        id: Long,
        dailyAllowanceMinutes: Int,
        lockFor24Hours: Boolean = false,
    ): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        var allowed = true
        getConnection().use { conn ->
            conn.prepareStatement("SELECT dailyAllowanceMinutes, lockUntilEpoch, quotaGroup FROM block_rules WHERE id = ?;").use { stmt ->
                stmt.setLong(1, id)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    val currentAllowance = rs.getInt("dailyAllowanceMinutes")
                    val currentLock = rs.getLong("lockUntilEpoch")
                    val quotaGroup = runCatching { rs.getString("quotaGroup") }.getOrNull()
                    // Loosening: increasing minutes or removing quota
                    val isLoosening = (currentAllowance in 0 until dailyAllowanceMinutes) || (currentAllowance >= 0 && dailyAllowanceMinutes == -1)
                    if (currentLock > now && isLoosening) {
                        allowed = false
                    } else {
                        val newLock = if (lockFor24Hours) maxOf(currentLock, now + (24L * 3600_000L)) else currentLock
                        if (!quotaGroup.isNullOrBlank()) {
                            conn.prepareStatement("UPDATE block_rules SET dailyAllowanceMinutes = ?, lockUntilEpoch = ? WHERE quotaGroup = ?;").use { updateStmt ->
                                updateStmt.setInt(1, dailyAllowanceMinutes)
                                updateStmt.setLong(2, newLock)
                                updateStmt.setString(3, quotaGroup)
                                updateStmt.executeUpdate()
                            }
                        } else {
                            conn.prepareStatement("UPDATE block_rules SET dailyAllowanceMinutes = ?, lockUntilEpoch = ? WHERE id = ?;").use { updateStmt ->
                                updateStmt.setInt(1, dailyAllowanceMinutes)
                                updateStmt.setLong(2, newLock)
                                updateStmt.setLong(3, id)
                                updateStmt.executeUpdate()
                            }
                        }
                    }
                }
            }
        }
        refreshBlockRules()
        allowed
    }

    suspend fun lockBlockRuleFor24Hours(id: Long) = withContext(Dispatchers.IO) {
        val lockEpoch = System.currentTimeMillis() + (24L * 3600_000L)
        getConnection().use { conn ->
            conn.prepareStatement("UPDATE block_rules SET lockUntilEpoch = ? WHERE id = ?;").use { stmt ->
                stmt.setLong(1, lockEpoch)
                stmt.setLong(2, id)
                stmt.executeUpdate()
            }
        }
        refreshBlockRules()
    }

    suspend fun insertBlockRule(
        target: String,
        ruleType: String = "APP",
        scheduleMode: String = "FOCUS_ONLY",
        dailyAllowanceMinutes: Int = -1,
        lockUntilEpoch: Long = 0L,
        quotaGroup: String? = null,
    ) = withContext(Dispatchers.IO) {
        val cleanTarget = sanitizeTarget(target, ruleType)
        if (cleanTarget.isBlank()) return@withContext
        getConnection().use { conn ->
            conn.prepareStatement(
                "INSERT OR IGNORE INTO block_rules (target, ruleType, enabled, scheduleMode, dailyAllowanceMinutes, lockUntilEpoch, quotaGroup) VALUES (?, ?, 1, ?, ?, ?, ?);",
            ).use { stmt ->
                stmt.setString(1, cleanTarget)
                stmt.setString(2, ruleType.uppercase())
                stmt.setString(3, scheduleMode)
                stmt.setInt(4, dailyAllowanceMinutes)
                stmt.setLong(5, lockUntilEpoch)
                stmt.setString(6, quotaGroup)
                stmt.executeUpdate()
            }
        }
        refreshBlockRules()
    }

    suspend fun insertClassSchedule(
        courseCode: String,
        courseName: String,
        sessionType: String,
        dayOfWeek: Int,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
    ) = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            conn.prepareStatement(
                "INSERT INTO class_schedules (courseCode, courseName, sessionType, dayOfWeek, startMinuteOfDay, endMinuteOfDay) VALUES (?, ?, ?, ?, ?, ?);",
            ).use { stmt ->
                stmt.setString(1, courseCode.trim())
                stmt.setString(2, courseName.trim())
                stmt.setString(3, sessionType.trim())
                stmt.setInt(4, dayOfWeek)
                stmt.setInt(5, startMinuteOfDay)
                stmt.setInt(6, endMinuteOfDay)
                stmt.executeUpdate()
            }
        }
        refreshClassSchedules()
    }

    suspend fun deleteClassSchedule(id: Long) = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            conn.prepareStatement("DELETE FROM class_schedules WHERE id = ?;").use { stmt ->
                stmt.setLong(1, id)
                stmt.executeUpdate()
            }
        }
        refreshClassSchedules()
    }

    suspend fun setBlockRuleEnabled(
        target: String,
        enabled: Boolean,
    ) = withContext(Dispatchers.IO) {
        val clean = target.trim().lowercase()
        getConnection().use { conn ->
            val updated =
                conn.prepareStatement("UPDATE block_rules SET enabled = ? WHERE LOWER(target) LIKE ?;").use { stmt ->
                    stmt.setInt(1, if (enabled) 1 else 0)
                    stmt.setString(2, "%$clean%")
                    stmt.executeUpdate()
                }
            if (updated == 0 && enabled) {
                val ruleType = if (clean.contains(".exe")) "APP" else "WEB"
                conn
                    .prepareStatement(
                        "INSERT OR IGNORE INTO block_rules (target, ruleType, enabled, scheduleMode) VALUES (?, ?, 1, 'ALWAYS_24_7');",
                    ).use { stmt ->
                        stmt.setString(1, clean)
                        stmt.setString(2, ruleType)
                        stmt.executeUpdate()
                    }
            }
        }
        refreshBlockRules()
    }

    suspend fun deleteBlockRule(id: Long): Boolean =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            var allowed = true
            getConnection().use { conn ->
                conn.prepareStatement("SELECT lockUntilEpoch FROM block_rules WHERE id = ?;").use { stmt ->
                    stmt.setLong(1, id)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        val lockUntil = rs.getLong("lockUntilEpoch")
                        if (lockUntil > now) {
                            allowed = false
                        } else {
                            conn.prepareStatement("DELETE FROM block_rules WHERE id = ?;").use { delStmt ->
                                delStmt.setLong(1, id)
                                delStmt.executeUpdate()
                            }
                        }
                    }
                }
            }
            refreshBlockRules()
            allowed
        }

    suspend fun getBlockRules(): List<String> =
        withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery("SELECT target FROM block_rules WHERE enabled = 1;")
                    val list = mutableListOf<String>()
                    while (rs.next()) {
                        list.add(rs.getString("target"))
                    }
                    list
                }
            }
        }

    fun getFocusSessions(): List<DesktopFocusSession> {
        val list = mutableListOf<DesktopFocusSession>()
        try {
            getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery("SELECT * FROM focus_sessions ORDER BY startedAtMillis ASC, id ASC;")
                    while (rs.next()) {
                        list.add(
                            DesktopFocusSession(
                                id = rs.getLong("id"),
                                taskTitle = rs.getString("taskTitle") ?: "",
                                durationSeconds = rs.getInt("durationSeconds"),
                                actualSeconds = rs.getInt("actualSeconds"),
                                completed = rs.getInt("completed") == 1,
                                startedAtMillis = rs.getLong("startedAtMillis"),
                                endedAtMillis = rs.getLong("endedAtMillis"),
                            ),
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
        return list
    }

    private fun createPreResetAutoBackup(reason: String): File? {
        return try {
            val dir = appDataDirectory
            if (!dir.exists()) dir.mkdirs()
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"))
            val backupFile = File(dir, "anchor_auto_backup_${reason}_$timestamp.json")
            exportDataToFile(backupFile)
            lastAutoBackupFile = backupFile
            backupFile
        } catch (_: Exception) {
            null
        }
    }

    suspend fun clearAllTasks() =
        withContext(Dispatchers.IO) {
            createPreResetAutoBackup("clear_tasks")
            getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute("DELETE FROM tasks;")
                    stmt.execute(
                        """
                        INSERT INTO tasks (title, notes, durationMinutes, difficulty, isCompleted, isNextAction, createdAtMillis, sortOrder)
                        VALUES ('Anchor on your next milestone', 'Welcome to your fresh slate! Press space or click Start to begin.', 25, 'EASY', 0, 1, ${System.currentTimeMillis()}, 0);
                        """.trimIndent(),
                    )
                }
            }
            refreshTasks()
        }

    suspend fun resetFocusHistoryAndStreak() =
        withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute("DELETE FROM focus_sessions;")
                    stmt.execute(
                        """
                        UPDATE harbor_stats 
                        SET totalSessions = 0,
                            totalMinutes = 0,
                            streakDays = 1,
                            last_focus_logical_date = ''
                        WHERE id = 1;
                        """.trimIndent(),
                    )
                }
            }
            refreshHarborState()
        }

    suspend fun refreshCourses() =
        withContext(Dispatchers.IO) {
            refreshCoursesSync()
        }

    private fun refreshCoursesSync() {
        val list = mutableListOf<DesktopCourse>()
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT id, code, name, colorHex, createdAtMillis FROM courses ORDER BY code ASC;")
                while (rs.next()) {
                    list.add(
                        DesktopCourse(
                            id = rs.getLong("id"),
                            code = rs.getString("code"),
                            name = rs.getString("name"),
                            colorHex = rs.getString("colorHex"),
                            createdAtMillis = rs.getLong("createdAtMillis"),
                        ),
                    )
                }
            }
        }
        _courses.value = list
    }

    suspend fun refreshSyllabusItems() =
        withContext(Dispatchers.IO) {
            refreshSyllabusItemsSync()
        }

    private fun refreshSyllabusItemsSync() {
        val list = mutableListOf<DesktopSyllabusItem>()
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs =
                    stmt.executeQuery(
                        "SELECT id, courseCode, title, itemType, dueDateText, dueDateMillis, weightPercent, prepSteps, isCompleted, createdAtMillis FROM syllabus_items ORDER BY isCompleted ASC, dueDateMillis ASC, id ASC;",
                    )
                while (rs.next()) {
                    val typeStr = rs.getString("itemType")
                    val itemType =
                        try {
                            SyllabusItemType.valueOf(typeStr)
                        } catch (_: Exception) {
                            SyllabusItemType.HOMEWORK
                        }
                    val stepsRaw = rs.getString("prepSteps") ?: ""
                    val steps = if (stepsRaw.isNotBlank()) stepsRaw.split("|||").filter { it.isNotBlank() } else emptyList()
                    list.add(
                        DesktopSyllabusItem(
                            id = rs.getLong("id"),
                            courseCode = rs.getString("courseCode"),
                            title = rs.getString("title"),
                            itemType = itemType,
                            dueDateText = rs.getString("dueDateText"),
                            dueDateMillis = rs.getLong("dueDateMillis"),
                            weightPercent = rs.getInt("weightPercent"),
                            prepSteps = steps,
                            isCompleted = rs.getInt("isCompleted") == 1,
                            createdAtMillis = rs.getLong("createdAtMillis"),
                        ),
                    )
                }
            }
        }
        _syllabusItems.value = list
    }

    suspend fun insertCourse(
        code: String,
        name: String,
        colorHex: String = "#4E8098",
    ): Long =
        withContext(Dispatchers.IO) {
            var generatedId = -1L
            getConnection().use { conn ->
                val sql = "INSERT OR REPLACE INTO courses (code, name, colorHex, createdAtMillis) VALUES (?, ?, ?, ?);"
                conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
                    stmt.setString(1, code.trim().uppercase())
                    stmt.setString(2, name.trim())
                    stmt.setString(3, colorHex)
                    stmt.setLong(4, System.currentTimeMillis())
                    stmt.executeUpdate()
                    val rs = stmt.generatedKeys
                    if (rs.next()) generatedId = rs.getLong(1)
                }
            }
            refreshCoursesSync()
            generatedId
        }

    suspend fun insertSyllabusItem(
        courseCode: String,
        title: String,
        itemType: SyllabusItemType = SyllabusItemType.HOMEWORK,
        dueDateText: String = "",
        dueDateMillis: Long = 0L,
        weightPercent: Int = 0,
        prepSteps: List<String> = emptyList(),
    ): Long =
        withContext(Dispatchers.IO) {
            var generatedId = -1L
            val stepsRaw = prepSteps.joinToString("|||")
            getConnection().use { conn ->
                val sql =
                    """
                    INSERT INTO syllabus_items (courseCode, title, itemType, dueDateText, dueDateMillis, weightPercent, prepSteps, isCompleted, createdAtMillis)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?);
                    """.trimIndent()
                conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
                    stmt.setString(1, courseCode.trim().uppercase())
                    stmt.setString(2, title.trim())
                    stmt.setString(3, itemType.name)
                    stmt.setString(4, dueDateText.trim())
                    stmt.setLong(5, dueDateMillis)
                    stmt.setInt(6, weightPercent)
                    stmt.setString(7, stepsRaw)
                    stmt.setLong(8, System.currentTimeMillis())
                    stmt.executeUpdate()
                    val rs = stmt.generatedKeys
                    if (rs.next()) generatedId = rs.getLong(1)
                }
            }
            refreshSyllabusItemsSync()
            generatedId
        }

    suspend fun toggleSyllabusItemCompleted(id: Long) =
        withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                val sql = "UPDATE syllabus_items SET isCompleted = CASE WHEN isCompleted = 1 THEN 0 ELSE 1 END WHERE id = ?;"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setLong(1, id)
                    stmt.executeUpdate()
                }
            }
            refreshSyllabusItemsSync()
        }

    suspend fun deleteSyllabusItem(id: Long) =
        withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                val sql = "DELETE FROM syllabus_items WHERE id = ?;"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setLong(1, id)
                    stmt.executeUpdate()
                }
            }
            refreshSyllabusItemsSync()
        }

    suspend fun deleteCourse(courseCode: String) =
        withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                conn.prepareStatement("DELETE FROM courses WHERE code = ?;").use { stmt ->
                    stmt.setString(1, courseCode)
                    stmt.executeUpdate()
                }
                conn.prepareStatement("DELETE FROM syllabus_items WHERE courseCode = ?;").use { stmt ->
                    stmt.setString(1, courseCode)
                    stmt.executeUpdate()
                }
            }
            refreshCoursesSync()
            refreshSyllabusItemsSync()
        }

    suspend fun resetAllData() =
        withContext(Dispatchers.IO) {
            createPreResetAutoBackup("factory_reset")
            getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute("DELETE FROM tasks;")
                    stmt.execute("DELETE FROM focus_sessions;")
                    stmt.execute("DELETE FROM courses;")
                    stmt.execute("DELETE FROM syllabus_items;")
                    stmt.execute(
                        """
                        UPDATE harbor_stats 
                        SET totalSessions = 0,
                            totalMinutes = 0,
                            streakDays = 1,
                            last_focus_logical_date = ''
                        WHERE id = 1;
                        """.trimIndent(),
                    )
                    stmt.execute(
                        """
                        INSERT INTO tasks (title, notes, durationMinutes, difficulty, isCompleted, isNextAction, createdAtMillis, sortOrder)
                        VALUES ('Anchor on your next milestone', 'Welcome to your fresh slate! Press space or click Start to begin.', 25, 'EASY', 0, 1, ${System.currentTimeMillis()}, 0);
                        """.trimIndent(),
                    )
                }
            }
            refreshAllSync()
        }

    fun exportDataJson(): String {
        refreshAllSync()
        val sessions = getFocusSessions()
        val harbor = _harborState.value
        val allFunLinks = getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT * FROM fun_links ORDER BY sortOrder ASC;").use { rs ->
                    buildJsonArray {
                        while (rs.next()) add(buildJsonObject {
                            put("id", rs.getLong("id"))
                            put("name", rs.getString("name"))
                            put("url", rs.getString("url"))
                            put("emoji", rs.getString("emoji"))
                            put("sortOrder", rs.getInt("sortOrder"))
                            put("enabled", rs.getInt("enabled") == 1)
                        })
                    }
                }
            }
        }
        val prettyJson = Json { prettyPrint = true }

        val root =
            buildJsonObject {
                put("version", 1)
                put("exportedAtMillis", System.currentTimeMillis())
                put("fun_links", allFunLinks)

                put(
                    "tasks",
                    buildJsonArray {
                        for (t in _tasks.value) {
                            add(
                                buildJsonObject {
                                    put("id", t.id)
                                    put("title", t.title)
                                    put("notes", t.notes)
                                    put("durationMinutes", t.durationMinutes)
                                    put("difficulty", t.difficulty)
                                    put("isCompleted", t.isCompleted)
                                    put("isNextAction", t.isNextAction)
                                    put("createdAtMillis", t.createdAtMillis)
                                    put("sortOrder", t.sortOrder)
                                },
                            )
                        }
                    },
                )

                put(
                    "courses",
                    buildJsonArray {
                        for (c in _courses.value) {
                            add(
                                buildJsonObject {
                                    put("id", c.id)
                                    put("code", c.code)
                                    put("name", c.name)
                                    put("colorHex", c.colorHex)
                                    put("createdAtMillis", c.createdAtMillis)
                                },
                            )
                        }
                    },
                )

                put(
                    "syllabus_items",
                    buildJsonArray {
                        for (s in _syllabusItems.value) {
                            add(
                                buildJsonObject {
                                    put("id", s.id)
                                    put("courseCode", s.courseCode)
                                    put("title", s.title)
                                    put("itemType", s.itemType.name)
                                    put("dueDateText", s.dueDateText)
                                    put("dueDateMillis", s.dueDateMillis)
                                    put("weightPercent", s.weightPercent)
                                    put(
                                        "prepSteps",
                                        buildJsonArray {
                                            for (step in s.prepSteps) {
                                                add(JsonPrimitive(step))
                                            }
                                        },
                                    )
                                    put("isCompleted", s.isCompleted)
                                    put("createdAtMillis", s.createdAtMillis)
                                },
                            )
                        }
                    },
                )

                put(
                    "class_schedules",
                    buildJsonArray {
                        for (cs in _classSchedules.value) {
                            add(
                                buildJsonObject {
                                    put("id", cs.id)
                                    put("courseCode", cs.courseCode)
                                    put("courseName", cs.courseName)
                                    put("sessionType", cs.sessionType)
                                    put("dayOfWeek", cs.dayOfWeek)
                                    put("startMinuteOfDay", cs.startMinuteOfDay)
                                    put("endMinuteOfDay", cs.endMinuteOfDay)
                                },
                            )
                        }
                    },
                )

                put(
                    "block_rules",
                    buildJsonArray {
                        for (br in _blockRules.value) {
                            add(
                                buildJsonObject {
                                    put("id", br.id)
                                    put("target", br.target)
                                    put("ruleType", br.ruleType)
                                    put("enabled", br.enabled)
                                    put("scheduleMode", br.scheduleMode)
                                    put("dailyAllowanceMinutes", br.dailyAllowanceMinutes)
                                    put("lockUntilEpoch", br.lockUntilEpoch)
                                    if (br.quotaGroup != null) {
                                        put("quotaGroup", br.quotaGroup)
                                    }
                                },
                            )
                        }
                    },
                )

                put(
                    "focus_sessions",
                    buildJsonArray {
                        for (fs in sessions) {
                            add(
                                buildJsonObject {
                                    put("id", fs.id)
                                    put("taskTitle", fs.taskTitle)
                                    put("durationSeconds", fs.durationSeconds)
                                    put("actualSeconds", fs.actualSeconds)
                                    put("completed", fs.completed)
                                    put("startedAtMillis", fs.startedAtMillis)
                                    put("endedAtMillis", fs.endedAtMillis)
                                },
                            )
                        }
                    },
                )

                put(
                    "harbor_stats",
                    buildJsonObject {
                        put("totalSessions", harbor.totalSessions)
                        put("totalMinutes", harbor.totalMinutes)
                        put("streakDays", harbor.streakDays)
                        put("last_focus_logical_date", harbor.lastFocusLogicalDate)
                    },
                )
            }

        return prettyJson.encodeToString(JsonObject.serializer(), root)
    }

    fun exportDataToFile(file: File) {
        file.parentFile?.let { parent ->
            if (!parent.exists()) parent.mkdirs()
        }
        file.writeText(exportDataJson(), Charsets.UTF_8)
    }

    fun importDataJson(
        jsonString: String,
        replaceExisting: Boolean = false,
    ): Int {
        val root = normalizeAndroidBackup(Json.parseToJsonElement(jsonString).jsonObject)
        var importedCount = 0

        getConnection().use { conn ->
            conn.autoCommit = false
            try {
                conn.createStatement().use { stmt ->
                    if (replaceExisting) {
                        stmt.execute("DELETE FROM tasks;")
                        stmt.execute("DELETE FROM courses;")
                        stmt.execute("DELETE FROM syllabus_items;")
                        stmt.execute("DELETE FROM class_schedules;")
                        stmt.execute("DELETE FROM block_rules;")
                        stmt.execute("DELETE FROM focus_sessions;")
                        stmt.execute("DELETE FROM fun_links;")
                        stmt.execute("UPDATE harbor_stats SET totalSessions = 0, totalMinutes = 0, streakDays = 1, last_focus_logical_date = '' WHERE id = 1;")
                    }
                }

                // 1. Tasks
                val tasksArray = root["tasks"]?.jsonArray
                if (tasksArray != null) {
                    if (!replaceExisting && tasksArray.isNotEmpty()) {
                        // Remove auto-generated fresh-slate placeholder task if it's the only one
                        conn.createStatement().use { stmt ->
                            stmt.execute(
                                "DELETE FROM tasks WHERE title = 'Anchor on your next milestone' AND notes LIKE 'Welcome to%';",
                            )
                        }
                    }
                    conn.prepareStatement(
                        """
                        INSERT INTO tasks (title, notes, durationMinutes, difficulty, isCompleted, isNextAction, createdAtMillis, sortOrder)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?);
                        """.trimIndent(),
                    ).use { stmt ->
                        for (el in tasksArray) {
                            val obj = el.jsonObject
                            val title = obj["title"]?.jsonPrimitive?.content ?: continue
                            stmt.setString(1, title)
                            stmt.setString(2, obj["notes"]?.jsonPrimitive?.content ?: "")
                            stmt.setInt(3, obj["durationMinutes"]?.jsonPrimitive?.intOrNull ?: 25)
                            stmt.setString(4, obj["difficulty"]?.jsonPrimitive?.content ?: "MEDIUM")
                            stmt.setInt(5, if (obj["isCompleted"]?.jsonPrimitive?.booleanOrNull == true) 1 else 0)
                            stmt.setInt(6, if (obj["isNextAction"]?.jsonPrimitive?.booleanOrNull == true) 1 else 0)
                            stmt.setLong(7, obj["createdAtMillis"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis())
                            stmt.setInt(8, obj["sortOrder"]?.jsonPrimitive?.intOrNull ?: 0)
                            stmt.executeUpdate()
                            importedCount++
                        }
                    }
                }

                // 2. Courses
                val coursesArray = root["courses"]?.jsonArray
                if (coursesArray != null) {
                    conn.prepareStatement(
                        "INSERT OR REPLACE INTO courses (code, name, colorHex, createdAtMillis) VALUES (?, ?, ?, ?);",
                    ).use { stmt ->
                        for (el in coursesArray) {
                            val obj = el.jsonObject
                            val code = obj["code"]?.jsonPrimitive?.content ?: continue
                            stmt.setString(1, code)
                            stmt.setString(2, obj["name"]?.jsonPrimitive?.content ?: code)
                            stmt.setString(3, obj["colorHex"]?.jsonPrimitive?.content ?: "#4E8098")
                            stmt.setLong(4, obj["createdAtMillis"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis())
                            stmt.executeUpdate()
                            importedCount++
                        }
                    }
                }

                // 3. Syllabus items
                val syllabusArray = (root["syllabus_items"] ?: root["syllabusItems"])?.jsonArray
                if (syllabusArray != null) {
                    conn.prepareStatement(
                        """
                        INSERT INTO syllabus_items (courseCode, title, itemType, dueDateText, dueDateMillis, weightPercent, prepSteps, isCompleted, createdAtMillis)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
                        """.trimIndent(),
                    ).use { stmt ->
                        for (el in syllabusArray) {
                            val obj = el.jsonObject
                            val courseCode = obj["courseCode"]?.jsonPrimitive?.content ?: continue
                            val title = obj["title"]?.jsonPrimitive?.content ?: continue
                            val prepStepsElement = obj["prepSteps"]
                            val stepsJoined =
                                when (prepStepsElement) {
                                    is JsonArray -> prepStepsElement.mapNotNull { it.jsonPrimitive.content }.joinToString("|||")
                                    is JsonPrimitive -> prepStepsElement.content
                                    else -> ""
                                }
                            stmt.setString(1, courseCode)
                            stmt.setString(2, title)
                            stmt.setString(3, obj["itemType"]?.jsonPrimitive?.content ?: "HOMEWORK")
                            stmt.setString(4, obj["dueDateText"]?.jsonPrimitive?.content ?: "")
                            stmt.setLong(5, obj["dueDateMillis"]?.jsonPrimitive?.longOrNull ?: 0L)
                            stmt.setInt(6, obj["weightPercent"]?.jsonPrimitive?.intOrNull ?: 0)
                            stmt.setString(7, stepsJoined)
                            stmt.setInt(8, if (obj["isCompleted"]?.jsonPrimitive?.booleanOrNull == true) 1 else 0)
                            stmt.setLong(9, obj["createdAtMillis"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis())
                            stmt.executeUpdate()
                            importedCount++
                        }
                    }
                }

                // 4. Class schedules
                val schedulesArray = (root["class_schedules"] ?: root["classSchedules"])?.jsonArray
                if (schedulesArray != null) {
                    conn.prepareStatement(
                        """
                        INSERT INTO class_schedules (courseCode, courseName, sessionType, dayOfWeek, startMinuteOfDay, endMinuteOfDay)
                        VALUES (?, ?, ?, ?, ?, ?);
                        """.trimIndent(),
                    ).use { stmt ->
                        for (el in schedulesArray) {
                            val obj = el.jsonObject
                            val courseCode = obj["courseCode"]?.jsonPrimitive?.content ?: continue
                            val courseName = obj["courseName"]?.jsonPrimitive?.content ?: ""
                            val sessionType = obj["sessionType"]?.jsonPrimitive?.content ?: "Lecture"
                            val dayOfWeek = obj["dayOfWeek"]?.jsonPrimitive?.intOrNull ?: 1
                            val startMin = obj["startMinuteOfDay"]?.jsonPrimitive?.intOrNull ?: 0
                            val endMin = obj["endMinuteOfDay"]?.jsonPrimitive?.intOrNull ?: 0
                            stmt.setString(1, courseCode)
                            stmt.setString(2, courseName)
                            stmt.setString(3, sessionType)
                            stmt.setInt(4, dayOfWeek)
                            stmt.setInt(5, startMin)
                            stmt.setInt(6, endMin)
                            stmt.executeUpdate()
                            importedCount++
                        }
                    }
                }

                // 5. Block rules
                val blockRulesArray = (root["block_rules"] ?: root["blockRules"])?.jsonArray
                if (blockRulesArray != null) {
                    conn.prepareStatement(
                        """
                        INSERT OR REPLACE INTO block_rules (target, ruleType, enabled, scheduleMode, dailyAllowanceMinutes, lockUntilEpoch, quotaGroup)
                        VALUES (?, ?, ?, ?, ?, ?, ?);
                        """.trimIndent(),
                    ).use { stmt ->
                        for (el in blockRulesArray) {
                            val obj = el.jsonObject
                            val rawTarget = obj["target"]?.jsonPrimitive?.content ?: continue
                            val ruleType = obj["ruleType"]?.jsonPrimitive?.content ?: "APP"
                            val cleanTarget = sanitizeTarget(rawTarget, ruleType)
                            if (cleanTarget.isBlank()) continue
                            val enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true
                            val scheduleMode = obj["scheduleMode"]?.jsonPrimitive?.content ?: "FOCUS_ONLY"
                            val allowance = obj["dailyAllowanceMinutes"]?.jsonPrimitive?.intOrNull ?: -1
                            val lockUntil = obj["lockUntilEpoch"]?.jsonPrimitive?.longOrNull ?: 0L
                            val quotaGroup = obj["quotaGroup"]?.jsonPrimitive?.content
                            stmt.setString(1, cleanTarget)
                            stmt.setString(2, ruleType)
                            stmt.setInt(3, if (enabled) 1 else 0)
                            stmt.setString(4, scheduleMode)
                            stmt.setInt(5, allowance)
                            stmt.setLong(6, lockUntil)
                            stmt.setString(7, quotaGroup)
                            stmt.executeUpdate()
                            importedCount++
                        }
                    }
                }

                // 6. Focus sessions
                val sessionsArray = (root["focus_sessions"] ?: root["focusSessions"])?.jsonArray
                if (sessionsArray != null) {
                    conn.prepareStatement(
                        """
                        INSERT INTO focus_sessions (taskTitle, durationSeconds, actualSeconds, completed, startedAtMillis, endedAtMillis)
                        VALUES (?, ?, ?, ?, ?, ?);
                        """.trimIndent(),
                    ).use { stmt ->
                        for (el in sessionsArray) {
                            val obj = el.jsonObject
                            val taskTitle = obj["taskTitle"]?.jsonPrimitive?.content ?: "Deep Work"
                            val durationSec = obj["durationSeconds"]?.jsonPrimitive?.intOrNull ?: 1500
                            val actualSec = obj["actualSeconds"]?.jsonPrimitive?.intOrNull ?: durationSec
                            val completed = obj["completed"]?.jsonPrimitive?.booleanOrNull ?: true
                            val startedAt = obj["startedAtMillis"]?.jsonPrimitive?.longOrNull ?: 0L
                            val endedAt = obj["endedAtMillis"]?.jsonPrimitive?.longOrNull ?: startedAt
                            stmt.setString(1, taskTitle)
                            stmt.setInt(2, durationSec)
                            stmt.setInt(3, actualSec)
                            stmt.setInt(4, if (completed) 1 else 0)
                            stmt.setLong(5, startedAt)
                            stmt.setLong(6, endedAt)
                            stmt.executeUpdate()
                            importedCount++
                        }
                    }
                }

                // 7. Harbor stats
                val harborObj = (root["harbor_stats"] ?: root["harborStats"])?.jsonObject
                if (harborObj != null) {
                    val totalSessions = harborObj["totalSessions"]?.jsonPrimitive?.intOrNull ?: 0
                    val totalMinutes = harborObj["totalMinutes"]?.jsonPrimitive?.intOrNull ?: 0
                    val streakDays = (harborObj["streakDays"]?.jsonPrimitive?.intOrNull ?: 1).coerceAtLeast(1)
                    val lastFocusDate =
                        (harborObj["last_focus_logical_date"] ?: harborObj["lastFocusLogicalDate"])
                            ?.jsonPrimitive?.content ?: ""
                    conn.prepareStatement(
                        """
                        UPDATE harbor_stats
                        SET totalSessions = ?,
                            totalMinutes = ?,
                            streakDays = ?,
                            last_focus_logical_date = ?
                        WHERE id = 1;
                        """.trimIndent(),
                    ).use { stmt ->
                        stmt.setInt(1, totalSessions)
                        stmt.setInt(2, totalMinutes)
                        stmt.setInt(3, streakDays)
                        stmt.setString(4, lastFocusDate)
                        stmt.executeUpdate()
                    }
                }

                val linksArray = (root["fun_links"] ?: root["funLinks"]) as? JsonArray
                if (linksArray != null) {
                    conn.prepareStatement(
                        "INSERT INTO fun_links (name, url, emoji, sortOrder, enabled) VALUES (?, ?, ?, ?, ?);",
                    ).use { stmt ->
                        for (el in linksArray) {
                            val obj = el.jsonObject
                            stmt.setString(1, obj["name"]?.jsonPrimitive?.contentOrNull ?: obj["title"]?.jsonPrimitive?.contentOrNull ?: "")
                            stmt.setString(2, obj["url"]?.jsonPrimitive?.contentOrNull ?: "")
                            stmt.setString(3, obj["emoji"]?.jsonPrimitive?.contentOrNull ?: "🔗")
                            stmt.setInt(4, obj["sortOrder"]?.jsonPrimitive?.intOrNull ?: 0)
                            stmt.setInt(5, if (obj["enabled"]?.jsonPrimitive?.booleanOrNull != false) 1 else 0)
                            stmt.executeUpdate()
                            importedCount++
                        }
                    }
                }

                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }

        refreshAllSync()
        return importedCount
    }

    private fun normalizeAndroidBackup(root: JsonObject): JsonObject = buildJsonObject {
        root.forEach { (key, value) -> put(key, value) }
        val tasks = (root["tasks"] as? JsonArray).orEmpty().map { it.jsonObject }
        put("tasks", buildJsonArray {
            tasks.forEach { task -> add(buildJsonObject {
                task.forEach { (key, value) -> put(key, value) }
                val difficulty = task["difficulty"]?.jsonPrimitive?.contentOrNull
                put("difficulty", when (difficulty) { "DEEP" -> "HARD"; "LIGHT" -> "EASY"; else -> difficulty ?: "MEDIUM" })
            }) }
        })
        val assignments = (root["assignments"] as? JsonArray).orEmpty().map { it.jsonObject }
        if (assignments.isNotEmpty()) {
            fun courseOf(item: JsonObject) = item["course"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "Imported course"
            put("courses", buildJsonArray {
                (root["courses"] as? JsonArray)?.forEach { add(it) }
                val existingCodes = (root["courses"] as? JsonArray).orEmpty().map { it.jsonObject["code"]?.jsonPrimitive?.contentOrNull }.toSet()
                assignments.map { courseOf(it) }.distinct().filter { it !in existingCodes }.forEach { code ->
                    add(buildJsonObject { put("code", code); put("name", code) })
                }
            })
            put("syllabus_items", buildJsonArray {
                ((root["syllabus_items"] ?: root["syllabusItems"]) as? JsonArray)?.forEach { add(it) }
                assignments.forEach { item -> add(buildJsonObject {
                    put("courseCode", courseOf(item))
                    put("title", item["title"] ?: JsonPrimitive("Assignment"))
                    put("itemType", "HOMEWORK")
                    put("dueDateMillis", item["dueAtMillis"] ?: JsonPrimitive(0L))
                    val due = item["dueAtMillis"]?.jsonPrimitive?.longOrNull ?: 0L
                    put("dueDateText", if (due > 0L) java.time.Instant.ofEpochMilli(due).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() else "TBD")
                    put("prepSteps", buildJsonArray {
                        item["notes"]?.jsonPrimitive?.contentOrNull.orEmpty().lines().filter { it.isNotBlank() }.forEach { add(JsonPrimitive(it)) }
                    })
                    put("isCompleted", item["isCompleted"] ?: JsonPrimitive(false))
                }) }
            })
        }
        val androidSessions = (root["focusSessions"] as? JsonArray).orEmpty().map { it.jsonObject }
        if (root["focusSessions"] is JsonArray) {
            put("focus_sessions", buildJsonArray {
                (root["focus_sessions"] as? JsonArray)?.forEach { add(it) }
                androidSessions.forEach { session -> add(buildJsonObject {
                    session.forEach { (key, value) -> put(key, value) }
                    val task = tasks.firstOrNull { it["id"] != null && it["id"] == session["taskId"] }
                    put("taskTitle", session["taskTitle"] ?: task?.get("title") ?: JsonPrimitive("Deep Work"))
                    val planned = session["plannedMinutes"]?.jsonPrimitive?.intOrNull ?: 25
                    val actual = session["actualMinutes"]?.jsonPrimitive?.intOrNull ?: if (session["completed"]?.jsonPrimitive?.booleanOrNull == true) planned else 0
                    put("durationSeconds", (planned.toLong().coerceIn(1, 1440) * 60).toInt())
                    put("actualSeconds", (actual.toLong().coerceIn(0, 1440) * 60).toInt())
                }) }
            })
        }
        val companion = when (val value = root["companionState"]) {
            is JsonObject -> value
            is JsonArray -> value.firstOrNull() as? JsonObject
            else -> null
        }
        if (companion != null && root["harbor_stats"] !is JsonObject && root["harborStats"] !is JsonObject) {
            put("harbor_stats", buildJsonObject {
                put("totalSessions", androidSessions.count { it["completed"]?.jsonPrimitive?.booleanOrNull == true })
                put("totalMinutes", androidSessions.sumOf { (it["actualMinutes"]?.jsonPrimitive?.intOrNull ?: 0).coerceAtLeast(0) })
                put("streakDays", companion["streakDays"] ?: JsonPrimitive(1))
                val lastDay = companion["lastActiveDayMillis"]?.jsonPrimitive?.longOrNull
                put("last_focus_logical_date", lastDay?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() }.orEmpty())
            })
        }
    }

    companion object {
        const val DAILY_RESET_HOUR = 4

        fun getLogicalDate(now: java.time.LocalDateTime = java.time.LocalDateTime.now()): java.time.LocalDate {
            // Daily reset occurs at 4:00 AM instead of midnight
            return if (now.hour < DAILY_RESET_HOUR) now.toLocalDate().minusDays(1) else now.toLocalDate()
        }

        fun getLogicalDateString(now: java.time.LocalDateTime = java.time.LocalDateTime.now()): String {
            return getLogicalDate(now).toString()
        }

        fun sanitizeTarget(target: String, ruleType: String): String {
            var clean = target.trim().lowercase()
            if (ruleType.equals("WEB", ignoreCase = true)) {
                if (clean.startsWith("https://")) clean = clean.removePrefix("https://")
                if (clean.startsWith("http://")) clean = clean.removePrefix("http://")
                if (clean.startsWith("www.")) clean = clean.removePrefix("www.")
                clean = clean.trimEnd('/')
                val slashIdx = clean.indexOf('/')
                if (slashIdx >= 0) {
                    clean = clean.substring(0, slashIdx)
                }
                val queryIdx = clean.indexOf('?')
                if (queryIdx >= 0) {
                    clean = clean.substring(0, queryIdx)
                }
                val hashIdx = clean.indexOf('#')
                if (hashIdx >= 0) {
                    clean = clean.substring(0, hashIdx)
                }
            } else {
                if (!clean.endsWith(".exe") && !clean.contains(".")) {
                    clean += ".exe"
                }
            }
            return clean.trim()
        }
    }
}
