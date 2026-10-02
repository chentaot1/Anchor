package com.anchor.adhd.domain

import com.anchor.adhd.data.model.CheckInTag
import com.anchor.adhd.data.model.EnergyLevel

object CheckInCodec {
    fun formatTags(tags: Set<CheckInTag>): String =
        tags.map { it.name }.sorted().joinToString(",")

    fun parseTags(raw: String): Set<CheckInTag> =
        if (raw.isBlank()) emptySet()
        else raw.split(",").mapNotNull { part ->
            when (part.trim()) {
                "RESTLESS" -> CheckInTag.SCATTERED // legacy combined-type label
                else -> runCatching { CheckInTag.valueOf(part.trim()) }.getOrNull()
            }
        }.toSet()

    fun activationLabel(level: EnergyLevel): String = when (level) {
        EnergyLevel.LOW -> "Drained"
        EnergyLevel.OK -> "Steady"
        EnergyLevel.HIGH -> "Sharp"
    }

    fun tagLabel(tag: CheckInTag): String = when (tag) {
        CheckInTag.FOGGY -> "Foggy"
        CheckInTag.SCATTERED -> "Scattered"
        CheckInTag.OVERWHELMED -> "Overwhelmed"
        CheckInTag.UNMOTIVATED -> "Low drive"
    }

    fun formatCheckInDetail(energy: EnergyLevel, tags: Set<CheckInTag>): String {
        val tagPart = tags.sortedBy { it.ordinal }.joinToString(" · ") { tagLabel(it) }
        return if (tagPart.isBlank()) activationLabel(energy) else "${activationLabel(energy)} · $tagPart"
    }
}
