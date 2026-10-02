package com.anchor.adhd.domain

import com.anchor.adhd.data.model.EnergyLevel
import com.anchor.adhd.data.model.TaskDifficulty
import com.anchor.adhd.data.model.TaskEntity

object EnergyTaskSorter {
    private val lowOrder = listOf(TaskDifficulty.LIGHT, TaskDifficulty.MEDIUM, TaskDifficulty.DEEP)
    private val okOrder = listOf(TaskDifficulty.MEDIUM, TaskDifficulty.LIGHT, TaskDifficulty.DEEP)
    private val highOrder = listOf(TaskDifficulty.DEEP, TaskDifficulty.MEDIUM, TaskDifficulty.LIGHT)

    fun sort(tasks: List<TaskEntity>, energy: EnergyLevel): List<TaskEntity> {
        val order = when (energy) {
            EnergyLevel.LOW -> lowOrder
            EnergyLevel.OK -> okOrder
            EnergyLevel.HIGH -> highOrder
        }
        return tasks.sortedWith(
            compareBy<TaskEntity>(
                { order.indexOf(it.difficulty).let { i -> if (i < 0) order.size else i } },
                { it.sortOrder },
                { it.createdAtMillis }
            )
        )
    }
}
