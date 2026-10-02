package com.anchor.adhd.data.seed

import com.anchor.adhd.data.db.AnchorDatabase
import com.anchor.adhd.data.model.BlockRuleEntity
import com.anchor.adhd.data.model.BlockRuleType
import com.anchor.adhd.data.model.CompanionStateEntity
import com.anchor.adhd.data.model.RoutineEntity
import com.anchor.adhd.data.model.RoutineStepEntity
import com.anchor.adhd.data.prefs.UserPreferences
import com.anchor.adhd.domain.StrategyCatalog
import kotlinx.coroutines.flow.first

object SeedData {
    suspend fun seedIfNeeded(database: AnchorDatabase, preferences: UserPreferences) {
        if (!preferences.isSeedComplete()) {
            seedCbt(database.cbtCardDao())
            seedCompanion(database)
            seedRoutines(database)
            seedDefaultBlocks(database)
            preferences.setSeedComplete()
        }
        upsertStrategies(database.cbtCardDao(), preferences)
    }

    private suspend fun seedCbt(dao: com.anchor.adhd.data.db.CbtCardDao) {
        dao.insertAll(StrategyCatalog.allStrategyCards().take(6))
    }

    suspend fun upsertStrategies(dao: com.anchor.adhd.data.db.CbtCardDao, preferences: UserPreferences) {
        if (preferences.getStrategiesSeedVersion() >= StrategyCatalog.SEED_VERSION) return
        dao.insertAll(StrategyCatalog.allStrategyCards())
        preferences.setStrategiesSeedVersion(StrategyCatalog.SEED_VERSION)
    }

    private suspend fun seedCompanion(database: AnchorDatabase) {
        if (database.companionDao().observe().first() == null) {
            database.companionDao().upsert(CompanionStateEntity())
        }
    }

    private suspend fun seedRoutines(database: AnchorDatabase) {
        val routineDao = database.routineDao()
        if (routineDao.observeEnabled().first().isNotEmpty()) return

        val morningId = routineDao.insertRoutine(
            RoutineEntity(
                name = "Morning launch",
                cue = "After I get out of bed",
                ifThen = "If I get out of bed, then I open Anchor and check today's first block."
            )
        )
        routineDao.insertSteps(
            listOf(
                RoutineStepEntity(routineId = morningId, title = "Water + bathroom", durationMinutes = 5, sortOrder = 0),
                RoutineStepEntity(routineId = morningId, title = "Get dressed", durationMinutes = 5, sortOrder = 1),
                RoutineStepEntity(routineId = morningId, title = "Open Anchor → today view", durationMinutes = 2, sortOrder = 2),
                RoutineStepEntity(routineId = morningId, title = "Pack bag checklist", durationMinutes = 5, sortOrder = 3)
            )
        )

        val studyId = routineDao.insertRoutine(
            RoutineEntity(
                name = "Study start",
                cue = "When I sit at my desk",
                ifThen = "If I sit at my desk, then I start a 10-minute focus timer on the first task."
            )
        )
        routineDao.insertSteps(
            listOf(
                RoutineStepEntity(routineId = studyId, title = "Phone face-down", durationMinutes = 1, sortOrder = 0),
                RoutineStepEntity(routineId = studyId, title = "Write one-sentence goal", durationMinutes = 2, sortOrder = 1),
                RoutineStepEntity(routineId = studyId, title = "Start focus timer", durationMinutes = 10, sortOrder = 2)
            )
        )
    }

    private suspend fun seedDefaultBlocks(database: AnchorDatabase) {
        val dao = database.blockRuleDao()
        if (dao.observeAll().first().isNotEmpty()) return

        listOf(
            "com.google.android.youtube",
            "com.zhiliaoapp.musically",
            "com.instagram.android",
            "com.reddit.frontpage",
            "com.discord"
        ).forEach { pkg ->
            dao.insert(BlockRuleEntity(packageName = pkg, ruleType = BlockRuleType.SESSION, enabled = true))
        }
    }
}
