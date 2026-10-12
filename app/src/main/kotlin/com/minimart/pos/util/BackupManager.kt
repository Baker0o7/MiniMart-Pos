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

    /** Back up the Room DB to the app's external files dir (backups/). */
    suspend fun backup(context: Context, passphrase: String? = null): BackupResult = withContext(Dispatchers.IO) {
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
            // App-specific external storage: needs no storage permission (so the app no longer
            // requests all-files access) and is not world-readable. Use "Share Latest Backup"
            // to copy a backup off the device — app-specific files are removed on uninstall.
            val backupDir = backupDir(context).apply { mkdirs() }

            // Optional passphrase: the backup is written as an encrypted .mmbak file (portable to
            // any device, unreadable without the passphrase). The -wal/-shm copies are skipped for
            // encrypted backups — the checkpoint above already flushed everything into the main
            // file, and the companions would otherwise sit next to it in plaintext.
            if (!passphrase.isNullOrEmpty()) {
                val enc = File(backupDir, "minimart_backup_$timestamp.mmbak")
                // The encrypted file cannot be opened without the passphrase, so check the data first.
                val plainCopy = File(context.cacheDir, "backup_check.db")
                try {
                    dbFile.copyTo(plainCopy, overwrite = true)
                    verify(plainCopy)?.let { problem ->
                        recordFailure(context, problem)
                        return@withContext BackupResult.Error("Backup was not saved: $problem")
                    }
                } finally { plainCopy.delete() }
                dbFile.inputStream().use { ins -> enc.outputStream().use { outs -> BackupCrypto.encrypt(ins, outs, passphrase) } }
                recordSuccess(context)
                return@withContext BackupResult.Success(enc, "Encrypted backup saved: ${enc.name}\nKeep the passphrase — it cannot be recovered.")
            }

            val dest = File(backupDir, "minimart_backup_$timestamp.db")
            dbFile.copyTo(dest, overwrite = true)
            // A backup that cannot be opened is worse than none: it gives false comfort.
            verify(dest)?.let { problem ->
                dest.delete()
                recordFailure(context, problem)
                return@withContext BackupResult.Error("Backup was not saved: $problem")
            }

            // Also copy WAL and SHM if they exist
            listOf("-wal", "-shm").forEach { suffix ->
                val extra = File(dbFile.parent, dbFile.name + suffix)
                if (extra.exists()) extra.copyTo(File(backupDir, dest.name + suffix), overwrite = true)
            }

            recordSuccess(context)
            BackupResult.Success(dest, "Backup saved and checked: ${dest.name}\nUse \"Share Latest Backup\" to copy it off this device.")
        } catch (e: Exception) {
            BackupResult.Error("Backup failed: ${e.message}")
        }
    }

    // ── Verification, automatic backups and status ───────────────────────────────

    private const val STATUS_PREFS = "minimart_backup_status"
    private const val KEEP_AUTO = 7
    private const val AUTO_PREFIX = "minimart_auto_"
    private const val STALE_AFTER_MS = 3L * 24 * 60 * 60 * 1000

    /** Returns a description of the problem, or null if [file] is a complete, readable MiniMart database. */
    fun verify(file: File): String? {
        if (!file.exists() || file.length() < 4096) return "the file is empty or too small"
        return try {
            android.database.sqlite.SQLiteDatabase.openDatabase(
                file.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                val check = db.rawQuery("PRAGMA integrity_check", null).use { c -> if (c.moveToFirst()) c.getString(0) else "no result" }
                if (check != "ok") return "database check failed ($check)"
                val tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                    buildSet { while (c.moveToNext()) add(c.getString(0)) }
                }
                val missing = listOf("users", "sales", "products").filter { it !in tables }
                if (missing.isNotEmpty()) "missing data: ${missing.joinToString()}" else null
            }
        } catch (e: Exception) {
            "cannot be opened (${e.message})"
        }
    }

    /**
     * Automatic backups are plain database copies, so they live in the app's private storage, not in the
     * external folder that other apps can read on older Android versions. (Manual backups can be
     * passphrase-encrypted and are meant to leave the phone.)
     */
    private fun autoDir(context: Context): File = File(context.filesDir, "auto_backups")

    /** Daily automatic backup: plain file in private storage, checked, with the oldest ones pruned. */
    suspend fun autoBackup(context: Context): BackupResult = withContext(Dispatchers.IO) {
        try {
            val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
            if (!dbFile.exists()) return@withContext BackupResult.Error("Database file not found")
            try {
                android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
                ).use { it.execSQL("PRAGMA wal_checkpoint(TRUNCATE)") }
            } catch (_: Exception) { /* verification below decides whether the copy is usable */ }
            val dir = autoDir(context).apply { mkdirs() }
            val dest = File(dir, "$AUTO_PREFIX${df.format(Date())}.db")
            dbFile.copyTo(dest, overwrite = true)
            verify(dest)?.let { problem ->
                dest.delete()
                recordFailure(context, problem)
                return@withContext BackupResult.Error("Automatic backup failed: $problem")
            }
            // Keep only the newest few automatic backups (manual ones are never touched).
            dir.listFiles { f -> f.name.startsWith(AUTO_PREFIX) && f.name.endsWith(".db") }
                ?.sortedByDescending { it.lastModified() }?.drop(KEEP_AUTO)?.forEach { it.delete() }
            // Earlier versions kept these in the external folder; remove those readable plain copies now
            // that a fresh verified one exists.
            backupDir(context).listFiles { f -> f.name.startsWith(AUTO_PREFIX) }?.forEach { it.delete() }
            recordSuccess(context)
            BackupResult.Success(dest, "Automatic backup saved: ${dest.name}")
        } catch (e: Exception) {
            recordFailure(context, e.message ?: "unknown error")
            BackupResult.Error("Automatic backup failed: ${e.message}")
        }
    }

    private fun recordSuccess(context: Context) {
        context.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE).edit()
            .putLong("last_ok_at", System.currentTimeMillis()).remove("last_error").apply()
    }

    private fun recordFailure(context: Context, message: String) {
        context.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE).edit()
            .putString("last_error", message).putLong("last_error_at", System.currentTimeMillis()).apply()
    }

    /** Time of the last backup that passed its check, or 0 if there has never been one. */
    fun lastVerifiedBackupAt(context: Context): Long =
        context.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE).getLong("last_ok_at", 0L)

    /** One line for the Settings screen. */
    fun statusText(context: Context): String {
        val p = context.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
        val ok = p.getLong("last_ok_at", 0L)
        val err = p.getString("last_error", null)
        val whenText = if (ok > 0) SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(ok)) else null
        return when {
            err != null && whenText != null -> "Last good backup $whenText. Latest attempt failed: $err"
            err != null -> "No good backup yet. Latest attempt failed: $err"
            whenText != null -> "Last good backup: $whenText"
            else -> "No backup yet. The first automatic one runs within a day."
        }
    }

    /** Message for a Home-screen warning, or null when backups are healthy. */
    fun warning(context: Context): String? {
        val p = context.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
        val ok = p.getLong("last_ok_at", 0L)
        val errAt = p.getLong("last_error_at", 0L)
        val now = System.currentTimeMillis()
        return when {
            p.getString("last_error", null) != null && errAt >= ok -> "The last backup failed. Open Settings → Data & Backup."
            ok == 0L -> null   // brand-new install: the first automatic backup is still due
            now - ok > STALE_AFTER_MS -> "No good backup for ${(now - ok) / 86_400_000L} days. Open Settings → Data & Backup."
            else -> null
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
    suspend fun restore(context: Context, backupFile: File, passphrase: String? = null): BackupResult = withContext(Dispatchers.IO) {
        if (!backupFile.exists()) return@withContext BackupResult.Error("Backup file not found")
        if (!BackupCrypto.isEncrypted(backupFile)) return@withContext restorePlain(context, backupFile)
        if (passphrase.isNullOrEmpty()) return@withContext BackupResult.Error("This backup is encrypted — enter its passphrase")
        val tmp = File(context.cacheDir, "restore_tmp.db")
        try {
            try {
                backupFile.inputStream().use { ins -> tmp.outputStream().use { outs -> BackupCrypto.decrypt(ins, outs, passphrase) } }
            } catch (_: BackupCrypto.WrongPassphraseException) {
                return@withContext BackupResult.Error("Wrong passphrase, or the backup is damaged")
            }
            restorePlain(context, tmp)
        } catch (e: Exception) {
            BackupResult.Error("Restore failed: ${e.message}")
        } finally {
            tmp.delete()
        }
    }

    private fun restorePlain(context: Context, backupFile: File): BackupResult {
        return try {
            if (!backupFile.exists()) return BackupResult.Error("Backup file not found")

            val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)

            // Refuse files that are not SQLite databases or come from a newer app version:
            // Room's destructive-migration fallback would otherwise WIPE the data on next launch.
            validateBackup(dbFile, backupFile)?.let { return BackupResult.Error(it) }

            // Keep a safety copy of the current database so a bad restore can be undone.
            try {
                android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
                ).use { it.execSQL("PRAGMA wal_checkpoint(TRUNCATE)") }
                dbFile.copyTo(File(context.filesDir, "pre_restore_backup.db"), overwrite = true)
            } catch (_: Exception) { /* best effort */ }

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

    /** Returns an error message if [backupFile] must not be restored, or null if it looks OK. */
    private fun validateBackup(liveDb: File, backupFile: File): String? {
        val magic = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        val header = ByteArray(magic.size)
        val readOk = try { backupFile.inputStream().use { it.read(header) == header.size } } catch (_: Exception) { false }
        if (!readOk || !header.contentEquals(magic)) return "This file is not a valid MiniMart backup"
        return try {
            val readOnly = android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            val backupVersion = android.database.sqlite.SQLiteDatabase
                .openDatabase(backupFile.absolutePath, null, readOnly).use { it.version }
            val liveVersion = if (liveDb.exists())
                android.database.sqlite.SQLiteDatabase.openDatabase(liveDb.absolutePath, null, readOnly).use { it.version }
            else Int.MAX_VALUE
            if (backupVersion > liveVersion) "This backup was made by a newer version of the app — update the app first" else null
        } catch (e: Exception) {
            "Backup file is damaged: ${e.message}"
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

    private fun backupDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "backups")

    /** List available backups: the app's backup folder plus any older ones left in Downloads. */
    fun listBackups(context: Context): List<File> {
        val legacyDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "MiniMartPOS/backups"
        )
        return listOf(backupDir(context), autoDir(context), legacyDir)
            .flatMap { dir -> dir.listFiles { f -> f.name.endsWith(".db") || f.name.endsWith(".mmbak") }?.toList() ?: emptyList() }
            .sortedByDescending { it.lastModified() }
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
