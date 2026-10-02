package com.anchor.adhd.desktop

import com.anchor.adhd.desktop.ai.DesktopAiEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopAiEngineTest {
    @Test
    fun courseworkFallback_startsWithAssignmentInsteadOfRepeatingWholeTask() {
        val steps = DesktopAiEngine.generateMicroSteps("complete Chin 103 written assignments")
        assertTrue(steps.first().contains("Chin 103"))
        assertTrue(steps.any { it.contains("exercise") })
        assertFalse(steps.any { it.contains("dishes") || it.contains("failing test") })
        assertFalse(steps.first().startsWith("Just complete"))
    }

    @Test
    fun breakdownParser_rejectsPromptPlaceholdersAndIncompleteJson() {
        val granularity = DesktopAiEngine.DesktopBreakdownGranularity.NORMAL
        assertTrue(DesktopAiEngine.parseBreakdownSteps(
            """{"steps":["Step 1","Step 2","Step 3"],"next_action":"Step 1","minutes_estimate":[2,10,15]}""",
            granularity,
        ).isEmpty())
        assertTrue(DesktopAiEngine.parseBreakdownSteps(
            """{"steps":["Open the worksheet","Read the instructions","Answer one question"],"next_action":"Open the worksheet"""",
            granularity,
        ).isEmpty())
    }

    @Test
    fun breakdownParser_checksStarterAndDurationConsistency() {
        val valid = """{"steps":["Open the worksheet","Read the instructions","Answer one question"],"next_action":"Open the worksheet","minutes_estimate":[2,3,5]}"""
        val granularity = DesktopAiEngine.DesktopBreakdownGranularity.NORMAL
        assertEquals(3, DesktopAiEngine.parseBreakdownSteps(valid, granularity).size)
        assertTrue(DesktopAiEngine.parseBreakdownSteps(valid, granularity, "Open unrelated materials").isEmpty())
        assertTrue(DesktopAiEngine.parseBreakdownSteps(valid.replace("\"next_action\":\"Open the worksheet\"", "\"next_action\":\"Step 1\""), granularity).isEmpty())
        assertTrue(DesktopAiEngine.parseBreakdownSteps(valid.replace("[2,3,5]", "[2,3]"), granularity).isEmpty())
        assertTrue(DesktopAiEngine.parseBreakdownSteps(valid.replace("[2,3,5]", "[20,3,5]"), granularity).isEmpty())
        assertEquals(3, DesktopAiEngine.parseBreakdownSteps(
            """{"steps":["Open the worksheet","Read the instructions","Answer one question"]}""",
            granularity,
        ).size)
    }

    @Test
    fun breakdownParser_acceptsSevenStepsForDeepStructure() {
        val result = """{"steps":["Open the worksheet","Read the directions","Find the first unanswered question","Draft its answer","Check spelling","Correct one mistake","Save the answer"],"next_action":"Open the worksheet","minutes_estimate":[2,3,2,5,3,2,1]}"""
        assertEquals(7, DesktopAiEngine.parseBreakdownSteps(result, DesktopAiEngine.DesktopBreakdownGranularity.SPICY).size)
        assertTrue(DesktopAiEngine.parseBreakdownSteps(result, DesktopAiEngine.DesktopBreakdownGranularity.NORMAL).isEmpty())
    }

    @Test
    fun generatesWritingMicroSteps() {
        val steps = DesktopAiEngine.generateMicroSteps("Write project essay introduction")
        assertTrue(steps.isNotEmpty())
        assertTrue(steps.any { it.contains("blank document", ignoreCase = true) })
    }

    @Test
    fun generatesCodingMicroSteps() {
        val steps = DesktopAiEngine.generateMicroSteps("Fix Kotlin null pointer bug in service")
        assertTrue(steps.isNotEmpty())
        assertTrue(steps.any { it.contains("reproduce", ignoreCase = true) || it.contains("test", ignoreCase = true) })
    }

    @Test
    fun generatesCleaningMicroSteps() {
        val steps = DesktopAiEngine.generateMicroSteps("Clean the messy kitchen dishes")
        assertTrue(steps.isNotEmpty())
        assertTrue(steps.any { it.contains("wash", ignoreCase = true) })
        assertTrue(steps.any { it.contains("rinse", ignoreCase = true) })
        assertFalse(steps.any { it.contains("trash", ignoreCase = true) })
    }

    @Test
    fun parseBrainDump_splitsMultilineNotes() {
        val dump =
            """
            - Reply to professor about deadline
            - Finish problem set 3
            - Take out recycling
            """.trimIndent()

        val parsed = DesktopAiEngine.parseBrainDump(dump)
        assertEquals(3, parsed.size)
        assertEquals("Reply to professor about deadline", parsed[0])
        assertEquals("Finish problem set 3", parsed[1])
        assertEquals("Take out recycling", parsed[2])
    }

    @Test
    fun extractJson_stripsThinkingAndExtractsBraces() {
        val raw =
            """
            <think>
            User wants a task breakdown. Let's make sure step 1 is physical.
            </think>
            Here is the JSON:
            {"steps":["Step 1","Step 2"],"next_action":"Step 1","minutes_estimate":[5,10]}
            <|im_end|>
            """.trimIndent()

        val json = DesktopAiEngine.extractJson(raw)
        assertNotNull(json)
        assertTrue(json!!.startsWith("{"))
        assertTrue(json.endsWith("}"))
        assertFalse(json.contains("<think>"))
        assertTrue(json.contains("\"steps\""))
    }

    @Test
    fun cleanOutput_stripsSpecialTokens() {
        val raw = "<s>Hello world! Take a breath.<|im_end|><|endoftext|></s>"
        val cleaned = DesktopAiEngine.cleanOutput(raw)
        assertEquals("Hello world! Take a breath.", cleaned)
    }

    @Test
    fun generateMicroStepsAsync_fallsBackInstantlyWhenModelNotReady() =
        runTest {
            val steps = DesktopAiEngine.generateMicroStepsAsync("Write chapter 2 review")
            assertTrue(steps.isNotEmpty())
            assertTrue(steps.any { it.contains("blank document", ignoreCase = true) })
        }

    @Test
    fun parseBrainDumpAsync_fallsBackInstantlyWhenModelNotReady() =
        runTest {
            val dump = "clean room; finish essay; call mom"
            val parsed = DesktopAiEngine.parseBrainDumpAsync(dump)
            assertEquals(3, parsed.size)
            assertEquals("clean room", parsed[0])
        }

    @Test
    fun triageTasksAsync_sortsByShortestQuickWinsWhenModelNotReady() =
        runTest {
            val tasks = listOf("Write entire research manuscript", "Email Dave", "Fix typo")
            val triaged = DesktopAiEngine.triageTasksAsync(tasks)
            assertEquals("Fix typo", triaged.first())
            assertEquals("Write entire research manuscript", triaged.last())
        }

    @Test
    fun replanTasksAsync_splitsRecommendedAndDeferredWhenModelNotReady() =
        runTest {
            val missed = listOf("Task 1", "Task 2", "Task 3", "Task 4")
            val (rec, deferred) = DesktopAiEngine.replanTasksAsync(missed)
            assertEquals(2, rec.size)
            assertEquals(2, deferred.size)
            assertEquals("Task 1", rec[0])
            assertEquals("Task 3", deferred[0])
        }

    @Test
    fun chatTurnAsync_returnsGroundedFallbackWhenModelNotReady() =
        runTest {
            val resp = DesktopAiEngine.chatTurnAsync("Energy: LOW", "I feel overwhelmed")
            assertTrue(resp.isNotEmpty())
            assertTrue(resp.contains("Anchor", ignoreCase = true) || resp.contains("breath", ignoreCase = true))
        }
}
