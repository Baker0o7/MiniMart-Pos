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

    /** Restore from a backup file — replaces current DB.
     *
     * Bug fix: this previously only copied the backup's MAIN .db file over the live
     * database, with no handling of the WAL (-wal) and SHM (-shm) companion files that
     * SQLite uses in WAL mode (the default on Android/Room). Two problems:
     *   1. The OLD -wal/-shm files (left over from BEFORE the restore) stayed in place
     *      next to the freshly-restored main db file. On next open, SQLite's WAL
     *      recovery logic would try to replay those stale frames against data they no
     *      longer correspond to — risking either a failure to open the database at all,
     *      or silent corruption of the just-restored data.
     *   2. backup() saves the WAL/SHM alongside the main file, but restore() never
     *      copied them back — so even a backup taken mid-write (before its own
     *      checkpoint fully flushed) couldn't be restored completely.
     * Also: the comment here used to say "(caller should have closed the DB)" as if
     * that were already handled — it wasn't enforced anywhere. The UI just called
     * restore() and showed a passive "please restart" message with nothing actually
     * forcing a restart, leaving the live (Hilt-singleton) Room connection pointed at
     * a database file that just got swapped out from under it. See restartApp() below,
     * which the UI now calls immediately after a successful restore.
     */
    suspend fun restore(context: Context, backupFile: File): BackupResult = withContext(Dispatchers.IO) {
        try {
            if (!backupFile.exists()) return@withContext BackupResult.Error("Backup file not found")

            val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)

            // Remove the CURRENT database's stale -wal/-shm files first — they describe
            // pending writes against the database we're about to replace wholesale, and
            // must never be allowed to apply against the restored file.
            listOf("-wal", "-shm").forEach { suffix ->
                File(dbFile.parent, dbFile.name + suffix).let { if (it.exists()) it.delete() }
            }

            backupFile.copyTo(dbFile, overwrite = true)

            // If the backup itself has matching -wal/-shm companions (e.g. it was taken
            // before a full checkpoint completed), restore those too so no committed data
            // is lost. Otherwise the deletion above already guarantees no stale ones remain.
            listOf("-wal", "-shm").forEach { suffix ->
                val backupCompanion = File(backupFile.parent, backupFile.name + suffix)
                if (backupCompanion.exists()) {
                    backupCompanion.copyTo(File(dbFile.parent, dbFile.name + suffix), overwrite = true)
                }
            }

            BackupResult.Success(dbFile, "Restore successful. Restarting app…")
        } catch (e: Exception) {
            BackupResult.Error("Restore failed: ${e.message}")
        }
    }

    /** Forces a clean process restart — necessary after restore() since the live
     * Room/SQLite connection (a Hilt @Singleton) is still pointed at the database file
     * that just got replaced out from under it. Relaunches the app's own launcher
     * activity in a fresh task, then kills the current process. */
    fun restartApp(context: Context) {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
        }
        Runtime.getRuntime().exit(0)
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
