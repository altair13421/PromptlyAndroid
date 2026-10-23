package com.promptly.app.util

import android.content.Context
import android.content.res.Configuration
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import com.promptly.app.data.model.RecurrenceRule
import com.promptly.app.data.model.RecurrenceRule.Frequency
import com.promptly.app.data.model.RecurrenceRule.WeekdayMask

/**
 * Time-zone-aware utilities used throughout the app.
 *
 *  - Today range calculation (in UTC millis for Room queries)
 *  - Pretty-printing ("Today, 3:00 PM", "Tomorrow, 8:00 AM", "Mon, Mar 5, 9:30 AM")
 *  - Computing the next occurrence of a recurring reminder
 */
@Singleton
class TimeUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val deviceTz: TimeZone get() = TimeZone.getDefault()

    /**
     * Returns [start, end) of "today" in the user's timezone, expressed as UTC
     * epoch millis. Suitable for passing to ReminderDao.observeDueBetween.
     */
    fun todayRangeUtc(): Pair<Long, Long> {
        val cal = Calendar.getInstance(deviceTz).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return start to cal.timeInMillis
    }

    /**
     * Format a trigger time for display in lists / detail screen.
     *
     * Examples:
     *   Today, 3:00 PM
     *   Tomorrow, 8:00 AM
     *   Mon, Mar 5, 9:30 AM
     *   Mar 5, 2026, 9:30 AM   (more than a week out or in a different year)
     */
    fun formatTrigger(atMillis: Long): String {
        val now = Calendar.getInstance(deviceTz)
        val then = Calendar.getInstance(deviceTz).apply { timeInMillis = atMillis }

        val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)

        val tomorrow = Calendar.getInstance(deviceTz).apply { add(Calendar.DAY_OF_MONTH, 1) }
        val isTomorrow = tomorrow.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
                tomorrow.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)

        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dayPart = when {
            sameDay -> "Today"
            isTomorrow -> "Tomorrow"
            // within a week
            (then.timeInMillis - now.timeInMillis) in 0..(7L * 24 * 60 * 60 * 1000) ->
                SimpleDateFormat("EEE", Locale.getDefault()).format(Date(atMillis))
            then.get(Calendar.YEAR) == now.get(Calendar.YEAR) ->
                SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(atMillis))
            else ->
                SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(atMillis))
        }
        return "$dayPart, ${timeFmt.format(Date(atMillis))}"
    }

    /**
     * Short relative time, e.g. "in 5m", "in 2h", "in 3d", "2h ago".
     */
    fun relativeTime(atMillis: Long, now: Long = System.currentTimeMillis()): String {
        val diff = atMillis - now
        val abs = kotlin.math.abs(diff)
        val sec = abs / 1000
        val min = sec / 60
        val hr = min / 60
        val day = hr / 24
        val suffix = if (diff >= 0) "in " else " ago"
        val body = when {
            min < 1 -> "now"
            hr < 1 -> "${min}m"
            day < 1 -> "${hr}h"
            else -> "${day}d"
        }
        return if (min < 1) body else "$suffix$body"
    }

    /**
     * Compute the next occurrence of a recurring reminder.
     *
     *  - For DAILY/CUSTOM_DAYS: triggerAt + interval days
     *  - For WEEKLY:            next matching weekday in the mask, interval weeks later
     *  - For MONTHLY:           same day-of-month, interval months later
     *
     * Returns null if [rule.endDate] or [rule.maxOccurrences] is exceeded.
     */
    fun nextOccurrence(
        fromMillis: Long,
        rule: RecurrenceRule,
        now: Long = System.currentTimeMillis()
    ): Long? {
        if (!rule.isRepeating) return null

        val start = Calendar.getInstance(deviceTz).apply {
            timeInMillis = if (fromMillis > now) fromMillis else now
        }

        var candidate: Long
        var attempts = 0
        do {
            attempts++
            if (attempts > 365) return null  // safety valve

            when (rule.frequency) {
                Frequency.DAILY, Frequency.CUSTOM_DAYS ->
                    start.add(Calendar.DAY_OF_MONTH, rule.interval)

                Frequency.WEEKLY -> {
                    // Skip 7*interval days, then align to next matching weekday.
                    start.add(Calendar.DAY_OF_MONTH, 7 * rule.interval)
                    val mask = if (rule.weekdays == 0) WeekdayMask.ALL else rule.weekdays
                    while ((1 shl (start.get(Calendar.DAY_OF_WEEK) - 1)) and mask == 0) {
                        start.add(Calendar.DAY_OF_MONTH, 1)
                    }
                }

                Frequency.MONTHLY ->
                    start.add(Calendar.MONTH, rule.interval)

                Frequency.NONE -> return null
            }
            candidate = start.timeInMillis
        } while (candidate <= now)

        // Check end conditions.
        rule.endDate?.let { if (candidate > it) return null }
        return candidate
    }

    /**
     * True if the device is currently in dark mode.
     */
    fun isSystemInDarkMode(): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }
}
