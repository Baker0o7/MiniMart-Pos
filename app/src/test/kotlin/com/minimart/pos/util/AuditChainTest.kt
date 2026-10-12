package com.minimart.pos.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditChainTest {
    private fun chain(vararg bodies: String): List<String> {
        var prev = "genesis"
        return bodies.map { b -> val h = AuditLogger.hashOf(prev, b); prev = h; "$b | h=$h" }
    }

    @Test fun intactChainVerifies() {
        val r = AuditLogger.verifyLines(chain("[t] A | user=x | one", "[t] B | user=x | two", "[t] C | user=y | three"))
        assertTrue(r.ok); assertEquals(3, r.entries)
    }

    @Test fun editedLineIsDetected() {
        val lines = chain("[t] A | user=x | one", "[t] B | user=x | two", "[t] C | user=y | three").toMutableList()
        lines[1] = lines[1].replace("two", "TWO")
        val r = AuditLogger.verifyLines(lines)
        assertFalse(r.ok); assertEquals(2, r.brokenAtLine)
    }

    @Test fun removedLineIsDetected() {
        val lines = chain("[t] A | user=x | one", "[t] B | user=x | two", "[t] C | user=y | three").toMutableList()
        lines.removeAt(1)
        assertFalse(AuditLogger.verifyLines(lines).ok)
    }

    @Test fun legacyLinesWithoutHashAreSkipped() {
        val lines = listOf("[t] OLD | user=x | no hash") + chain("[t] A | user=x | one")
        assertTrue(AuditLogger.verifyLines(lines).ok)
    }

    @Test fun rotatedFileContinuesChain() {
        val first = chain("[t] A | user=x | one")
        val lastHash = first.last().substringAfter("h=")
        val marker = "[t] LOG_ROTATED | user= | prev=$lastHash"
        val h = AuditLogger.hashOf(lastHash, marker)
        val h2 = AuditLogger.hashOf(h, "[t] B | user=x | two")
        val lines = listOf("$marker | h=$h", "[t] B | user=x | two | h=$h2")
        assertTrue(AuditLogger.verifyLines(lines).ok)
    }
}
