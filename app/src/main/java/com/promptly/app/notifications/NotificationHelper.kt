package com.promptly.app.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.promptly.app.MainActivity
import com.promptly.app.R
import com.promptly.app.data.model.ReminderEntity
import com.promptly.app.data.repository.SettingsRepository
import com.promptly.app.data.ringtone.RingtoneHelper
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds NotificationChannel + the actual notification for a fired reminder.
 *
 * On Android 8+ every notification needs a channel. We use ONE channel for all
 * reminders because the user expects all reminders to behave the same way.
 * Per-reminder ringtone is set on the notification itself via `setSound`, NOT
 * on the channel (channels own sound on Android 8+, but per-reminder overrides
 * require multiple channels which is messy).
 *
 *  Strategy: we set the channel to silent, then attach the per-reminder sound
 *  directly on the NotificationCompat builder. This works on all API levels
 *  and gives us per-reminder ringtone control.
 */
@Singleton
class NotificationHelper @Inject constructor(
    private val context: Context,
    private val ringtoneHelper: RingtoneHelper,
    private val settings: SettingsRepository
) {

    companion object {
        const val CHANNEL_REMINDERS = "promptly.reminders"
        const val CHANNEL_BACKUP = "promptly.backup"

        const val EXTRA_REMINDER_ID = "reminder_id"
        const val ACTION_DONE = "com.promptly.app.action.DONE"
        const val ACTION_SNOOZE = "com.promptly.app.action.SNOOZE"

        // We use one notification id per reminder so multiple reminders can fire
        // simultaneously without colliding. Reminder id is small (1..N), so we
        // offset by 1000 to avoid clashing with other notification ids in the app.
        fun notifIdFor(reminderId: Long): Int = (1000 + reminderId).toInt()
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        ensureChannels()
    }

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Main reminders channel — silent, because we attach sound per-reminder.
            if (notificationManager.getNotificationChannel(CHANNEL_REMINDERS) == null) {
                val ch = NotificationChannel(
                    CHANNEL_REMINDERS,
                    context.getString(R.string.notif_channel_reminders),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notif_channel_reminders_desc)
                    // Silent channel — sound attached per-notification.
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setShowBadge(true)
                }
                notificationManager.createNotificationChannel(ch)
            }

            // Backup channel — low priority, no sound.
            if (notificationManager.getNotificationChannel(CHANNEL_BACKUP) == null) {
                val ch = NotificationChannel(
                    CHANNEL_BACKUP,
                    "Backup & restore",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Status messages for backup operations."
                    setSound(null, null)
                }
                notificationManager.createNotificationChannel(ch)
            }
        }
    }

    /**
     * Build + fire the notification for a reminder that's due right now.
     */
    fun fire(reminder: ReminderEntity) {
        ensureChannels()

        val storedSound = resolveStoredSound(reminder)
        val sound = ringtoneHelper.notificationSoundUri(storedSound)
        val vibration = resolveVibrate(reminder)
        val channelId = ensureReminderChannel(
            sound,
            vibration,
            ringtoneHelper.titleFor(storedSound)
        )

        val contentIntent = PendingIntent.getActivity(
            context,
            reminder.id.toInt(),
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_REMINDER_ID, reminder.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val doneIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            Intent(context, DoneActionReceiver::class.java).apply {
                action = ACTION_DONE
                putExtra(EXTRA_REMINDER_ID, reminder.id)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            (reminder.id + 100_000L).toInt(),
            Intent(context, SnoozeActionReceiver::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(EXTRA_REMINDER_ID, reminder.id)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(reminder.title)
            .setContentText(reminder.notes ?: context.getString(R.string.app_tagline))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.notif_action_done), doneIntent)
            .addAction(0, context.getString(R.string.notif_action_snooze), snoozeIntent)
            .extend(NotificationCompat.WearableExtender().setContentAction(0))

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(sound)
            if (vibration) {
                builder.setVibrate(longArrayOf(0, 250, 200, 250))
            }
        }

        // Always use BigTextStyle — BigPictureStyle requires decoding the
        // attached picture to a Bitmap, which we skip in this version. The
        // picture is still visible in the detail screen.
        builder.setStyle(
            NotificationCompat.BigTextStyle()
                .bigText(reminder.notes ?: context.getString(R.string.app_tagline))
        )

        notificationManager.notify(notifIdFor(reminder.id), builder.build())
    }

    fun cancel(reminderId: Long) {
        notificationManager.cancel(notifIdFor(reminderId))
    }

    private fun resolveStoredSound(reminder: ReminderEntity): String {
        val stored = reminder.ringtoneUri
            ?: runCatching {
                kotlinx.coroutines.runBlocking { settings.defaultRingtoneUri.first() }
            }.getOrNull()
        return stored ?: ringtoneHelper.defaultRingtoneUri
    }

    private fun ensureReminderChannel(
        sound: Uri,
        vibration: Boolean,
        soundTitle: String
    ): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return CHANNEL_REMINDERS

        val key = "${sound}|$vibration".toByteArray()
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(key)
            .take(12)
            .joinToString("") { "%02x".format(it) }
        val channelId = "$CHANNEL_REMINDERS.$hash"
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                "Promptly: ${soundTitle.take(70)}",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_reminders_desc)
                setSound(
                    sound,
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(vibration)
                if (vibration) setVibrationPattern(longArrayOf(0, 250, 200, 250))
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        return channelId
    }

    private fun resolveVibrate(reminder: ReminderEntity): Boolean {
        return reminder.vibrate ?: runCatching {
            kotlinx.coroutines.runBlocking { settings.vibrate.first() }
        }.getOrDefault(true)
    }
}
