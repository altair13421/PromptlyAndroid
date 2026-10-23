package com.promptly.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.promptly.app.data.model.RecurrenceRule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent app-level preferences, stored via Jetpack DataStore.
 *
 * Stored keys:
 *   - dark_mode          : "system" | "on" | "off"
 *   - dynamic_color      : Boolean (Material You)
 *   - default_ringtone   : String URI (content:// or promptly://raw/<name>)
 *   - vibrate            : Boolean
 *   - default_recurrence : JSON-encoded [RecurrenceRule] for new reminders
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "promptly_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    enum class DarkMode { SYSTEM, ON, OFF }

    val darkMode: Flow<DarkMode> = context.dataStore.data
        .map { p ->
            when (p[DARK_MODE]) {
                "on" -> DarkMode.ON
                "off" -> DarkMode.OFF
                else -> DarkMode.SYSTEM
            }
        }

    val dynamicColor: Flow<Boolean> = context.dataStore.data
        .map { it[DYNAMIC_COLOR] ?: true }

    val defaultRingtoneUri: Flow<String?> = context.dataStore.data
        .map { it[DEFAULT_RINGTONE] }

    val vibrate: Flow<Boolean> = context.dataStore.data
        .map { it[VIBRATE] ?: true }

    val defaultRecurrence: Flow<RecurrenceRule> = context.dataStore.data
        .map { p ->
            p[DEFAULT_RECURRENCE]?.let {
                runCatching { json.decodeFromString<RecurrenceRule>(it) }.getOrNull()
            } ?: RecurrenceRule()
        }

    suspend fun setDarkMode(mode: DarkMode) {
        context.dataStore.edit { it[DARK_MODE] = mode.name.lowercase() }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    suspend fun setDefaultRingtoneUri(uri: String?) {
        context.dataStore.edit {
            if (uri == null) it.remove(DEFAULT_RINGTONE)
            else it[DEFAULT_RINGTONE] = uri
        }
    }

    suspend fun setVibrate(enabled: Boolean) {
        context.dataStore.edit { it[VIBRATE] = enabled }
    }

    suspend fun setDefaultRecurrence(rule: RecurrenceRule) {
        context.dataStore.edit { it[DEFAULT_RECURRENCE] = json.encodeToString(rule) }
    }

    companion object {
        private val DARK_MODE = stringPreferencesKey("dark_mode")
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val DEFAULT_RINGTONE = stringPreferencesKey("default_ringtone")
        private val VIBRATE = booleanPreferencesKey("vibrate")
        private val DEFAULT_RECURRENCE = stringPreferencesKey("default_recurrence")
    }
}
