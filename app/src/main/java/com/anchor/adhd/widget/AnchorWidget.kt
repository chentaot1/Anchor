package com.anchor.adhd.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.anchor.adhd.MainActivity
import com.anchor.adhd.data.db.AnchorDatabase
import com.anchor.adhd.domain.GroveLevel
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

class AnchorWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AnchorDatabase.getInstance(context)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val scheduled = db.taskDao().observeScheduledForDay(start, end).first()
        val weekStart = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val weeklyFocus = db.focusSessionDao().observeCompletedCountBetween(weekStart, end).first()
        val vitality = GroveLevel.vitalityLabel(GroveLevel.vitalityStageForWeeklySessions(weeklyFocus))
        val next = scheduled.firstOrNull()
        val title = next?.title ?: "Your harbor is clear"
        val detail = next?.let { "${it.durationMinutes} min" } ?: "Open Grove"
        val nextTaskId = next?.id

        provideContent {
            WidgetContent(context, title, detail, nextTaskId, vitality)
        }
    }
}

@Composable
private fun WidgetContent(
    context: Context,
    title: String,
    detail: String,
    nextTaskId: Long?,
    vitalityLabel: String
) {
    val openApp = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_GROVE)
        }
    )
    val startFocus = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_START_FOCUS, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            nextTaskId?.let { putExtra(MainActivity.EXTRA_TASK_ID, it) }
        }
    )
    val onSurface = ColorProvider(Color(0xFFE8EDF3), Color(0xFFE8EDF3))
    val muted = ColorProvider(Color(0xFFA8B4C0), Color(0xFFA8B4C0))
    val action = ColorProvider(Color(0xFFFFAB6B), Color(0xFFFFAB6B))

    Column(
        modifier = GlanceModifier
            .background(ColorProvider(Color(0xFF1A2836), Color(0xFF1A2836)))
            .padding(12.dp)
            .clickable(openApp)
    ) {
        Text("Grove · $vitalityLabel", style = TextStyle(fontSize = 12.sp, color = muted))
        Text(title, style = TextStyle(fontSize = 16.sp, color = onSurface))
        Text(detail, style = TextStyle(fontSize = 12.sp, color = muted))
        Row(modifier = GlanceModifier.padding(top = 8.dp)) {
            Text("Start", style = TextStyle(fontSize = 12.sp, color = action), modifier = GlanceModifier.clickable(startFocus))
        }
    }
}

class AnchorWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AnchorWidget()
}
