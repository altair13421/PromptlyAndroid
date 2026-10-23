package com.promptly.app.ui.screens.create

import android.Manifest
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.promptly.app.R
import com.promptly.app.data.model.RecurrenceRule
import com.promptly.app.util.TimeUtils
import coil.compose.AsyncImage
import java.io.File
import java.util.Calendar
import java.util.TimeZone
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateReminderScreen(
    state: CreateReminderUiState,
    isEdit: Boolean = false,
    onTitleChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onTimeChange: (Int, Int) -> Unit,
    onDateChange: (Int, Int, Int) -> Unit,
    onRecurrenceChange: (RecurrenceRule) -> Unit,
    onRingtoneChange: (String?) -> Unit,
    onCustomRingtonePicked: (Uri) -> Unit,
    onPicturePicked: (Uri) -> Unit,
    onCreateCameraCapture: () -> File,
    onPictureCaptured: (String) -> Unit,
    onPictureCaptureCancelled: (String) -> Unit,
    onPictureRemoved: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    vm: CreateReminderViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val pictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let(onPicturePicked)
    }
    val audioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let(onCustomRingtonePicked) }
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }
    var cameraPermissionDenied by remember { mutableStateOf(false) }
    var cameraUnavailable by remember { mutableStateOf(false) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { captured ->
        pendingCameraPath?.let { path ->
            if (captured) onPictureCaptured(path) else onPictureCaptureCancelled(path)
        }
        pendingCameraPath = null
    }
    val launchCamera: () -> Unit = {
        val file = onCreateCameraCapture()
        pendingCameraPath = file.absolutePath
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            cameraLauncher.launch(uri)
        } catch (e: ActivityNotFoundException) {
            onPictureCaptureCancelled(file.absolutePath)
            pendingCameraPath = null
            cameraUnavailable = true
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionDenied = !granted
        if (granted) launchCamera()
    }

    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showRecurrencePicker by remember { mutableStateOf(false) }
    var showRingtonePicker by remember { mutableStateOf(false) }

    val cal = remember(state.triggerAt) {
        Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = state.triggerAt }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isEdit) R.string.edit_title else R.string.create_title)) },
                navigationIcon = {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.create_cancel))
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = !state.isSaving && !state.isLoading) {
                        Text(stringResource(if (isEdit) R.string.create_update else R.string.create_save))
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.create_field_title)) },
                placeholder = { Text(stringResource(R.string.create_field_title_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                leadingIcon = { Icon(Icons.Outlined.Alarm, contentDescription = null) }
            )

            // Notes
            OutlinedTextField(
                value = state.notes,
                onValueChange = onNotesChange,
                label = { Text(stringResource(R.string.create_field_notes)) },
                placeholder = { Text(stringResource(R.string.create_field_notes_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            // Time row
            Card(
                modifier = Modifier.fillMaxWidth().clickable { showTimePicker = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.create_field_time), style = MaterialTheme.typography.labelMedium)
                        val timeStr = String.format("%tl:%tM %Tp", cal, cal, cal)
                        Text(timeStr, style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(onClick = { showDatePicker = true }) {
                        val dateStr = String.format("%1\$tb %1\$te, %1\$tY", cal)
                        Text(dateStr)
                    }
                }
            }

            // Recurrence row
            Card(
                modifier = Modifier.fillMaxWidth().clickable { showRecurrencePicker = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Outlined.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.create_field_recurrence), style = MaterialTheme.typography.labelMedium)
                        Text(recurrenceLabel(state.recurrence), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().clickable { showRingtonePicker = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.create_field_sound), style = MaterialTheme.typography.labelMedium)
                        Text(
                            state.ringtoneUri?.let(vm::ringtoneTitle)
                                ?: stringResource(R.string.create_sound_app_default),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    TextButton(onClick = { showRingtonePicker = true }) {
                        Text(stringResource(R.string.create_choose_sound))
                    }
                }
            }

            // Picture attach
            if (state.pictureUri == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pictureLauncher.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.create_attach_picture))
                    }
                    OutlinedButton(
                        onClick = {
                            cameraUnavailable = false
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.create_take_picture))
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = Uri.parse(state.pictureUri),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { pictureLauncher.launch(arrayOf("image/*")) }) {
                                Text(stringResource(R.string.create_change_picture))
                            }
                            TextButton(
                                onClick = {
                                    cameraUnavailable = false
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            ) {
                                Text(stringResource(R.string.create_take_picture))
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = onPictureRemoved, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                Text(stringResource(R.string.create_remove_picture))
                            }
                        }
                    }
                }
            }
            if (cameraPermissionDenied) {
                Text(
                    stringResource(R.string.create_camera_permission_denied),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (cameraUnavailable) {
                Text(
                    stringResource(R.string.create_camera_unavailable),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Error message
            state.errorMessage?.let { msg ->
                Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    // Time picker dialog
    if (showTimePicker) {
        TimePickerDialog(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            onConfirm = { h, m ->
                onTimeChange(h, m)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }

    // Date picker dialog
    if (showDatePicker) {
        DatePickerDialog(
            initialYear = cal.get(Calendar.YEAR),
            initialMonth = cal.get(Calendar.MONTH),
            initialDay = cal.get(Calendar.DAY_OF_MONTH),
            onConfirm = { y, m, d ->
                onDateChange(y, m, d)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    // Recurrence picker dialog
    if (showRecurrencePicker) {
        RecurrencePickerDialog(
            current = state.recurrence,
            onPick = { rule ->
                onRecurrenceChange(rule)
                showRecurrencePicker = false
            },
            onDismiss = { showRecurrencePicker = false }
        )
    }

    if (showRingtonePicker) {
        RingtonePickerDialog(
            ringtoneHelper = vm.ringtoneHelper,
            selectedUri = state.ringtoneUri,
            onPick = { uri ->
                onRingtoneChange(uri)
                showRingtonePicker = false
            },
            onImport = {
                showRingtonePicker = false
                audioLauncher.launch(arrayOf("audio/*"))
            },
            onDismiss = { showRingtonePicker = false }
        )
    }
}

// --- Pickers ---------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val pickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(pickerState.hour, pickerState.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = pickerState) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialog(
    initialYear: Int,
    initialMonth: Int,
    initialDay: Int,
    onConfirm: (Int, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = Calendar.getInstance().apply {
            set(initialYear, initialMonth, initialDay)
        }.timeInMillis
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val ms = state.selectedDateMillis ?: return@TextButton
                val c = Calendar.getInstance().apply { timeInMillis = ms }
                onConfirm(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { DatePicker(state = state) }
    )
}

@Composable
private fun RecurrencePickerDialog(
    current: RecurrenceRule,
    onPick: (RecurrenceRule) -> Unit,
    onDismiss: () -> Unit
) {
    var freq by remember { mutableStateOf(current.frequency) }
    var interval by remember { mutableStateOf(current.interval.coerceAtLeast(1)) }
    var weekdays by remember { mutableStateOf(current.weekdays) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(
                    RecurrenceRule(
                        frequency = freq,
                        interval = interval,
                        weekdays = weekdays,
                        endDate = current.endDate,
                        maxOccurrences = current.maxOccurrences
                    )
                )
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Repeat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RecurrenceRule.Frequency.values().forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = freq == f, onClick = { freq = f })
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (f) {
                                RecurrenceRule.Frequency.NONE -> "Once"
                                RecurrenceRule.Frequency.DAILY -> "Every day"
                                RecurrenceRule.Frequency.WEEKLY -> "Weekly"
                                RecurrenceRule.Frequency.MONTHLY -> "Monthly"
                                RecurrenceRule.Frequency.CUSTOM_DAYS -> "Every N days"
                            }
                        )
                    }
                }
                if (freq == RecurrenceRule.Frequency.DAILY || freq == RecurrenceRule.Frequency.CUSTOM_DAYS) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Every")
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = interval.toString(),
                            onValueChange = { v -> interval = v.toIntOrNull()?.coerceIn(1, 365) ?: interval },
                            modifier = Modifier.width(80.dp),
                            singleLine = true
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (freq == RecurrenceRule.Frequency.WEEKLY) "weeks" else "days")
                    }
                }
                if (freq == RecurrenceRule.Frequency.WEEKLY) {
                    Text("On these days:", style = MaterialTheme.typography.labelMedium)
                    val labels = listOf("S", "M", "T", "W", "T", "F", "S")
                    val masks = listOf(
                        RecurrenceRule.WeekdayMask.SUN,
                        RecurrenceRule.WeekdayMask.MON,
                        RecurrenceRule.WeekdayMask.TUE,
                        RecurrenceRule.WeekdayMask.WED,
                        RecurrenceRule.WeekdayMask.THU,
                        RecurrenceRule.WeekdayMask.FRI,
                        RecurrenceRule.WeekdayMask.SAT
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        labels.indices.forEach { i ->
                            val on = (weekdays and masks[i]) != 0
                            FilterChip(
                                selected = on,
                                onClick = { weekdays = if (on) weekdays and masks[i].inv() else weekdays or masks[i] },
                                label = { Text(labels[i]) }
                            )
                        }
                    }
                }
            }
        }
    )
}

private fun recurrenceLabel(rule: RecurrenceRule): String = when (rule.frequency) {
    RecurrenceRule.Frequency.NONE -> "Once"
    RecurrenceRule.Frequency.DAILY -> if (rule.interval == 1) "Daily" else "Every ${rule.interval} days"
    RecurrenceRule.Frequency.WEEKLY -> if (rule.interval == 1) "Weekly" else "Every ${rule.interval} weeks"
    RecurrenceRule.Frequency.MONTHLY -> if (rule.interval == 1) "Monthly" else "Every ${rule.interval} months"
    RecurrenceRule.Frequency.CUSTOM_DAYS -> "Every ${rule.interval} days"
}

@Composable
private fun RingtonePickerDialog(
    ringtoneHelper: com.promptly.app.data.ringtone.RingtoneHelper,
    selectedUri: String?,
    onPick: (String?) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    val bundled = ringtoneHelper.bundledTones
    val custom = remember { ringtoneHelper.customTones() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.create_choose_sound)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())
            ) {
                TextButton(
                    onClick = { onPick(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.create_sound_app_default),
                        color = if (selectedUri == null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
                bundled.forEach { tone ->
                    TextButton(
                        onClick = { onPick(tone.uri) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            tone.name.replace('_', ' ').replaceFirstChar { it.uppercase() },
                            color = if (selectedUri == tone.uri) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                custom.forEach { tone ->
                    TextButton(
                        onClick = { onPick(tone.uri) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            tone.title,
                            color = if (selectedUri == tone.uri) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                TextButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.create_import_sound))
                }
            }
        }
    )
}
