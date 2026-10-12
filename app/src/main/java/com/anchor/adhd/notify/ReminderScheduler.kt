package com.anchor.adhd.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.anchor.adhd.data.db.AnchorDatabase
import com.anchor.adhd.data.prefs.ReminderSettings
import com.anchor.adhd.data.prefs.UserPreferences
import com.anchor.adhd.domain.ReminderQuietHours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object ReminderScheduler {
    private const val REQ_BLOCK_BASE = 10_000
    private const val REQ_BLOCK_SLOTS = 1000
    private const val REQ_ASSIGN_BASE = 20_000
    private const val REQ_ASSIGN_SLOTS = 1000
    private const val REQ_MORNING = 30_000
    private const val REQ_SHUTDOWN = 30_001
    private const val REQ_DUE_BASE = 40_000
    private const val REQ_DUE_SLOTS = 1000

    private val lock = Mutex()

    suspend fun rescheduleAll(context: Context) = withContext(Dispatchers.IO) {
        lock.withLock { rescheduleAllLocked(context) }
    }

    private suspend fun rescheduleAllLocked(context: Context) {
        com.anchor.adhd.widget.WidgetUpdater.updateAll(context)
        val prefs = UserPreferences(context)
        val settings = prefs.getReminderSettings()
        val planning = prefs.getPlanningSettings()
        cancelAll(context)
        if (!settings.enabled) return

        val db = AnchorDatabase.getInstance(context)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        if (settings.blockReminders) {
            val tasks = db.taskDao().observeScheduledForDay(dayStart, dayEnd).first()
            tasks.forEach { task ->
                val start = task.scheduledStartMillis ?: return@forEach
                if (start <= now) return@forEach
                scheduleAt(context, blockRequestCode(task.id, 0), start - 10 * 60_000L, settings) {
                    ReminderReceiver.blockIntent(context, task.id, task.title, minutesBefore = 10)
                }
                scheduleAt(context, blockRequestCode(task.id, 1), start, settings) {
                    ReminderReceiver.blockIntent(context, task.id, task.title, minutesBefore = 0)
                }
                val end = start + task.durationMinutes * 60_000L
                scheduleAt(context, blockRequestCode(task.id, 2), end, settings) {
                    ReminderReceiver.transitionIntent(context, task.id, task.title)
                }
            }
        }

        if (settings.assignmentReminders) {
            val assignments = db.assignmentDao().observeOpen().first()
            assignments.forEach { assignment ->
                val due = assignment.dueAtMillis
                listOf(48L to "48h", 24L to "24h", 0L to "today").forEachIndexed { offset, (hours, label) ->
                    val trigger = due - hours * 3_600_000L
                    if (trigger > now) {
                        scheduleAt(
                            context,
                            assignmentRequestCode(assignment.id, offset),
                            trigger,
                            settings
                        ) {
                            ReminderReceiver.assignmentIntent(context, assignment.id, assignment.title, label, offset)
                        }
                    }
                }
            }
        }

        if (settings.morningReplan) {
            scheduleNextMorning(context, settings, now)
        }

        // Task due date reminders
        val activeTasks = db.taskDao().observeActiveTasks().first()
        activeTasks.forEach { task ->
            val due = task.dueAtMillis ?: return@forEach
            if (due <= now) return@forEach
            scheduleAt(context, dueRequestCode(task.id), due, settings) {
                ReminderReceiver.dueTaskIntent(context, task.id, task.title)
            }
        }

        if (planning.shutdownNotificationEnabled) {
            scheduleNextShutdown(context, planning.shutdownHour, settings, now)
        }
    }

    fun scheduleNextShutdown(
        context: Context,
        shutdownHour: Int,
        settings: ReminderSettings,
        now: Long = System.currentTimeMillis()
    ) {
        val zone = ZoneId.systemDefault()
        val hour = shutdownHour.coerceIn(17, 23)
        val todayShutdown = LocalDate.now(zone).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        val trigger = if (todayShutdown > now) todayShutdown else {
            LocalDate.now(zone).plusDays(1).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        }
        scheduleAt(context, REQ_SHUTDOWN, trigger, settings) {
            ReminderReceiver.shutdownIntent(context)
        }
    }

    fun scheduleNextMorning(context: Context, settings: ReminderSettings, now: Long = System.currentTimeMillis()) {
        val zone = ZoneId.systemDefault()
        val nextMorning = LocalDate.now(zone).atTime(settings.morningReplanHour, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val trigger = if (nextMorning > now) nextMorning else {
            LocalDate.now(zone).plusDays(1).atTime(settings.morningReplanHour, 0)
                .atZone(zone).toInstant().toEpochMilli()
        }
        scheduleAt(context, REQ_MORNING, trigger, settings) {
            ReminderReceiver.morningReplanIntent(context)
        }
    }

    fun blockRequestCode(taskId: Long, kind: Int): Int {
        val slot = (taskId % REQ_BLOCK_SLOTS).toInt()
        return REQ_BLOCK_BASE + slot * 3 + kind
    }

    fun assignmentRequestCode(assignmentId: Long, offset: Int): Int {
        val slot = (assignmentId % REQ_ASSIGN_SLOTS).toInt()
        return REQ_ASSIGN_BASE + slot * 3 + offset
    }

    fun dueRequestCode(taskId: Long): Int {
        val slot = (taskId % REQ_DUE_SLOTS).toInt()
        return REQ_DUE_BASE + slot
    }

    private fun scheduleAt(
        context: Context,
        requestCode: Int,
        triggerAtMillis: Long,
        settings: ReminderSettings,
        intentBuilder: () -> Intent
    ) {
        val adjusted = deferOutOfQuietHours(triggerAtMillis, settings) ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = intentBuilder()
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, adjusted, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, adjusted, pi)
        }
    }

    /** Shift past quiet hours; never permanently drop. */
    private fun deferOutOfQuietHours(triggerAtMillis: Long, settings: ReminderSettings): Long? {
        var candidate = triggerAtMillis
        repeat(168) {
            if (!isQuietHours(candidate, settings)) return candidate
            candidate = nextNonQuietMillis(candidate, settings)
        }
        return nextNonQuietMillis(triggerAtMillis, settings)
    }

    private fun nextNonQuietMillis(triggerAtMillis: Long, settings: ReminderSettings): Long {
        val zone = ZoneId.systemDefault()
        val zdt = Instant.ofEpochMilli(triggerAtMillis).atZone(zone)
        val endHour = settings.quietHoursEnd
        var target = zdt.toLocalDate().atTime(endHour, 0).atZone(zone)
        if (!target.isAfter(zdt)) {
            target = target.plusDays(1)
        }
        return target.toInstant().toEpochMilli()
    }

    private fun isQuietHours(triggerAtMillis: Long, settings: ReminderSettings): Boolean {
        val zone = ZoneId.systemDefault()
        val hour = Instant.ofEpochMilli(triggerAtMillis).atZone(zone).hour
        return ReminderQuietHours.isQuietHour(hour, settings.quietHoursStart, settings.quietHoursEnd)
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        (REQ_BLOCK_BASE until REQ_BLOCK_BASE + REQ_BLOCK_SLOTS * 3).forEach { code ->
            cancelPending(context, am, code, ReminderReceiver.ACTION_BLOCK)
            cancelPending(context, am, code, ReminderReceiver.ACTION_TRANSITION)
        }
        (REQ_ASSIGN_BASE until REQ_ASSIGN_BASE + REQ_ASSIGN_SLOTS * 3).forEach { code ->
            cancelPending(context, am, code, ReminderReceiver.ACTION_ASSIGNMENT)
        }
        (REQ_DUE_BASE until REQ_DUE_BASE + REQ_DUE_SLOTS).forEach { code ->
            cancelPending(context, am, code, ReminderReceiver.ACTION_DUE_TASK)
        }
        cancelPending(context, am, REQ_MORNING, ReminderReceiver.ACTION_MORNING)
        cancelPending(context, am, REQ_SHUTDOWN, ReminderReceiver.ACTION_SHUTDOWN)
    }

    private fun cancelPending(context: Context, am: AlarmManager, requestCode: Int, action: String) {
        val intent = Intent(context, ReminderReceiver::class.java).apply { this.action = action }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pi?.let { am.cancel(it) }
    }
}
