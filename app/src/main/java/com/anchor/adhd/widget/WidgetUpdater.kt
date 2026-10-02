package com.anchor.adhd.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WidgetUpdater {
    fun updateAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(AnchorWidget::class.java).forEach { id ->
                AnchorWidget().update(context, id)
            }
        }
    }
}
