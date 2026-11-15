package com.minimart.pos.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanBufferTest {
    private fun ScanBuffer.type(text: String, start: Long, gap: Long = 5): Long {
        var t = start; text.forEach { printable(it, t); t += gap }; return t
    }

    @Test fun aFastScanEndedByEnterGivesTheBarcode() {
        val b = ScanBuffer(); val t = b.type("6001234567890", 1_000)
        val step = b.enter(t)
        assertTrue(step.consumed); assertEquals("6001234567890", step.barcode)
    }

    @Test fun aPauseThrowsAwayAHalfTypedCode() {
        val b = ScanBuffer(); b.type("123", 1_000)
        val t = b.type("98765", 2_000)                // more than 300 ms later: a new code
        assertEquals("98765", b.enter(t).barcode)
    }

    @Test fun aGapExactlyAtTheLimitKeepsTheCode() {
        val b = ScanBuffer(); b.printable('1', 1_000); b.printable('2', 1_300); b.printable('3', 1_600)
        assertEquals("123", b.enter(1_605).barcode)
    }

    @Test fun enterWithNothingTypedIsNotOursSoTheFormGetsIt() {
        assertFalse(ScanBuffer().enter(1_000).consumed)
    }

    @Test fun veryShortCodesAreConsumedButNotReported() {
        val b = ScanBuffer(); val t = b.type("12", 1_000)
        val step = b.enter(t)
        assertTrue(step.consumed); assertNull(step.barcode)
    }

    @Test fun symbolsAreIgnoredAndDashesAreKept() {
        val b = ScanBuffer(); val t = b.type("AB*-12 #3", 1_000)
        assertEquals("AB-123", b.enter(t).barcode)
    }

    @Test fun aCodeNeverGrowsPastTheLimit() {
        val b = ScanBuffer(maxLength = 10); val t = b.type("0123456789ABCDEF", 1_000)
        assertEquals("0123456789", b.enter(t).barcode)
    }

    @Test fun twoScansInARowAreSeparate() {
        val b = ScanBuffer()
        var t = b.type("111", 1_000); assertEquals("111", b.enter(t).barcode)
        t = b.type("222", t + 50);    assertEquals("222", b.enter(t).barcode)
    }

    @Test fun otherKeysAreNotConsumedButStillCountAsTimePassing() {
        val b = ScanBuffer(); b.type("123", 1_000)
        assertFalse(b.other(5_000).consumed)           // long pause, then an arrow key
        assertFalse(b.enter(5_001).consumed)           // the old digits are gone
    }

    @Test fun clearForgetsWhatWasTyped() {
        val b = ScanBuffer(); val t = b.type("12345", 1_000); b.clear()
        assertFalse(b.enter(t).consumed)
    }
}
