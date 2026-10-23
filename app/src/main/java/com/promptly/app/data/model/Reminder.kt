package com.promptly.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A single reminder record. Persisted in Room as the [ReminderEntity].
 *
 * Time is stored as epoch millis in UTC. UI conversion happens at the boundary.
 */
@Serializable
@Entity(
    tableName = "reminders",
    indices = [Index("triggerAt"), Index("status")]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val title: String,
    val notes: String? = null,

    /** When this reminder should next fire (epoch millis, UTC). */
    val triggerAt: Long,

    /** Time zone the user originally picked ( Olson ID, e.g. "America/New_York" ). */
    val timeZone: String,

    /** Status of THIS occurrence. */
    val status: ReminderStatus = ReminderStatus.PENDING,

    /** Recurrence rule (serialized JSON in DB). */
    val recurrence: RecurrenceRule = RecurrenceRule(),

    /** URI to attached picture, relative to app's pictures dir or a content:// URI. */
    val pictureUri: String? = null,

    /** User-chosen ringtone URI or app-private imported audio path. null = use app default. */
    val ringtoneUri: String? = null,

    /** Per-reminder override for vibration. null = use app default. */
    val vibrate: Boolean? = null,

    /** Creation + audit fields. */
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    /** When the last occurrence fired (for "missed reminder" handling). */
    val lastFiredAt: Long? = null,

    /** How many times this recurring reminder has fired so far. */
    val occurrenceCount: Int = 0
) {
    /** True if this reminder should be considered active (visible in default list). */
    val isActive: Boolean get() = status == ReminderStatus.PENDING || status == ReminderStatus.SNOOZED
}
