package com.anchor.adhd.domain

import com.anchor.adhd.ai.AiBreakdownResult
import com.anchor.adhd.ai.AiGrammar
import com.anchor.adhd.data.model.BreakdownGranularity
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

    @Test
    fun mildGranularity_enforcesTwoToThreeSteps() {
        val steps = (1..6).map { "Write paragraph $it" }
        val clamped = BreakdownClamp.clamp(
            steps = steps,
            blockedPackages = emptyList(),
            granularity = BreakdownGranularity.MILD,
            taskTitle = "Write history essay"
        )
        assertTrue(clamped.size in 2..3)
        assertGrammarArrayBounds(BreakdownGranularity.MILD, 2..3)
    }

    @Test
    fun normalGranularity_enforcesThreeToFiveSteps() {
        val clamped = BreakdownClamp.clamp(
            steps = listOf("Open the doc"),
            blockedPackages = emptyList(),
            granularity = BreakdownGranularity.NORMAL,
            taskTitle = "Write biology paper"
        )
        assertTrue(clamped.size in 3..5)
        assertGrammarArrayBounds(BreakdownGranularity.NORMAL, 3..5)
    }

    @Test
    fun spicyGranularity_enforcesFiveToSevenSteps() {
        val clamped = BreakdownClamp.clamp(
            steps = listOf("Open IDE"),
            blockedPackages = emptyList(),
            granularity = BreakdownGranularity.SPICY,
            taskTitle = "Refactor login feature"
        )
        assertTrue(clamped.size in 5..7)
        assertGrammarArrayBounds(BreakdownGranularity.SPICY, 5..7)
    }

    private fun assertGrammarArrayBounds(granularity: BreakdownGranularity, expected: IntRange) {
        val grammar = AiGrammar.breakdownGrammarFor(granularity)
        for ((name, item) in listOf("steps-array" to "string", "mins-array" to "number")) {
            val rule = grammar.lineSequence().single { it.startsWith("$name ::=") }
            val totalItems = Regex("\\b$item\\b").findAll(rule).count()
            val optionalItems = Regex("\\([^)]*\\b$item\\b[^)]*\\)\\?").findAll(rule).count()
            assertEquals("$name minimum", expected.first, totalItems - optionalItems)
            assertEquals("$name maximum", expected.last, totalItems)
        }
    }

    @Test
    fun placeholderStrings_rejectedAndReplacedWithDraftSteps() {
        val clamped = BreakdownClamp.clamp(
            steps = listOf("step 1", "Step 2", "<first action>", "action"),
            blockedPackages = emptyList(),
            granularity = BreakdownGranularity.NORMAL,
            taskTitle = "Write research essay"
        )
        assertTrue(clamped.size in 3..5)
        assertFalse(clamped.any { it.equals("step 1", ignoreCase = true) })
        assertFalse(clamped.any { it.startsWith("<") })
        assertFalse(clamped.any { it.equals("action", ignoreCase = true) })
    }

    @Test
    fun clampResult_ensuresFirstMinuteEstimateAtMostTwoAndMatchesStepCount() {
        val raw = AiBreakdownResult(
            steps = listOf("step 1", "Draft outline", "Write body paragraph"),
            minutes_estimate = listOf(15),
            next_action = "step 1",
            if_then = null
        )
        val clamped = BreakdownClamp.clampResult(
            result = raw,
            blockedPackages = emptyList(),
            granularity = BreakdownGranularity.NORMAL,
            taskTitle = "Write essay"
        )
        assertTrue(clamped.steps.size in 3..5)
        assertEquals(clamped.steps.size, clamped.minutes_estimate.size)
        assertTrue(clamped.minutes_estimate.first() in 1..2)
        assertEquals(clamped.steps.first(), clamped.next_action)
    }
}
