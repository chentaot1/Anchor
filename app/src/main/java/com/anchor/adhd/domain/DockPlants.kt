package com.anchor.adhd.domain

import com.anchor.adhd.data.model.FocusGardenEntity

fun dockSpecies(garden: List<FocusGardenEntity>, max: Int = 8): List<PlantSpecies> {
    if (garden.isEmpty()) return emptyList()
    val newestFirst = garden
    val unique = LinkedHashSet<PlantSpecies>()
    for (row in newestFirst) {
        unique += PlantSpecies.fromTreeType(row.treeType)
        if (unique.size >= max) return unique.toList()
    }
    val filled = unique.toMutableList()
    for (row in newestFirst) {
        if (filled.size >= max) break
        filled += PlantSpecies.fromTreeType(row.treeType)
    }
    return filled
}
