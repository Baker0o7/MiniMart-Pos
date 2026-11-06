package com.minimart.pos.util

/**
 * Spots an app that keeps closing right after it opens, so background work (syncing, the web hook, workers,
 * one-off clean-ups) can be held back long enough for the person to sell. Whatever was crashing is then not
 * started on top of the till screen. A start counts as "stable" once the app has stayed open for [STABLE_AFTER_MS].
 */
object CrashLoopGuard {
    const val WINDOW_MS = 5L * 60_000
    const val THRESHOLD = 3
    const val STABLE_AFTER_MS = 60_000L
    const val HOLD_BACK_MS = 120_000L
    private const val KEEP = 10

    fun record(starts: List<Long>, now: Long): List<Long> = (starts + now).filter { now - it in 0..WINDOW_MS }.takeLast(KEEP)
    fun inLoop(starts: List<Long>, now: Long): Boolean = starts.count { now - it in 0..WINDOW_MS } >= THRESHOLD
    fun holdBackMs(inLoop: Boolean): Long = if (inLoop) HOLD_BACK_MS else 0L

    fun encode(starts: List<Long>) = starts.joinToString(",")
    fun decode(s: String?): List<Long> = s.orEmpty().split(',').mapNotNull { it.toLongOrNull() }.takeLast(KEEP)
}
