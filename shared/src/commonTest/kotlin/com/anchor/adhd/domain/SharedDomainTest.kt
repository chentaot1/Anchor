package com.anchor.adhd.domain

import com.anchor.adhd.ui.grove.calculateHarborPalette
import com.anchor.adhd.ui.theme.TimeOfDay
import com.anchor.adhd.ui.theme.currentTimeOfDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedDomainTest {

    @Test
    fun airlockHeuristic_handlesBlankInput() {
        val res = AirlockHeuristic.parse("   ")
        assertEquals("", res.primaryTask)
        assertTrue(res.secondaryTasks.isEmpty())
        assertEquals("", res.defaultSomaticStarter)
    }

    @Test
    fun airlockHeuristic_splitsMultiItemBrainDumpAndBuildsStarter() {
        val res = AirlockHeuristic.parse("1. Write psych lab intro, 2. email TA about extension and then buy groceries")
        assertEquals("Write psych lab intro", res.primaryTask)
        assertEquals(listOf("email TA about extension", "buy groceries"), res.secondaryTasks)
        assertEquals("Just write psych lab intro", res.defaultSomaticStarter)
    }

    @Test
    fun airlockHeuristic_nonVerbPrimaryUsesOpenMaterialsStarter() {
        val res = AirlockHeuristic.parse("Organic chemistry chapter 4; laundry")
        assertEquals("Organic chemistry chapter 4", res.primaryTask)
        assertEquals(listOf("laundry"), res.secondaryTasks)
        assertEquals("Just open materials for \"Organic chemistry chapter 4\"", res.defaultSomaticStarter)
    }

    @Test
    fun airlockHeuristic_extractCleanSomaticStarter_parsesJsonAndFallsBackOnBrokenJson() {
        val fallback = "Just open blank doc"
        assertEquals(
            "Just Open notes to page 12",
            AirlockHeuristic.extractCleanSomaticStarter(
                """{"steps":["Open notes to page 12","Read summary"],"next_action":"Open notes to page 12"}""",
                fallback
            )
        )
        assertEquals(
            "Just Grab pen and notebook",
            AirlockHeuristic.extractCleanSomaticStarter(
                """{"steps":["Step 1: Grab pen and notebook"]}""",
                fallback
            )
        )
        assertEquals(
            fallback,
            AirlockHeuristic.extractCleanSomaticStarter("""{"broken_json": [""", fallback)
        )
    }

    @Test
    fun groveLevel_mapsWeeklySessionCountsToStagesAndProgress() {
        assertEquals(0, GroveLevel.vitalityStageForWeeklySessions(0))
        assertEquals("Seed", GroveLevel.vitalityLabel(0))
        assertEquals(0.12f, GroveLevel.vitalityProgress(0))

        assertEquals(1, GroveLevel.vitalityStageForWeeklySessions(2))
        assertEquals("Sprout", GroveLevel.vitalityLabel(1))

        assertEquals(2, GroveLevel.vitalityStageForWeeklySessions(5))
        assertEquals("Growing", GroveLevel.vitalityLabel(2))

        assertEquals(3, GroveLevel.vitalityStageForWeeklySessions(9))
        assertEquals("Rooted", GroveLevel.vitalityLabel(3))

        assertEquals(4, GroveLevel.vitalityStageForWeeklySessions(12))
        assertEquals("Flourishing", GroveLevel.vitalityLabel(4))
        assertEquals(1f, GroveLevel.vitalityProgress(12))
    }

    @Test
    fun plantCatalog_unlocksSpeciesByStageAndSelectsDefaultSpecies() {
        val stage0 = PlantCatalog.unlockedAtStage(0)
        assertEquals(listOf(PlantSpecies.OAK, PlantSpecies.PINE), stage0)
        assertTrue(PlantCatalog.isUnlocked(PlantSpecies.OAK, 0))
        assertFalse(PlantCatalog.isUnlocked(PlantSpecies.ANCIENT_OAK, 4))
        assertTrue(PlantCatalog.isUnlocked(PlantSpecies.ANCIENT_OAK, 8))

        assertEquals(PlantSpecies.PINE, PlantCatalog.defaultPlantedSpecies(0))
        assertEquals(PlantSpecies.CYPRESS, PlantCatalog.defaultPlantedSpecies(10))
    }

    @Test
    fun plantSpecies_fromTreeType_matchesAssetOrEnumNameCaseInsensitively() {
        assertEquals(PlantSpecies.LANTERN_BLOOM, PlantSpecies.fromTreeType("lantern_bloom"))
        assertEquals(PlantSpecies.LANTERN_BLOOM, PlantSpecies.fromTreeType("LANTERN_BLOOM"))
        assertEquals(PlantSpecies.WILLOW, PlantSpecies.fromTreeType("Willow"))
        assertEquals(PlantSpecies.OAK, PlantSpecies.fromTreeType("unknown_species"))
    }

    @Test
    fun timeOfDay_and_harborPalette_coverFull24Hours() {
        assertEquals(TimeOfDay.DAWN, currentTimeOfDay(6))
        assertEquals(TimeOfDay.DAY, currentTimeOfDay(12))
        assertEquals(TimeOfDay.DUSK, currentTimeOfDay(18))
        assertEquals(TimeOfDay.NIGHT, currentTimeOfDay(23))
        assertTrue(currentTimeOfDay(2).isNight)

        val midnightPalette = calculateHarborPalette(0)
        val noonPalette = calculateHarborPalette(720)
        val clampedPalette = calculateHarborPalette(9999)
        assertTrue(midnightPalette.skyHorizon != noonPalette.skyHorizon)
        assertEquals(calculateHarborPalette(1439), clampedPalette)
    }
}
