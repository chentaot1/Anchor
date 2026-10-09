package com.anchor.adhd

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.anchor.adhd.data.AnchorContainer
import com.anchor.adhd.data.seed.SeedData
import com.anchor.adhd.service.FocusTimerService
import com.anchor.adhd.service.PendingPostFocus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AnchorApp : Application() {
    lateinit var container: AnchorContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val sessionEndReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != FocusTimerService.ACTION_SESSION_ENDED) return
            val sessionId = intent.getLongExtra(FocusTimerService.EXTRA_SESSION_ID, -1L)
            val completed = intent.getBooleanExtra(FocusTimerService.EXTRA_COMPLETED, false)
            if (sessionId < 0) return
            appScope.launch {
                val session = container.database.focusSessionDao().getById(sessionId) ?: return@launch
                val endedAt = System.currentTimeMillis()
                val planned = session.plannedMinutes.coerceAtLeast(1)
                val actual = if (completed) {
                    planned
                } else {
                    ((endedAt - session.startedAtMillis) / 60_000L).toInt().coerceIn(1, planned)
                }
                if (completed) {
                    val worked = actual.coerceIn(0, session.plannedMinutes.coerceAtLeast(0))
                    container.advancedBlocker.creditSession(sessionId, worked, session.startedAtMillis + worked * 60_000L)
                }
                val task = session.taskId?.let { container.planRepository.getTask(it) }
                val summary = com.anchor.adhd.data.model.PostFocusSummary(
                    sessionId = sessionId,
                    taskId = session.taskId,
                    taskTitle = task?.title,
                    plannedMinutes = session.plannedMinutes,
                    actualMinutes = actual,
                    completed = completed,
                    blockedAttempts = com.anchor.adhd.service.FocusBlockService.blockedAttempts
                )
                container.preferences.savePendingPostFocus(summary)
                PendingPostFocus.set(summary)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        container = AnchorContainer(this)
        appScope.launch(Dispatchers.IO) {
            SeedData.seedIfNeeded(container.database, container.preferences)
            runCatching { container.modelDownloadManager.installBundledModel() }
                .onFailure { android.util.Log.e("AnchorApp", "Bundled desktop model installation failed", it) }
        }
        val filter = IntentFilter(FocusTimerService.ACTION_SESSION_ENDED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sessionEndReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(sessionEndReceiver, filter)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level == ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        ) {
            if (::container.isInitialized) container.gpuRam.unloadNow()
        }
    }
}
