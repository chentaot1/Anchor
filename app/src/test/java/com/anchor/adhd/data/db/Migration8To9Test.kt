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
    fun migration_createsEmptyFunLinksTableAndPreservesExistingLinks() {
        db.query("SELECT id, name, url, emoji, sortOrder, enabled FROM fun_links").use {
            assertEquals(0, it.count)
        }
        db.execSQL("INSERT INTO fun_links (name, url, emoji, sortOrder, enabled) VALUES ('Custom', 'https://example.com', '', 0, 1)")
        MIGRATION_8_9.migrate(db)
        db.query("SELECT name, url FROM fun_links").use {
            assertEquals(1, it.count)
            assertTrue(it.moveToFirst())
            assertEquals("Custom", it.getString(0))
            assertEquals("https://example.com", it.getString(1))
        }
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
