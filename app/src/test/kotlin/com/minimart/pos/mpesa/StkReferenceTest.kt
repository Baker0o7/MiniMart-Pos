package com.minimart.pos.mpesa

import com.minimart.pos.data.repository.PaymentValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StkReferenceTest {
    private val id = "ws_CO_14102026051234567890123456"

    @Test fun derivedReferencePassesTheSaleValidator() =
        assertTrue(PaymentValidator.isWellFormedMpesaRef(StkReference.forPayment(null, id)))

    @Test fun isStableForTheSamePush() =
        assertEquals(StkReference.forPayment(null, id), StkReference.forPayment(null, id))

    @Test fun differsBetweenPushes() =
        assertNotEquals(StkReference.forPayment(null, id), StkReference.forPayment(null, id + "1"))

    @Test fun usesARealReceiptWhenDarajaGivesOne() =
        assertEquals("SJK4L9X2QP", StkReference.forPayment(" sjk4l9x2qp ", id))

    @Test fun ignoresAMalformedReceipt() =
        assertTrue(StkReference.forPayment("bad ref!", id).startsWith("STK"))
}
