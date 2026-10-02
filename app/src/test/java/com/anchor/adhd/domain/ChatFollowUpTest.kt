package com.anchor.adhd.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatFollowUpTest {

    @Test
    fun resolve_breakThatDown_usesLastTask() {
        assertEquals(
            "break down \"essay\"",
            ChatFollowUp.resolve("break that down", "essay"),
        )
    }

    @Test
    fun resolve_markItDone_usesLastTask() {
        assertEquals(
            "mark done \"call mom\"",
            ChatFollowUp.resolve("mark it done", "call mom"),
        )
    }

    @Test
    fun resolve_snoozeThat_usesLastTask() {
        assertEquals(
            "snooze \"chem lab\"",
            ChatFollowUp.resolve("snooze that", "chem lab"),
        )
    }

    @Test
    fun resolve_withoutLastTask_leavesText() {
        assertEquals("break that down", ChatFollowUp.resolve("break that down", null))
    }

    @Test
    fun resolve_unrelatedText_unchanged() {
        assertEquals("buy milk", ChatFollowUp.resolve("buy milk", "essay"))
    }

    @Test
    fun affirmative_yesVariants() {
        assertTrue(ChatFollowUp.isAffirmative("yes"))
        assertTrue(ChatFollowUp.isAffirmative("OK"))
        assertTrue(ChatFollowUp.isAffirmative("add it"))
        assertFalse(ChatFollowUp.isAffirmative("maybe later"))
    }

    @Test
    fun negative_noVariants() {
        assertTrue(ChatFollowUp.isNegative("nope"))
        assertTrue(ChatFollowUp.isNegative("never mind"))
        assertFalse(ChatFollowUp.isNegative("not sure"))
    }
}
