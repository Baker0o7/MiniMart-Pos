package com.minimart.pos.util

import android.content.Context

/** Stores the recent start times for [CrashLoopGuard] in a tiny preference file that is readable immediately. */
object StartupGuard {
    private const val PREFS = "startup_guard"
    private const val KEY = "starts"

    /** Records this start and says whether the app has been restarting in a loop. */
    fun onStart(context: Context, now: Long = System.currentTimeMillis()): Boolean = try {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val starts = CrashLoopGuard.record(CrashLoopGuard.decode(p.getString(KEY, null)), now)
        p.edit().putString(KEY, CrashLoopGuard.encode(starts)).apply()
        CrashLoopGuard.inLoop(starts, now)
    } catch (_: Exception) { false }

    fun isInLoop(context: Context, now: Long = System.currentTimeMillis()): Boolean = try {
        CrashLoopGuard.inLoop(CrashLoopGuard.decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)), now)
    } catch (_: Exception) { false }

    /** The app stayed open long enough to be called stable: forget the earlier starts. */
    fun markStable(context: Context) {
        try { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply() } catch (_: Exception) {}
    }
}
