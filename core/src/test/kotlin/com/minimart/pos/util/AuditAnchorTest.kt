package com.minimart.pos.util

import org.junit.Assert.*
import org.junit.Test

class AuditAnchorTest {
    @Test fun roundTrip() = assertEquals(AuditAnchor(5, "abc"), AuditAnchor.decode(AuditAnchor(5, "abc").encode()))
    @Test fun badInputDecodesToNull() {
        listOf(null, "", "x", "5:", "-1:a", "a:b", "1:2:3").forEach { assertNull(it, AuditAnchor.decode(it)) }
    }
    @Test fun noAnchorIsFine() = assertNull(AuditAnchor.check(null, 0, null))
    @Test fun intactLogPasses() = assertNull(AuditAnchor.check(AuditAnchor(3, "h"), 4, "h"))
    @Test fun deletedTailDetected() = assertNotNull(AuditAnchor.check(AuditAnchor(5, "h"), 3, "h"))
    @Test fun rewrittenLineDetected() = assertNotNull(AuditAnchor.check(AuditAnchor(3, "h"), 3, "other"))
}
