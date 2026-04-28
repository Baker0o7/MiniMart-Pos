package com.minimart.pos.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.minimart.pos.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

sealed class BackupResult {
    data class Success(val file: File, val message: String) : BackupResult()
    data class Error(val message: String) : BackupResult()
}

object BackupManager {

    private val df = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())

    /** Back up the Room DB to Downloads/MiniMartPOS/backups/ */
    suspend fun backup(context: Context): BackupResult = withContext(Dispatchers.IO) {
        try {
            val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
            if (!dbFile.exists()) return@withContext BackupResult.Error("Database file not found")

            // Checkpoint WAL using direct SQLite to flush all data into main db file
            try {
                val sqLiteDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
                )
                sqLiteDb.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
                sqLiteDb.close()
            } catch (_: Exception) { /* continue with backup even if checkpoint fails */ }

            val timestamp = df.format(Date())
            val backupDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "MiniMartPOS/backups"
            ).apply { mkdirs() }

            val dest = File(backupDir, "minimart_backup_$timestamp.db")
            dbFile.copyTo(dest, overwrite = true)

            // Also copy WAL and SHM if they exist
            listOf("-wal", "-shm").forEach { suffix ->
                val extra = File(dbFile.parent, dbFile.name + suffix)
                if (extra.exists()) extra.copyTo(File(backupDir, dest.name + suffix), overwrite = true)
            }

            BackupResult.Success(dest, "Backup saved to Downloads/MiniMartPOS/backups/\n${dest.name}")
        } catch (e: Exception) {
            BackupResult.Error("Backup failed: ${e.message}")
        }
    }

    /** Restore from a backup file — replaces current DB */
    suspend fun restore(context: Context, backupFile: File): BackupResult = withContext(Dispatchers.IO) {
        try {
            if (!backupFile.exists()) return@withContext BackupResult.Error("Backup file not found")

            val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)

            // Close all Room connections before replacing
            // (caller should have closed the DB — restart recommended after restore)
            backupFile.copyTo(dbFile, overwrite = true)

            BackupResult.Success(dbFile, "Restore successful. Please restart the app to apply changes.")
        } catch (e: Exception) {
            BackupResult.Error("Restore failed: ${e.message}")
        }
    }

    /** List all available backups from Downloads */
    fun listBackups(): List<File> {
        val backupDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "MiniMartPOS/backups"
        )
        return if (backupDir.exists()) {
            backupDir.listFiles { f -> f.name.endsWith(".db") }
                ?.sortedByDescending { it.lastModified() } ?: emptyList()
        } else emptyList()
    }

    /** Share the backup file via intent (USB OTG, cloud, etc.) */
    fun shareBackup(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "MiniMart POS Backup — ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share Backup").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
