package com.minimart.pos.data

import com.minimart.pos.data.repository.MpesaVerdict
import com.minimart.pos.data.repository.MpesaVerifier
import org.junit.Assert.*
import org.junit.Test

class MpesaVerifierTest {
    private fun v(ref: String, due: Double = 500.0, got: Double? = null, used: Int = 0, on: Boolean = true) =
        MpesaVerifier.assess(ref, due, got, used, on).verdict

    @Test fun blankIsEmpty() = assertEquals(MpesaVerdict.EMPTY, v("  "))
    @Test fun shortCodeIsBadFormat() = assertEquals(MpesaVerdict.BAD_FORMAT, v("AB12"))
    @Test fun usedCodeIsRejectedEvenIfReceived() = assertEquals(MpesaVerdict.ALREADY_USED, v("SJK4H2LMNO", got = 500.0, used = 1))
    @Test fun matchingPaymentVerifies() = assertEquals(MpesaVerdict.VERIFIED, v("SJK4H2LMNO", got = 500.0))
    @Test fun overpaymentVerifies() = assertEquals(MpesaVerdict.VERIFIED, v("SJK4H2LMNO", got = 650.0))
    @Test fun shortPaymentIsUnderpaid() = assertEquals(MpesaVerdict.UNDERPAID, v("SJK4H2LMNO", got = 499.0))
    @Test fun centRoundingTolerated() = assertEquals(MpesaVerdict.VERIFIED, v("SJK4H2LMNO", got = 499.995))
    @Test fun unseenWithTrackingIsNotReceived() = assertEquals(MpesaVerdict.NOT_RECEIVED, v("SJK4H2LMNO"))
    @Test fun unseenWithoutTrackingIsUnchecked() = assertEquals(MpesaVerdict.UNCHECKED, v("SJK4H2LMNO", on = false))
    @Test fun strictOnlyPassesVerified() {
        assertTrue(MpesaVerifier.assess("SJK4H2LMNO", 100.0, 100.0, 0, true).passesStrict)
        assertFalse(MpesaVerifier.assess("SJK4H2LMNO", 100.0, null, 0, false).passesStrict)
    }

    private val now = 1_000_000_000_000L
    private fun at(ageMs: Long) = MpesaVerifier.assess("SJK4H2LMNO", 500.0, 500.0, 0, true, now - ageMs, now)

    @Test fun recentPaymentVerifies() = assertEquals(MpesaVerdict.VERIFIED, at(10 * 60_000L).verdict)
    @Test fun paymentFromHoursAgoIsStale() = assertEquals(MpesaVerdict.STALE, at(MpesaVerifier.STALE_AFTER_MS + 1).verdict)
    @Test fun staleIsAWarningNotAStop() {
        val a = at(MpesaVerifier.STALE_AFTER_MS + 1)
        assertTrue(a.passesStrict)
        assertFalse(a.isBlocking)
    }
    @Test fun unknownTimeIsNeverStale() {
        assertEquals(MpesaVerdict.VERIFIED, MpesaVerifier.assess("SJK4H2LMNO", 500.0, 500.0, 0, true, null, now).verdict)
        assertEquals(MpesaVerdict.VERIFIED, MpesaVerifier.assess("SJK4H2LMNO", 500.0, 500.0, 0, true, 0L, now).verdict)
    }
    @Test fun underpaidBeatsStale() =
        assertEquals(MpesaVerdict.UNDERPAID, MpesaVerifier.assess("SJK4H2LMNO", 500.0, 100.0, 0, true, 1L, now).verdict)
}
