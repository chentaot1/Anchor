package com.anchor.adhd.data

import android.content.Context
import com.anchor.adhd.ai.AiBenchmarkRunner
import com.anchor.adhd.ai.AiRepository
import com.anchor.adhd.ai.GpuRamCoordinator
import com.anchor.adhd.ai.LocalAiEngine
import com.anchor.adhd.ai.ModelDownloadManager
import com.anchor.adhd.calendar.DeviceCalendarSync
import com.anchor.adhd.calendar.GoogleCalendarImporter
import com.anchor.adhd.data.db.AnchorDatabase
import com.anchor.adhd.data.prefs.UserPreferences
import com.anchor.adhd.data.repository.FocusRepository
import com.anchor.adhd.data.repository.GrowRepository
import com.anchor.adhd.data.repository.HabitRepository
import com.anchor.adhd.data.repository.PlanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AnchorContainer(context: Context) {
    val appContext = context.applicationContext

    val database = AnchorDatabase.getInstance(appContext)
    val preferences = UserPreferences(appContext)
    val userPreferences: UserPreferences get() = preferences
    val advancedBlocker = com.anchor.adhd.data.repository.AdvancedBlockerRepository(appContext)

    val planRepository = PlanRepository(
        taskDao = database.taskDao(),
        routineDao = database.routineDao(),
        calendarDao = database.calendarEventDao(),
        assignmentDao = database.assignmentDao(),
        replanDao = database.replanDao(),
        db = database
    )

    val focusRepository = FocusRepository(
        context = appContext,
        sessionDao = database.focusSessionDao(),
        blockRuleDao = database.blockRuleDao(),
        bundleDao = database.temptationBundleDao(),
        taskDao = database.taskDao(),
        preferences = preferences,
        companionDao = database.companionDao(),
        advancedBlocker = advancedBlocker
    )

    val growRepository = GrowRepository(
        checkInDao = database.checkInDao(),
        companionDao = database.companionDao(),
        sessionDao = database.focusSessionDao(),
        taskDao = database.taskDao(),
        replanDao = database.replanDao(),
        preferences = preferences
    )

    val habitRepository = HabitRepository(
        habitDao = database.habitDao()
    )

    val chatTurnDao = database.chatTurnDao()
    val chatUndoDao = database.chatUndoDao()
    val funLinkDao = database.funLinkDao()

    val aiEngine = LocalAiEngine(appContext, preferences)
    private val ramScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val gpuRam = GpuRamCoordinator(aiEngine, ramScope)
    val aiBenchmarkRunner = AiBenchmarkRunner(appContext, aiEngine)
    val aiRepository = AiRepository(
        aiEngine = aiEngine,
        aiJobDao = database.aiJobDao(),
        preferences = preferences
    )

    val modelDownloadManager = ModelDownloadManager(appContext, preferences)
    val samsungHealth = com.anchor.adhd.health.SamsungHealthBridge(appContext)
    val calendarImporter = GoogleCalendarImporter(appContext)
    val deviceCalendarSync = DeviceCalendarSync(appContext)
    val backupManager = BackupManager(appContext, database)
    val appResetManager = AppResetManager(appContext, database, preferences, focusRepository, aiEngine)
}
