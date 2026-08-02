package com.minimart.pos.util

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class AuditEvent {
    LOGIN_SUCCESS, LOGIN_FAILED, LOGOUT,
    SALE_COMPLETED, SALE_VOIDED,
    DISCOUNT_APPLIED,
    CREDIT_ADDED, CREDIT_USED,
    USER_CREATED, USER_DELETED, PIN_CHANGED,
    PRODUCT_EDITED, PRODUCT_DELETED,
    SETTINGS_CHANGED,
    SESSION_EXPIRED
}

@Singleton
class AuditLogger @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val logFile: File by lazy {
        File(context.filesDir, "audit.log").also {
            if (!it.exists()) it.createNewFile()
        }
    }
    // Bug fix: this class is a Hilt @Singleton injected into CartViewModel,
    // AuthViewModel, and SessionViewModel, all of which can call log() concurrently
    // from coroutines running on different dispatcher threads (e.g. a sale completing
    // on one thread while a session expires on another). Two problems existed with no
    // synchronization at all:
    //   1. logFile.appendText(line) had no mutual exclusion — concurrent writes could
    //      interleave mid-line, corrupting the log file, or one call's IOException
    //      (silently swallowed by the empty catch) could be caused by another
    //      concurrent writer.
    //   2. SimpleDateFormat is well-known to NOT be thread-safe for concurrent format()
    //      calls on the same instance — it mutates internal Calendar state during
    //      formatting, so two threads calling df.format() at the same moment on this
    //      shared instance can each get a corrupted/wrong timestamp, independent of
    //      the file-write issue above.
    // A single lock object guards every method that touches logFile or df, so a
    // concurrent read (getRecentLogs) or truncation (clearOldLogs) also can't observe
    // a partially-written line from an in-progress log() call.
    private val lock = Any()

    fun log(event: AuditEvent, user: String = "", detail: String = "") {
        synchronized(lock) {
            val timestamp = df.format(Date())
            val line = "[$timestamp] ${event.name} | user=$user | $detail\n"
            try {
                logFile.appendText(line)
                Log.i("AUDIT", line.trim())
            } catch (_: Exception) {}
        }
    }

    fun getRecentLogs(limit: Int = 50): List<String> {
        return synchronized(lock) {
            try {
                logFile.readLines().takeLast(limit)
            } catch (_: Exception) { emptyList() }
        }
    }

    fun clearOldLogs() {
        synchronized(lock) {
            try {
                val lines = logFile.readLines()
                if (lines.size > 500) {
                    logFile.writeText(lines.takeLast(300).joinToString("\n") + "\n")
                }
            } catch (_: Exception) {}
        }
    }
}
