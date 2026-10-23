package com.promptly.app.data.db

import androidx.room.TypeConverter
import com.promptly.app.data.model.RecurrenceRule
import com.promptly.app.data.model.ReminderStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room type converters for our value types.
 *
 *  - [ReminderStatus]  -> stored as String
 *  - [RecurrenceRule]  -> stored as JSON string
 *
 * Add new converters here when you introduce new value types.
 */
class Converters {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // --- ReminderStatus ---

    @TypeConverter
    fun statusToString(status: ReminderStatus): String = status.name

    @TypeConverter
    fun stringToStatus(value: String): ReminderStatus =
        runCatching { ReminderStatus.valueOf(value) }.getOrDefault(ReminderStatus.PENDING)

    // --- RecurrenceRule ---

    @TypeConverter
    fun recurrenceToString(rule: RecurrenceRule): String = json.encodeToString(rule)

    @TypeConverter
    fun stringToRecurrence(value: String): RecurrenceRule =
        runCatching { json.decodeFromString<RecurrenceRule>(value) }
            .getOrDefault(RecurrenceRule())
}
