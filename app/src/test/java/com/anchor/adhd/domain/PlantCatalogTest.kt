package com.anchor.adhd.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlantCatalogTest {
    @Test
    fun allSpeciesCount() {
        assertEquals(20, PlantCatalog.all.size)
    }

    @Test
    fun oakUnlockedAtStageZero() {
        assertTrue(PlantCatalog.isUnlocked(PlantSpecies.OAK, 0))
    }

    @Test
    fun mushroomLockedUntilStageFive() {
        assertFalse(PlantCatalog.isUnlocked(PlantSpecies.MUSHROOM, 2))
        assertTrue(PlantCatalog.isUnlocked(PlantSpecies.MUSHROOM, 5))
    }
}
