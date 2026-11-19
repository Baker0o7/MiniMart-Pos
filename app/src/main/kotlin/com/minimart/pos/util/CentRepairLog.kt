package com.minimart.pos.util

import android.content.Context

/**
 * Remembers start-up "cent repairs" (see IntegrityChecker.repairSaleCents) so Store health can show them. Normally
 * nothing is ever written here: a repair means a sale was saved with cents that did not match its amount.
 */
object CentRepairLog {
    private const val PREFS = "cent_repair_log"
    private const val WINDOW_MS = 14L * 24 * 60 * 60 * 1000

    fun note(context: Context, repaired: Int, now: Long = System.currentTimeMillis()) {
        if (repaired <= 0) return
        try {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            // One entry per repair so the two-week count can be worked out later; kept short.
            val entries = (p.getString("events", "").orEmpty().split(';').filter { it.isNotBlank() } + "$now:$repaired").takeLast(50)
            p.edit().putString("events", entries.joinToString(";")).apply()
        } catch (_: Throwable) { /* reporting must never break start-up */ }
        ProblemRecorder.record(IllegalStateException("Start-up repaired the stored total of $repaired sale(s)"), "checking sale totals")
    }

    /** Sales repaired in the last two weeks. */
    fun recent(context: Context, now: Long = System.currentTimeMillis()): Int = try {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("events", "").orEmpty().split(';').sumOf { e ->
            val p = e.split(':'); val at = p.getOrNull(0)?.toLongOrNull(); val n = p.getOrNull(1)?.toIntOrNull()
            if (at != null && n != null && now - at <= WINDOW_MS) n else 0
        }
    } catch (_: Throwable) { 0 }
}
