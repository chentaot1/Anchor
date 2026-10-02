package com.anchor.adhd.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AirlockHeuristicTest {

    @Test
    fun parseEmptyOrBlankReturnsEmpty() {
        val res = AirlockHeuristic.parse("   ")
        assertEquals("", res.primaryTask)
        assertTrue(res.secondaryTasks.isEmpty())
        assertEquals("", res.defaultSomaticStarter)
    }

    @Test
    fun parseSingleTaskWithActionVerbGeneratesImperativeStarter() {
        val res = AirlockHeuristic.parse("write chemistry conclusion")
        assertEquals("write chemistry conclusion", res.primaryTask)
        assertTrue(res.secondaryTasks.isEmpty())
        assertEquals("Just write chemistry conclusion", res.defaultSomaticStarter)
    }

    @Test
    fun parseSingleTaskWithoutActionVerbGeneratesMaterialStarter() {
        val res = AirlockHeuristic.parse("Biology Chapter 4")
        assertEquals("Biology Chapter 4", res.primaryTask)
        assertTrue(res.secondaryTasks.isEmpty())
        assertEquals("Just open materials for \"Biology Chapter 4\"", res.defaultSomaticStarter)
    }

    @Test
    fun parseMessyConjunctionDelimitedDumpSplitsCorrectly() {
        val dump = "finish chem lab, email professor about extension and then buy groceries\npack backpack"
        val res = AirlockHeuristic.parse(dump)

        assertEquals("finish chem lab", res.primaryTask)
        assertEquals(listOf("email professor about extension", "buy groceries", "pack backpack"), res.secondaryTasks)
        assertEquals("Just finish chem lab", res.defaultSomaticStarter)
    }

    @Test
    fun parseNumberedListCleansNumbering() {
        val dump = "1. Read paper 2) Email advisor; 3. Submit draft"
        val res = AirlockHeuristic.parse(dump)

        assertEquals("Read paper", res.primaryTask)
        assertEquals(listOf("Email advisor", "Submit draft"), res.secondaryTasks)
        assertEquals("Just read paper", res.defaultSomaticStarter)
    }

    @Test
    fun extractCleanSomaticStarter_sanitizesRawJsonCorrectly() {
        val rawJson = """
            {"steps":["Open the materials","Do the smallest first step","Work for one focus block"],"next_action":"Open the materials","minutes_estimate":[5,15,25],"if_then":"If I sit at my desk, then I open the materials."}
        """.trimIndent()
        val fallback = "Just take out notes"
        val result = AirlockHeuristic.extractCleanSomaticStarter(rawJson, fallback)
        assertEquals("Just Open the materials", result)
    }

    @Test
    fun extractCleanSomaticStarter_extractsFirstStepWhenNextActionMissing() {
        val rawJson = """
            {"steps":["Open page 42","Read definition"]}
        """.trimIndent()
        val fallback = "Just begin"
        val result = AirlockHeuristic.extractCleanSomaticStarter(rawJson, fallback)
        assertEquals("Just Open page 42", result)
    }

    @Test
    fun extractCleanSomaticStarter_stripsPrefixesAndQuotes() {
        val raw = "First physical step: \"Open textbook to page 10\"."
        val fallback = "Just start"
        val result = AirlockHeuristic.extractCleanSomaticStarter(raw, fallback)
        assertEquals("Just Open textbook to page 10", result)
    }

    @Test
    fun extractCleanSomaticStarter_fallsBackOnMalformedJsonOrGarbage() {
        val malformed = "{\"broken_json\": [foo"
        val fallback = "Just open materials"
        val result = AirlockHeuristic.extractCleanSomaticStarter(malformed, fallback)
        assertEquals(fallback, result)
    }
}
