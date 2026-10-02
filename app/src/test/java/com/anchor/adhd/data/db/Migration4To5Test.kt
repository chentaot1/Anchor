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
class Migration4To5Test {
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
            .create(
                androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name("migration_test.db")
                    .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
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
        MIGRATION_4_5.migrate(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun migration_addsDueAtMillisColumn() {
        db.execSQL(
            "INSERT INTO tasks (title, notes, inboxState, durationMinutes, difficulty, isNextAction, isCompleted, aiGenerated, createdAtMillis, sortOrder, dueAtMillis) " +
                "VALUES ('Test', '', 'TODAY', 20, 'MEDIUM', 0, 0, 0, 0, 0, 12345)"
        )
        val cursor = db.query("SELECT dueAtMillis FROM tasks WHERE title = 'Test'")
        assertTrue(cursor.moveToFirst())
        assertTrue(cursor.getLong(0) == 12345L)
        cursor.close()
    }
}
