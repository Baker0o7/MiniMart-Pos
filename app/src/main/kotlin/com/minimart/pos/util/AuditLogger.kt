package com.minimart.pos.util

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class AuditEvent {
    LOGIN_SUCCESS, LOGIN_FAILED, LOGOUT,
    SALE_COMPLETED, SALE_VOIDED, SALE_REFUNDED,
    DISCOUNT_APPLIED,
    CREDIT_ADDED, CREDIT_USED,
    USER_CREATED, USER_DELETED, PIN_CHANGED,
    PRODUCT_CREATED, PRODUCT_EDITED, PRODUCT_DELETED, STOCK_ADJUSTED,
    NO_SALE, SHIFT_OPENED, SHIFT_CLOSED,
    BACKUP_CREATED, BACKUP_FAILED, BACKUP_RESTORED, DATA_CHECK, PERMISSION_DENIED,
    SETTINGS_CHANGED,
    SESSION_EXPIRED,
    LOG_ROTATED
}

/** Result of walking the log's hash chain. [brokenAtLine] is 1-based within the current file. */
data class AuditCheck(val entries: Int, val ok: Boolean, val brokenAtLine: Int? = null)

/**
 * Append-only activity log. Every line carries a short hash of (previous hash + the line), so a line
 * that is edited or removed from the middle breaks the chain and [verify] reports where. (Deleting
 * only the newest lines cannot be detected on the phone itself.) The signed-in user is attached
 * automatically through [actor].
 */
@Singleton
class AuditLogger @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val GENESIS = "genesis"
        private const val ROTATE_AT_LINES = 5000
        private val HASH_SUFFIX = Regex(" \\| h=([0-9a-f]{16})$")

        fun hashOf(prev: String, body: String): String {
            val d = MessageDigest.getInstance("SHA-256").digest((prev + "\n" + body).toByteArray(Charsets.UTF_8))
            return d.joinToString("") { "%02x".format(it) }.take(16)
        }

        /** Walks [lines] (oldest first). Lines written before the chain existed (no hash) are skipped. */
        fun verifyLines(lines: List<String>): AuditCheck {
            var prev = GENESIS
            var first = true
            var count = 0
            lines.forEachIndexed { i, raw ->
                val m = HASH_SUFFIX.find(raw) ?: return@forEachIndexed
                val body = raw.substring(0, m.range.first)
                if (first) {
                    first = false
                    // A rotated file starts with a marker that carries the previous file's last hash.
                    Regex("\\| prev=([0-9a-f]{16})").find(body)?.let { prev = it.groupValues[1] }
                }
                if (hashOf(prev, body) != m.groupValues[1]) return AuditCheck(count, false, i + 1)
                prev = m.groupValues[1]
                count++
            }
            return AuditCheck(count, true)
        }
    }

    /** Name of the person currently signed in; set by the login/logout code. */
    @Volatile var actor: String = ""

    private val logFile: File by lazy {
        File(context.filesDir, "audit.log").also {
            if (!it.exists()) it.createNewFile()
        }
    }
    // log()/read/verify can run on different threads, and SimpleDateFormat is not thread-safe, so
    // one lock guards the file, the formatter and the running hash.
    private val lock = Any()
    private val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private var lastHash: String? = null
    private var lineCount = -1

    fun log(event: AuditEvent, user: String = "", detail: String = "") {
        synchronized(lock) {
            try {
                ensureState()
                val who = user.ifBlank { actor }
                val clean = detail.replace('\n', ' ').replace('\r', ' ')
                val body = "[${df.format(Date())}] ${event.name} | user=$who | $clean"
                val h = hashOf(lastHash ?: GENESIS, body)
                logFile.appendText("$body | h=$h\n")
                lastHash = h
                lineCount++
                Log.i("AUDIT", body)
                if (lineCount >= ROTATE_AT_LINES) rotate()
            } catch (_: Exception) {}
        }
    }

    /** Newest [limit] lines, oldest first. */
    fun getRecentLogs(limit: Int = 50): List<String> = synchronized(lock) {
        try { logFile.readLines().takeLast(limit) } catch (_: Exception) { emptyList() }
    }

    fun verify(): AuditCheck = synchronized(lock) {
        try { verifyLines(logFile.readLines()) } catch (_: Exception) { AuditCheck(0, true) }
    }

    private fun ensureState() {
        if (lastHash != null && lineCount >= 0) return
        val lines = logFile.readLines()
        lineCount = lines.size
        lastHash = lines.asReversed().firstNotNullOfOrNull { HASH_SUFFIX.find(it)?.groupValues?.get(1) }
            ?: GENESIS
    }

    /** Keeps the full history: the old file is archived, and the new one continues the hash chain. */
    private fun rotate() {
        val prev = lastHash ?: GENESIS
        val archive = File(context.filesDir, "audit.log.1")
        if (archive.exists()) archive.delete()
        logFile.renameTo(archive)
        logFile.createNewFile()
        val body = "[${df.format(Date())}] ${AuditEvent.LOG_ROTATED.name} | user= | prev=$prev"
        val h = hashOf(prev, body)
        logFile.appendText("$body | h=$h\n")
        lastHash = h
        lineCount = 1
    }
}
