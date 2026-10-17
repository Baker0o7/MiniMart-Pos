package com.minimart.pos.sync

import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.Sale
import com.minimart.pos.data.entity.SaleItem
import com.minimart.pos.data.entity.SaleStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SyncPayloadsTest {
    private val sale = Sale(
        receiptNumber = "RCP-20261017-0001", subtotal = 100.0, taxAmount = 13.79, discountAmount = 5.0, totalAmount = 95.0,
        amountPaid = 100.0, changeGiven = 5.0, cashPortion = 95.0, paymentMethod = PaymentMethod.CASH,
        mpesaRef = null, cashierId = 3, notes = "café", createdAt = 1_800_000_000_000L
    )
    private val items = listOf(
        SaleItem(saleId = 9, productId = 4, productBarcode = "6001007519173", productName = "Coca-Cola 500ml",
            unitPrice = 50.0, quantity = 2, discountAmount = 5.0, taxAmount = 6.9, lineTotal = 95.0),
        SaleItem(saleId = 9, productId = 5, productBarcode = "2000123004567", productName = "Sugar",
            unitPrice = 180.0, quantity = 1, lineTotal = 90.0, weightKg = 0.5)
    )

    private fun created() = SyncPayloads.saleCreated(sale, items, "Amina", "0712345678", "juma", 0.0, 95.0)

    @Test fun aSaleSurvivesTheTripBetweenDevices() {
        val back = SyncPayloads.parseSale(JSONObject(created().toString()))
        assertEquals(sale.receiptNumber, back.sale.receiptNumber)
        assertEquals(95.0, back.sale.totalAmount, 1e-9)
        assertEquals(sale.createdAt, back.sale.createdAt)
        assertEquals("café", back.sale.notes)
        assertEquals(PaymentMethod.CASH, back.sale.paymentMethod)
        assertEquals("Amina", back.customerName)
        assertEquals("juma", back.cashierUsername)
        assertEquals(95.0, back.purchaseAmount, 1e-9)
    }

    @Test fun lineItemsKeepBarcodesAndWeights() {
        val back = SyncPayloads.parseSale(created())
        assertEquals(listOf("6001007519173", "2000123004567"), back.items.map { it.productBarcode })
        assertEquals(0.5, back.items[1].weightKg, 1e-9)
        assertEquals(2, back.items[0].quantity)
    }

    @Test fun deviceLocalIdsAreNeverTrusted() {
        val back = SyncPayloads.parseSale(created())
        assertEquals(0L, back.sale.id)
        assertEquals(0L, back.sale.cashierId)          // resolved from the username on the receiving device
        assertTrue(back.items.all { it.productId == 0L && it.saleId == 0L })
    }

    @Test fun aReceivedSaleIsAlwaysCompleted() {
        val tampered = created().put("status", "VOIDED")
        assertEquals(SaleStatus.COMPLETED, SyncPayloads.parseSale(tampered).sale.status)
    }

    @Test fun optionalFieldsMayBeMissing() {
        val o = created().apply { remove("customerName"); remove("customerPhone"); remove("cashierUsername"); remove("mpesaRef") }
        val back = SyncPayloads.parseSale(o)
        assertNull(back.customerName); assertNull(back.customerPhone); assertNull(back.cashierUsername); assertNull(back.sale.mpesaRef)
    }

    @Test fun requiredFieldsMissingIsRejected() {
        for (field in listOf("receiptNumber", "totalAmount", "createdAt", "items", "paymentMethod")) {
            try {
                SyncPayloads.parseSale(created().apply { remove(field) })
                fail("a payload without $field must be rejected")
            } catch (_: Exception) { }
        }
    }

    @Test fun anUnknownPaymentMethodIsRejected() {
        try { SyncPayloads.parseSale(created().put("paymentMethod", "BITCOIN")); fail() } catch (_: IllegalArgumentException) { }
    }

    @Test fun aNewerPayloadVersionIsRefusedRatherThanMisread() {
        try { SyncPayloads.parseSale(created().put("v", 2)); fail() } catch (_: IllegalArgumentException) { }
    }

    @Test fun reversalsRoundTrip() {
        val r = SyncPayloads.parseReversal(SyncPayloads.saleReversed(sale, SaleStatus.REFUNDED, "wrong size"))
        assertEquals("RCP-20261017-0001", r.receiptNumber)
        assertEquals(SaleStatus.REFUNDED, r.status)
        assertEquals("wrong size", r.reason)
        assertEquals(sale.createdAt, r.createdAt)
    }

    @Test fun aSaleIsNotMistakenForAReversalOrTheOtherWayRound() {
        try { SyncPayloads.parseReversal(created()); fail() } catch (_: IllegalArgumentException) { }
        try { SyncPayloads.parseSale(SyncPayloads.saleReversed(sale, SaleStatus.VOIDED, "")); fail() } catch (_: Exception) { }
    }
}
