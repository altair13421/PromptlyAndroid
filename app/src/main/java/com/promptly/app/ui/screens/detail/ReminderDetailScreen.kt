package com.promptly.app.ui.screens.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.promptly.app.R
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDetailScreen(
    state: ReminderDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSnooze: () -> Unit,
    onMarkDone: () -> Unit,
    onDelete: () -> Unit,
    vm: ReminderDetailViewModel = hiltViewModel(),
) {
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val r = state.reminder ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(r.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.detail_edit))
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero card: title + time
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Reminder", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(r.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text(vm.timeUtils.formatTrigger(r.triggerAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(vm.timeUtils.relativeTime(r.triggerAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Notes
            if (!r.notes.isNullOrBlank()) {
                Text("Notes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        r.notes,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Picture
            r.pictureUri?.let { path ->
                Text("Attached picture", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = try { URI(path).toString() } catch (_: Exception) { path },
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Recurrence info
            if (r.recurrence.isRepeating) {
                val label = when (r.recurrence.frequency) {
                    com.promptly.app.data.model.RecurrenceRule.Frequency.DAILY ->
                        if (r.recurrence.interval == 1) "Repeats daily" else "Repeats every ${r.recurrence.interval} days"
                    com.promptly.app.data.model.RecurrenceRule.Frequency.WEEKLY ->
                        if (r.recurrence.interval == 1) "Repeats weekly" else "Repeats every ${r.recurrence.interval} weeks"
                    com.promptly.app.data.model.RecurrenceRule.Frequency.MONTHLY ->
                        if (r.recurrence.interval == 1) "Repeats monthly" else "Repeats every ${r.recurrence.interval} months"
                    com.promptly.app.data.model.RecurrenceRule.Frequency.CUSTOM_DAYS ->
                        "Repeats every ${r.recurrence.interval} days"
                    com.promptly.app.data.model.RecurrenceRule.Frequency.NONE -> ""
                }
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.detail_snooze)) }
                Button(
                    onClick = onMarkDone,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text(stringResource(R.string.detail_done)) }
            }

            TextButton(
                onClick = { vm.showDeleteConfirm(true) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.detail_delete))
            }
        }
    }

    // Delete confirmation
    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { vm.showDeleteConfirm(false) },
            title = { Text(stringResource(R.string.detail_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = { vm.showDeleteConfirm(false); onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.detail_delete_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.showDeleteConfirm(false) }) {
                    Text(stringResource(R.string.detail_delete_confirm_no))
                }
            }
        )
    }
}
