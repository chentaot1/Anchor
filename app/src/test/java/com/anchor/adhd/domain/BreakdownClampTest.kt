package com.anchor.adhd.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakdownClampTest {
    @Test
    fun startEssay_rewrittenToOpen() {
        val out = BreakdownClamp.clamp(listOf("Start essay", "Write a sentence"), emptyList())
        assertTrue(out.first().startsWith("Open"))
        assertFalse(out.any { it.startsWith("Start") })
    }

    @Test
    fun watchOnYoutube_droppedIfBlocked() {
        val out = BreakdownClamp.clamp(
            listOf("Open the doc", "watch on YouTube", "Write one paragraph"),
            listOf("com.google.android.youtube")
        )
        assertEquals(2, out.size)
        assertFalse(out.any { it.contains("YouTube", ignoreCase = true) })
    }

    @Test
    fun clampsToSevenSteps() {
        val steps = (1..10).map { "Open step $it" }
        assertEquals(7, BreakdownClamp.clamp(steps, emptyList()).size)
    }

    @Test
    fun padsToAtLeastTwo() {
        val out = BreakdownClamp.clamp(listOf("Open the fridge"), emptyList())
        assertEquals(2, out.size)
    }

    @Test
    fun reviewNotes_notPrefixedWithOpen() {
        val out = BreakdownClamp.clamp(listOf("Review notes", "Write a sentence"), emptyList())
        assertEquals("Review notes", out.first())
        assertEquals("Write a sentence", out[1])
    }

    @Test
    fun instagramPackage_doesNotDropAndroidSettings() {
        val out = BreakdownClamp.clamp(
            listOf("Open the Android settings", "Write a sentence"),
            listOf("com.instagram.android")
        )
        assertTrue(out.any { it.contains("Android", ignoreCase = true) })
    }

    @Test
    fun pythonStep_notDroppedForYoutubeBlock() {
        val out = BreakdownClamp.clamp(
            listOf("Open the doc", "Run the python script"),
            listOf("com.google.android.youtube")
        )
        assertTrue(out.any { it.contains("python", ignoreCase = true) })
    }
}
