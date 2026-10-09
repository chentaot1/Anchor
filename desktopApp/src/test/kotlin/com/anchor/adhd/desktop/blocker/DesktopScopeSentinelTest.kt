package com.anchor.adhd.desktop.blocker

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DesktopScopeSentinelTest {
    @Before
    fun setUp() {
        DesktopScopeSentinel.clearCache()
    }

    @Test
    fun wordBoundaryMatching_preventsSubstringFalsePositives() {
        // "humeral" contains "hume", should match anatomy (IN_SCOPE), not Philosophy
        val humeral = DesktopScopeSentinel.evaluateHeuristics("Humeral fracture anatomy - Google Docs")
        assertEquals(ScopeVerdict.IN_SCOPE, humeral.verdict)

        // "flora" contains "lora", should match biology (IN_SCOPE), not CS/AI
        val flora = DesktopScopeSentinel.evaluateHeuristics("Gut flora and cellular biology review")
        assertEquals(ScopeVerdict.IN_SCOPE, flora.verdict)

        // "locked" contains "locke", should match syllabus (IN_SCOPE), not Philosophy
        val locked = DesktopScopeSentinel.evaluateHeuristics("Locked course syllabus PDF")
        assertEquals(ScopeVerdict.IN_SCOPE, locked.verdict)

        // "enrollment" contains "llm", should not trigger CS/AI false positive
        val enrollment = DesktopScopeSentinel.evaluateHeuristics("Student Fall Enrollment Form")
        assertEquals(ScopeVerdict.UNKNOWN, enrollment.verdict)
    }

    @Test
    fun genuineOutOfScopeTopics_areDetectedAccurately() {
        val phil = DesktopScopeSentinel.evaluateHeuristics("David Hume - Stanford Encyclopedia of Philosophy")
        assertEquals(ScopeVerdict.OUT_OF_SCOPE, phil.verdict)
        assertEquals("Philosophy", phil.category)

        val ai = DesktopScopeSentinel.evaluateHeuristics("LoRA fine-tuning guide on HuggingFace")
        assertEquals(ScopeVerdict.OUT_OF_SCOPE, ai.verdict)
        assertEquals("Computer Science / AI", ai.category)
    }

    @Test
    fun activeTaskTitleCache_isScopedToCurrentTask() {
        val windowTitle = "Chapter 4 Vocabulary Flashcards"
        val matchDuringTask =
            DesktopScopeSentinel.evaluateHeuristics(
                windowTitle = windowTitle,
                activeTaskTitle = "Review Vocabulary Flashcards",
            )
        assertEquals(ScopeVerdict.IN_SCOPE, matchDuringTask.verdict)
        assertEquals("Current Task", matchDuringTask.category)

        // Switching to a different task should not reuse the previous task's IN_SCOPE cache entry
        val afterTaskSwitch =
            DesktopScopeSentinel.evaluateHeuristics(
                windowTitle = windowTitle,
                activeTaskTitle = "Write lab report",
            )
        assertEquals(ScopeVerdict.UNKNOWN, afterTaskSwitch.verdict)
    }
}
