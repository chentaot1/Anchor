package com.anchor.adhd.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PipStagesTest {
    @Test
    fun stageForWeeklySessions_boundaries() {
        assertEquals(0, PipStages.stageForWeeklySessions(0))
        assertEquals(1, PipStages.stageForWeeklySessions(1))
        assertEquals(1, PipStages.stageForWeeklySessions(2))
        assertEquals(2, PipStages.stageForWeeklySessions(3))
        assertEquals(2, PipStages.stageForWeeklySessions(5))
        assertEquals(3, PipStages.stageForWeeklySessions(6))
        assertEquals(3, PipStages.stageForWeeklySessions(9))
        assertEquals(4, PipStages.stageForWeeklySessions(10))
    }

    @Test
    fun label_clampsOutOfRange() {
        assertEquals("Seed", PipStages.label(-1))
        assertEquals("Flourishing", PipStages.label(99))
    }
}
