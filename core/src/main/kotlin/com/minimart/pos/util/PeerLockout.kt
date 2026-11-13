package com.minimart.pos.util

/**
 * Slows down someone guessing the sync pairing code, one device at a time.
 *
 * The first version kept a single counter for the whole phone, so one stranger on the shop Wi-Fi sending wrong
 * codes locked every real till out too. Here each address has its own count: [maxFailures] wrong tries lock only
 * that address, for [baseLockMs] and then twice as long each time it comes back and fails again (up to [maxLockMs]).
 * Time is passed in (use a clock that cannot be set back) so the rules can be tested.
 */
class PeerLockout(
    private val maxFailures: Int = 5,
    private val baseLockMs: Long = 30_000L,
    private val maxLockMs: Long = 15 * 60_000L,
    private val windowMs: Long = 10 * 60_000L,
    private val forgetStrikesMs: Long = 60 * 60_000L,
    private val maxPeers: Int = 256
) {
    private class State(var failures: Int = 0, var strikes: Int = 0, var lockedUntil: Long = 0L, var lastFailure: Long = 0L, var lastSeen: Long = 0L)

    private val peers = LinkedHashMap<String, State>()

    /** How many addresses are being tracked (bounded, so a flood of addresses cannot fill memory). */
    @Synchronized fun trackedPeers(): Int = peers.size

    /** Milliseconds until [peer] may try again, or 0 when it is not locked out. */
    @Synchronized fun lockedFor(peer: String, now: Long): Long {
        val s = peers[peer] ?: return 0L
        return if (now < s.lockedUntil) s.lockedUntil - now else 0L
    }

    /** Records a wrong code and returns how long [peer] is now locked for (0 when it still has tries left). */
    @Synchronized fun recordFailure(peer: String, now: Long): Long {
        val s = peers.getOrPut(peer) { State() }
        s.lastSeen = now
        if (now < s.lockedUntil) return s.lockedUntil - now          // already locked: no extension, no new strike
        if (s.failures > 0 && now - s.lastFailure > windowMs) s.failures = 0
        if (s.strikes > 0 && now - s.lastFailure > forgetStrikesMs) s.strikes = 0
        s.failures++
        s.lastFailure = now
        var locked = 0L
        if (s.failures >= maxFailures) {
            val factor = 1L shl s.strikes.coerceAtMost(20)
            locked = (baseLockMs * factor).coerceAtMost(maxLockMs)
            s.lockedUntil = now + locked
            s.strikes++
            s.failures = 0
        }
        trim()
        return locked
    }

    /** A correct code clears everything held against [peer]. */
    @Synchronized fun recordSuccess(peer: String) { peers.remove(peer) }

    private fun trim() {
        if (peers.size <= maxPeers) return
        // Drop the quietest addresses first.
        val victims = peers.entries.sortedBy { it.value.lastSeen }.take(peers.size - maxPeers).map { it.key }
        victims.forEach { peers.remove(it) }
    }
}
