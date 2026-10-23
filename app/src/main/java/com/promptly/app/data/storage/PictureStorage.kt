package com.promptly.app.data.storage

import android.content.Context
import android.net.Uri
import com.promptly.app.data.db.PromptlyDatabase.Companion.DB_NAME
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores attached reminder pictures inside the app's internal files dir
 * (filesDir/pictures/). This means:
 *
 *  + No storage permission required (we are API 29+, but this still avoids
 *    scoped-storage MediaStore complexity for files that are tightly tied to
 *    reminder rows).
 *  + Files are automatically wiped when the app is uninstalled.
 *  + Files are NOT visible in the user's gallery (intentional — they belong
 *    to the reminder, not to the photo roll).
 *
 * For backup, [BackupManager] zips this folder alongside the JSON dump.
 */
@Singleton
class PictureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val picturesDir: File by lazy {
        File(context.filesDir, "pictures").apply { mkdirs() }
    }

    /**
     * Copy a content:// URI (returned by the photo picker) into app-private
     * storage. Returns the newly created file's absolute path.
     *
     * The path is what we store in [ReminderEntity.pictureUri] — NOT a
     * content:// URI, because content URIs from ACTION_OPEN_DOCUMENT need
     * persistable permission grants that the user might revoke.
     */
    suspend fun importFromUri(source: Uri): String = withContext(Dispatchers.IO) {
        val fileName = "img_${UUID.randomUUID()}.jpg"
        val dest = File(picturesDir, fileName)
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Could not open input stream for $source" }
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest.absolutePath
    }

    fun createCaptureFile(): File =
        File(picturesDir, "capture_${UUID.randomUUID()}.jpg").apply {
            check(createNewFile()) { "Could not create a camera capture file." }
        }

    /**
     * Convert an absolute path (as stored in [ReminderEntity.pictureUri]) back
     * to a Uri suitable for Coil / Compose to render. Returns null if the file
     * no longer exists (was wiped, restored on a different device, etc.).
     */
    fun resolve(path: String?): Uri? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return Uri.fromFile(file)
    }

    /**
     * Delete a picture file. No-op if missing.
     */
    suspend fun delete(path: String?) = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext
        runCatching { File(path).delete() }
    }

    /**
     * Internal access for [BackupManager] to zip the entire pictures folder.
     */
    fun picturesDirectory(): File = picturesDir

    /**
     * Internal access for restore: write a picture from a backup stream.
     */
    suspend fun restoreFromStream(name: String, input: InputStream): String =
        withContext(Dispatchers.IO) {
            val dest = File(picturesDir, name)
            dest.outputStream().use { input.copyTo(it) }
            dest.absolutePath
        }

    /**
     * Reference to the DB file — used by backup to optionally include the SQLite file.
     */
    fun databaseFile(): File = context.getDatabasePath(DB_NAME)
}
