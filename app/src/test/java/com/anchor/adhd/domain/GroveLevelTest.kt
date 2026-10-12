package com.anchor.adhd.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GroveLevelTest {
    @Test
    fun vitalityStage_matchesWeeklySessions() {
        assertEquals(0, GroveLevel.vitalityStageForWeeklySessions(0))
        assertEquals(1, GroveLevel.vitalityStageForWeeklySessions(2))
        assertEquals(4, GroveLevel.vitalityStageForWeeklySessions(10))
    }

    @Test
    fun vitalityLabel_returnsExpected() {
        assertEquals("Seed", GroveLevel.vitalityLabel(0))
        assertEquals("Flourishing", GroveLevel.vitalityLabel(4))
    }
}
