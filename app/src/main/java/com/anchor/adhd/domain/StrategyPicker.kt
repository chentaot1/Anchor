package com.anchor.adhd.domain

import com.anchor.adhd.data.model.CbtCardEntity
import com.anchor.adhd.data.model.CheckInTag
import java.time.LocalDate
import java.time.ZoneId

object StrategyPicker {
    fun pickDaily(
        cards: List<CbtCardEntity>,
        checkInTags: Set<CheckInTag>,
        dayIndex: Long,
        lastShownId: String?
    ): CbtCardEntity? {
        if (cards.isEmpty()) return null
        val tagIds = StrategyCatalog.cardsForCheckIn(checkInTags)
        val tagMatch = cards.filter { it.id in tagIds }.randomOrNull()
        if (tagMatch != null) return tagMatch
        val pool = cards.filter { it.id != lastShownId }.ifEmpty { cards }
        return pool[(dayIndex % pool.size).toInt().coerceAtLeast(0)]
    }

    fun dayIndex(): Long {
        val zone = ZoneId.systemDefault()
        return LocalDate.now(zone).toEpochDay()
    }
}
