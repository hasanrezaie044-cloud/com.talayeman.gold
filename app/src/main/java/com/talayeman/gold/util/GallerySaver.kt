package com.talayeman.gold.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * OPTIONAL "save to device gallery". Never called automatically; only when the user taps the
 * save button. Android 10+ uses MediaStore (no permission). Android 8-9 need the legacy
 * WRITE_EXTERNAL_STORAGE permission, which the UI requests only at that moment.
 */
object GallerySaver {

    private const val ALBUM = "TalayeMan"

    val needsLegacyPermission: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /** [source] is a [File] or a [Uri]. Returns true on success. */
    suspend fun save(context: Context, source: Any): Boolean = withContext(Dispatchers.IO) {
        try {
            val name = "TalayeMan_${System.currentTimeMillis()}.jpg"
            val input: InputStream = when (source) {
                is File -> source.inputStream()
                is Uri -> context.contentResolver.openInputStream(source) ?: return@withContext false
                else -> return@withContext false
            }
            input.use { stream ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, name)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: return@withContext false
                    try {
                        resolver.openOutputStream(target)?.use { out -> stream.copyTo(out) }
                            ?: throw IllegalStateException("no output stream")
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(target, values, null, null)
                    } catch (e: Exception) {
                        resolver.delete(target, null, null)
                        return@withContext false
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
                    if (!dir.exists() && !dir.mkdirs()) return@withContext false
                    val out = File(dir, name)
                    FileOutputStream(out).use { stream.copyTo(it) }
                    MediaScannerConnection.scanFile(context, arrayOf(out.absolutePath), arrayOf("image/jpeg"), null)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
