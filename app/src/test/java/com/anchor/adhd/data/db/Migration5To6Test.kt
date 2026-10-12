package com.anchor.adhd.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Migration5To6Test {
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
            .create(
                androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name("migration_test_5_6.db")
                    .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(5) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                """
                                CREATE TABLE tasks (
                                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                    title TEXT NOT NULL,
                                    notes TEXT NOT NULL DEFAULT '',
                                    inboxState TEXT NOT NULL,
                                    scheduledStartMillis INTEGER,
                                    durationMinutes INTEGER NOT NULL,
                                    difficulty TEXT NOT NULL,
                                    isNextAction INTEGER NOT NULL DEFAULT 0,
                                    isCompleted INTEGER NOT NULL DEFAULT 0,
                                    parentTaskId INTEGER,
                                    routineId INTEGER,
                                    aiGenerated INTEGER NOT NULL DEFAULT 0,
                                    estimatedMinutes INTEGER,
                                    actualMinutes INTEGER,
                                    dueAtMillis INTEGER,
                                    ifThen TEXT,
                                    createdAtMillis INTEGER NOT NULL,
                                    completedAtMillis INTEGER,
                                    sortOrder INTEGER NOT NULL DEFAULT 0
                                )
                                """.trimIndent()
                            )
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    })
                    .build()
            )
            .writableDatabase
        MIGRATION_5_6.migrate(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun migration_createsHabitTables() {
        db.execSQL(
            """
            INSERT INTO habits (name, scheduleType, scheduleDays, targetPerWeek, gracePerWeek, autoSource, autoThreshold, sortOrder, isArchived, createdAtMillis)
            VALUES ('Walk', 'DAILY', '', 7, 1, 'NONE', 0, 0, 0, 1000)
            """.trimIndent()
        )
        val cursor = db.query("SELECT name FROM habits WHERE name = 'Walk'")
        assertTrue(cursor.moveToFirst())
        cursor.close()

        db.execSQL(
            "INSERT INTO habit_completions (habitId, dayMillis, completed, autoCompleted) VALUES (1, 5000, 1, 0)"
        )
        val completionCursor = db.query("SELECT dayMillis FROM habit_completions WHERE habitId = 1")
        assertTrue(completionCursor.moveToFirst())
        assertTrue(completionCursor.getLong(0) == 5000L)
        completionCursor.close()
    }
}
