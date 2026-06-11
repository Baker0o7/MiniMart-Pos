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

    fun log(event: AuditEvent, user: String = "", detail: String = "") {
        val timestamp = df.format(Date())
        val line = "[$timestamp] ${event.name} | user=$user | $detail\n"
        try {
            logFile.appendText(line)
            Log.i("AUDIT", line.trim())
        } catch (_: Exception) {}
    }

    fun getRecentLogs(limit: Int = 50): List<String> {
        return try {
            logFile.readLines().takeLast(limit)
        } catch (_: Exception) { emptyList() }
    }

    fun clearOldLogs() {
        try {
            val lines = logFile.readLines()
            if (lines.size > 500) {
                logFile.writeText(lines.takeLast(300).joinToString("\n") + "\n")
            }
        } catch (_: Exception) {}
    }
}
