package com.minimart.pos.util

import android.content.Context
import android.util.Log
import com.minimart.pos.data.db.AppDatabase
import com.minimart.pos.data.security.KeystoreKey
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.security.SecureRandom

/**
 * Keeps the shop database encrypted on the phone (SQLCipher, AES-256).
 *
 * Why it is built so defensively: a mistake here can cost a shop its sales history, and there is no way to
 * test every phone. So:
 *  - the key is random, kept only in the Android Keystore, and never replaced if it exists but cannot be read;
 *  - an existing plain database is converted into a NEW file, checked (integrity, version, every table's row
 *    count), and only then swapped in; any failure leaves the plain database untouched and the app keeps working
 *    unencrypted, with the reason shown in Settings;
 *  - a plain copy of the old database is kept for [SAFETY_COPY_DAYS] days, then deleted, so a bad conversion can
 *    be undone by support, without leaving readable data behind forever;
 *  - if the Keystore key is lost (for example the phone was reset around the app), the unreadable file is put
 *    aside rather than crashing on every start.
 * Backups are described in BackupManager.
 */
object DbEncryption {
    private const val TAG = "DbEncryption"
    private const val PREFS = "minimart_dbkey"
    private const val KEY_ALIAS = "minimart_db_key_v1"
    private const val ENTRY = "db_key"
    private const val STATUS = "status"
    private const val SAFETY_COPY_DAYS = 3L

    @Volatile private var loaded = false
    private fun load() {
        if (loaded) return
        synchronized(this) { if (!loaded) { System.loadLibrary("sqlcipher"); loaded = true } }
    }

    // ── Key ──────────────────────────────────────────────────────────────────────

    /** The 32-byte database key, or null when it does not exist (and [create] is false) or cannot be trusted. */
    fun key(context: Context, create: Boolean): ByteArray? = try {
        val box = AesGcmBox(KeystoreKey.getOrCreate(KEY_ALIAS))
        // Some phones create a Keystore key but fail when using it; find out now, not after the data is encrypted.
        check(box.open("selftest", box.seal("selftest", byteArrayOf(1, 2, 3)))?.size == 3) { "Keystore self-test failed" }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sealed = prefs.getString(ENTRY, null)
        val existing = sealed?.let { box.open(ENTRY, it) }
        when {
            existing != null && existing.size == 32 -> existing
            sealed != null -> null                         // there is a key but it cannot be opened: never overwrite it
            create -> {
                val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
                prefs.edit().putString(ENTRY, box.seal(ENTRY, fresh)).commit()
                // Only trust a key that reads back; otherwise the data would be encrypted with a key we cannot recover.
                prefs.getString(ENTRY, null)?.let { box.open(ENTRY, it) }?.takeIf { it.contentEquals(fresh) }
            }
            else -> null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Key store unavailable", e); null
    }

    /** The key as text, usable as a passphrase for files that must be readable only on this phone. */
    fun keyPassphrase(context: Context): String? = key(context, create = false)?.let { DbFormat.rawKeyPassphrase(it) }

    // ── Files ────────────────────────────────────────────────────────────────────

    fun kind(file: File): DbFormat.Kind = try {
        if (!file.exists() || file.length() == 0L) DbFormat.Kind.MISSING_OR_EMPTY
        else file.inputStream().use { ins ->
            val b = ByteArray(16); val n = ins.read(b)
            DbFormat.classify(if (n <= 0) null else b.copyOf(n))
        }
    } catch (e: Exception) { DbFormat.Kind.ENCRYPTED }   // unreadable: never treat it as something to convert

    private const val RW = SQLiteDatabase.OPEN_READWRITE
    private const val RO = SQLiteDatabase.OPEN_READONLY

    private fun open(file: File, passphrase: String, flags: Int): SQLiteDatabase {
        load()
        return SQLiteDatabase.openDatabase(file.absolutePath, passphrase, null, flags, null)
    }

    private fun scalarText(db: SQLiteDatabase, sql: String): String? =
        db.rawQuery(sql, emptyArray<Any?>()).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun tableNames(db: SQLiteDatabase): List<String> =
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name", emptyArray<Any?>())
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    private fun rowCounts(db: SQLiteDatabase): Map<String, Long> =
        tableNames(db).associateWith { t -> scalarText(db, "SELECT COUNT(*) FROM `$t`")?.toLongOrNull() ?: -1L }

    // ── Starting the app ─────────────────────────────────────────────────────────

    /**
     * Called once before Room opens the database. Returns the passphrase bytes Room must use, or null when the
     * database stays plain (no usable key store, or a conversion that could not be verified).
     */
    @Synchronized
    fun prepare(context: Context, name: String = AppDatabase.DATABASE_NAME): ByteArray? {
        val file = context.getDatabasePath(name)
        deleteOldSafetyCopy(file)
        when (kind(file)) {
            DbFormat.Kind.MISSING_OR_EMPTY -> {
                val k = key(context, create = true) ?: return plain(context, "Not encrypted: this phone's secure key store is not available")
                setStatus(context, "Encrypted")
                return DbFormat.rawKeyPassphrase(k).toByteArray()
            }
            DbFormat.Kind.PLAIN -> {
                val k = key(context, create = true) ?: return plain(context, "Not encrypted: this phone's secure key store is not available")
                val problem = convert(file, DbFormat.rawKeyPassphrase(k))
                return if (problem == null) {
                    setStatus(context, "Encrypted")
                    DbFormat.rawKeyPassphrase(k).toByteArray()
                } else {
                    Log.e(TAG, "Conversion failed: $problem")
                    plain(context, "Not encrypted yet: the safety check failed ($problem). Your data is untouched.")
                }
            }
            DbFormat.Kind.ENCRYPTED -> {
                val k = key(context, create = false)
                if (k != null && canOpen(file, DbFormat.rawKeyPassphrase(k))) return DbFormat.rawKeyPassphrase(k).toByteArray()
                // Locked out: put the file aside so the app can start, and say so. Restoring a backup brings the data back.
                val aside = File(file.parentFile, "${file.name}.unreadable-${System.currentTimeMillis()}")
                file.renameTo(aside)
                listOf("-wal", "-shm").forEach { File(file.parentFile, file.name + it).delete() }
                Log.e(TAG, "Database could not be unlocked; moved to ${aside.name}")
                setStatus(context, "The shop data could not be unlocked on this phone, so a new empty shop was started. Restore a backup in Settings. The old file was kept.")
                val fresh = key(context, create = true) ?: return null
                return DbFormat.rawKeyPassphrase(fresh).toByteArray()
            }
        }
    }

    private fun plain(context: Context, why: String): ByteArray? { setStatus(context, why); return null }

    private fun canOpen(file: File, passphrase: String): Boolean = try {
        val db = open(file, passphrase, RO)
        try { scalarText(db, "SELECT COUNT(*) FROM sqlite_master") != null } finally { db.close() }
    } catch (e: Exception) { false }

    /** Plain → encrypted into a new file, verified, then swapped in. Returns null on success, or what went wrong. */
    private fun convert(plain: File, passphrase: String): String? {
        val dir = plain.parentFile
        val tmp = File(dir, plain.name + ".enc.tmp")
        val safety = File(dir, plain.name + ".preenc")
        tmp.delete()
        try {
            // Fold the write-ahead log into the main file so nothing recent is left out of the copy.
            open(plain, "", RW).let { db -> try { scalarText(db, "PRAGMA wal_checkpoint(TRUNCATE)") } finally { db.close() } }
            plain.copyTo(safety, overwrite = true)

            var sourceCounts: Map<String, Long> = emptyMap(); var version = 0
            val src = open(plain, "", RW)
            try {
                version = src.version
                sourceCounts = rowCounts(src)
                src.execSQL("ATTACH DATABASE '${tmp.absolutePath}' AS encrypted KEY \"$passphrase\"")
                scalarText(src, "SELECT sqlcipher_export('encrypted')")
                // sqlcipher_export copies the tables but not the schema version Room checks on every start.
                src.execSQL("PRAGMA encrypted.user_version = $version")
                src.execSQL("DETACH DATABASE encrypted")
            } finally { src.close() }

            val check = open(tmp, passphrase, RO)
            try {
                if (scalarText(check, "PRAGMA integrity_check") != "ok") return "integrity check failed"
                if (check.version != version) return "schema version did not carry over"
                val copied = rowCounts(check)
                if (copied != sourceCounts) return "row counts differ"
            } finally { check.close() }

            listOf("-wal", "-shm").forEach { File(dir, plain.name + it).delete() }
            if (!tmp.renameTo(plain)) return "could not replace the database file"
            return null
        } catch (e: Throwable) {
            tmp.delete(); safety.delete()
            return e.message ?: e.javaClass.simpleName
        }
    }

    private fun deleteOldSafetyCopy(db: File) {
        val safety = File(db.parentFile, db.name + ".preenc")
        if (safety.exists() && System.currentTimeMillis() - safety.lastModified() > SAFETY_COPY_DAYS * 86_400_000L) safety.delete()
    }

    // ── For backups and checks ───────────────────────────────────────────────────

    /** Opens any MiniMart database file, plain or encrypted with this phone's key, and reports what is in it. */
    class Inspection(val version: Int, val integrity: String, val tables: Set<String>)

    fun inspect(context: Context, file: File): Inspection {
        val pass = if (kind(file) == DbFormat.Kind.PLAIN) "" else keyPassphrase(context) ?: error("this backup is locked to another phone")
        val db = open(file, pass, RO)
        try {
            return Inspection(db.version, scalarText(db, "PRAGMA integrity_check") ?: "no result", tableNames(db).toSet())
        } finally { db.close() }
    }

    /** Folds the write-ahead log into the main file of the live database, so a plain file copy is complete. */
    fun checkpoint(context: Context, name: String = AppDatabase.DATABASE_NAME) {
        val file = context.getDatabasePath(name)
        val pass = if (kind(file) == DbFormat.Kind.PLAIN) "" else keyPassphrase(context) ?: return
        val db = open(file, pass, RW)
        try { scalarText(db, "PRAGMA wal_checkpoint(TRUNCATE)") } finally { db.close() }
    }

    /**
     * Writes a complete, readable-anywhere plain copy of the live database to [dest]. Manual backups use this,
     * so they can be restored on a different phone (an encrypted file is locked to this phone's key).
     */
    fun snapshotPlain(context: Context, dest: File, name: String = AppDatabase.DATABASE_NAME) {
        val file = context.getDatabasePath(name)
        require(file.exists()) { "Database file not found" }
        dest.delete()
        when (kind(file)) {
            DbFormat.Kind.PLAIN -> { checkpoint(context, name); file.copyTo(dest, overwrite = true) }
            else -> {
                val pass = keyPassphrase(context) ?: error("the database key is not available")
                val db = open(file, pass, RW)
                try {
                    scalarText(db, "PRAGMA wal_checkpoint(TRUNCATE)")
                    val version = db.version
                    db.execSQL("ATTACH DATABASE '${dest.absolutePath}' AS plaintext KEY ''")
                    scalarText(db, "SELECT sqlcipher_export('plaintext')")
                    db.execSQL("PRAGMA plaintext.user_version = $version")
                    db.execSQL("DETACH DATABASE plaintext")
                } finally { db.close() }
            }
        }
    }

    // ── Status for Settings ──────────────────────────────────────────────────────

    private fun setStatus(context: Context, text: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(STATUS, text).apply()
    }

    fun status(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(STATUS, null) ?: "Not checked yet"

    fun isEncrypted(context: Context, name: String = AppDatabase.DATABASE_NAME): Boolean =
        kind(context.getDatabasePath(name)) == DbFormat.Kind.ENCRYPTED
}
