package com.minimart.pos.data

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
import com.minimart.pos.data.repository.ProductRepository
import com.minimart.pos.data.repository.SaleRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/** A sale and its sync record are saved together or not at all, so no sale is ever lost to the main device. */
class SaleSyncAtomicityTest : DbTestBase() {
    private val repo by lazy {
        SaleRepository(
            db.saleDao(), ProductRepository(db.productDao()), CustomerRepository(db.customerDao(), db),
            BatchRepository(db.batchDao(), db.productDao()), db
        )
    }

    private fun sale() = Sale(receiptNumber = "A-1", subtotal = 100.0, taxAmount = 0.0, totalAmount = 100.0,
        amountPaid = 100.0, changeGiven = 0.0, cashPortion = 100.0, paymentMethod = PaymentMethod.CASH,
        cashierId = 1, createdAt = 1_000L)

    private fun item() = SaleItem(saleId = 0, productId = 0, productBarcode = "S1", productName = "Soda",
        unitPrice = 50.0, quantity = 2, lineTotal = 100.0)

    private fun log(id: Long) = SyncLog(entityType = SyncEntityType.SALE, entityId = id,
        operation = SyncOperation.INSERT, deviceId = "till-2", payload = "{}")

    private suspend fun stock(pid: Long) = db.productDao().getProductById(pid)?.stock

    @Test fun saleAndSyncRecordAreSavedTogether() = runBlocking {
        val pid = db.productDao().insertProduct(Product(barcode = "S1", name = "Soda", price = 50.0, stock = 10))
        val saleId = repo.completeSale(sale(), listOf(item().copy(productId = pid))) { db.syncDao().insertLog(log(it)) }
        assertEquals(1, db.syncDao().countPending())
        assertEquals(saleId, db.syncDao().getPendingLogs().single().entityId)
    }

    @Test fun ifQueueingFailsTheSaleIsNotSavedAndStockIsUntouched() = runBlocking {
        val pid = db.productDao().insertProduct(Product(barcode = "S1", name = "Soda", price = 50.0, stock = 10))
        try {
            repo.completeSale(sale(), listOf(item().copy(productId = pid))) { error("disk full") }
            fail("the failure must reach the caller")
        } catch (_: IllegalStateException) { }
        assertNull(db.saleDao().findByReceiptAndTime("A-1", 1_000L))
        assertEquals(10, stock(pid))
        assertEquals(0, db.syncDao().countPending())
    }

    @Test fun ifQueueingARefundFailsTheRefundIsRolledBack() = runBlocking {
        val pid = db.productDao().insertProduct(Product(barcode = "S1", name = "Soda", price = 50.0, stock = 10))
        val id = repo.completeSale(sale(), listOf(item().copy(productId = pid)))
        try {
            repo.refundSale(id, "wrong item") { error("disk full") }
            fail("the failure must reach the caller")
        } catch (_: IllegalStateException) { }
        assertEquals(SaleStatus.COMPLETED, db.saleDao().getSaleWithItems(id)?.sale?.status)
        assertEquals(8, stock(pid))
    }
}
