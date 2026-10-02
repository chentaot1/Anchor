package com.anchor.adhd.domain

/**
 * Grove collection catalog — species unlock by grove level (cosmetic; coins in v7).
 */
object PlantCatalog {
    data class Entry(val species: PlantSpecies, val unlockStage: Int)

    val all: List<Entry> = listOf(
        Entry(PlantSpecies.OAK, 0),
        Entry(PlantSpecies.PINE, 0),
        Entry(PlantSpecies.CLOVER, 1),
        Entry(PlantSpecies.FERN, 1),
        Entry(PlantSpecies.MOSS, 1),
        Entry(PlantSpecies.BUSH, 2),
        Entry(PlantSpecies.LAVENDER, 2),
        Entry(PlantSpecies.BLOSSOM, 2),
        Entry(PlantSpecies.WILLOW, 3),
        Entry(PlantSpecies.BIRCH, 3),
        Entry(PlantSpecies.SUNFLOWER, 3),
        Entry(PlantSpecies.WILDFLOWER, 4),
        Entry(PlantSpecies.VINE, 4),
        Entry(PlantSpecies.CYPRESS, 4),
        Entry(PlantSpecies.MUSHROOM, 5),
        Entry(PlantSpecies.SEAGRASS, 5),
        Entry(PlantSpecies.PALM, 6),
        Entry(PlantSpecies.IRIS, 6),
        Entry(PlantSpecies.LANTERN_BLOOM, 7),
        Entry(PlantSpecies.ANCIENT_OAK, 8)
    )

    fun unlockedAtStage(stage: Int): List<PlantSpecies> =
        all.filter { it.unlockStage <= stage }.map { it.species }

    fun isUnlocked(species: PlantSpecies, stage: Int): Boolean =
        all.find { it.species == species }?.unlockStage?.let { it <= stage } == true

    /** Default species planted after a focus session (until shop picks a species). */
    fun defaultPlantedSpecies(weeklySessions: Int): PlantSpecies {
        val stage = GroveLevel.vitalityStageForWeeklySessions(weeklySessions)
        return unlockedAtStage(stage).lastOrNull { it != PlantSpecies.ANCIENT_OAK } ?: PlantSpecies.OAK
    }
}
