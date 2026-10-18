package com.minimart.pos.util

enum class BackupHealth { NONE, GOOD, STALE, FAILED }

/** Plain-language helpers for the backup screen. Pure, so the wording and thresholds are tested. */
object BackupAdvice {
    const val STALE_AFTER_DAYS = 3
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /** [lastOkAt] is 0 when there has never been a good backup; [failedSince] is true when the latest attempt failed. */
    fun health(lastOkAt: Long, lastErrorAt: Long, failedSince: Boolean, now: Long): BackupHealth = when {
        failedSince && lastErrorAt >= lastOkAt -> BackupHealth.FAILED
        lastOkAt <= 0L -> BackupHealth.NONE
        now - lastOkAt > STALE_AFTER_DAYS * DAY -> BackupHealth.STALE
        else -> BackupHealth.GOOD
    }

    /** "just now", "5 minutes ago", "3 hours ago", "yesterday", "4 days ago". */
    fun ago(thenMs: Long, nowMs: Long): String {
        val d = (nowMs - thenMs).coerceAtLeast(0L)
        return when {
            d < MINUTE -> "just now"
            d < HOUR -> (d / MINUTE).let { "$it minute${if (it == 1L) "" else "s"} ago" }
            d < DAY -> (d / HOUR).let { "$it hour${if (it == 1L) "" else "s"} ago" }
            d < 2 * DAY -> "yesterday"
            else -> "${d / DAY} days ago"
        }
    }

    /** 0 = empty, 1 = weak, 2 = fair, 3 = strong. Length matters most, then mixing letters, digits and symbols. */
    fun passphraseStrength(p: String): Int {
        if (p.isEmpty()) return 0
        val kinds = listOf(p.any { it.isLowerCase() }, p.any { it.isUpperCase() }, p.any { it.isDigit() }, p.any { !it.isLetterOrDigit() }).count { it }
        return when {
            p.length < 8 -> 1
            p.length >= 14 || (p.length >= 10 && kinds >= 3) -> 3
            kinds >= 2 -> 2
            else -> 1
        }
    }

    fun strengthLabel(level: Int) = when (level) { 1 -> "Weak: easy to guess"; 2 -> "Fair"; 3 -> "Strong"; else -> "" }

    fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
        else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
