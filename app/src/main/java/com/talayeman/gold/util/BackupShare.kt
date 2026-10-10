package com.talayeman.gold.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Save backup to Google Drive".
 *
 * Builds the same portable .zip backup and hands it to the Google Drive app through the standard
 * Android share mechanism. Drive then lets the user pick the Google account and folder and uploads
 * the file. This needs NO Google sign-in inside this app, no OAuth client / API key, and no extra
 * permission, and it works with whichever Google account the user has in Drive.
 * If Drive is not installed, the normal share sheet is shown (any cloud app can be used).
 */
object BackupShare {

    const val DRIVE_PACKAGE = "com.google.android.apps.docs"
    private const val DIR = "backups"

    sealed interface Outcome {
        object OpenedDrive : Outcome
        object OpenedChooser : Outcome
        data class Failed(val message: String) : Outcome
    }

    /** Creates the backup file in the cache and opens Drive (or the share sheet) for it. */
    suspend fun shareToDrive(context: Context): Outcome {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // keep only the latest temporary backup
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val file = File(dir, "talayeman-backup-$stamp.zip")

        val result = BackupManager(context).createBackup(file)
        if (result.isFailure) {
            return Outcome.Failed(result.exceptionOrNull()?.message ?: "ساخت فایل پشتیبان ناموفق بود")
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "پشتیبان طلای من")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return try {
            context.startActivity(Intent(send).setPackage(DRIVE_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Outcome.OpenedDrive
        } catch (e: ActivityNotFoundException) {
            try {
                val chooser = Intent.createChooser(send, "ذخیره پشتیبان (Google Drive یا برنامه دیگر)")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                Outcome.OpenedChooser
            } catch (e2: Exception) {
                Outcome.Failed("برنامه‌ای برای ارسال فایل پیدا نشد")
            }
        } catch (e: Exception) {
            Outcome.Failed(e.message ?: "باز کردن Google Drive ممکن نشد")
        }
    }
}
