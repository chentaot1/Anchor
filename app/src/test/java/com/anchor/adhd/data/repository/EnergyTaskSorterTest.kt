package com.anchor.adhd.data.repository

import com.anchor.adhd.data.model.EnergyLevel
import com.anchor.adhd.data.model.TaskDifficulty
import com.anchor.adhd.data.model.TaskEntity
import com.anchor.adhd.domain.EnergyTaskSorter
import org.junit.Assert.assertEquals
import org.junit.Test

class EnergyTaskSorterTest {
    @Test
    fun lowEnergy_prefersLightTasksFirst() {
        val tasks = listOf(
            task("Deep", TaskDifficulty.DEEP, sortOrder = 0),
            task("Light", TaskDifficulty.LIGHT, sortOrder = 1),
            task("Medium", TaskDifficulty.MEDIUM, sortOrder = 2)
        )

        val sorted = EnergyTaskSorter.sort(tasks, EnergyLevel.LOW)

        assertEquals(listOf("Light", "Medium", "Deep"), sorted.map { it.title })
    }

    @Test
    fun highEnergy_prefersDeepTasksFirst() {
        val tasks = listOf(
            task("Light", TaskDifficulty.LIGHT, sortOrder = 0),
            task("Deep", TaskDifficulty.DEEP, sortOrder = 1)
        )

        val sorted = EnergyTaskSorter.sort(tasks, EnergyLevel.HIGH)

        assertEquals(listOf("Deep", "Light"), sorted.map { it.title })
    }

    private fun task(title: String, difficulty: TaskDifficulty, sortOrder: Int) =
        TaskEntity(title = title, difficulty = difficulty, sortOrder = sortOrder)
}
