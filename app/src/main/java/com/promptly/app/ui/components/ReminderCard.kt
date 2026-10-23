package com.promptly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.promptly.app.data.model.ReminderEntity
import com.promptly.app.data.model.ReminderStatus
import com.promptly.app.util.Extensions
import com.promptly.app.util.TimeUtils
import java.util.concurrent.TimeUnit

/**
 * A single reminder row used by HomeScreen lists.
 *
 * Minimal layout:
 *  - left: leading icon (clock if active, check if done)
 *  - middle: title (bold) + time/notes (subtle)
 *  - optional picture thumbnail if attached
 *
 * Tints: amber for "due soon", green for normal, gray for done.
 */
@Composable
fun ReminderCard(
    reminder: ReminderEntity,
    timeUtils: TimeUtils,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val millisUntil = reminder.triggerAt - System.currentTimeMillis()
    val isDone = reminder.status == ReminderStatus.DONE
    val tint = if (isDone) MaterialTheme.colorScheme.outline
               else Extensions.dueTint(millisUntil)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Leading icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDone) Icons.Outlined.CheckCircle else Icons.Outlined.Alarm,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDone) MaterialTheme.colorScheme.outline
                            else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = timeUtils.formatTrigger(reminder.triggerAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (reminder.recurrence.isRepeating) {
                        Spacer(Modifier.width(4.dp))
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    when (reminder.recurrence.frequency) {
                                        com.promptly.app.data.model.RecurrenceRule.Frequency.DAILY -> "Daily"
                                        com.promptly.app.data.model.RecurrenceRule.Frequency.WEEKLY -> "Weekly"
                                        com.promptly.app.data.model.RecurrenceRule.Frequency.MONTHLY -> "Monthly"
                                        com.promptly.app.data.model.RecurrenceRule.Frequency.CUSTOM_DAYS -> "Every ${reminder.recurrence.interval}d"
                                        com.promptly.app.data.model.RecurrenceRule.Frequency.NONE -> ""
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            ),
                            border = null
                        )
                    }
                }
                if (!reminder.notes.isNullOrBlank()) {
                    Text(
                        text = reminder.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Picture thumb
            if (reminder.pictureUri != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
