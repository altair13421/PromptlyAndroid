package com.promptly.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.promptly.app.MainActivity
import com.promptly.app.R
import com.promptly.app.data.repository.ReminderRepository
import com.promptly.app.util.TimeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Home-screen widget that shows the next 3 active reminders.
 *
 * RemoteViews is restrictive (only TextView/ImageView/etc., no Compose), so we
 * pre-render the next three rows in Kotlin and ship them via setText.
 *
 * Updates are triggered by:
 *   - The system every [updatePeriodMillis] (set in widget_info.xml = 30 min)
 *   - The [WidgetUpdater] whenever a reminder is created/edited/deleted
 *   - On widget added by the user
 */
@AndroidEntryPoint
class PromptlyWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var timeUtils: TimeUtils

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            renderOne(context, mgr, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Self-broadcast from WidgetUpdater — refresh all instances.
        if (intent.action == "com.promptly.app.WIDGET_UPDATE" ||
            intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE
        ) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, PromptlyWidgetProvider::class.java))
            for (id in ids) renderOne(context, mgr, id)
        }
    }

    private fun renderOne(context: Context, mgr: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_reminder)
        // Open the app when tapped.
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widgetTitle, pi)

        scope.launch {
            val next = runCatching { repository.getNextActive(3) }.getOrDefault(emptyList())
            if (next.isEmpty()) {
                views.setViewVisibility(R.id.widgetItem1, android.view.View.GONE)
                views.setViewVisibility(R.id.widgetItem2, android.view.View.GONE)
                views.setViewVisibility(R.id.widgetItem3, android.view.View.GONE)
                views.setViewVisibility(R.id.widgetEmpty, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widgetEmpty, android.view.View.GONE)
                val ids = listOf(R.id.widgetItem1, R.id.widgetItem2, R.id.widgetItem3)
                for (i in ids.indices) {
                    if (i < next.size) {
                        val r = next[i]
                        views.setViewVisibility(ids[i], android.view.View.VISIBLE)
                        views.setTextViewText(
                            ids[i],
                            "${timeUtils.formatTrigger(r.triggerAt)} — ${r.title}"
                        )
                    } else {
                        views.setViewVisibility(ids[i], android.view.View.GONE)
                    }
                }
            }
            mgr.updateAppWidget(widgetId, views)
        }
    }
}
