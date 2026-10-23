package com.minimart.pos.util

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ReplayCacheTest {
    @Test fun secondUseRejected() {
        val c = ReplayCache()
        assertTrue(c.accept("a", 2_000, 1_000))
        assertFalse(c.accept("a", 2_000, 1_001))
    }
    @Test fun expiredEntriesForgotten() {
        val c = ReplayCache()
        c.accept("a", 2_000, 1_000)
        assertTrue(c.accept("a", 9_000, 2_000))
    }
    @Test fun failsClosedWhenFull() {
        val c = ReplayCache(maxEntries = 2)
        assertTrue(c.accept("a", 9_000, 1)); assertTrue(c.accept("b", 9_000, 1))
        assertFalse(c.accept("c", 9_000, 1))
    }
    @Test fun survivesRestart() {
        val f = File.createTempFile("replay", ".txt"); f.deleteOnExit()
        val now = System.currentTimeMillis()
        val a = ReplayCache(file = f)
        assertTrue(a.accept("sig1", now + 60_000, now))
        a.flush(now)
        val b = ReplayCache(file = f)
        assertFalse(b.accept("sig1", now + 60_000, now + 1))
        assertTrue(b.accept("sig2", now + 60_000, now + 1))
    }
    @Test fun ignoresCorruptFile() {
        val f = File.createTempFile("replay", ".txt"); f.deleteOnExit()
        f.writeText("garbage\nx\tnotanumber\n\t\n")
        assertEquals(0, ReplayCache(file = f).size())
    }
}
