package com.anchor.adhd.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Migration7To8Test {
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
            .create(
                androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name("migration_test_7_8.db")
                    .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(7) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                """
                                CREATE TABLE focus_sessions (
                                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                    taskId INTEGER,
                                    startedAtMillis INTEGER NOT NULL,
                                    endedAtMillis INTEGER,
                                    plannedMinutes INTEGER NOT NULL,
                                    actualMinutes INTEGER,
                                    completed INTEGER NOT NULL DEFAULT 0,
                                    locked INTEGER NOT NULL DEFAULT 1,
                                    endTag TEXT
                                )
                                """.trimIndent()
                            )
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    })
                    .build()
            )
            .writableDatabase
        MIGRATION_7_8.migrate(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun migration_addsLeakColumnsDefaultingToZero() {
        db.execSQL(
            "INSERT INTO focus_sessions (startedAtMillis, plannedMinutes, completed, locked) VALUES (1000, 20, 0, 1)"
        )
        val cursor = db.query("SELECT blockedAttempts, topBlockedPackage FROM focus_sessions")
        assertTrue(cursor.moveToFirst())
        assertEquals(0, cursor.getInt(0))
        assertTrue(cursor.isNull(1))
        cursor.close()
    }

    @Test
    fun migration_createsChatUndoOpsTable() {
        db.execSQL(
            "INSERT INTO chat_undo_ops (source, kind, payload, createdAtMillis) VALUES ('panic', 'created', '1,2', 1000)"
        )
        val cursor = db.query("SELECT source, kind FROM chat_undo_ops")
        assertTrue(cursor.moveToFirst())
        assertEquals("panic", cursor.getString(0))
        assertEquals("created", cursor.getString(1))
        cursor.close()
    }
}
