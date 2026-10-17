package com.minimart.pos.data

import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.Product
import com.minimart.pos.data.entity.Sale
import com.minimart.pos.data.entity.SaleItem
import com.minimart.pos.data.entity.SaleStatus
import com.minimart.pos.data.repository.BatchRepository
import com.minimart.pos.data.repository.CustomerRepository
import com.minimart.pos.data.repository.ProductRepository
import com.minimart.pos.data.repository.SaleRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/** A sale arriving from another till goes through the same checks as one rung up here. */
class RemoteSaleTest : DbTestBase() {
    private val repo by lazy {
        SaleRepository(
            db.saleDao(), ProductRepository(db.productDao()), CustomerRepository(db.customerDao(), db),
            BatchRepository(db.batchDao(), db.productDao()), db
        )
    }

    private fun sale(receipt: String, total: Double, method: PaymentMethod = PaymentMethod.CASH, ref: String? = null, at: Long = 1_000L) =
        Sale(receiptNumber = receipt, subtotal = total, taxAmount = 0.0, totalAmount = total, amountPaid = total,
            changeGiven = 0.0, cashPortion = if (method == PaymentMethod.CASH) total else 0.0,
            paymentMethod = method, mpesaRef = ref, cashierId = 1, createdAt = at)

    private fun line(barcode: String, qty: Int, price: Double = 50.0, lineTotal: Double = price * qty) =
        SaleItem(saleId = 0, productId = 0, productBarcode = barcode, productName = "Item $barcode",
            unitPrice = price, quantity = qty, lineTotal = lineTotal)

    private suspend fun apply(s: Sale, items: List<SaleItem>) =
        repo.applyRemoteSale(s, items, customerName = null, customerPhone = null, creditAmount = 0.0, purchaseAmount = 0.0)

    @Test fun knownProductHasItsStockReduced() = runBlocking {
        val pid = db.productDao().insertProduct(Product(barcode = "A1", name = "Soda", price = 50.0, stock = 10))
        apply(sale("R-1", 100.0), listOf(line("A1", 2)))
        assertEquals(8, db.productDao().getProductById(pid)?.stock)
    }

    @Test fun stockNeverGoesNegativeBecauseTheGoodsAlreadyLeftTheShop() = runBlocking {
        val pid = db.productDao().insertProduct(Product(barcode = "A2", name = "Soda", price = 50.0, stock = 1))
        apply(sale("R-2", 150.0), listOf(line("A2", 3)))
        assertEquals(0, db.productDao().getProductById(pid)?.stock)
    }

    @Test fun anUnknownBarcodeGetsAStubProductSoHistoryStaysIntact() = runBlocking {
        val id = apply(sale("R-3", 50.0), listOf(line("NEW-9", 1)))
        assertNotNull(db.productDao().getAnyByBarcode("NEW-9"))
        assertEquals(1, db.saleDao().getSaleWithItems(id)?.items?.size)
    }

    @Test fun aTamperedTotalIsRejectedAndNothingIsStored() = runBlocking {
        val pid = db.productDao().insertProduct(Product(barcode = "A4", name = "Soda", price = 50.0, stock = 10))
        try {
            apply(sale("R-4", 1.0), listOf(line("A4", 2))) // two sodas "cost" 1 shilling
            fail("a total that does not match the items must be rejected")
        } catch (_: IllegalArgumentException) { }
        assertEquals(10, db.productDao().getProductById(pid)?.stock)
        assertNull(db.saleDao().findByReceiptAndTime("R-4", 1_000L))
    }

    @Test fun aNegativeQuantityIsRejected() = runBlocking {
        try {
            apply(sale("R-5", 50.0), listOf(line("A5", -1, lineTotal = 50.0)))
            fail("negative quantities must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun theSameMpesaReferenceCannotPayTwoSales() = runBlocking {
        apply(sale("R-6", 50.0, PaymentMethod.MPESA, ref = "QWE123ABCD", at = 1_000L), listOf(line("A6", 1)))
        try {
            apply(sale("R-7", 50.0, PaymentMethod.MPESA, ref = "QWE123ABCD", at = 2_000L), listOf(line("A6", 1)))
            fail("a reused M-Pesa reference must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun voidingARemoteSaleBringsTheStockBack() = runBlocking {
        val pid = db.productDao().insertProduct(Product(barcode = "A8", name = "Soda", price = 50.0, stock = 10))
        val id = apply(sale("R-8", 100.0), listOf(line("A8", 2)))
        repo.voidSale(id, "customer left")
        assertEquals(10, db.productDao().getProductById(pid)?.stock)
        assertEquals(SaleStatus.VOIDED, db.saleDao().getSaleWithItems(id)?.sale?.status)
        repo.voidSale(id, "again") // a second void must not add the stock twice
        assertEquals(10, db.productDao().getProductById(pid)?.stock)
    }

    @Test fun findSaleLocatesASaleByReceiptAndTime() = runBlocking {
        apply(sale("R-9", 50.0, at = 5_000L), listOf(line("A9", 1)))
        assertNotNull(repo.findSale("R-9", 5_000L))
        assertNull(repo.findSale("R-9", 5_001L))
    }
}
