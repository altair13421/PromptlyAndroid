package com.promptly.app.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptly.app.data.model.ReminderEntity
import com.promptly.app.data.repository.ReminderRepository
import com.promptly.app.util.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReminderDetailUiState(
    val reminder: ReminderEntity? = null,
    val isLoading: Boolean = true,
    val showDeleteConfirm: Boolean = false
)

@HiltViewModel
class ReminderDetailViewModel @Inject constructor(
    private val repository: ReminderRepository,
    val timeUtils: TimeUtils
) : ViewModel() {

    private val _idFlow = MutableStateFlow<Long?>(null)
    private val _showDelete = MutableStateFlow(false)

    val uiState: StateFlow<ReminderDetailUiState> =
        kotlinx.coroutines.flow.combine(
            _idFlow.flatMapLatest { id ->
                if (id == null) kotlinx.coroutines.flow.flowOf(null)
                else repository.observeById(id)
            },
            _showDelete
        ) { reminder, showDelete ->
            ReminderDetailUiState(
                reminder = reminder,
                isLoading = reminder == null && _idFlow.value != null,
                showDeleteConfirm = showDelete
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReminderDetailUiState()
        )

    fun load(id: Long) { _idFlow.value = id }

    fun onSnooze() {
        val id = _idFlow.value ?: return
        viewModelScope.launch { repository.snooze(id, minutes = 10) }
    }

    fun onMarkDone() {
        val id = _idFlow.value ?: return
        viewModelScope.launch { repository.markDone(id) }
    }

    fun onDelete() {
        val id = _idFlow.value ?: return
        viewModelScope.launch { repository.delete(id) }
    }

    fun showDeleteConfirm(show: Boolean) { _showDelete.value = show }
}
