package com.promptly.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptly.app.data.model.ReminderEntity
import com.promptly.app.data.repository.ReminderRepository
import com.promptly.app.util.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HomeTab(val label: String) {
    ALL("All"),
    TODAY("Today"),
    UPCOMING("Upcoming"),
    DONE("Done")
}

data class HomeUiState(
    val tab: HomeTab = HomeTab.ALL,
    val reminders: List<ReminderEntity> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ReminderRepository,
    val timeUtils: TimeUtils
) : ViewModel() {

    private val _tab = MutableStateFlow(HomeTab.ALL)

    val uiState: StateFlow<HomeUiState> = _tab
        .flatMapLatest { tab ->
            when (tab) {
                HomeTab.ALL -> repository.observeAll()
                HomeTab.TODAY -> repository.observeToday()
                HomeTab.UPCOMING -> repository.observeActive()
                HomeTab.DONE -> repository.observeDone()
            }.flatMapLatest { items ->
                flowOf(
                    HomeUiState(
                        tab = tab,
                        reminders = items,
                        isLoading = false
                    )
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState()
        )

    fun onTabSelected(tab: HomeTab) { _tab.value = tab }

    fun onMarkDone(id: Long) {
        viewModelScope.launch { repository.markDone(id) }
    }

    fun onDelete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
