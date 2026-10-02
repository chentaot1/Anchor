package com.anchor.adhd.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS temptation_bundles (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                rewardPackageName TEXT NOT NULL,
                unlockMinutes INTEGER NOT NULL,
                enabled INTEGER NOT NULL,
                unlockUntilMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE focus_sessions ADD COLUMN actualMinutes INTEGER")
        db.execSQL("ALTER TABLE focus_sessions ADD COLUMN endTag TEXT")
        db.execSQL("ALTER TABLE companion_state ADD COLUMN stage INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE cbt_cards ADD COLUMN momentTag TEXT NOT NULL DEFAULT 'GENERAL'")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS check_ins (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                energyLevel TEXT NOT NULL,
                tags TEXT NOT NULL,
                recordedAtMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO check_ins (id, energyLevel, tags, recordedAtMillis)
            SELECT id,
                CASE
                    WHEN moodScore <= 2 THEN 'LOW'
                    WHEN moodScore >= 4 THEN 'HIGH'
                    ELSE 'OK'
                END,
                '',
                recordedAtMillis
            FROM mood_entries
            """.trimIndent()
        )
        db.execSQL("DROP TABLE mood_entries")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN dueAtMillis INTEGER")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS habits (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                scheduleType TEXT NOT NULL,
                scheduleDays TEXT NOT NULL DEFAULT '',
                targetPerWeek INTEGER NOT NULL DEFAULT 7,
                gracePerWeek INTEGER NOT NULL DEFAULT 1,
                autoSource TEXT NOT NULL DEFAULT 'NONE',
                autoThreshold INTEGER NOT NULL DEFAULT 0,
                sortOrder INTEGER NOT NULL DEFAULT 0,
                isArchived INTEGER NOT NULL DEFAULT 0,
                createdAtMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS habit_completions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                habitId INTEGER NOT NULL,
                dayMillis INTEGER NOT NULL,
                completed INTEGER NOT NULL DEFAULT 1,
                autoCompleted INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_habit_completions_habitId_dayMillis ON habit_completions (habitId, dayMillis)"
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_turns (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                fromUser INTEGER NOT NULL,
                text TEXT NOT NULL,
                taskId INTEGER,
                createdAtMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE focus_sessions ADD COLUMN blockedAttempts INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE focus_sessions ADD COLUMN topBlockedPackage TEXT DEFAULT NULL")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_undo_ops (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                source TEXT NOT NULL,
                kind TEXT NOT NULL,
                payload TEXT NOT NULL,
                createdAtMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS fun_links (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                url TEXT NOT NULL,
                emoji TEXT NOT NULL,
                sortOrder INTEGER NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL("INSERT OR IGNORE INTO fun_links (name, url, emoji, sortOrder, enabled) VALUES ('Wordle', 'https://www.nytimes.com/games/wordle', '🟩', 0, 1)")
        db.execSQL("INSERT OR IGNORE INTO fun_links (name, url, emoji, sortOrder, enabled) VALUES ('Connections', 'https://www.nytimes.com/games/connections', '🟪', 1, 1)")
        db.execSQL("INSERT OR IGNORE INTO fun_links (name, url, emoji, sortOrder, enabled) VALUES ('Chess', 'https://lichess.org/training', '♟️', 2, 1)")
        db.execSQL("INSERT OR IGNORE INTO fun_links (name, url, emoji, sortOrder, enabled) VALUES ('Contexto', 'https://contexto.me', '🧩', 3, 1)")
        db.execSQL("UPDATE fun_links SET name = 'Chess' WHERE name = 'Chess Puzzles'")
    }
}

