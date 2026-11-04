package com.minimart.pos.data

import androidx.room.withTransaction
import com.minimart.pos.data.entity.MpesaPayment
import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.Product
import com.minimart.pos.data.entity.Sale
import com.minimart.pos.data.entity.SaleItem
import com.minimart.pos.data.entity.SaleStatus
import com.minimart.pos.data.entity.SyncEntityType
import com.minimart.pos.data.entity.SyncLog
import com.minimart.pos.data.entity.SyncOperation
import com.minimart.pos.data.repository.BatchRepository
import com.minimart.pos.data.repository.CustomerRepository
import com.minimart.pos.data.repository.IntegrityChecker
import com.minimart.pos.data.repository.ProductRepository
import com.minimart.pos.data.repository.SaleRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * M-Pesa payments, stock and the sync hand-over must stay consistent through every sale, refund and void,
 * and the integrity check must notice when they are not.
 */
class PaymentStockConsistencyTest : DbTestBase() {
    private val batches by lazy { BatchRepository(db.batchDao(), db.productDao()) }
    private val repo by lazy {
        SaleRepository(db.saleDao(), ProductRepository(db.productDao()), CustomerRepository(db.customerDao(), db), batches, db)
    }

    private fun sale(receipt: String, total: Double, method: PaymentMethod = PaymentMethod.CASH, ref: String? = null, at: Long = 1_000L) =
        Sale(receiptNumber = receipt, subtotal = total, taxAmount = 0.0, totalAmount = total, amountPaid = total,
            changeGiven = 0.0, cashPortion = if (method == PaymentMethod.CASH) total else 0.0,
            paymentMethod = method, mpesaRef = ref, cashierId = 1, createdAt = at)

    private fun line(pid: Long, barcode: String, qty: Int, price: Double = 50.0) =
        SaleItem(saleId = 0, productId = pid, productBarcode = barcode, productName = "Item $barcode",
            unitPrice = price, quantity = qty, lineTotal = price * qty)

    private suspend fun product(barcode: String, stock: Int): Long {
        val id = db.productDao().insertProduct(Product(barcode = barcode, name = "Item $barcode", price = 50.0, stock = stock))
        batches.addBatch(id, stock.toDouble(), 30.0, "test")
        return id
    }

    private suspend fun stock(pid: Long) = db.productDao().getProductById(pid)?.stock
    private fun issues() = IntegrityChecker(db).check().issues

    // ── M-Pesa ───────────────────────────────────────────────────────────────

    @Test fun aRefundedSalesCodeCannotPayForNewGoods() = runBlocking {
        val pid = product("M1", 10)
        val id = repo.completeSale(sale("R-1", 50.0, PaymentMethod.MPESA, "QWE123ABCD"), listOf(line(pid, "M1", 1)))
        repo.refundSale(id, "returned")
        try {
            repo.completeSale(sale("R-2", 50.0, PaymentMethod.MPESA, "QWE123ABCD", at = 2_000L), listOf(line(pid, "M1", 1)))
            fail("the money for this code was already handed back")
        } catch (_: IllegalArgumentException) { }
        assertEquals(10, stock(pid))
    }

    @Test fun aVoidedSalesCodeCanBeUsedWhenTheSaleIsRungAgain() = runBlocking {
        val pid = product("M2", 10)
        val id = repo.completeSale(sale("R-1", 50.0, PaymentMethod.MPESA, "QWE123ABCD"), listOf(line(pid, "M2", 1)))
        repo.voidSale(id, "rung wrong")
        repo.completeSale(sale("R-2", 50.0, PaymentMethod.MPESA, "QWE123ABCD", at = 2_000L), listOf(line(pid, "M2", 1)))
        assertEquals(9, stock(pid))
    }

    @Test fun aCodeTypedInLowerCaseIsStillTheSameCode() = runBlocking {
        val pid = product("M3", 10)
        repo.completeSale(sale("R-1", 50.0, PaymentMethod.MPESA, "QWE123ABCD"), listOf(line(pid, "M3", 1)))
        try {
            repo.completeSale(sale("R-2", 50.0, PaymentMethod.MPESA, "qwe123abcd", at = 2_000L), listOf(line(pid, "M3", 1)))
            fail("codes are not case sensitive")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun aSaleLargerThanItsRecordedPaymentIsRejectedAtTheTill() = runBlocking {
        val pid = product("M4", 10)
        db.mpesaPaymentDao().insert(MpesaPayment(code = "PAY1234567", amount = 40.0, senderName = "A", senderPhone = "0700",
            receivedAt = 1L, rawMessage = "x"))
        try {
            repo.completeSale(sale("R-1", 50.0, PaymentMethod.MPESA, "PAY1234567"), listOf(line(pid, "M4", 1)))
            fail("40 was paid, 50 is due")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun theIntegrityCheckFlagsAnMpesaSaleThatWasNeverFullyPaid() = runBlocking {
        val pid = product("M5", 10)
        db.mpesaPaymentDao().insert(MpesaPayment(code = "PAY7654321", amount = 40.0, senderName = "A", senderPhone = "0700",
            receivedAt = 1L, rawMessage = "x"))
        // Saved directly, as if the payment record was corrected after the sale.
        db.saleDao().insertSaleWithItems(sale("R-1", 50.0, PaymentMethod.MPESA, "PAY7654321"), listOf(line(pid, "M5", 1)))
        assertTrue(issues().any { it.contains("M-Pesa payment was only") })
    }

    // ── Stock and sales ──────────────────────────────────────────────────────

    @Test fun aSaleTakesStockAndBatchTogetherAndTheCheckIsClean() = runBlocking {
        val pid = product("S1", 10)
        repo.completeSale(sale("R-1", 150.0), listOf(line(pid, "S1", 3)))
        assertEquals(7, stock(pid))
        assertEquals(7.0, db.batchDao().openBatches(pid).sumOf { it.remaining }, 0.0001)
        assertEquals(emptyList<String>(), issues())
    }

    @Test fun aRefundPutsStockAndBatchBackExactlyOnce() = runBlocking {
        val pid = product("S2", 10)
        val id = repo.completeSale(sale("R-1", 150.0), listOf(line(pid, "S2", 3)))
        repo.refundSale(id, "returned"); repo.refundSale(id, "again"); repo.voidSale(id, "and again")
        assertEquals(10, stock(pid))
        assertEquals(10.0, db.batchDao().openBatches(pid).sumOf { it.remaining }, 0.0001)
        assertEquals(SaleStatus.REFUNDED, db.saleDao().getSaleWithItems(id)?.sale?.status)
        assertEquals(emptyList<String>(), issues())
    }

    @Test fun aSaleAcrossTwoBatchesIsRestoredToTheSameBatches() = runBlocking {
        val pid = db.productDao().insertProduct(Product(barcode = "S3", name = "Item", price = 50.0, stock = 10))
        batches.addBatch(pid, 4.0, 20.0, "old"); batches.addBatch(pid, 6.0, 30.0, "new")
        val id = repo.completeSale(sale("R-1", 250.0), listOf(line(pid, "S3", 5)))
        assertEquals(listOf(5.0), db.batchDao().openBatches(pid).map { it.remaining })
        repo.voidSale(id, "cancel")
        assertEquals(listOf(4.0, 6.0), db.batchDao().openBatches(pid).map { it.remaining })
        assertEquals(emptyList<String>(), issues())
    }

    @Test fun theCheckNoticesABatchThatHoldsMoreThanItShouldAfterASale() = runBlocking {
        val pid = product("S4", 10)
        repo.completeSale(sale("R-1", 150.0), listOf(line(pid, "S4", 3)))
        db.openHelper.writableDatabase.execSQL("UPDATE stock_batches SET remaining = quantity WHERE productId = $pid")
        assertTrue(issues().any { it.contains("hold more than was received") })
    }

    @Test fun theCheckNoticesStockStillHeldByAVoidedSale() = runBlocking {
        val pid = product("S5", 10)
        val id = repo.completeSale(sale("R-1", 150.0), listOf(line(pid, "S5", 3)))
        db.openHelper.writableDatabase.execSQL("UPDATE sales SET status = 'VOIDED' WHERE id = $id")
        assertTrue(issues().any { it.contains("still held by refunded or voided") })
    }

    @Test fun theCheckNoticesALineWhoseStockWasNotFullyTaken() = runBlocking {
        val pid = product("S6", 10)
        repo.completeSale(sale("R-1", 150.0), listOf(line(pid, "S6", 3)))
        db.openHelper.writableDatabase.execSQL("UPDATE sale_item_costs SET quantity = 1")
        assertTrue(issues().any { it.contains("stock was taken for") })
    }

    @Test fun aSaleThatCannotGetStockLeavesNothingBehind() = runBlocking {
        val pid = product("S7", 2)
        try {
            repo.completeSale(sale("R-1", 150.0), listOf(line(pid, "S7", 3)))
            fail("only 2 in stock")
        } catch (_: IllegalStateException) { }
        assertEquals(2, stock(pid))
        assertEquals(2.0, db.batchDao().openBatches(pid).sumOf { it.remaining }, 0.0001)
        assertEquals(0, db.saleDao().saleCount())
    }

    // ── Offline sync hand-over (what SyncApplier does for each queued sale) ──

    private fun log(receipt: String) = SyncLog(entityType = SyncEntityType.SALE, entityId = 1, operation = SyncOperation.CREATE,
        deviceId = "till-2", payload = receipt, createdAt = 5_000L)

    private suspend fun applyFromTill(receipt: String, s: Sale, items: List<SaleItem>): Boolean = db.withTransaction {
        if (db.syncDao().insertLogIfNew(log(receipt)) == null) false
        else { repo.applyRemoteSale(s, items, null, null, 0.0, 0.0); true }
    }

    @Test fun aRetriedPushDoesNotSellTheGoodsTwice() = runBlocking {
        val pid = product("Y1", 10)
        val s = sale("T2-1", 100.0, at = 5_000L)
        assertTrue(applyFromTill("T2-1", s, listOf(line(pid, "Y1", 2))))
        assertFalse(applyFromTill("T2-1", s, listOf(line(pid, "Y1", 2))))   // connection dropped, till sent it again
        assertEquals(8, stock(pid))
        assertEquals(1, db.saleDao().saleCount())
    }

    @Test fun aRejectedSaleLeavesNoSyncRecordSoItCanBeRetriedAfterItIsFixed() = runBlocking {
        val pid = product("Y2", 10)
        val bad = sale("T2-2", 1.0, at = 5_000L)   // total does not match the items
        try { applyFromTill("T2-2", bad, listOf(line(pid, "Y2", 2))); fail("must be rejected") } catch (_: IllegalArgumentException) { }
        assertEquals(0, db.syncDao().countMatchingLog("till-2", SyncEntityType.SALE, 1, SyncOperation.CREATE, 5_000L))
        assertEquals(10, stock(pid))
        assertTrue(applyFromTill("T2-2", sale("T2-2", 100.0, at = 5_000L), listOf(line(pid, "Y2", 2))))
        assertEquals(8, stock(pid))
    }

    @Test fun twoTillsUsingTheSameMpesaCodeOnlyTheFirstIsAccepted() = runBlocking {
        val pid = product("Y3", 10)
        repo.applyRemoteSale(sale("T2-3", 50.0, PaymentMethod.MPESA, "ABC123DEFG", 1_000L), listOf(line(pid, "Y3", 1)), null, null, 0.0, 0.0)
        try {
            repo.applyRemoteSale(sale("T3-1", 50.0, PaymentMethod.MPESA, "ABC123DEFG", 2_000L), listOf(line(pid, "Y3", 1)), null, null, 0.0, 0.0)
            fail("same code, second till")
        } catch (_: IllegalArgumentException) { }
        assertEquals(9, stock(pid))
        assertEquals(emptyList<String>(), issues())
    }
}
