package com.promptly.app.data.ringtone

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.promptly.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ringtone catalog + URI helpers.
 *
 * Two sources of ringtones:
 *   1. Bundled — royalty-free .mp3 files dropped in res/raw/ by the developer.
 *      Listed in [BUNDLED_TONES].
 *   2. Device  — any ringtone / notification sound the user has on their phone,
 *      picked via [RingtoneManager].
 *
 * Bundled tones use stable `android.resource` URIs, while imported tones are
 * copied into app-private storage and referenced by absolute path.
 */
@Singleton
class RingtoneHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Resource names (without extension) of bundled ringtones in res/raw/.
     * To ship a new tone:
     *   1. Drop the .mp3 in app/src/main/res/raw/
     *   2. Add its name here.
     */
    private val bundledResources: List<String> = listOf(
        "chime_gentle",
        "bell_soft",
        "marimba",
        "chord_warm"
    )

    private val ringtonesDir: File by lazy {
        File(context.filesDir, "ringtones").apply { mkdirs() }
    }

    /** The actual list of available bundled tones (filters out missing resources). */
    val bundledTones: List<BundledTone> by lazy {
        bundledResources.mapNotNull { name ->
            val resId = context.resources.getIdentifier(name, "raw", context.packageName)
            if (resId != 0) BundledTone(name, "android.resource://${context.packageName}/raw/$name", resId)
            else null
        }
    }

    /** Default ringtone URI — first bundled tone if any, else system default. */
    val defaultRingtoneUri: String
        get() = bundledTones.firstOrNull()?.uri
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString()

    /** Convert a stored URI string to a playable [Uri]. */
    fun toUri(stored: String?): Uri {
        if (stored.isNullOrBlank()) return Uri.parse(defaultRingtoneUri)
        return Uri.parse(stored)
    }

    /** Resolve a stored URI to a human-readable title. */
    fun titleFor(stored: String?): String {
        if (stored.isNullOrBlank()) return context.getString(R.string.settings_ringtone_default)
        val storedFile = File(stored)
        if (storedFile.isAbsolute) {
            return storedFile.nameWithoutExtension.substringBeforeLast("__")
                .replace('_', ' ')
        }
        val uri = Uri.parse(stored)
        // Bundled tone?
        if (uri.scheme == "android.resource" && uri.host == context.packageName) {
            val seg = uri.lastPathSegment ?: return stored
            val name = bundledResources.firstOrNull { seg.endsWith(it) }
            return name?.replace('_', ' ')?.replaceFirstChar { it.uppercaseChar() }
                ?: context.getString(R.string.settings_ringtone_default)
        }
        // Device tone — look it up via RingtoneManager.
        return runCatching {
            RingtoneManager.getRingtone(context, uri)?.getTitle(context)
        }.getOrNull() ?: stored
    }

    /**
     * Returns a list of (uri, title) for system notification sounds, used by
     * the device-picker dialog.
     *
     * Note: this is a blocking call (cursor scan). Invoke on a background
     * thread from the caller.
     */
    fun listDeviceTones(): List<DeviceTone> {
        val manager = RingtoneManager(context).apply {
            setType(RingtoneManager.TYPE_NOTIFICATION)
            stopPreviousRingtone()
        }
        // Use getRingtoneUri(position) — simpler + avoids the column-index
        // confusion (URI_COLUMN_INDEX and ID_COLUMN_INDEX are int column
        // positions, not column names, so cursor.getColumnIndex(int) is wrong).
        val count = manager.cursor.count
        val out = ArrayList<DeviceTone>(count)
        for (i in 0 until count) {
            val uri = manager.getRingtoneUri(i) ?: continue
            val title = runCatching {
                RingtoneManager.getRingtone(context, uri)?.getTitle(context)
            }.getOrNull() ?: continue
            out.add(DeviceTone(uri.toString(), title))
        }
        return out
    }

    fun customTones(): List<DeviceTone> =
        ringtonesDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.name.lowercase() }
            ?.map {
                DeviceTone(
                    it.absolutePath,
                    it.nameWithoutExtension.substringBeforeLast("__").replace('_', ' ')
                )
            }
            .orEmpty()

    fun ringtonesDirectory(): File = ringtonesDir

    fun restoreCustomTone(name: String, input: java.io.InputStream): String {
        val safeName = File(name).name
        require(safeName.isNotBlank()) { "Invalid ringtone file name." }
        val destination = File(ringtonesDir, safeName)
        destination.outputStream().use { input.copyTo(it) }
        return destination.absolutePath
    }

    suspend fun importCustomTone(source: Uri): String = withContext(Dispatchers.IO) {
        val displayName = context.contentResolver.query(
            source,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        val input = requireNotNull(context.contentResolver.openInputStream(source)) {
            "Could not open the selected audio file."
        }
        val extension = displayName
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
            ?: "audio"
        val baseName = displayName
            ?.substringBeforeLast('.')
            ?.replace(Regex("[^A-Za-z0-9_-]"), "_")
            ?.take(40)
            ?.trim('_')
            ?.ifBlank { null }
            ?: "audio"
        val destination = File(ringtonesDir, "${baseName}__${UUID.randomUUID()}.$extension")
        try {
            input.use { sourceStream ->
                destination.outputStream().use { output -> sourceStream.copyTo(output) }
            }
            destination.absolutePath
        } catch (e: Exception) {
            destination.delete()
            throw e
        }
    }

    fun notificationSoundUri(stored: String): Uri {
        val file = File(stored)
        if (!file.isAbsolute) return Uri.parse(stored)
        check(file.isFile) { "The selected ringtone file is missing: $stored" }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        context.grantUriPermission(
            "com.android.systemui",
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        context.grantUriPermission(
            "android",
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        return uri
    }

    data class BundledTone(val name: String, val uri: String, val resId: Int)

    data class DeviceTone(val uri: String, val title: String)
}
