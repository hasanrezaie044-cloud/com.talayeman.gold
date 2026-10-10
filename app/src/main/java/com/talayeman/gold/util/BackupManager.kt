package com.talayeman.gold.util

import android.content.Context
import com.talayeman.gold.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Local backup / restore of database + attachment files.
 * Produces a portable .zip that can be moved to another device.
 */
class BackupManager(private val context: Context) {

    suspend fun createBackup(outputFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            // Flush the write-ahead log into the main DB file so the copy below is complete and
            // consistent. (The database stays open: closing it while the UI observes it is unsafe.)
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }

            val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
            val attachmentsDir = File(context.filesDir, "attachments")
            val photosDir = File(context.filesDir, "photos")

            ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
                // Database
                if (dbFile.exists()) {
                    zip.putNextEntry(ZipEntry("database/${AppDatabase.DB_NAME}"))
                    FileInputStream(dbFile).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                // Attachments
                listOf(attachmentsDir, photosDir).forEach { dir ->
                    if (dir.exists()) {
                        dir.walkTopDown().filter { it.isFile }.forEach { file ->
                            val entryName = "files/${dir.name}/${file.name}"
                            zip.putNextEntry(ZipEntry(entryName))
                            FileInputStream(file).use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
                // Metadata
                val meta = JSONObject().apply {
                    put("version", 2) // DB schema version contained in this backup
                    put("appId", "com.talayeman.gold")
                    put("createdAt", System.currentTimeMillis())
                }
                zip.putNextEntry(ZipEntry("meta.json"))
                zip.write(meta.toString().toByteArray())
                zip.closeEntry()
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreBackup(backupFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            db.close()

            val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
            val tempDir = File(context.cacheDir, "restore_temp").apply {
                deleteRecursively()
                mkdirs()
            }

            ZipInputStream(FileInputStream(backupFile)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val outFile = File(tempDir, entry.name)
                    // Zip-slip protection: never write outside the temp directory.
                    if (!outFile.canonicalPath.startsWith(tempDir.canonicalPath + File.separator)) {
                        throw SecurityException("فایل پشتیبان نامعتبر است")
                    }
                    outFile.parentFile?.mkdirs()
                    if (!entry.isDirectory) {
                        FileOutputStream(outFile).use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            // Validate meta
            val metaFile = File(tempDir, "meta.json")
            if (metaFile.exists()) {
                val meta = JSONObject(metaFile.readText())
                if (meta.optString("appId") != "com.talayeman.gold") {
                    throw IllegalArgumentException("پشتیبان مربوط به این برنامه نیست")
                }
            }

            // Restore DB
            val restoredDb = File(tempDir, "database/${AppDatabase.DB_NAME}")
            if (!restoredDb.exists()) {
                throw IllegalArgumentException("پایگاه داده‌ای در فایل پشتیبان یافت نشد")
            }
            dbFile.parentFile?.mkdirs()
            // Stale WAL / SHM files of the old database must not be applied to the restored one.
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            restoredDb.copyTo(dbFile, overwrite = true)

            // Restore files
            val filesDir = File(tempDir, "files")
            if (filesDir.exists()) {
                filesDir.listFiles()?.forEach { dir ->
                    val target = File(context.filesDir, dir.name)
                    target.mkdirs()
                    dir.listFiles()?.forEach { f ->
                        f.copyTo(File(target, f.name), overwrite = true)
                    }
                }
            }

            tempDir.deleteRecursively()
            AppDatabase.getInstance(context)
            Result.success(Unit)
        } catch (e: Exception) {
            AppDatabase.getInstance(context)
            Result.failure(e)
        }
    }
}
