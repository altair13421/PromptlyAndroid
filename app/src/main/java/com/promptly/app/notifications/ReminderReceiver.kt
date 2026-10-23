package com.promptly.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.promptly.app.data.model.ReminderEntity
import com.promptly.app.data.model.ReminderStatus
import com.promptly.app.data.repository.ReminderRepository
import com.promptly.app.util.TimeUtils
import com.promptly.app.widget.WidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives the AlarmManager fire broadcast and:
 *   1. Pulls the reminder from the DB
 *   2. Fires the notification via [NotificationHelper]
 *   3. If recurring, advances triggerAt to the next occurrence + resets status
 *      to PENDING so the live-arm picks it up and re-arms the next alarm.
 *   4. Refreshes the home-screen widget.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var timeUtils: TimeUtils
    @Inject lateinit var widgetUpdater: WidgetUpdater

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(NotificationHelper.EXTRA_REMINDER_ID, -1L)
        if (id < 0) return

        // Acquire a temporary wakelock so the device stays awake long enough to
        // post the notification. The system gives us a goAsync window; we use a
        // coroutine to do the DB work and rely on the notification system to
        // keep the device up while playing sound.
        val pending = goAsync()

        scope.launch {
            try {
                val reminder = repository.getById(id) ?: return@launch
                notificationHelper.fire(reminder)

                if (reminder.recurrence.isRepeating) {
                    val next = timeUtils.nextOccurrence(
                        fromMillis = reminder.triggerAt,
                        rule = reminder.recurrence,
                        now = System.currentTimeMillis()
                    )
                    if (next == null) {
                        repository.update(
                            reminder.copy(
                                status = ReminderStatus.DONE,
                                lastFiredAt = System.currentTimeMillis()
                            )
                        )
                    } else {
                        repository.update(
                            reminder.copy(
                                triggerAt = next,
                                status = ReminderStatus.PENDING,
                                lastFiredAt = System.currentTimeMillis(),
                                occurrenceCount = reminder.occurrenceCount + 1
                            )
                        )
                    }
                } else {
                    repository.update(
                        reminder.copy(
                            status = ReminderStatus.FIRED,
                            lastFiredAt = System.currentTimeMillis()
                        )
                    )
                }

                widgetUpdater.refresh()
            } finally {
                pending.finish()
            }
        }
    }
}
