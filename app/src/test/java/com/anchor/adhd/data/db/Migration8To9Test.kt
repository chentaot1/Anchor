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
class Migration8To9Test {
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
            .create(
                androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name("migration_test_8_9.db")
                    .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(8) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL("CREATE TABLE sample_v8 (id INTEGER PRIMARY KEY NOT NULL)")
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    })
                    .build()
            )
            .writableDatabase
        MIGRATION_8_9.migrate(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun migration_createsFunLinksTableAndSeedsDefaults() {
        val cursor = db.query("SELECT id, name, url, emoji, sortOrder, enabled FROM fun_links ORDER BY sortOrder ASC")
        assertTrue(cursor.moveToFirst())
        assertEquals(4, cursor.count)

        // Wordle
        assertEquals("Wordle", cursor.getString(1))
        assertEquals("https://www.nytimes.com/games/wordle", cursor.getString(2))
        assertEquals("🟩", cursor.getString(3))
        assertEquals(0, cursor.getInt(4))
        assertEquals(1, cursor.getInt(5))

        // Connections
        assertTrue(cursor.moveToNext())
        assertEquals("Connections", cursor.getString(1))
        assertEquals("https://www.nytimes.com/games/connections", cursor.getString(2))
        assertEquals("🟪", cursor.getString(3))
        assertEquals(1, cursor.getInt(4))
        assertEquals(1, cursor.getInt(5))

        // Chess
        assertTrue(cursor.moveToNext())
        assertEquals("Chess", cursor.getString(1))
        assertEquals("https://lichess.org/training", cursor.getString(2))
        assertEquals("♟️", cursor.getString(3))
        assertEquals(2, cursor.getInt(4))
        assertEquals(1, cursor.getInt(5))

        // Contexto
        assertTrue(cursor.moveToNext())
        assertEquals("Contexto", cursor.getString(1))
        assertEquals("https://contexto.me", cursor.getString(2))
        assertEquals("🧩", cursor.getString(3))
        assertEquals(3, cursor.getInt(4))
        assertEquals(1, cursor.getInt(5))

        cursor.close()
    }

    @Test
    fun migration_supportsCustomFunLinkInsertion() {
        db.execSQL(
            "INSERT INTO fun_links (name, url, emoji, sortOrder, enabled) VALUES ('Custom Game', 'https://example.com/game', '🎮', 4, 1)"
        )
        val cursor = db.query("SELECT name, emoji, enabled FROM fun_links WHERE name = 'Custom Game'")
        assertTrue(cursor.moveToFirst())
        assertEquals("Custom Game", cursor.getString(0))
        assertEquals("🎮", cursor.getString(1))
        assertEquals(1, cursor.getInt(2))
        cursor.close()
    }
}
