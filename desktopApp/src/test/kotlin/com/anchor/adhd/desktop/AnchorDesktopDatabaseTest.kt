package com.anchor.adhd.desktop

import com.anchor.adhd.desktop.db.AnchorDesktopDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class AnchorDesktopDatabaseTest {
    private lateinit var tempFile: File
    private lateinit var db: AnchorDesktopDatabase

    @Before
    fun setup() {
        tempFile = File.createTempFile("anchor_test_", ".db")
        db = AnchorDesktopDatabase(CoroutineScope(Dispatchers.Unconfined), customDbFile = tempFile)
    }

    @After
    fun tearDown() {
        tempFile.delete()
    }

    @Test
    fun testInitializationAndSeedData() =
        runBlocking {
            db.initialize()

            val funLinks = db.funLinks.value
            assertEquals(4, funLinks.size)
            assertTrue(funLinks.any { it.name == "Wordle" })
            assertTrue(funLinks.any { it.name == "Connections" })
            assertTrue(funLinks.any { it.name == "Chess Puzzles" })
            assertTrue(funLinks.any { it.name == "Contexto" })

            val blockRules = db.getBlockRules()
            assertTrue(blockRules.contains("discord.exe"))
            assertTrue(blockRules.contains("spotify.exe"))
            assertFalse(blockRules.contains("steam.exe"))
            assertTrue(blockRules.contains("youtube"))
        }

    @Test
    fun testTaskCrud() =
        runBlocking {
            db.initialize()

            db.insertTask("Complete Windows 11 port", 30)
            var tasks = db.tasks.value
            val task = tasks.find { it.title == "Complete Windows 11 port" }
            assertTrue(task != null)
            assertFalse(task!!.isCompleted)
            assertEquals(30, task.durationMinutes)

            // Toggle completed
            db.toggleTaskCompleted(task.id)
            tasks = db.tasks.value
            val updated = tasks.find { it.id == task.id }
            assertTrue(updated!!.isCompleted)

            // Delete task
            db.deleteTask(task.id)
            tasks = db.tasks.value
            assertFalse(tasks.any { it.id == task.id })
        }

    @Test
    fun testFocusSessionAndHarborProgression() =
        runBlocking {
            db.initialize()

            var harbor = db.harborState.value
            assertFalse(harbor.unlockedBoat)

            // Record 1st session -> unlocks boat
            db.recordFocusSession("Deep Coding", 1500, 1500, true)
            harbor = db.harborState.value
            assertEquals(1, harbor.totalSessions)
            assertTrue(harbor.unlockedBoat)
            assertFalse(harbor.unlockedWake)

            // Record 4 more sessions -> total 5 -> unlocks wake
            repeat(4) {
                db.recordFocusSession("Deep Coding", 1500, 1500, true)
            }
            harbor = db.harborState.value
            assertEquals(5, harbor.totalSessions)
            assertTrue(harbor.unlockedWake)
        }
}
