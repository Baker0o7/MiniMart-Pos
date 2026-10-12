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
}
