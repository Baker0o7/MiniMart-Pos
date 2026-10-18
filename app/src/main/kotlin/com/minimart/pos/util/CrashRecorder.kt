package com.minimart.pos.util

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps a local note of why the app last closed unexpectedly, so the owner can send it over instead of
 * describing "it just closed". Nothing is uploaded: the report is a small text file in the app's private
 * folder, and it only leaves the phone if the owner taps Share.
 */
object CrashRecorder {
    private const val DIR = "crash_reports"
    private const val KEEP = 5
    private const val MAX_TRACE_CHARS = 20_000
    private const val PREFS = "crash_recorder"
    private const val KEY_SEEN = "seen_at"

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val version = try { app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "?" } catch (_: Exception) { "?" }
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val now = System.currentTimeMillis()
                val dir = File(app.filesDir, DIR).apply { mkdirs() }
                File(dir, "crash_$now.txt").writeText(format(now, version, Build.MODEL, Build.VERSION.SDK_INT, thread.name, error))
                prune(dir, KEEP)
            } catch (_: Throwable) { /* recording must never make a crash worse */ }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The report text (no personal data beyond what the exception message itself contains). */
    internal fun format(now: Long, version: String, model: String, sdk: Int, threadName: String, error: Throwable): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString().take(MAX_TRACE_CHARS)
        val whenText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(now))
        return "MiniMart POS crash report\nTime: $whenText\nApp version: $version\nDevice: $model (Android SDK $sdk)\nThread: $threadName\n\n$trace"
    }

    /** Deletes all but the newest [keep] reports. */
    internal fun prune(dir: File, keep: Int) {
        dir.listFiles { f -> f.name.startsWith("crash_") && f.name.endsWith(".txt") }
            ?.sortedByDescending { timestampOf(it) }
            ?.drop(keep)?.forEach { it.delete() }
    }

    internal fun timestampOf(f: File): Long = f.name.removePrefix("crash_").removeSuffix(".txt").toLongOrNull() ?: 0L

    /** The newest report the owner has not been told about yet, or null. */
    fun unseen(context: Context): File? {
        val seen = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_SEEN, 0L)
        return File(context.filesDir, DIR).listFiles { f -> f.name.startsWith("crash_") }
            ?.filter { timestampOf(it) > seen }?.maxByOrNull { timestampOf(it) }
    }

    fun markSeen(context: Context, report: File) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_SEEN, timestampOf(report)).apply()
    }
}
