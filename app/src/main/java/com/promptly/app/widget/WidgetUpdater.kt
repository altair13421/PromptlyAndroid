package com.promptly.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.promptly.app.data.repository.ReminderRepository
import com.promptly.app.util.TimeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Triggers a self-broadcast that updates all Promptly widgets.
 *
 * The actual RemoteViews rendering happens in [PromptlyWidgetProvider.onUpdate]
 * (and onUpdate is invoked by AppWidgetManager as a result of our broadcast).
 */
@Singleton
class WidgetUpdater @Inject constructor(
    private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun refresh() {
        scope.launch {
            val mgr = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context, PromptlyWidgetProvider::class.java)
            val ids = mgr.getAppWidgetIds(provider)
            if (ids.isNotEmpty()) {
                val intent = Intent(context, PromptlyWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
