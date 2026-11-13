package com.minimart.pos.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PeerLockoutTest {
    private fun fail(l: PeerLockout, peer: String, times: Int, at: Long) = repeat(times) { l.recordFailure(peer, at) }

    @Test fun `one stranger locking themselves out does not lock anyone else`() {
        val l = PeerLockout()
        fail(l, "10.0.0.66", 5, 1_000)
        assertTrue(l.lockedFor("10.0.0.66", 1_001) > 0)
        assertEquals(0L, l.lockedFor("10.0.0.7", 1_001))
    }

    @Test fun `four wrong tries are still allowed`() {
        val l = PeerLockout()
        fail(l, "a", 4, 0)
        assertEquals(0L, l.lockedFor("a", 1))
    }

    @Test fun `lock ends after the time and a right code clears the record`() {
        val l = PeerLockout()
        fail(l, "a", 5, 0)
        assertEquals(30_000L - 10, l.lockedFor("a", 10))
        assertEquals(0L, l.lockedFor("a", 30_000))
        l.recordSuccess("a")
        fail(l, "a", 4, 40_000)
        assertEquals(0L, l.lockedFor("a", 40_001))
    }

    @Test fun `coming back and failing again doubles the wait up to the cap`() {
        val l = PeerLockout()
        var now = 0L
        val waits = mutableListOf<Long>()
        repeat(8) {
            repeat(4) { l.recordFailure("a", now) }
            waits += l.recordFailure("a", now)
            now += waits.last() + 1
        }
        assertEquals(listOf(30_000L, 60_000L, 120_000L, 240_000L, 480_000L, 900_000L, 900_000L, 900_000L), waits)
    }

    @Test fun `trying while locked does not extend the lock`() {
        val l = PeerLockout()
        fail(l, "a", 5, 0)
        repeat(50) { l.recordFailure("a", 5_000) }
        assertEquals(30_000L - 6_000, l.lockedFor("a", 6_000))
    }

    @Test fun `slow guesses spread over a long time never lock`() {
        val l = PeerLockout()
        repeat(50) { l.recordFailure("a", it * 11 * 60_000L) }
        assertEquals(0L, l.lockedFor("a", 50 * 11 * 60_000L))
    }

    @Test fun `a flood of addresses cannot make the table grow without limit`() {
        val l = PeerLockout(maxPeers = 100)
        repeat(5_000) { l.recordFailure("10.1.${it / 250}.${it % 250}", it.toLong()) }
        assertTrue(l.trackedPeers() <= 100)
    }
}
