package com.minimart.pos.util

import android.content.Context
import android.util.Log
import com.minimart.pos.BuildConfig
import com.minimart.pos.data.db.AppDatabase
import java.io.File

/**
 * Keeps one safety copy of the shop database right before a new app version first opens it, so a failed upgrade
 * cannot cost the shop its sales history. The files are copied as they are (still encrypted), into a folder that is
 * replaced by the next upgrade, so it never grows. A failure here never stops the app from starting.
 */
object UpgradeCopy {
    private const val PREFS = "upgrade_safety"
    private const val KEY_LAST = "last_run_version"
    private const val FOLDER = "pre-upgrade"

    fun runBeforeOpen(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val last = prefs.getInt(KEY_LAST, 0)
            val db = context.getDatabasePath(AppDatabase.DATABASE_NAME)
            val parts = listOf(db, File(db.path + "-wal"), File(db.path + "-shm")).filter { it.exists() }
            val size = parts.sumOf { it.length() }
            if (UpgradeSafety.shouldCopy(last, BuildConfig.VERSION_CODE, size, db.parentFile?.usableSpace ?: 0L)) {
                val dir = File(context.filesDir, FOLDER)
                dir.deleteRecursively()
                dir.mkdirs()
                parts.forEach { it.copyTo(File(dir, it.name), overwrite = true) }
                File(dir, "from_version.txt").writeText(last.toString())
            }
            prefs.edit().putInt(KEY_LAST, BuildConfig.VERSION_CODE).apply()
        } catch (e: Exception) {
            // Never block start-up over a safety copy; the version is not recorded, so the next start tries again.
            Log.w("UpgradeCopy", "Could not keep an upgrade copy", e)
        }
    }
}
