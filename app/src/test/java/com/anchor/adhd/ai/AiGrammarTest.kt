package com.anchor.adhd.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiGrammarTest {

    @Test
    fun intentClassifierGrammar_containsAllVerbs() {
        val grammar = AiGrammar.intentClassifierGrammar
        val verbs = listOf(
            "capture_tasks", "whats_today", "remind", "mark_done", "move_someday",
            "breakdown", "brain_dump", "schedule", "triage", "replan", "start_focus", "snooze", "chat", "clarify"
        )
        verbs.forEach { verb ->
            assertTrue("grammar should contain verb $verb", grammar.contains("\"$verb\""))
        }
        assertTrue(grammar.contains("root"))
    }

    @Test
    fun chatTurnGrammar_isClosedObjectWithQuery() {
        val grammar = AiGrammar.chatTurnGrammar
        assertTrue(grammar.contains("intent"))
        assertTrue(grammar.contains("query"))
        assertTrue(grammar.contains("pick_one"))
        assertTrue(grammar.contains("ws ::="))
        assertFalse(grammar.contains("ws*"))
    }
}
