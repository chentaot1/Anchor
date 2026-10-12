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
        assertEquals(ScopeVerdict.UNKNOWN, DesktopScopeSentinel.evaluateHeuristics("Humeral fracture", "Study Hume").verdict)
        assertEquals(ScopeVerdict.UNKNOWN, DesktopScopeSentinel.evaluateHeuristics("Gut flora review", "Study LoRA").verdict)
        assertEquals(ScopeVerdict.IN_SCOPE, DesktopScopeSentinel.evaluateHeuristics("Hume essay", "Study Hume").verdict)
    }

    @Test
    fun modelClassification_isScopedToTaskAndDoesNotBecomeManual() {
        val title = "Example resource"
        val result = ScopeEvaluationResult(ScopeVerdict.OUT_OF_SCOPE, "Unrelated Activity", "Unrelated to task", "LOCAL_AI")
        DesktopScopeSentinel.recordTaskClassification(title, "Write essay", result)
        assertEquals(result, DesktopScopeSentinel.evaluateHeuristics(title, "Write essay"))
        assertEquals(ScopeVerdict.UNKNOWN, DesktopScopeSentinel.evaluateHeuristics(title, "Solve exercise").verdict)
        DesktopScopeSentinel.recordManualClassification(title, ScopeVerdict.IN_SCOPE)
        assertEquals(ScopeVerdict.IN_SCOPE, DesktopScopeSentinel.evaluateHeuristics(title, "Write essay").verdict)
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
