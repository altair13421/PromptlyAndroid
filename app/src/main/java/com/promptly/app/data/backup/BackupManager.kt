package com.promptly.app.data.backup

import android.content.Context
import android.net.Uri
import com.promptly.app.data.model.RecurrenceRule
import com.promptly.app.data.model.ReminderEntity
import com.promptly.app.data.model.ReminderStatus
import com.promptly.app.data.repository.ReminderRepository
import com.promptly.app.data.ringtone.RingtoneHelper
import com.promptly.app.data.storage.PictureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup format
 * -------------
 * A backup is a single .zip file containing:
 *
 *   reminders.json     -- serialized list of all [ReminderEntity]
 *   pictures/<uuid>.jpg -- attached pictures, name matches the file in pictureUri
 *   ringtones/<name>   -- imported custom reminder sounds
 *   meta.json          -- { "appVersion": "1.0.0", "schemaVersion": 1, "createdAt": <millis> }
 *
 * On restore we:
 *   1. Parse meta.json + reminders.json
 *   2. Wipe current reminders table
 *   3. Restore pictures to PictureStorage
 *   4. Re-insert reminders, fixing pictureUri paths to point to the new files
 *   5. Trigger ReminderScheduler.reArmAll() (caller's responsibility)
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ReminderRepository,
    private val pictureStorage: PictureStorage,
    private val ringtoneHelper: RingtoneHelper
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Serializable
    data class Meta(
        val appVersion: String = "1.0.0",
        val schemaVersion: Int = 1,
        val createdAt: Long = System.currentTimeMillis()
    )

    @Serializable
    data class BackupFile(
        val meta: Meta,
        val reminders: List<ReminderEntity>
    )

    /**
     * Export to a temp file inside app's cache dir, return the File. Caller is
     * responsible for sharing (via FileProvider) or copying to MediaStore.Downloads.
     */
    suspend fun export(): File = withContext(Dispatchers.IO) {
        val reminders = repository.getAll()
        val meta = Meta()

        // Build JSON
        val backupFile = BackupFile(meta, reminders)
        val jsonBytes = json.encodeToString(backupFile).toByteArray(Charsets.UTF_8)

        // Stage backup file in cache
        val outFile = File(context.cacheDir, "backups").apply { mkdirs() }
            .let { File(it, "promptly_backup_${System.currentTimeMillis()}.zip") }

        ZipOutputStream(FileOutputStream(outFile).buffered()).use { zip ->
            // meta + reminders
            zip.putNextEntry(ZipEntry("meta.json"))
            zip.write(json.encodeToString(meta).toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("reminders.json"))
            zip.write(jsonBytes)
            zip.closeEntry()

            // pictures — copy each file referenced by a reminder
            val picturesDir = pictureStorage.picturesDirectory()
            val seen = mutableSetOf<String>()
            for (r in reminders) {
                val p = r.pictureUri ?: continue
                if (p in seen) continue
                seen += p
                val src = File(p)
                if (!src.exists()) continue
                val relName = "pictures/${src.name}"
                zip.putNextEntry(ZipEntry(relName))
                FileInputStream(src).use { it.copyTo(zip) }
                zip.closeEntry()
            }

            val ringtonesDir = ringtoneHelper.ringtonesDirectory()
            val seenRingtones = mutableSetOf<String>()
            for (reminder in reminders) {
                val path = reminder.ringtoneUri ?: continue
                val source = File(path)
                if (!source.isFile || source.parentFile != ringtonesDir) continue
                if (!seenRingtones.add(source.absolutePath)) continue
                zip.putNextEntry(ZipEntry("ringtones/${source.name}"))
                FileInputStream(source).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }

        outFile
    }

    /**
     * Restore from a backup zip URI (typically returned by ACTION_OPEN_DOCUMENT).
     * Returns the number of reminders restored.
     */
    suspend fun restore(uri: Uri): Int = withContext(Dispatchers.IO) {
        val pathRemap = mutableMapOf<String, String>()  // old path -> new path
        var restoredReminders: List<ReminderEntity>? = null

        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open backup: $uri" }
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "reminders.json" -> {
                            val text = zip.readBytes().toString(Charsets.UTF_8)
                            val parsed = json.decodeFromString<BackupFile>(text)
                            restoredReminders = parsed.reminders
                        }

                        entry.name == "meta.json" -> {
                            // Could validate schema version here; for now just read.
                            zip.readBytes()
                        }

                        entry.name.startsWith("pictures/") -> {
                            val name = entry.name.removePrefix("pictures/")
                            val newPath = pictureStorage.restoreFromStream(name, zip)
                            // Track remap by the OLD absolute path the JSON will reference.
                            // The JSON stored the original absolute path; we restore the
                            // file under the same basename, so remap old -> new path:
                            //   old path = picturesDir/<name>
                            //   new path = whatever PictureStorage gave us
                            val oldPath = File(pictureStorage.picturesDirectory(), name).absolutePath
                            pathRemap[oldPath] = newPath
                        }

                        entry.name.startsWith("ringtones/") -> {
                            val name = entry.name.removePrefix("ringtones/")
                            val newPath = ringtoneHelper.restoreCustomTone(name, zip)
                            val oldPath = File(ringtoneHelper.ringtonesDirectory(), File(name).name)
                                .absolutePath
                            pathRemap[oldPath] = newPath
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        val reminders = requireNotNull(restoredReminders) {
            "Backup archive does not contain reminders."
        }.map { reminder ->
            reminder.copy(
                pictureUri = reminder.pictureUri?.let { pathRemap[it] ?: it },
                ringtoneUri = reminder.ringtoneUri?.let { pathRemap[it] ?: it }
            )
        }
        repository.replaceAll(reminders)
        reminders.size
    }
}
