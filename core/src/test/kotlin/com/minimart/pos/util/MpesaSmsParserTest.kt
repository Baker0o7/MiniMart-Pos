package com.minimart.pos.util

import org.junit.Assert.*
import org.junit.Test

class MpesaSmsParserTest {
    @Test fun personalReceive() {
        val p = MpesaSmsParser.parse("UJ71ABC234 Confirmed. You have received Ksh1,500.00 from JOHN DOE 0712345678 on 7/10/26 at 2:15 PM. New M-PESA balance is Ksh9,000.00.")!!
        assertEquals("UJ71ABC234", p.code)
        assertEquals(1500.0, p.amount, 0.001)
        assertEquals("JOHN DOE", p.senderName)
        assertEquals("0712345678", p.senderPhone)
        assertNotNull(p.timestamp)
    }

    @Test fun tillReceive() {
        val p = MpesaSmsParser.parse("UJ71ABC234 Confirmed.Ksh500.50 received from 254712345678 JANE WANJIKU on 7/10/26 at 11:05 AM. New Account balance is Ksh1,000.00")!!
        assertEquals(500.5, p.amount, 0.001)
        assertEquals("JANE WANJIKU", p.senderName)
        assertEquals("254712345678", p.senderPhone)
    }

    @Test fun ignoresOutgoingAndOther() {
        assertNull(MpesaSmsParser.parse("UJ71ABC234 Confirmed. Ksh200.00 sent to SHOP 0722000000 on 7/10/26 at 2:15 PM."))
        assertNull(MpesaSmsParser.parse("Dear customer, win big with Ksh1,000 received from nowhere"))
        assertNull(MpesaSmsParser.parse("hello"))
    }

    @Test fun trustedSenderOnly() {
        assertTrue(MpesaSmsParser.isTrustedSender("MPESA"))
        assertTrue(MpesaSmsParser.isTrustedSender("Mpesa"))
        assertFalse(MpesaSmsParser.isTrustedSender("+254712345678"))
        assertFalse(MpesaSmsParser.isTrustedSender(null))
    }
}
