package com.promptly.app.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptly.app.data.backup.BackupManager
import com.promptly.app.data.repository.SettingsRepository
import com.promptly.app.data.ringtone.RingtoneHelper
import com.promptly.app.util.Permissions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val darkMode: SettingsRepository.DarkMode = SettingsRepository.DarkMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val defaultRingtoneUri: String? = null,
    val defaultRingtoneTitle: String = "",
    val vibrate: Boolean = true,
    val isExporting: Boolean = false,
    val isRestoring: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    val ringtoneHelper: RingtoneHelper,
    private val backupManager: BackupManager,
    val permissions: Permissions
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.darkMode,
        settings.dynamicColor,
        settings.defaultRingtoneUri,
        settings.vibrate
    ) { dark, dyn, ring, vib ->
        SettingsUiState(
            darkMode = dark,
            dynamicColor = dyn,
            defaultRingtoneUri = ring,
            defaultRingtoneTitle = ringtoneHelper.titleFor(ring),
            vibrate = vib
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun onDarkModeChange(mode: SettingsRepository.DarkMode) {
        viewModelScope.launch { settings.setDarkMode(mode) }
    }

    fun onDynamicColorChange(enabled: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(enabled) }
    }

    fun onRingtonePicked(uri: String?) {
        viewModelScope.launch { settings.setDefaultRingtoneUri(uri) }
    }

    fun onVibrateChange(enabled: Boolean) {
        viewModelScope.launch { settings.setVibrate(enabled) }
    }

    fun onExportBackup() {
        viewModelScope.launch {
            // BackupManager.export returns a File in cacheDir/backups/.
            // Sharing that file via FileProvider is the responsibility of the
            // Activity (via SAF or share-sheet). For simplicity here, we just
            // kick off the export and post a status message.
            try {
                backupManager.export()
                _message.emit("Backup created — check app cache.")
            } catch (t: Throwable) {
                _message.emit("Backup failed: ${t.message}")
            }
        }
    }

    fun onRestoreBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val n = backupManager.restore(uri)
                _message.emit("Restored $n reminders")
            } catch (t: Throwable) {
                _message.emit("Restore failed: ${t.message}")
            }
        }
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun clearMessage() { _message.value = null }
}
