package com.minimart.pos.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RetryPolicyTest {
    @Test fun retriesUntilTheLastAllowedAttempt() {
        val p = RetryPolicy(maxAttempts = 3)
        assertTrue(p.shouldRetry(0)); assertTrue(p.shouldRetry(1)); assertFalse(p.shouldRetry(2)); assertFalse(p.shouldRetry(9))
    }
    @Test fun singleAttemptNeverRetries() = assertFalse(RetryPolicy(1).shouldRetry(0))
    @Test fun delayDoublesThenCaps() {
        val p = RetryPolicy(5, baseDelayMs = 1_000, maxDelayMs = 10_000)
        assertEquals(1_000, p.delayMs(0)); assertEquals(2_000, p.delayMs(1)); assertEquals(4_000, p.delayMs(2))
        assertEquals(8_000, p.delayMs(3)); assertEquals(10_000, p.delayMs(4)); assertEquals(10_000, p.delayMs(500))
    }
    @Test fun rejectsNonsense() {
        assertThrows(IllegalArgumentException::class.java) { RetryPolicy(0) }
        assertThrows(IllegalArgumentException::class.java) { RetryPolicy(2, baseDelayMs = 10, maxDelayMs = 5) }
    }
}
