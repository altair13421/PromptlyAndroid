package com.promptly.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.promptly.app.widget.WidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles the "Done" action button on a fired notification.
 *
 *  - Marks the reminder done (which advances it to the next occurrence if recurring)
 *  - Cancels the notification
 *  - Refreshes the widget
 */
@AndroidEntryPoint
class DoneActionReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: com.promptly.app.data.repository.ReminderRepository
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var widgetUpdater: WidgetUpdater

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(NotificationHelper.EXTRA_REMINDER_ID, -1L)
        if (id < 0) return
        val pending = goAsync()

        scope.launch {
            try {
                repository.markDone(id)
                notificationHelper.cancel(id)
                widgetUpdater.refresh()
            } finally {
                pending.finish()
            }
        }
    }
}
