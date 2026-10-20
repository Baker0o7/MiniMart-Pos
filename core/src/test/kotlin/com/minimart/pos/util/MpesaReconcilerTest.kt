package com.minimart.pos.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MpesaReconcilerTest {
    private fun pay(code: String, amount: Double, at: Long = 1000) = PaymentRecord(code, amount, at)
    private fun sale(id: Long, code: String, total: Double, only: Boolean = true) =
        MpesaSaleRecord(id, "RCP-$id", code, total, only, createdAt = id)

    @Test fun cleanDayHasNothingToFix() {
        val r = MpesaReconciler.reconcile(listOf(pay("ABC123XYZ1", 500.0)), listOf(sale(1, "abc123xyz1", 500.0)))
        assertEquals(1, r.matched.size)
        assertFalse(r.needsAttention)
    }

    @Test fun paymentWithNoSaleIsUnclaimed() {
        val r = MpesaReconciler.reconcile(listOf(pay("AAA", 300.0), pay("BBB", 200.0)), listOf(sale(1, "AAA", 300.0)))
        assertEquals(listOf("BBB"), r.unclaimedPayments.map { it.code })
        assertEquals(200.0, r.unclaimedTotal, 0.001)
    }

    @Test fun saleWithNoMessageIsUnverified() {
        val r = MpesaReconciler.reconcile(emptyList(), listOf(sale(1, "ZZZ", 450.0)))
        assertEquals(1, r.unverifiedSales.size)
        assertEquals(450.0, r.unverifiedTotal, 0.001)
    }

    @Test fun wrongAmountOnAnMpesaOnlySaleIsFlagged() {
        val r = MpesaReconciler.reconcile(listOf(pay("AAA", 400.0)), listOf(sale(1, "AAA", 500.0)))
        assertEquals(1, r.mismatches.size)
        assertEquals(-100.0, r.mismatches.single().difference, 0.001)
    }

    @Test fun smallRoundingIsTolerated() {
        val r = MpesaReconciler.reconcile(listOf(pay("AAA", 499.8)), listOf(sale(1, "AAA", 500.0)))
        assertTrue(r.mismatches.isEmpty())
    }

    @Test fun splitSaleOnlyNeedsPaymentNotToExceedTotal() {
        assertTrue(MpesaReconciler.reconcile(listOf(pay("AAA", 200.0)), listOf(sale(1, "AAA", 500.0, only = false))).mismatches.isEmpty())
        assertEquals(1, MpesaReconciler.reconcile(listOf(pay("AAA", 900.0)), listOf(sale(1, "AAA", 500.0, only = false))).mismatches.size)
    }

    @Test fun oneCodeOnTwoSalesIsReported() {
        val r = MpesaReconciler.reconcile(listOf(pay("AAA", 500.0)), listOf(sale(1, "AAA", 500.0), sale(2, "AAA", 500.0)))
        assertEquals(setOf("AAA"), r.reusedCodes.keys)
        assertTrue(r.needsAttention)
    }

    @Test fun blankCodesAreIgnored() {
        val r = MpesaReconciler.reconcile(emptyList(), listOf(sale(1, "  ", 100.0)))
        assertTrue(r.unverifiedSales.isEmpty())
    }
}
