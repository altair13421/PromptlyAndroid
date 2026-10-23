package com.promptly.app.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.RingVolume
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.promptly.app.R
import com.promptly.app.data.repository.SettingsRepository
import com.promptly.app.data.ringtone.RingtoneHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onDarkModeChange: (SettingsRepository.DarkMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onRingtonePicked: (String?) -> Unit,
    onVibrateChange: (Boolean) -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: (Uri) -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    var showRingtonePicker by remember { mutableStateOf(false) }
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let(onRestoreBackup) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Appearance
            SectionHeader(stringResource(R.string.settings_appearance))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_dark_mode)) },
                        supportingContent = { Text(when (state.darkMode) {
                            SettingsRepository.DarkMode.SYSTEM -> stringResource(R.string.settings_dark_mode_system)
                            SettingsRepository.DarkMode.ON -> stringResource(R.string.settings_dark_mode_on)
                            SettingsRepository.DarkMode.OFF -> stringResource(R.string.settings_dark_mode_off)
                        }) },
                        leadingContent = { Icon(Icons.Outlined.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                TextButton(onClick = { expanded = true }) { Text("Choose"); }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    SettingsRepository.DarkMode.values().forEach { mode ->
                                        DropdownMenuItem(
                                            text = { Text(when (mode) {
                                                SettingsRepository.DarkMode.SYSTEM -> "Follow system"
                                                SettingsRepository.DarkMode.ON -> "Always on"
                                                SettingsRepository.DarkMode.OFF -> "Always off"
                                            }) },
                                            onClick = { onDarkModeChange(mode); expanded = false }
                                        )
                                    }
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                        supportingContent = { Text("Material You colors from your wallpaper (Android 12+)") },
                        leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Switch(checked = state.dynamicColor, onCheckedChange = onDynamicColorChange)
                        }
                    )
                }
            }

            // Sound
            SectionHeader(stringResource(R.string.settings_sound))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_ringtone)) },
                        supportingContent = {
                            Text(
                                state.defaultRingtoneTitle.ifBlank { stringResource(R.string.settings_ringtone_default) },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingContent = { Icon(Icons.Outlined.RingVolume, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { showRingtonePicker = true }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_vibrate)) },
                        supportingContent = { Text("Vibrate when a reminder fires") },
                        leadingContent = { Icon(Icons.Outlined.Vibration, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Switch(checked = state.vibrate, onCheckedChange = onVibrateChange)
                        }
                    )
                }
            }

            // Data
            SectionHeader(stringResource(R.string.settings_data))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_backup)) },
                        supportingContent = { Text("Export reminders + pictures to a .zip") },
                        leadingContent = { Icon(Icons.Outlined.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { onExportBackup() }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_restore)) },
                        supportingContent = { Text("Replace current data with a backup") },
                        leadingContent = { Icon(Icons.Outlined.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }
                    )
                }
            }

            // About
            SectionHeader(stringResource(R.string.settings_about))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_about_version)) },
                        supportingContent = { Text("1.0.0") }
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_about_made)) }
                    )
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    if (showRingtonePicker) {
        RingtonePickerDialog(
            ringtoneHelper = vm.ringtoneHelper,
            onPick = { uri ->
                onRingtonePicked(uri)
                showRingtonePicker = false
            },
            onDismiss = { showRingtonePicker = false }
        )
    }

    // Show transient message
    LaunchedEffect(vm.message) {
        vm.message.collect { /* could show a snackbar here; left as TODO */ }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun RingtonePickerDialog(
    ringtoneHelper: com.promptly.app.data.ringtone.RingtoneHelper,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val bundled = ringtoneHelper.bundledTones
    val device = remember { ringtoneHelper.listDeviceTones() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Choose ringtone") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Bundled", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                bundled.forEach { t ->
                    TextButton(onClick = { onPick(t.uri) }, modifier = Modifier.fillMaxWidth()) {
                        Text(t.name.replace('_', ' ').replaceFirstChar { it.uppercase() })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("On device", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                device.forEach { t ->
                    TextButton(onClick = { onPick(t.uri) }, modifier = Modifier.fillMaxWidth()) {
                        Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    )
}
