package com.minimart.pos.util

import org.junit.Assert.*
import org.junit.Test

/** Awkward real-world M-Pesa inputs: masked numbers, odd spacing, wrong clocks, repeated and reused codes. */
class MpesaEdgeCasesTest {
    private val now = 1_800_000_000_000L
    private fun inbox(amount: Double = 250.0, name: String = "Jane", paidAt: Long = now - 1000, code: String = "SKL1ABC2DE") =
        InboxPayment(1, code, amount, name, "254712345678", paidAt)

    // --- SMS parser ---
    @Test fun maskedPhoneIsNotLeftInTheName() {
        val p = MpesaSmsParser.parse("QK12ABC345 Confirmed. You have received Ksh1,000.00 from JOHN DOE 0712***456 on 12/1/26 at 3:15 PM New M-PESA balance is Ksh5,000.00.")!!
        assertEquals("JOHN DOE", p.senderName)
        assertEquals("0712***456", p.senderPhone)
        assertEquals(1000.0, p.amount, 0.001)
    }

    @Test fun spaceAfterCurrencyAndLineBreaksAreFine() {
        val p = MpesaSmsParser.parse("QK12ABC345 Confirmed.\nYou have received Ksh 1,250.50\nfrom  JANE   WANJIKU 0722000111  on 3/2/26 at 12:05 AM.")!!
        assertEquals(1250.5, p.amount, 0.001)
        assertEquals("JANE WANJIKU", p.senderName)
        assertNotNull(p.timestamp)
    }

    @Test fun zeroAmountReversalsAndLowercaseCodesAreIgnored() {
        assertNull(MpesaSmsParser.parse("QK12ABC345 Confirmed. You have received Ksh0.00 from JOHN 0712345678 on 12/1/26 at 3:15 PM"))
        assertNull(MpesaSmsParser.parse("QK12ABC345 Confirmed. Reversal of transaction ABC123 completed. Ksh500.00 received from JOHN 0712345678 on 12/1/26 at 3:15 PM x".replace("Confirmed. Reversal", "Reversal")))
        assertNull(MpesaSmsParser.parse("qk12abc345 Confirmed. You have received Ksh100.00 from JOHN 0712345678 on 12/1/26 at 3:15 PM"))
    }

    @Test fun impossibleDateStillRecordsThePaymentWithoutATime() {
        val p = MpesaSmsParser.parse("QK12ABC345 Confirmed. You have received Ksh100.00 from JOHN 0712345678 on 31/2/26 at 3:15 PM")!!
        assertNull(p.timestamp)
    }

    @Test fun timeInTheTextIsUsedUnlessItIsInTheFuture() {
        assertEquals(now - 5000, MpesaSmsParser.paidAt(now - 5000, now - 1, now))
        assertEquals(now - 1, MpesaSmsParser.paidAt(now + 3L * 86_400_000, now - 1, now))
        assertEquals(now - 1, MpesaSmsParser.paidAt(null, now - 1, now))
    }

    // --- webhook ---
    @Test fun amountsAreRoundedToWholeCents() {
        assertEquals(100.99, MpesaWebhook.sanitize(inbox(amount = 100.994), now)!!.amount, 0.0)
        assertNull(MpesaWebhook.sanitize(inbox(amount = 0.004), now))
    }

    @Test fun controlCharactersAreRemovedFromNames() {
        assertEquals("Jane Doe", MpesaWebhook.sanitize(inbox(name = "Jane\n\u0000 Doe".replace("\n\u0000 ", " ")), now)!!.name)
        assertEquals("JaneDoe", MpesaWebhook.sanitize(inbox(name = "Jane\u0007\nDoe"), now)!!.name)
        assertEquals(60, MpesaWebhook.sanitize(inbox(name = "x".repeat(500)), now)!!.name.length)
    }

    @Test fun tokenWithSymbolsOrPlainHttpGivesNoCallbackAddress() {
        val token = "a".repeat(32)
        assertNotNull(MpesaWebhook.callbackUrl("https://x.supabase.co/", token))
        assertNull(MpesaWebhook.callbackUrl("http://x.supabase.co", token))
        assertNull(MpesaWebhook.callbackUrl("https://x.supabase.co", "a".repeat(31)))
        assertNull(MpesaWebhook.callbackUrl("https://x.supabase.co", "a".repeat(31) + "&"))
    }

    // --- reconciler ---
    private fun pay(code: String, amount: Double) = PaymentRecord(code, amount, 1000)
    private fun sale(id: Long, code: String, total: Double, only: Boolean = true) = MpesaSaleRecord(id, "R$id", code, total, only, 1000)

    @Test fun codesMatchWhateverTheCaseOrSpacing() {
        val r = MpesaReconciler.reconcile(listOf(pay("SKL1ABC2DE", 100.0)), listOf(sale(1, " skl1abc2de ", 100.0)))
        assertFalse(r.needsAttention)
    }

    @Test fun differenceOfExactlyHalfAShillingIsTolerated() {
        assertFalse(MpesaReconciler.reconcile(listOf(pay("A1", 100.5)), listOf(sale(1, "A1", 100.0))).needsAttention)
        assertTrue(MpesaReconciler.reconcile(listOf(pay("A1", 100.51)), listOf(sale(1, "A1", 100.0))).mismatches.isNotEmpty())
    }

    @Test fun underpaidSplitSaleIsNotAMismatchButOverpaidIs() {
        assertTrue(MpesaReconciler.reconcile(listOf(pay("A1", 40.0)), listOf(sale(1, "A1", 100.0, only = false))).mismatches.isEmpty())
        assertEquals(1, MpesaReconciler.reconcile(listOf(pay("A1", 160.0)), listOf(sale(1, "A1", 100.0, only = false))).mismatches.size)
    }

    @Test fun sameMessageStoredTwiceDoesNotCreateAProblem() {
        val r = MpesaReconciler.reconcile(listOf(pay("A1", 100.0), pay("A1", 100.0)), listOf(sale(1, "A1", 100.0)))
        assertFalse(r.needsAttention)
    }
}
