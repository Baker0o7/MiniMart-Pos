package com.minimart.pos.util

/**
 * How long a PIN lockout still has to run. The phone's wall clock can be set by anyone holding the phone, so while the
 * phone has not restarted the lockout is measured with the monotonic "time since boot" clock, which settings cannot move.
 * After a restart that clock starts again, so the wall clock is used, but never trusted for longer than the lockout itself.
 */
object LockoutClock {
    fun remainingMs(untilWall: Long, durationMs: Long, startElapsed: Long, nowWall: Long, nowElapsed: Long): Long {
        if (untilWall <= 0L) return 0L
        // A lockout saved by an older version has no monotonic reading: wall clock only.
        if (durationMs <= 0L || startElapsed < 0L) return (untilWall - nowWall).coerceAtLeast(0L)
        if (nowElapsed >= startElapsed) return (durationMs - (nowElapsed - startElapsed)).coerceIn(0L, durationMs)
        return (untilWall - nowWall).coerceIn(0L, durationMs)
    }
}
