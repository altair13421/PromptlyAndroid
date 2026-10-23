package com.promptly.app.data.model

import kotlinx.serialization.Serializable

/**
 * Recurrence rule for a reminder. Designed to be human-readable and serializable,
 * not a full iCalendar RRULE implementation. Covers the 95% use cases.
 *
 *  - NONE         : fires once
 *  - DAILY        : fires every [interval] days at the same time
 *  - WEEKLY       : fires every [interval] weeks on the days in [weekdays]
 *                   (bitmask: 1=Sun, 2=Mon, 4=Tue, 8=Wed, 16=Thu, 32=Fri, 64=Sat)
 *  - MONTHLY      : fires every [interval] months on the same day-of-month
 *  - CUSTOM_DAYS  : fires every [interval] days (alias for daily, used when the
 *                   user picks "every N days" from the custom picker)
 */
@Serializable
data class RecurrenceRule(
    val frequency: Frequency = Frequency.NONE,
    val interval: Int = 1,
    val weekdays: Int = 0,
    val endDate: Long? = null,        // epoch millis, null = forever
    val maxOccurrences: Int? = null   // optional cap
) {
    enum class Frequency { NONE, DAILY, WEEKLY, MONTHLY, CUSTOM_DAYS }

    /** True if the reminder ever fires more than once. */
    val isRepeating: Boolean get() = frequency != Frequency.NONE

    /** Convenience masks for weekday bitfield. */
    object WeekdayMask {
        const val SUN = 1
        const val MON = 2
        const val TUE = 4
        const val WED = 8
        const val THU = 16
        const val FRI = 32
        const val SAT = 64
        const val WEEKDAYS = MON or TUE or WED or THU or FRI
        const val WEEKEND = SUN or SAT
        const val ALL = 127
    }
}
