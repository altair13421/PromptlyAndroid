package com.promptly.app.ui.screens.create

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptly.app.data.model.RecurrenceRule
import com.promptly.app.data.model.ReminderEntity
import com.promptly.app.data.model.ReminderStatus
import com.promptly.app.data.repository.ReminderRepository
import com.promptly.app.data.ringtone.RingtoneHelper
import com.promptly.app.data.storage.PictureStorage
import com.promptly.app.util.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject

data class CreateReminderUiState(
    val id: Long? = null,           // null = creating new, non-null = editing
    val title: String = "",
    val notes: String = "",
    val triggerAt: Long = defaultTriggerTime(),
    val recurrence: RecurrenceRule = RecurrenceRule(),
    val pictureUri: String? = null,
    val ringtoneUri: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

private fun defaultTriggerTime(): Long {
    // Default = next round hour from now.
    val cal = Calendar.getInstance().apply {
        add(Calendar.HOUR_OF_DAY, 1)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

@HiltViewModel
class CreateReminderViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val pictureStorage: PictureStorage,
    val ringtoneHelper: RingtoneHelper,
    private val timeUtils: TimeUtils
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateReminderUiState())
    val uiState: StateFlow<CreateReminderUiState> = _uiState.asStateFlow()

    fun ringtoneTitle(uri: String): String = ringtoneHelper.titleFor(uri)

    fun loadForEdit(id: Long) {
        _uiState.update { it.copy(id = id, isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val reminder = repository.getById(id)
                    ?: throw IllegalStateException("This reminder no longer exists.")
                _uiState.value = CreateReminderUiState(
                    id = reminder.id,
                    title = reminder.title,
                    notes = reminder.notes.orEmpty(),
                    triggerAt = reminder.triggerAt,
                    recurrence = reminder.recurrence,
                    pictureUri = reminder.pictureUri,
                    ringtoneUri = reminder.ringtoneUri
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Could not load reminder.")
                }
            }
        }
    }

    fun onTitleChange(v: String) = _uiState.update { it.copy(title = v) }
    fun onNotesChange(v: String) = _uiState.update { it.copy(notes = v) }

    fun onTimeChange(hour: Int, minute: Int) {
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = _uiState.value.triggerAt
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        _uiState.update { it.copy(triggerAt = cal.timeInMillis) }
    }

    fun onDateChange(year: Int, month: Int, day: Int) {
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = _uiState.value.triggerAt
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
        }
        _uiState.update { it.copy(triggerAt = cal.timeInMillis) }
    }

    fun onRecurrenceChange(rule: RecurrenceRule) = _uiState.update { it.copy(recurrence = rule) }
    fun onRingtoneChange(uri: String?) = _uiState.update { it.copy(ringtoneUri = uri) }

    fun onCustomRingtonePicked(uri: Uri) {
        viewModelScope.launch {
            try {
                val path = ringtoneHelper.importCustomTone(uri)
                _uiState.update { it.copy(ringtoneUri = path, errorMessage = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Could not import the audio file.")
                }
            }
        }
    }

    fun onPicturePicked(uri: Uri) {
        viewModelScope.launch {
            try {
                val path = pictureStorage.importFromUri(uri)
                _uiState.update { it.copy(pictureUri = path, errorMessage = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Could not attach the picture.")
                }
            }
        }
    }

    fun createCameraCaptureFile(): File = pictureStorage.createCaptureFile()

    fun onPictureCaptured(path: String) {
        _uiState.update { it.copy(pictureUri = path, errorMessage = null) }
    }

    fun onPictureCaptureCancelled(path: String) {
        viewModelScope.launch { pictureStorage.delete(path) }
    }

    fun onPictureRemoved() {
        val path = _uiState.value.pictureUri ?: return
        viewModelScope.launch {
            pictureStorage.delete(path)
            _uiState.update { it.copy(pictureUri = null, errorMessage = null) }
        }
    }

    fun onSave(onSaved: () -> Unit) {
        val s = _uiState.value
        if (s.isLoading || s.isSaving) return
        if (s.title.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Add a title first") }
            return
        }
        if (s.id == null && s.triggerAt <= System.currentTimeMillis()) {
            _uiState.update { it.copy(errorMessage = "Pick a future time") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val existing = s.id?.let { id ->
                    repository.getById(id)
                        ?: throw IllegalStateException("This reminder no longer exists.")
                }
                if (s.triggerAt <= System.currentTimeMillis() &&
                    (existing == null || existing.triggerAt != s.triggerAt)
                ) {
                    _uiState.update {
                        it.copy(isSaving = false, errorMessage = "Pick a future time")
                    }
                    return@launch
                }
                val entity = if (existing != null) {
                    existing.copy(
                        title = s.title.trim(),
                        notes = s.notes.trim().ifBlank { null },
                        triggerAt = s.triggerAt,
                        timeZone = TimeZone.getDefault().id,
                        recurrence = s.recurrence,
                        pictureUri = s.pictureUri,
                        ringtoneUri = s.ringtoneUri
                    )
                } else {
                    ReminderEntity(
                        title = s.title.trim(),
                        notes = s.notes.trim().ifBlank { null },
                        triggerAt = s.triggerAt,
                        timeZone = TimeZone.getDefault().id,
                        status = ReminderStatus.PENDING,
                        recurrence = s.recurrence,
                        pictureUri = s.pictureUri,
                        ringtoneUri = s.ringtoneUri
                    )
                }
                if (existing == null) repository.create(entity)
                else repository.update(entity)
                _uiState.update { it.copy(isSaving = false) }
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = e.message ?: "Could not save reminder."
                    )
                }
            }
        }
    }
}
