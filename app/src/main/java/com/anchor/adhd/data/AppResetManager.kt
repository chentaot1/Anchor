package com.anchor.adhd.data

import android.content.Context
import android.content.Intent
import com.anchor.adhd.ai.LocalAiEngine
import com.anchor.adhd.data.db.AnchorDatabase
import com.anchor.adhd.data.prefs.UserPreferences
import com.anchor.adhd.data.repository.FocusRepository
import com.anchor.adhd.data.seed.SeedData
import com.anchor.adhd.notify.ReminderScheduler
import com.anchor.adhd.service.FocusTimerService
import com.anchor.adhd.service.FocusTimerState
import com.anchor.adhd.service.PendingPostFocus
import com.anchor.adhd.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppResetManager(
    private val context: Context,
    private val database: AnchorDatabase,
    private val preferences: UserPreferences,
    private val focusRepository: FocusRepository,
    private val aiEngine: LocalAiEngine
) {
    suspend fun resetAll(deleteModel: Boolean) = withContext(Dispatchers.IO) {
        context.startService(
            Intent(context, FocusTimerService::class.java).apply {
                action = FocusTimerService.ACTION_CANCEL
            }
        )
        FocusTimerState.reset()
        PendingPostFocus.set(null)
        ReminderScheduler.cancelAll(context)

        aiEngine.ensureUnloaded()

        database.clearAllTables()
        preferences.clearAll()
        (context.applicationContext as? com.anchor.adhd.AnchorApp)?.container?.advancedBlocker?.clear()

        if (deleteModel) {
            preferences.deleteDownloadedModel()
        }

        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith("anchor-backup-") && it.extension == "json" }
            ?.forEach { it.delete() }

        SeedData.seedIfNeeded(database, preferences)
        focusRepository.refreshBlockedPackages()
        WidgetUpdater.updateAll(context)
        ReminderScheduler.rescheduleAll(context)
    }
}

