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
 * Re-arms all reminder alarms after device boot, app update, or unlock.
 *
 * AlarmManager alarms do NOT survive device reboots — we have to re-establish
 * them. We use a coroutine to pull all active reminders + call ReminderScheduler
 * which arms a fresh [AlarmManager.setAlarmClock] for each.
 *
 * We also fire overdue reminders (those whose triggerAt is in the past) so the
 * user gets a notification for anything missed while the phone was off.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var repository: com.promptly.app.data.repository.ReminderRepository
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var widgetUpdater: WidgetUpdater

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                // 1. Re-arm all alarms for active reminders.
                scheduler.reArmAll()

                // 2. Fire any reminders that became overdue while the device was off.
                val overdue = repository.getOverdue()
                for (r in overdue) {
                    notificationHelper.fire(r)
                    // For recurring reminders, advance to next occurrence so the
                    // scheduler doesn't immediately re-fire.
                    if (r.recurrence.isRepeating) {
                        // markDone handles "advance to next occurrence"
                        repository.markDone(r.id)
                        // After markDone the reminder is PENDING again with the next
                        // triggerAt; if no more occurrences, status becomes DONE.
                    } else {
                        repository.markDone(r.id)
                    }
                }

                widgetUpdater.refresh()
            } finally {
                pending.finish()
            }
        }
    }
}
