package com.minimart.pos.util

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import net.sqlcipher.database.SQLiteDatabase
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Migrates an existing plaintext Room/SQLite database to SQLCipher encryption.
 *
 * This must be called BEFORE Room opens the database. On first run after
 * installing the update:
 *   1. The plaintext .db file exists and has no password.
 *   2. We open it via SQLCipher with an empty passphrase (sqlcipher accepts
 *      this for unencrypted files via the sqlcipher_export() SQL function).
 *   3. We export it to a new encrypted file using the real passphrase.
 *   4. We atomically replace the original with the encrypted copy.
 *   5. On all subsequent launches the file is already encrypted — this is a no-op.
 *
 * A sentinel SharedPreferences key tracks whether migration is done, so
 * we skip the (relatively slow) file-open step on every subsequent launch.
 */
@Singleton
class DbEncryptionMigrator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "DbEncryptionMigrator"
        private const val PREFS = "minimart_db_enc_prefs"
        private const val KEY_MIGRATED = "db_encryption_migrated_v1"
    }

    /**
     * Call this before Room.databaseBuilder().build().
     * @param dbName  the database file name (AppDatabase.DATABASE_NAME)
     * @param passphrase the plaintext hex key from DatabaseKeyManager
     */
    fun migrateIfNeeded(dbName: String, passphrase: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED, false)) return   // already done

        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) {
            // Fresh install — no plaintext DB, mark migrated and return.
            prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
            return
        }

        // Load the SQLCipher native libs before any SQLiteDatabase call
        SQLiteDatabase.loadLibs(context)

        // Check whether the file is already encrypted (SQLCipher will throw if the
        // passphrase is wrong; opening with an empty string succeeds on a plaintext file)
        val isPlaintext = try {
            val db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath, "", null,
                SQLiteDatabase.OPEN_READONLY,
                null, null
            )
            db.close()
            true
        } catch (_: Exception) {
            false   // already encrypted — nothing to do
        }

        if (!isPlaintext) {
            prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
            return
        }

        Log.i(TAG, "Migrating plaintext DB to SQLCipher encryption…")

        val tmpFile = File(dbFile.parent, "$dbName.cipher_tmp")
        try {
            // Open the plaintext DB with no passphrase
            val db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath, "", null,
                SQLiteDatabase.OPEN_READWRITE,
                null, null
            )
            // Export to an encrypted copy using the real passphrase.
            // ATTACH creates the new encrypted file; sqlcipher_export() copies all
            // schema + data; DETACH commits and closes the new file.
            db.rawExecSQL("ATTACH DATABASE '${tmpFile.absolutePath}' AS encrypted KEY '$passphrase'")
            db.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            db.rawExecSQL("DETACH DATABASE encrypted")
            db.close()

            // Atomic swap: encrypted tmp → original file
            // Also remove WAL/SHM companions of the old plaintext DB
            listOf("-wal", "-shm").forEach { suffix ->
                File(dbFile.parent, dbFile.name + suffix).let { if (it.exists()) it.delete() }
            }
            if (!tmpFile.renameTo(dbFile)) {
                // renameTo can fail across filesystems — fall back to copy+delete
                tmpFile.copyTo(dbFile, overwrite = true)
                tmpFile.delete()
            }

            prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
            Log.i(TAG, "DB encryption migration complete.")
        } catch (e: Exception) {
            Log.e(TAG, "DB encryption migration FAILED — reverting to plaintext", e)
            tmpFile.delete()   // leave original plaintext DB intact
            // Don't set the sentinel: we'll try again next launch
        }
    }
}
