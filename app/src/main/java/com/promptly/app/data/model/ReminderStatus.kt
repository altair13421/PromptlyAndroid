package com.promptly.app.data.model

import kotlinx.serialization.Serializable

/**
 * Lifecycle status of a reminder.
 */
@Serializable
enum class ReminderStatus {
    /** Scheduled but not yet fired. */
    PENDING,

    /** Fired once, will fire again on the next recurrence. */
    FIRED,

    /** Acknowledged by the user via Done button. */
    DONE,

    /** Snoozed — will fire again shortly. */
    SNOOZED,

    /** User skipped this occurrence. */
    SKIPPED
}
