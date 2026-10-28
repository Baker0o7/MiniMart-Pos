package com.minimart.pos.util

/**
 * Decides what a background job does after a failed try. Shops lose signal for hours, so a job keeps retrying
 * with growing gaps and only gives up for this round after [maxAttempts]; the next scheduled run starts fresh.
 * Giving up is never an error the owner sees: the data stays queued on the phone.
 */
data class RetryPolicy(val maxAttempts: Int, val baseDelayMs: Long = 30_000L, val maxDelayMs: Long = 6 * 60 * 60_000L) {
    init { require(maxAttempts >= 1 && baseDelayMs > 0 && maxDelayMs >= baseDelayMs) }

    /** [attempt] is how many times the job has already run (0 on the first try). */
    fun shouldRetry(attempt: Int): Boolean = attempt + 1 < maxAttempts

    /** Wait before the next try: doubles each time, never above [maxDelayMs]. */
    fun delayMs(attempt: Int): Long {
        if (attempt <= 0) return baseDelayMs
        val shift = attempt.coerceAtMost(40)
        val d = baseDelayMs * (1L shl shift)
        return if (d < 0 || d > maxDelayMs) maxDelayMs else d
    }

    companion object {
        val SYNC = RetryPolicy(maxAttempts = 7)
        val CLOUD = RetryPolicy(maxAttempts = 4)
        val QUICK = RetryPolicy(maxAttempts = 3)
    }
}
