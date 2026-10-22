package com.minimart.pos.util

import org.junit.Assert.*
import org.junit.Test

class MpesaWebhookTest {
    private val now = 1_800_000_000_000L
    private fun pay(code: String = "SKL1ABC2DE", amount: Double = 250.0, phone: String = "254712345678", paidAt: Long = now - 1000) =
        InboxPayment(1, code, amount, " Jane Doe ", phone, paidAt)

    @Test fun goodPaymentIsKeptAndTidied() {
        val p = MpesaWebhook.sanitize(pay(code = " skl1abc2de "), now)!!
        assertEquals("SKL1ABC2DE", p.code); assertEquals("Jane Doe", p.name)
    }

    @Test fun badCodesAndAmountsAreRejected() {
        assertNull(MpesaWebhook.sanitize(pay(code = "SHORT"), now))
        assertNull(MpesaWebhook.sanitize(pay(code = "ABC DEF123"), now))
        assertNull(MpesaWebhook.sanitize(pay(code = "ABCDEFGHIJKLM"), now))
        assertNull(MpesaWebhook.sanitize(pay(amount = 0.0), now))
        assertNull(MpesaWebhook.sanitize(pay(amount = -5.0), now))
        assertNull(MpesaWebhook.sanitize(pay(amount = Double.NaN), now))
        assertNull(MpesaWebhook.sanitize(pay(amount = Double.POSITIVE_INFINITY), now))
        assertNull(MpesaWebhook.sanitize(pay(amount = 1_000_001.0), now))
    }

    @Test fun absurdTimesFallBackToArrival() {
        assertEquals(now, MpesaWebhook.sanitize(pay(paidAt = now + 5L * 86_400_000), now)!!.paidAtMs)
        assertEquals(now, MpesaWebhook.sanitize(pay(paidAt = 0), now)!!.paidAtMs)
        assertEquals(now - 1000, MpesaWebhook.sanitize(pay(), now)!!.paidAtMs)
    }

    @Test fun hashedOrMessyPhonesAreCleaned() {
        assertEquals("", MpesaWebhook.sanitize(pay(phone = ""), now)!!.phone)
        assertEquals("254712345678", MpesaWebhook.sanitize(pay(phone = "2547 1234-5678"), now)!!.phone)
        assertTrue(MpesaWebhook.sanitize(pay(phone = "9".repeat(40)), now)!!.phone.length <= 15)
    }

    @Test fun callbackUrlIsBuiltOnlyFromSafeParts() {
        val token = "a".repeat(48)
        assertEquals("https://abc.supabase.co/functions/v1/till-payments?t=$token", MpesaWebhook.callbackUrl("https://abc.supabase.co/", token))
        assertNull(MpesaWebhook.callbackUrl("http://abc.supabase.co", token))
        assertNull(MpesaWebhook.callbackUrl("https://abc.supabase.co", "short"))
        assertNull(MpesaWebhook.callbackUrl("https://abc.supabase.co", "a".repeat(40) + "&x=1&&&&"))
    }

    @Test fun addressAvoidsWordsSafaricomRefuses() {
        val url = MpesaWebhook.callbackUrl("https://abc.supabase.co", "0123456789abcdef".repeat(3))!!.lowercase()
        listOf("mpesa", "safaricom", "sql", "exe", "cmd").forEach { assertFalse(it, url.contains(it)) }
    }

    @Test fun summaryReadsNaturally() {
        assertEquals("No new payments.", MpesaWebhook.summary(0, 0, 0))
        assertEquals("3 new payment(s) recorded, 1 already known, 2 ignored as invalid.", MpesaWebhook.summary(3, 1, 2))
    }
}
