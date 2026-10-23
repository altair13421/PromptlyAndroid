package com.promptly.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.promptly.app.data.model.ReminderEntity
import com.promptly.app.data.repository.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arms AlarmManager exact alarms for every active (PENDING / SNOOZED) reminder.
 *
 *  - For each reminder, we schedule a single exact alarm via
 *    [AlarmManager.setAlarmClock] (which is the most reliable option for
 *    user-visible time-based alarms — Android will NOT defer it for Doze).
 *  - We also fall back to setExactAndAllowWhileIdle on devices that block
 *    setAlarmClock for non-default apps.
 *
 *  Watch strategy: [observeAndArm] subscribes to the active-reminders Flow and
 *  re-arms ALL alarms whenever the set changes. This is O(N) per change but N
 *  is small (user has at most dozens of active reminders), and the simplicity
 *  wins are large: any DB mutation anywhere flows through automatically.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    private val context: Context,
    private val repository: ReminderRepository,
    private val notificationHelper: NotificationHelper
) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Subscribe to the active-reminders Flow and re-arm alarms on every change.
     * Returns a Flow<Unit> that ticks once per re-arm — callers can collect it
     * to attach additional side-effects (e.g. widget refresh).
     *
     * Cold-flow design: the underlying subscription is started lazily by the
     * caller's `launchIn`. There is no internal launchIn here — avoids the
     * double-subscription bug where the upstream Room flow would be collected
     * twice.
     */
    fun observeAndArm(): Flow<Unit> = flow {
        repository.observeActive().collect { reminders ->
            reArmAll(reminders)
            emit(Unit)
        }
    }.distinctUntilChanged()

    /**
     * Re-arm every active reminder. Idempotent — cancels all existing alarms
     * first, then schedules fresh ones.
     *
     * Pass [reminders] explicitly if you already have them; otherwise they're
     * pulled from the repository. (Can't be a default param value because
     * `repository.getAllActive()` is suspend and Kotlin forbids suspend calls
     * in default parameter expressions.)
     */
    suspend fun reArmAll(reminders: List<ReminderEntity>? = null) {
        val active = reminders ?: repository.getAllActive()
        // Cancel all existing reminder alarms.
        for (r in active) {
            cancelAlarm(r.id)
        }
        // Schedule fresh.
        for (r in active) {
            schedule(r)
        }
    }

    /**
     * Schedule (or re-schedule) a single reminder's alarm.
     */
    private fun schedule(reminder: ReminderEntity) {
        if (reminder.triggerAt <= System.currentTimeMillis()) {
            // Already due — fire immediately rather than arming an alarm in the past.
            // The ReminderReceiver will handle firing the notification + advancing
            // recurrence.
            sendFireBroadcast(reminder)
            return
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.promptly.app.action.FIRE"
            putExtra(NotificationHelper.EXTRA_REMINDER_ID, reminder.id)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Use setAlarmClock — most reliable, shows an alarm indicator in the
        // status bar, and is exempt from Doze restrictions.
        try {
            val info = AlarmManager.AlarmClockInfo(reminder.triggerAt, null)
            alarmManager.setAlarmClock(info, pi)
        } catch (_: SecurityException) {
            // Fallback: setExactAndAllowWhileIdle (requires SCHEDULE_EXACT_ALARM on S+)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminder.triggerAt,
                        pi
                    )
                } else {
                    @Suppress("DEPRECATION")
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pi)
                }
            } catch (_: SecurityException) {
                // Last resort: inexact. May fire late but at least fires.
                alarmManager.set(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pi)
            }
        }
    }

    private fun cancelAlarm(reminderId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.promptly.app.action.FIRE"
        }
        val pi = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pi != null) alarmManager.cancel(pi)
    }

    private fun sendFireBroadcast(reminder: ReminderEntity) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.promptly.app.action.FIRE"
            putExtra(NotificationHelper.EXTRA_REMINDER_ID, reminder.id)
        }
        context.sendBroadcast(intent)
    }
}
