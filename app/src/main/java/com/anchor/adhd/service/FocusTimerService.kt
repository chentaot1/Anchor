package com.anchor.adhd.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.anchor.adhd.MainActivity
import com.anchor.adhd.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import com.anchor.adhd.data.prefs.UserPreferences
import kotlinx.coroutines.launch

class FocusTimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null
    private var timer5MinWarning = true

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            timer5MinWarning = UserPreferences(this@FocusTimerService).getReminderSettings().timer5MinWarning
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            FocusBlockService.sessionActive = false
            FocusTimerState.reset()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_START -> {
                val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L).takeIf { it >= 0 }
                val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE)
                val workMin = intent.getIntExtra(EXTRA_WORK_MINUTES, 20)
                val breakMin = intent.getIntExtra(EXTRA_BREAK_MINUTES, 5)
                val locked = intent.getBooleanExtra(EXTRA_LOCKED, true)
                startWorkPhase(sessionId, taskId, taskTitle, workMin, breakMin, locked)
            }
            ACTION_CANCEL -> stopSession(completed = false, notify = false)
            ACTION_STOP -> stopSession(completed = false, notify = true)
            ACTION_COMPLETE -> stopSession(completed = true, notify = true)
        }
        return START_STICKY
    }

    private fun startWorkPhase(
        sessionId: Long,
        taskId: Long?,
        taskTitle: String?,
        workMin: Int,
        breakMin: Int,
        locked: Boolean
    ) {
        FocusBlockService.sessionActive = locked
        FocusBlockService.resetSessionBlocks()
        FocusTimerState.update {
            FocusTimerState.State(
                sessionId = sessionId,
                taskId = taskId,
                taskTitle = taskTitle,
                phase = FocusTimerState.Phase.WORK,
                remainingSeconds = workMin * 60,
                totalWorkSeconds = workMin * 60,
                workMinutes = workMin,
                breakMinutes = breakMin,
                locked = locked,
                startedAtMillis = System.currentTimeMillis()
            )
        }
        startForeground(NOTIFICATION_ID, buildNotification(workMin * 60, "Focus"))
        startTicking(onWorkDone = {
            if (breakMin > 0) {
                startBreakPhase(sessionId, taskId, breakMin)
            } else {
                stopSession(completed = true, notify = true)
            }
        })
    }

    private fun startBreakPhase(sessionId: Long, taskId: Long?, breakMin: Int) {
        FocusBlockService.sessionActive = false
        FocusTimerState.update {
            it.copy(
                phase = FocusTimerState.Phase.BREAK,
                remainingSeconds = breakMin * 60
            )
        }
        updateNotification(breakMin * 60, "Break")
        startTicking(onWorkDone = { stopSession(completed = true, notify = true) })
    }

    private fun startTicking(onWorkDone: () -> Unit) {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (FocusTimerState.state.value.remainingSeconds > 0) {
                delay(1000)
                val phase = FocusTimerState.state.value.phase
                val next = FocusTimerState.state.value.remainingSeconds - 1
                val current = FocusTimerState.state.value
                if (phase == FocusTimerState.Phase.WORK && next == 300 && !current.fiveMinWarned) {
                    FocusTimerState.update { s -> s.copy(fiveMinWarned = true) }
                    if (shouldWarn5Min()) showFiveMinWarning()
                }
                FocusTimerState.update { s -> s.copy(remainingSeconds = next) }
                val label = if (phase == FocusTimerState.Phase.WORK) "Focus" else "Break"
                updateNotification(next, label)
            }
            onWorkDone()
        }
    }

    private fun stopSession(completed: Boolean, notify: Boolean) {
        tickJob?.cancel()
        FocusBlockService.sessionActive = false
        val sessionId = FocusTimerState.state.value.sessionId
        if (notify && sessionId != null && sessionId >= 0) {
            sendBroadcast(
                Intent(ACTION_SESSION_ENDED).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_SESSION_ID, sessionId)
                    putExtra(EXTRA_COMPLETED, completed)
                }
            )
        }
        FocusTimerState.reset()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(seconds: Int, label: String): Notification {
        ensureChannel()
        return notificationBuilder(seconds, label).build()
    }

    private fun updateNotification(seconds: Int, label: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notificationBuilder(seconds, label).build())
    }

    private fun notificationBuilder(seconds: Int, label: String): NotificationCompat.Builder {
        val open = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val endEarly = PendingIntent.getService(
            this,
            NOTIFICATION_ID + 2,
            Intent(this, FocusTimerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val state = FocusTimerState.state.value
        val total = if (state.phase == FocusTimerState.Phase.BREAK) {
            state.breakMinutes * 60
        } else {
            state.totalWorkSeconds.coerceAtLeast(1)
        }
        val elapsed = (total - seconds).coerceIn(0, total)
        val endAt = System.currentTimeMillis() + seconds * 1000L
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$label — ${seconds / 60}:${"%02d".format(seconds % 60)}")
            .setContentText(state.taskTitle ?: "Anchor focus session")
            .setOngoing(true)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(endAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setProgress(total, elapsed, false)
            .setColor(0xFFFFAB6B.toInt())
            .addAction(0, "End early", endEarly)
        builder.extras.putBoolean("android.requestPromotedOngoing", true)
        return builder
    }

    private fun shouldWarn5Min(): Boolean = timer5MinWarning

    private fun showFiveMinWarning() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel()
        nm.notify(
            NOTIFICATION_ID + 1,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("5 minutes left")
                .setContentText("Focus block ending soon")
                .setAutoCancel(true)
                .build()
        )
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Focus timer", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onDestroy() {
        tickJob?.cancel()
        FocusBlockService.sessionActive = false
        FocusTimerState.reset()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.anchor.adhd.timer.START"
        const val ACTION_CANCEL = "com.anchor.adhd.timer.CANCEL"
        const val ACTION_STOP = "com.anchor.adhd.timer.STOP"
        const val ACTION_COMPLETE = "com.anchor.adhd.timer.COMPLETE"
        const val ACTION_SESSION_ENDED = "com.anchor.adhd.timer.ENDED"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_TITLE = "task_title"
        const val EXTRA_WORK_MINUTES = "work_minutes"
        const val EXTRA_BREAK_MINUTES = "break_minutes"
        const val EXTRA_LOCKED = "locked"
        const val EXTRA_COMPLETED = "completed"
        private const val CHANNEL_ID = "focus_timer"
        private const val NOTIFICATION_ID = 1001

        fun startIntent(
            context: Context,
            sessionId: Long,
            taskId: Long?,
            taskTitle: String?,
            workMinutes: Int,
            breakMinutes: Int,
            locked: Boolean
        ): Intent = Intent(context, FocusTimerService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_SESSION_ID, sessionId)
            taskId?.let { putExtra(EXTRA_TASK_ID, it) }
            taskTitle?.let { putExtra(EXTRA_TASK_TITLE, it) }
            putExtra(EXTRA_WORK_MINUTES, workMinutes)
            putExtra(EXTRA_BREAK_MINUTES, breakMinutes)
            putExtra(EXTRA_LOCKED, locked)
        }
    }
}
