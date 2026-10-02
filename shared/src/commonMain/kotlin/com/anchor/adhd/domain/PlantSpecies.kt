package com.anchor.adhd.domain

/** Twenty collectible grove species. Growth stages (seed/sapling) are visual scale only. */
enum class PlantSpecies(val displayName: String, val assetName: String) {
    OAK("Oak", "oak"),
    PINE("Pine", "pine"),
    WILLOW("Willow", "willow"),
    BIRCH("Birch", "birch"),
    CYPRESS("Cypress", "cypress"),
    PALM("Palm", "palm"),
    LAVENDER("Lavender", "lavender"),
    SUNFLOWER("Sunflower", "sunflower"),
    BLOSSOM("Blossom", "blossom"),
    WILDFLOWER("Wildflower", "wildflower"),
    FERN("Fern", "fern"),
    MOSS("Moss", "moss"),
    BUSH("Bush", "bush"),
    VINE("Vine", "vine"),
    MUSHROOM("Mushroom", "mushroom"),
    SEAGRASS("Seagrass", "seagrass"),
    CLOVER("Clover", "clover"),
    IRIS("Iris", "iris"),
    LANTERN_BLOOM("Lantern bloom", "lantern_bloom"),
    ANCIENT_OAK("Ancient oak", "ancient_oak");

    val modelAssetPath: String get() = "plants/$assetName.glb"

    companion object {
        fun fromTreeType(treeType: String): PlantSpecies =
            entries.find { it.assetName.equals(treeType, ignoreCase = true) }
                ?: entries.find { it.name.equals(treeType, ignoreCase = true) }
                ?: OAK
    }
}
