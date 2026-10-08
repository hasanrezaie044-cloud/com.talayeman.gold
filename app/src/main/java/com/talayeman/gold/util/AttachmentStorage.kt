package com.talayeman.gold.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import com.talayeman.gold.domain.model.AttachmentType
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/**
 * Stores asset photos / invoice images inside app-private storage.
 *
 * - Files go to filesDir/photos (PHOTO) and filesDir/attachments (INVOICE): exactly the
 *   two folders already included by [BackupManager], so backup/restore keeps working.
 * - The DB stores a path RELATIVE to filesDir (e.g. "photos/photo_...jpg"), so images
 *   still resolve after restoring a backup on another device / build variant.
 * - Images are down-scaled (max [MAX_DIMENSION] px) and JPEG-compressed; nothing is stored
 *   as a blob in the database.
 */
object AttachmentStorage {

    private const val MAX_DIMENSION = 2048
    private const val JPEG_QUALITY = 85
    private const val CAMERA_DIR = "camera"

    data class StoredImage(val relativePath: String, val fileName: String, val size: Long)

    fun dirNameFor(type: AttachmentType): String = when (type) {
        AttachmentType.PHOTO -> "photos"
        AttachmentType.INVOICE -> "attachments"
    }

    fun authority(context: Context): String = "${context.packageName}.fileprovider"

    /** Creates an empty temp file in cache and returns a content:// Uri for the camera app. */
    fun createCameraTempUri(context: Context): Uri {
        val dir = File(context.cacheDir, CAMERA_DIR).apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, authority(context), file)
    }

    /** Deletes a camera temp file created by [createCameraTempUri]; ignores other Uris. */
    fun deleteCameraTemp(context: Context, uri: Uri) {
        if (uri.authority != authority(context)) return
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: return
        runCatching { File(File(context.cacheDir, CAMERA_DIR), name).delete() }
    }

    /**
     * Copies (and compresses) the image behind [uri] into app storage.
     * Returns null if the image cannot be read/decoded. Call from a background thread.
     */
    fun importImage(context: Context, uri: Uri, type: AttachmentType): StoredImage? {
        return try {
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // Note: decodeStream returns null in bounds-only mode, so don't chain "?: return".
            val boundsStream = resolver.openInputStream(uri) ?: return null
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIMENSION) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null

            val rotation = runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    when (ExifInterface(input).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                    )) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            }.getOrDefault(0f)

            val bitmap = scaleAndRotate(decoded, rotation)

            val dir = File(context.filesDir, dirNameFor(type)).apply { mkdirs() }
            val fileName = "${type.name.lowercase()}_${System.currentTimeMillis()}_" +
                "${UUID.randomUUID().toString().take(8)}.jpg"
            val out = File(dir, fileName)
            val ok = FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (bitmap !== decoded) decoded.recycle()
            bitmap.recycle()
            if (!ok || out.length() == 0L) {
                out.delete()
                return null
            }
            StoredImage("${dir.name}/$fileName", fileName, out.length())
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private fun scaleAndRotate(src: Bitmap, rotation: Float): Bitmap {
        val longest = max(src.width, src.height)
        val scale = if (longest > MAX_DIMENSION) MAX_DIMENSION.toFloat() / longest else 1f
        if (scale == 1f && rotation == 0f) return src
        val matrix = Matrix().apply {
            if (scale != 1f) postScale(scale, scale)
            if (rotation != 0f) postRotate(rotation)
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    /**
     * Resolves a stored path to a File. Supports relative paths (current format) and
     * absolute paths; falls back to "<dir>/<name>" under filesDir for restored backups.
     */
    fun resolve(context: Context, storedPath: String): File {
        val f = File(storedPath)
        if (!f.isAbsolute) return File(context.filesDir, storedPath)
        if (f.exists()) return f
        val parent = f.parentFile?.name
        return if (parent != null) File(File(context.filesDir, parent), f.name) else f
    }

    fun delete(context: Context, storedPath: String) {
        runCatching {
            val file = resolve(context, storedPath).canonicalFile
            // Only ever delete inside our own files directory.
            if (file.path.startsWith(context.filesDir.canonicalPath)) file.delete()
        }
    }
}
