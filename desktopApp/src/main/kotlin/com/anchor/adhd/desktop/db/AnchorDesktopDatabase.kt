package com.anchor.adhd.desktop.db

import com.anchor.adhd.desktop.DesktopDurationLimits

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
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@kotlinx.serialization.Serializable
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
    val streakDays: Int = 0,
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
    private val backupFileWriter: (File, String) -> Unit = DesktopBackupFiles::write,
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

    private val dataAccess = DesktopDatabaseAccess()
    internal val waitingDataAccessCount: Int get() = dataAccess.waitingCount

    private fun <T> withConnection(action: (Connection) -> T): T =
        dataAccess.connection { getConnection().use(action) }

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
            withConnection { conn ->
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

                    // Capture acknowledgements are local bookkeeping, removed with their task.
                    stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS task_captures (
                            captureKey TEXT PRIMARY KEY NOT NULL,
                            taskId INTEGER NOT NULL UNIQUE REFERENCES tasks(id) ON DELETE CASCADE,
                            title TEXT NOT NULL
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

                    // Idempotent retries for recovered focus-session writes.
                    try {
                        stmt.execute("ALTER TABLE focus_sessions ADD COLUMN saveKey TEXT;")
                    } catch (_: java.sql.SQLException) {
                        // Already migrated on an earlier launch.
                    }
                    stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS focus_sessions_save_key ON focus_sessions(saveKey);")
                    stmt.execute("CREATE INDEX IF NOT EXISTS focus_sessions_end_time ON focus_sessions(endedAtMillis);")

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
                            streakDays INTEGER DEFAULT 0,
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
                        VALUES (1, 0, 0, 0, '');
                        """.trimIndent(),
                    )

                    // Correct the former Day 1 seed only for empty profiles; retain imported progress.
                    stmt.executeUpdate("""
                        UPDATE harbor_stats SET streakDays = 0
                        WHERE totalSessions = 0 AND totalMinutes = 0 AND streakDays = 1
                            AND last_focus_logical_date = ''
                            AND NOT EXISTS (SELECT 1 FROM focus_sessions)
                            AND NOT EXISTS (SELECT 1 FROM meta_kv WHERE key = 'zero_streak_seed_v1');
                    """.trimIndent())
                    stmt.executeUpdate("INSERT OR IGNORE INTO meta_kv (key, value) VALUES ('zero_streak_seed_v1', '1');")

                    // Public profiles start without saved links or protection rules.
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

                    // Courses, assignments and timetables are supplied by the user.

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
        withConnection { conn -> _tasks.value = readTasks(conn) }
    }

    private fun readTasks(conn: Connection): List<DesktopTask> = conn.createStatement().use { stmt ->
        stmt.executeQuery("SELECT * FROM tasks ORDER BY isCompleted ASC, isNextAction DESC, sortOrder ASC, id DESC;").use { rs ->
            buildList {
                while (rs.next()) add(readTask(rs))
            }
        }
    }

    /** One capture identity inserts once; retry after an interrupted acknowledgement refreshes only. */
    internal suspend fun captureThought(key: String, title: String, durationMinutes: Int = 5) = withContext(Dispatchers.IO) {
        require(java.util.UUID.fromString(key).toString() == key) { "Invalid capture identity" }
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "A thought cannot be empty" }
        require(durationMinutes in DesktopDurationLimits.MIN_MINUTES..DesktopDurationLimits.MAX_MINUTES) { "Invalid capture duration" }
        withTaskTransaction { conn -> insertCapturedTask(conn, key, cleanTitle, durationMinutes) }
        Unit
    }

    /** All items commit together. Stable per-item identities also cover alternate actions on one preview. */
    internal suspend fun saveRouterTasks(key: String, titles: List<String>) = withContext(Dispatchers.IO) {
        require(java.util.UUID.fromString(key).toString() == key) { "Invalid router save identity" }
        val cleanTitles = titles.map(String::trim).filter(String::isNotEmpty)
        if (cleanTitles.isEmpty()) return@withContext
        withTaskTransaction { conn ->
            val occurrences = mutableMapOf<String, Int>()
            cleanTitles.forEach { title ->
                val occurrence = occurrences.getOrDefault(title, 0)
                occurrences[title] = occurrence + 1
                val itemKey = java.util.UUID.nameUUIDFromBytes(
                    "$key:${title.length}:$title:$occurrence".toByteArray(Charsets.UTF_8),
                ).toString()
                insertCapturedTask(conn, itemKey, title, 15)
            }
        }
    }

    /** Airlock items, acknowledgements, and optional NOW selection commit as one batch. */
    internal suspend fun saveAirlockTasks(request: DesktopAirlockSave): DesktopTask? = withContext(Dispatchers.IO) {
        require(java.util.UUID.fromString(request.key).toString() == request.key)
        require(request.tasks.isNotEmpty())
        require(request.tasks.all { it.title.isNotBlank() && it.durationMinutes in DesktopDurationLimits.MIN_MINUTES..DesktopDurationLimits.MAX_MINUTES })
        require(request.anchorIndex == null || request.anchorIndex in request.tasks.indices)
        withTaskTransaction { conn ->
            saveCapturedTaskBatch(conn, request)
        }
    }

    internal suspend fun saveMilestoneSteps(request: DesktopMilestoneStepSave): DesktopTask? = withContext(Dispatchers.IO) {
        require(java.util.UUID.fromString(request.key).toString() == request.key)
        require(request.steps.isNotEmpty() && request.steps.all { it.isNotBlank() })
        withTaskTransaction { conn ->
            val source = readTasks(conn).singleOrNull { it.id == request.sourceId }
            check(source != null && !source.isCompleted && source.title == request.sourceTitle) {
                "The source task changed. Prepare its steps again."
            }
            val steps = if (request.startFirst) request.steps.take(1) else request.steps
            saveCapturedTaskBatch(conn, DesktopAirlockSave(request.key, steps.map { DesktopAirlockTask(it, 5) },
                if (request.startFirst) 0 else null))
        }
    }

    private fun saveCapturedTaskBatch(conn: Connection, request: DesktopAirlockSave): DesktopTask? {
        val occurrences = mutableMapOf<String, Int>()
        val ids = request.tasks.map { task ->
            val title = task.title.trim()
            val occurrence = occurrences.getOrDefault(title, 0)
            occurrences[title] = occurrence + 1
            val itemKey = java.util.UUID.nameUUIDFromBytes(
                "${request.key}:${title.length}:$title:$occurrence".toByteArray(Charsets.UTF_8),
            ).toString()
            insertCapturedTask(conn, itemKey, title, task.durationMinutes)
        }
        return request.anchorIndex?.let { index -> promoteTaskNow(conn, ids[index]) }
    }


    private fun insertCapturedTask(conn: Connection, key: String, title: String, minutes: Int): Long {
        val previous = conn.prepareStatement("SELECT taskId, title FROM task_captures WHERE captureKey = ?;").use { stmt ->
            stmt.setString(1, key)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) to rs.getString(2) else null }
        }
        if (previous != null) {
            check(previous.second == title) { "Capture identity belongs to a different thought" }
            return previous.first
        }
        conn.prepareStatement("INSERT INTO tasks (title, durationMinutes, createdAtMillis, sortOrder) VALUES (?, ?, ?, 0);").use { stmt ->
            stmt.setString(1, title)
            stmt.setInt(2, minutes)
            stmt.setLong(3, System.currentTimeMillis())
            stmt.executeUpdate()
        }
        val taskId = conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT last_insert_rowid();").use { rs -> rs.next(); rs.getLong(1) }
        }
        conn.prepareStatement("INSERT INTO task_captures (captureKey, taskId, title) VALUES (?, ?, ?);").use { stmt ->
            stmt.setString(1, key)
            stmt.setLong(2, taskId)
            stmt.setString(3, title)
            stmt.executeUpdate()
        }
        return taskId
    }

    private fun <T> withTaskTransaction(action: (Connection) -> T): T = dataAccess.replace {
        // Keep commit and publication together so another writer cannot be hidden by this snapshot.
        withConnection { conn ->
            conn.autoCommit = false
            try {
                val result = action(conn)
                // Read before commit so a snapshot error rolls back tasks and acknowledgements.
                val tasks = readTasks(conn)
                conn.commit()
                _tasks.value = tasks
                result
            } catch (error: Exception) {
                conn.rollback()
                throw error
            }
        }
    }

    private fun refreshFunLinks() {
        withConnection { conn ->
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
        withConnection { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT * FROM harbor_stats WHERE id = 1;")
                if (rs.next()) {
                    val sessions = rs.getInt("totalSessions")
                    val minutes = rs.getInt("totalMinutes")
                    val streak = rs.getInt("streakDays").coerceAtLeast(0)
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
        withConnection { conn ->
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

    suspend fun toggleTaskCompleted(id: Long): DesktopTask = withContext(Dispatchers.IO) {
        withTaskTransaction { conn ->
            val target = readTasks(conn).singleOrNull { it.id == id }
                ?: error("This task is no longer available. Check your milestones again.")
            completeTask(conn, id, !target.isCompleted)
        }
    }

    suspend fun setTaskCompleted(id: Long, completed: Boolean): DesktopTask = withContext(Dispatchers.IO) {
        withTaskTransaction { conn -> completeTask(conn, id, completed) }
    }

    private fun completeTask(conn: Connection, id: Long, completed: Boolean): DesktopTask {
        check(readTasks(conn).any { it.id == id }) { "This task is no longer available. Check your milestones again." }
        conn.prepareStatement("UPDATE tasks SET isCompleted = ? WHERE id = ?;").use { stmt ->
            stmt.setInt(1, if (completed) 1 else 0)
            stmt.setLong(2, id)
            check(stmt.executeUpdate() == 1) { "The task could not be updated. Check your milestones and try again." }
        }
        return readTasks(conn).single { it.id == id }.also {
            check(it.isCompleted == completed) { "The task could not be updated. Check your milestones and try again." }
        }
    }

    private fun readTask(rs: ResultSet) = DesktopTask(
        id = rs.getLong("id"),
        title = rs.getString("title"),
        notes = rs.getString("notes") ?: "",
        durationMinutes = rs.getInt("durationMinutes"),
        difficulty = rs.getString("difficulty") ?: "MEDIUM",
        isCompleted = rs.getInt("isCompleted") == 1,
        isNextAction = rs.getInt("isNextAction") == 1,
        createdAtMillis = rs.getLong("createdAtMillis"),
        sortOrder = rs.getInt("sortOrder"),
    )

    internal suspend fun deleteTask(id: Long): DesktopTaskDeletion = deleteTasks("id = ?", id)

    internal suspend fun deleteCompletedTasks(): DesktopTaskDeletion = deleteTasks("isCompleted = 1")

    /** Capture the exact rows being deleted in the same transaction as the deletion. */
    private suspend fun deleteTasks(condition: String, id: Long? = null): DesktopTaskDeletion = withContext(Dispatchers.IO) {
        withTaskTransaction { conn ->
            val snapshot = conn.prepareStatement("SELECT * FROM tasks WHERE $condition ORDER BY id;").use { stmt ->
                if (id != null) stmt.setLong(1, id)
                stmt.executeQuery().use { rs -> buildList { while (rs.next()) add(readTask(rs)) } }
            }
            val captures = conn.prepareStatement(
                "SELECT captureKey, taskId, title FROM task_captures WHERE taskId IN (SELECT id FROM tasks WHERE $condition) ORDER BY taskId;",
            ).use { stmt ->
                if (id != null) stmt.setLong(1, id)
                stmt.executeQuery().use { rs -> buildList {
                    while (rs.next()) add(DesktopDeletedCapture(rs.getString(1), rs.getLong(2), rs.getString(3)))
                } }
            }
            conn.prepareStatement("DELETE FROM tasks WHERE $condition;").use { stmt ->
                if (id != null) stmt.setLong(1, id)
                check(stmt.executeUpdate() == snapshot.size) { "The tasks could not be removed. Check your milestones and try again." }
            }
            DesktopTaskDeletion(snapshot, captures)
        }
    }

    /** Restore a deletion atomically; an existing ID is never overwritten. */
    internal suspend fun restoreDeletedTasks(deletion: DesktopTaskDeletion) = withContext(Dispatchers.IO) {
        val deleted = deletion.tasks
        if (deleted.isEmpty()) {
            require(deletion.captures.isEmpty())
            return@withContext
        }
        val ids = deleted.map { it.id }.toSet()
        require(ids.size == deleted.size && deletion.captures.all { it.taskId in ids })
        require(deletion.captures.map { it.key }.distinct().size == deletion.captures.size)
        require(deletion.captures.map { it.taskId }.distinct().size == deletion.captures.size)
        withTaskTransaction { conn ->
            var hasNextAction = conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT EXISTS(SELECT 1 FROM tasks WHERE isNextAction = 1);").use { rs ->
                    rs.next() && rs.getBoolean(1)
                }
            }
            conn.prepareStatement(
                """
                INSERT INTO tasks (id, title, notes, durationMinutes, difficulty, isCompleted, isNextAction, createdAtMillis, sortOrder)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
                """.trimIndent(),
            ).use { stmt ->
                for (task in deleted) {
                    val restoreNextAction = task.isNextAction && !hasNextAction
                    stmt.setLong(1, task.id)
                    stmt.setString(2, task.title)
                    stmt.setString(3, task.notes)
                    stmt.setInt(4, task.durationMinutes)
                    stmt.setString(5, task.difficulty)
                    stmt.setInt(6, if (task.isCompleted) 1 else 0)
                    stmt.setInt(7, if (restoreNextAction) 1 else 0)
                    stmt.setLong(8, task.createdAtMillis)
                    stmt.setInt(9, task.sortOrder)
                    check(stmt.executeUpdate() == 1) { "The task could not be restored. Try Undo again." }
                    if (restoreNextAction) hasNextAction = true
                }
            }
            conn.prepareStatement("INSERT INTO task_captures (captureKey, taskId, title) VALUES (?, ?, ?);").use { stmt ->
                for (capture in deletion.captures) {
                    stmt.setString(1, capture.key)
                    stmt.setLong(2, capture.taskId)
                    stmt.setString(3, capture.title)
                    check(stmt.executeUpdate() == 1) { "The saved task identity could not be restored. Try Undo again." }
                }
            }
        }
    }


    suspend fun setNextActionTask(id: Long): DesktopTask = withContext(Dispatchers.IO) {
        try {
            withTaskTransaction { conn -> promoteTaskNow(conn, id) }
        } catch (failure: java.sql.SQLException) {
            throw IllegalStateException("Try selecting this task again.", failure)
        }
    }

    /** Validate the exact target and return its fresh snapshot inside the caller's transaction. */
    private fun promoteTaskNow(conn: Connection, id: Long): DesktopTask {
        val tasks = readTasks(conn)
        val target = tasks.singleOrNull { it.id == id }
        check(target != null && !target.isCompleted) { "This task is no longer available. Choose an incomplete task again." }
        val minSort = tasks.minOfOrNull { it.sortOrder } ?: 0
        val expectedRanks = tasks.associate { it.id to it.sortOrder }.toMutableMap()
        val nextSort = if (minSort == Int.MIN_VALUE) {
            // Imported ranks can use the whole Int range. Preserve the existing base order,
            // including the newest-ID tie break, while making room ahead of every other task.
            val others = tasks.filter { it.id != id }
                .sortedWith(compareBy<DesktopTask> { it.sortOrder }.thenByDescending { it.id })
            conn.prepareStatement("UPDATE tasks SET sortOrder = ? WHERE id = ?;").use { stmt ->
                others.forEachIndexed { rank, task ->
                    stmt.setInt(1, rank)
                    stmt.setLong(2, task.id)
                    check(stmt.executeUpdate() == 1) { "The task order could not be updated. Try selecting this task again." }
                    expectedRanks[task.id] = rank
                }
            }
            -1
        } else {
            minSort - 1
        }
        expectedRanks[id] = nextSort
        conn.createStatement().use { it.executeUpdate("UPDATE tasks SET isNextAction = 0;") }
        conn.prepareStatement("UPDATE tasks SET isNextAction = 1, sortOrder = ? WHERE id = ?;").use { stmt ->
            stmt.setInt(1, nextSort)
            stmt.setLong(2, id)
            check(stmt.executeUpdate() == 1) { "The task could not be selected. Try again." }
        }
        val fresh = readTasks(conn)
        check(fresh.map { it.id }.toSet() == expectedRanks.keys &&
            fresh.all { expectedRanks[it.id] == it.sortOrder } && fresh.count { it.isNextAction } == 1) {
            "The task order could not be updated. Try selecting this task again."
        }
        return fresh.single { it.id == id }.also { check(it.isNextAction && !it.isCompleted) }
    }

    suspend fun updateTaskDetails(
        id: Long,
        title: String,
        durationMinutes: Int,
    ) = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext
        withConnection { conn ->
            conn.prepareStatement("UPDATE tasks SET title = ?, durationMinutes = ? WHERE id = ?;").use { stmt ->
                stmt.setString(1, title.trim())
                stmt.setInt(2, durationMinutes.coerceIn(DesktopDurationLimits.MIN_MINUTES, DesktopDurationLimits.MAX_MINUTES))
                stmt.setLong(3, id)
                stmt.executeUpdate()
            }
        }
        refreshTasks()
    }

    /** Credits saved work to its end day, matching streaks and recovered completion timestamps. */
    suspend fun getTodayFocusMinutes(now: java.time.ZonedDateTime = java.time.ZonedDateTime.now()): Int =
        withContext(Dispatchers.IO) {
            val startOfLogicalDayMillis =
                getLogicalDate(now.toLocalDateTime())
                    .atTime(DAILY_RESET_HOUR, 0)
                    .atZone(now.zone)
                    .toInstant()
                    .toEpochMilli()

            var minutes = 0
            try {
                withConnection { conn ->
                    conn.prepareStatement("SELECT SUM(actualSeconds) FROM focus_sessions WHERE endedAtMillis >= ? AND endedAtMillis <= ?;").use { stmt ->
                        stmt.setLong(1, startOfLogicalDayMillis)
                        stmt.setLong(2, now.toInstant().toEpochMilli())
                        val rs = stmt.executeQuery()
                        if (rs.next()) {
                            minutes = (rs.getLong(1) / 60).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
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
        withTaskTransaction { conn ->
            val existing = readTasks(conn).firstOrNull { !it.isCompleted && it.title.equals(cleanTitle, ignoreCase = true) }
            val targetId = if (existing == null) {
                conn.prepareStatement("INSERT INTO tasks (title, durationMinutes, createdAtMillis, sortOrder) VALUES (?, ?, ?, 0);").use { stmt ->
                    stmt.setString(1, cleanTitle)
                    stmt.setInt(2, durationMinutes.coerceIn(DesktopDurationLimits.MIN_MINUTES, DesktopDurationLimits.MAX_MINUTES))
                    stmt.setLong(3, System.currentTimeMillis())
                    stmt.executeUpdate()
                }
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT last_insert_rowid();").use { rs -> rs.next(); rs.getLong(1) }
                }
            } else {
                conn.prepareStatement("UPDATE tasks SET durationMinutes = ? WHERE id = ?;").use { stmt ->
                    stmt.setInt(1, durationMinutes.coerceIn(DesktopDurationLimits.MIN_MINUTES, DesktopDurationLimits.MAX_MINUTES))
                    stmt.setLong(2, existing.id)
                    stmt.executeUpdate()
                }
                existing.id
            }
            promoteTaskNow(conn, targetId)
        }
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
        saveKey: String? = null,
    ) = withContext(Dispatchers.IO) {
        val nowMillis =
            now
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        val start = nowMillis - (actualSeconds * 1000L)
        val currentLogicalDate = getLogicalDateString(now)

        executeTransaction { conn ->
            conn
                .prepareStatement(
                    """
                    INSERT INTO focus_sessions (taskTitle, durationSeconds, actualSeconds, completed, startedAtMillis, endedAtMillis, saveKey)
                    VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT(saveKey) DO NOTHING;
                    """.trimIndent(),
                ).use { stmt ->
                    stmt.setString(1, taskTitle)
                    stmt.setInt(2, durationSeconds)
                    stmt.setInt(3, actualSeconds)
                    stmt.setInt(4, if (completed) 1 else 0)
                    stmt.setLong(5, start)
                    stmt.setLong(6, nowMillis)
                    stmt.setString(7, saveKey)
                    if (stmt.executeUpdate() == 0) return@executeTransaction
                }

            // Carry sub-minute work across sessions without changing imported aggregate progress.
            val totalRecordedSeconds = conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT COALESCE(SUM(actualSeconds), 0) FROM focus_sessions;").use { rows ->
                    rows.next()
                    rows.getLong(1)
                }
            }
            val minutesToAdd = (totalRecordedSeconds / 60 - (totalRecordedSeconds - actualSeconds) / 60).toInt()

            var currentStreak = 0
            var lastFocusLogicalDate = ""
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT streakDays, last_focus_logical_date FROM harbor_stats WHERE id = 1;")
                if (rs.next()) {
                    currentStreak = rs.getInt("streakDays").coerceAtLeast(0)
                    lastFocusLogicalDate = runCatching { rs.getString("last_focus_logical_date") }.getOrNull()?.trim() ?: ""
                }
            }

            val isOlderCompletion = runCatching {
                LocalDate.parse(lastFocusLogicalDate).isAfter(LocalDate.parse(currentLogicalDate))
            }.getOrDefault(false)
            val newStreak =
                if (!completed) {
                    currentStreak
                } else {
                    when {
                        lastFocusLogicalDate.isBlank() -> 1
                        isOlderCompletion -> currentStreak.coerceAtLeast(1)
                        lastFocusLogicalDate == currentLogicalDate -> currentStreak.coerceAtLeast(1)
                        runCatching {
                            LocalDate.parse(lastFocusLogicalDate).plusDays(1) == LocalDate.parse(currentLogicalDate)
                        }.getOrDefault(false) -> currentStreak.coerceAtLeast(1) + 1
                        else -> 1
                    }
                }
            val updatedLogicalDate = if (completed && !isOlderCompletion) currentLogicalDate else lastFocusLogicalDate

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
        withConnection { conn ->
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
        withConnection { conn ->
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
        withConnection { conn ->
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
            withConnection { conn ->
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
            withConnection { conn ->
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
            withConnection { conn ->
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
        withConnection { conn ->
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
            withConnection { conn ->
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
        withConnection { conn ->
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
        withConnection { conn ->
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
        withConnection { conn ->
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
        withConnection { conn ->
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
        withConnection { conn ->
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
        withConnection { conn ->
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
        withConnection { conn ->
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
            withConnection { conn ->
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
            withConnection { conn ->
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
            withConnection { conn ->
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

    fun hasFocusSave(saveKey: String): Boolean = withConnection { connection ->
        connection.prepareStatement("SELECT 1 FROM focus_sessions WHERE saveKey = ? LIMIT 1").use { statement ->
            statement.setString(1, saveKey)
            statement.executeQuery().use { it.next() }
        }
    }

    private fun createPreResetAutoBackup(reason: String): File? {
        return try {
            val dir = appDataDirectory
            if (!dir.exists()) dir.mkdirs()
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"))
            val backupFile = File(dir, "anchor_auto_backup_${reason}_${timestamp}_${java.util.UUID.randomUUID()}.json")
            exportDataToFile(backupFile)
            lastAutoBackupFile = backupFile
            backupFile
        } catch (_: Exception) {
            null
        }
    }

    private fun requireRecoveryBackup(reason: String): File = createPreResetAutoBackup(reason)
        ?: error("Could not save a recovery backup. Your existing data has not been replaced.")

    private fun executeTransaction(action: (Connection) -> Unit) {
        withConnection { conn ->
            conn.autoCommit = false
            try {
                action(conn)
                conn.commit()
            } catch (error: Exception) {
                conn.rollback()
                throw error
            }
        }
    }

    suspend fun clearAllTasks() =
        withContext(Dispatchers.IO) {
            dataAccess.replace {
                requireRecoveryBackup("clear_tasks")
                executeTransaction { conn ->
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
        }

    suspend fun resetFocusHistoryAndStreak() =
        withContext(Dispatchers.IO) {
            dataAccess.replace {
                requireRecoveryBackup("focus_history")
                executeTransaction { conn ->
                    conn.createStatement().use { stmt ->
                        stmt.execute("DELETE FROM focus_sessions;")
                        stmt.execute(
                            """
                            UPDATE harbor_stats
                            SET totalSessions = 0,
                                totalMinutes = 0,
                                streakDays = 0,
                                last_focus_logical_date = ''
                            WHERE id = 1;
                            """.trimIndent(),
                        )
                    }
                }
                refreshHarborState()
            }
        }

    suspend fun refreshCourses() =
        withContext(Dispatchers.IO) {
            refreshCoursesSync()
        }

    private fun refreshCoursesSync() {
        val list = mutableListOf<DesktopCourse>()
        withConnection { conn ->
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
        withConnection { conn ->
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
            withConnection { conn ->
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
            withConnection { conn ->
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
            withConnection { conn ->
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
            withConnection { conn ->
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
            withConnection { conn ->
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
            dataAccess.replace {
                requireRecoveryBackup("factory_reset")
                executeTransaction { conn ->
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
                                streakDays = 0,
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
        }

    fun exportDataJson(): String = withConnection { DesktopBackupSnapshot.read(it) }

    fun exportDataToFile(file: File) {
        backupFileWriter(file, exportDataJson())
    }

    private fun parseBackup(jsonString: String): JsonObject {
        val parsed = Json.parseToJsonElement(jsonString)
        require(parsed is JsonObject) { "An Anchor backup must be a JSON object." }
        DesktopBackupValidator.validate(parsed)
        return normalizeAndroidBackup(parsed)
    }

    fun previewBackup(jsonString: String): DesktopBackupSummary = DesktopBackupValidator.summarize(parseBackup(jsonString))

    /** Keep a recovery file before replacing any data; a failed backup prevents the restore. */
    fun restoreBackup(jsonString: String): Pair<Int, File> = dataAccess.replace {
        parseBackup(jsonString)
        val recoveryFile = requireRecoveryBackup("restore")
        try {
            importDataJson(jsonString, replaceExisting = true) to recoveryFile
        } catch (error: Exception) {
            throw IllegalStateException("Restore could not finish. Recovery backup: ${recoveryFile.absolutePath}", error)
        }
    }

    fun importDataJson(
        jsonString: String,
        replaceExisting: Boolean = false,
    ): Int = dataAccess.replace {
        val root = parseBackup(jsonString)
        var importedCount = 0

        withConnection { conn ->
            conn.autoCommit = false
            try {
                conn.createStatement().use { stmt ->
                    if (replaceExisting) {
                        val replacementSections = mapOf(
                            "tasks" to "tasks", "courses" to "courses",
                            "syllabus_items" to "syllabus_items", "syllabusItems" to "syllabus_items",
                            "class_schedules" to "class_schedules", "classSchedules" to "class_schedules",
                            "block_rules" to "block_rules", "blockRules" to "block_rules",
                            "focus_sessions" to "focus_sessions", "focusSessions" to "focus_sessions",
                            "fun_links" to "fun_links", "funLinks" to "fun_links",
                        )
                        replacementSections.filterKeys { it in root }.values.toSet().forEach { table ->
                            stmt.execute("DELETE FROM $table;")
                        }
                        if ("harbor_stats" in root || "harborStats" in root) {
                            stmt.execute("UPDATE harbor_stats SET totalSessions = 0, totalMinutes = 0, streakDays = 0, last_focus_logical_date = '' WHERE id = 1;")
                        }
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
                        INSERT INTO focus_sessions (taskTitle, durationSeconds, actualSeconds, completed, startedAtMillis, endedAtMillis, saveKey)
                        VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT(saveKey) DO NOTHING;
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
                            stmt.setString(7, obj["saveKey"]?.jsonPrimitive?.contentOrNull)
                            importedCount += stmt.executeUpdate()
                        }
                    }
                }

                // 7. Harbor stats
                val harborObj = (root["harbor_stats"] ?: root["harborStats"])?.jsonObject
                if (harborObj != null) {
                    val totalSessions = harborObj["totalSessions"]?.jsonPrimitive?.intOrNull ?: 0
                    val totalMinutes = harborObj["totalMinutes"]?.jsonPrimitive?.intOrNull ?: 0
                    val streakDays = (harborObj["streakDays"]?.jsonPrimitive?.intOrNull ?: 0).coerceAtLeast(0)
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
                } else if (sessionsArray != null) {
                    val completedDays = mutableSetOf<LocalDate>()
                    conn.createStatement().use { stmt ->
                        val rows = stmt.executeQuery("SELECT endedAtMillis FROM focus_sessions WHERE completed = 1;")
                        while (rows.next()) {
                            val ended = java.time.Instant.ofEpochMilli(rows.getLong(1)).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                            completedDays.add(getLogicalDate(ended))
                        }
                    }
                    val (streak, lastDate) = DesktopStreak.fromCompletedDays(completedDays)
                    conn.createStatement().use { stmt ->
                        stmt.executeUpdate(
                            """
                            UPDATE harbor_stats SET
                                totalSessions = (SELECT COUNT(*) FROM focus_sessions),
                                totalMinutes = (SELECT COALESCE(SUM(actualSeconds), 0) / 60 FROM focus_sessions)
                            WHERE id = 1;
                            """.trimIndent(),
                        )
                    }
                    conn.prepareStatement("UPDATE harbor_stats SET streakDays = ?, last_focus_logical_date = ? WHERE id = 1;").use { stmt ->
                        stmt.setInt(1, streak)
                        stmt.setString(2, lastDate)
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
        importedCount
    }

    private fun normalizeAndroidBackup(root: JsonObject): JsonObject = buildJsonObject {
        root.forEach { (key, value) -> put(key, value) }
        val tasks = (root["tasks"] as? JsonArray).orEmpty().map { it.jsonObject }
        if (root["tasks"] is JsonArray) put("tasks", buildJsonArray {
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
                    put("durationSeconds", session["durationSeconds"] ?: JsonPrimitive((planned.toLong().coerceIn(1, 1440) * 60).toInt()))
                    put("actualSeconds", session["actualSeconds"] ?: JsonPrimitive((actual.toLong().coerceIn(0, 1440) * 60).toInt()))
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
                put("streakDays", companion["streakDays"] ?: JsonPrimitive(0))
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
