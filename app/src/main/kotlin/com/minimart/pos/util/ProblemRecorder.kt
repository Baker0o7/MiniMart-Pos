package com.minimart.pos.util

import android.app.Application
import android.content.Context
import java.io.File

/**
 * Keeps the handled-problem list ([ProblemLog]) in a small private text file. Never uploaded: it leaves the
 * phone only if the owner taps Share. Recording is best effort and must never throw into the caller.
 */
object ProblemRecorder {
    private const val FILE = "problem_log.txt"
    private val lock = Any()
    @Volatile private var dir: File? = null

    fun install(app: Application) {
        dir = app.filesDir
        ErrorAdvice.onProblem = { error, doing -> record(error, doing) }
    }

    fun record(error: Throwable, doing: String) {
        val d = dir ?: return
        try {
            synchronized(lock) {
                val f = File(d, FILE)
                val now = System.currentTimeMillis()
                val list = if (f.exists()) ProblemLog.decode(f.readText()) else emptyList()
                f.writeText(ProblemLog.encode(ProblemLog.add(list, ProblemLog.entryFor(error, doing, now))))
            }
        } catch (_: Throwable) { /* the log must never be the thing that fails */ }
    }

    fun load(context: Context): List<ProblemEntry> = try {
        synchronized(lock) { File(context.filesDir, FILE).takeIf { it.exists() }?.let { ProblemLog.decode(it.readText()) } ?: emptyList() }
    } catch (_: Throwable) { emptyList() }

    fun clear(context: Context) {
        runCatching { synchronized(lock) { File(context.filesDir, FILE).delete() } }
    }
}
