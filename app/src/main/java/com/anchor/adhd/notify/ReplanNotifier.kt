package com.anchor.adhd.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.anchor.adhd.MainActivity
import com.anchor.adhd.R

object ReplanNotifier {
    private const val CHANNEL_ID = "replan"
    private const val NOTIFICATION_ID = 2001
    private const val PI_REQUEST_CODE = 2001

    fun notifyReplanQueue(context: Context, count: Int) {
        if (count <= 0) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Replan", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context,
            PI_REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_REPLAN
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Replan queue")
            .setContentText("$count missed block${if (count == 1) "" else "s"} — tap to reschedule")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }
}
