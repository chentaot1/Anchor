package com.anchor.adhd.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.anchor.adhd.MainActivity
import com.anchor.adhd.R
import com.anchor.adhd.data.db.AnchorDatabase
import com.anchor.adhd.data.prefs.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class ReminderReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_BLOCK -> show(
                context,
                intent.getIntExtra(EXTRA_NOTIF_ID, 3001),
                intent.getStringExtra(EXTRA_TITLE) ?: "Block starting",
                intent.getStringExtra(EXTRA_BODY) ?: "Open Anchor",
                taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L),
                openReplan = false,
                openFocus = true
            )
            ACTION_ASSIGNMENT -> show(
                context,
                intent.getIntExtra(EXTRA_NOTIF_ID, 3002),
                intent.getStringExtra(EXTRA_TITLE) ?: "Assignment due",
                intent.getStringExtra(EXTRA_BODY) ?: "Open Anchor",
                taskId = -1L,
                openReplan = false,
                openFocus = false
            )
            ACTION_DUE_TASK -> show(
                context,
                intent.getIntExtra(EXTRA_NOTIF_ID, 3006),
                intent.getStringExtra(EXTRA_TITLE) ?: "Task due today",
                intent.getStringExtra(EXTRA_BODY) ?: "Open Anchor",
                taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L),
                openReplan = false,
                openFocus = true
            )
            ACTION_MORNING -> {
                val pending = goAsync()
                scope.launch {
                    try {
                        val db = AnchorDatabase.getInstance(context)
                        val planRepo = com.anchor.adhd.data.repository.PlanRepository(
                            db.taskDao(),
                            db.routineDao(),
                            db.calendarEventDao(),
                            db.assignmentDao(),
                            db.replanDao(),
                            db
                        )
                        planRepo.queueReplanForMissedTasks(
                            LocalDate.now(),
                            excludeTaskId = com.anchor.adhd.domain.FocusSessionGuard.lockedTaskId()
                        )
                        show(
                            context,
                            3003,
                            "Morning check-in",
                            "Tap to start your morning ritual",
                            taskId = -1L,
                            openReplan = false,
                            openFocus = false,
                            openMorningRitual = true
                        )
                        val settings = UserPreferences(context).getReminderSettings()
                        ReminderScheduler.scheduleNextMorning(context, settings)
                    } finally {
                        pending.finish()
                    }
                }
            }
            ACTION_SHUTDOWN -> show(
                context,
                intent.getIntExtra(EXTRA_NOTIF_ID, 3005),
                "Shutdown time",
                intent.getStringExtra(EXTRA_BODY) ?: "Wrap up open loops for today",
                taskId = -1L,
                openReplan = false,
                openFocus = false,
                openShutdown = true
            )
            ACTION_TRANSITION -> show(
                context,
                intent.getIntExtra(EXTRA_NOTIF_ID, 3004),
                "Block ended",
                intent.getStringExtra(EXTRA_BODY) ?: "Next block or break",
                taskId = -1L,
                openReplan = true,
                openFocus = false
            )
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val pending = goAsync()
                scope.launch {
                    try {
                        ReminderScheduler.rescheduleAll(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private fun show(
        context: Context,
        id: Int,
        title: String,
        body: String,
        taskId: Long,
        openReplan: Boolean,
        openFocus: Boolean,
        openMorningRitual: Boolean = false,
        openShutdown: Boolean = false
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                when {
                    openMorningRitual -> action = MainActivity.ACTION_OPEN_MORNING_RITUAL
                    openShutdown -> action = MainActivity.ACTION_SHUTDOWN
                    openReplan -> action = MainActivity.ACTION_OPEN_REPLAN
                    openFocus && taskId >= 0 -> {
                        putExtra(MainActivity.EXTRA_START_FOCUS, true)
                        putExtra(MainActivity.EXTRA_TASK_ID, taskId)
                    }
                }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        nm.notify(
            id,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }

    companion object {
        const val ACTION_BLOCK = "com.anchor.adhd.reminder.BLOCK"
        const val ACTION_ASSIGNMENT = "com.anchor.adhd.reminder.ASSIGNMENT"
        const val ACTION_DUE_TASK = "com.anchor.adhd.reminder.DUE_TASK"
        const val ACTION_MORNING = "com.anchor.adhd.reminder.MORNING"
        const val ACTION_SHUTDOWN = "com.anchor.adhd.reminder.SHUTDOWN"
        const val ACTION_TRANSITION = "com.anchor.adhd.reminder.TRANSITION"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_NOTIF_ID = "notif_id"
        private const val CHANNEL_ID = "reminders"

        fun blockIntent(context: Context, taskId: Long, title: String, minutesBefore: Int): Intent =
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_BLOCK
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_NOTIF_ID, (3000 + taskId % 10000 + minutesBefore).toInt())
                putExtra(
                    EXTRA_TITLE,
                    if (minutesBefore > 0) "In $minutesBefore min · $title" else "Now · $title"
                )
                putExtra(EXTRA_BODY, "Tap to start focus")
            }

        fun assignmentIntent(context: Context, id: Long, title: String, label: String, offset: Int): Intent =
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_ASSIGNMENT
                putExtra(EXTRA_NOTIF_ID, (3100 + (id % 3333) * 3 + offset).toInt())
                putExtra(EXTRA_TITLE, "Due $label")
                putExtra(EXTRA_BODY, title)
            }

        fun morningReplanIntent(context: Context): Intent =
            Intent(context, ReminderReceiver::class.java).apply { action = ACTION_MORNING }

        fun dueTaskIntent(context: Context, taskId: Long, title: String): Intent =
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_DUE_TASK
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_NOTIF_ID, (3300 + taskId % 10000).toInt())
                putExtra(EXTRA_TITLE, "Task due today: $title")
                putExtra(EXTRA_BODY, "Tap to start focus")
            }

        fun shutdownIntent(context: Context): Intent =
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_SHUTDOWN
                putExtra(EXTRA_NOTIF_ID, 3005)
                putExtra(EXTRA_BODY, "Tap to run shutdown and clear open loops")
            }

        fun transitionIntent(context: Context, taskId: Long, title: String): Intent =
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_TRANSITION
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_NOTIF_ID, (3200 + taskId % 10000).toInt())
                putExtra(EXTRA_BODY, "$title finished · what's next?")
            }
    }
}
