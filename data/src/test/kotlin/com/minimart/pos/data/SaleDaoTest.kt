package com.minimart.pos.data

import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.Product
import com.minimart.pos.data.entity.Sale
import com.minimart.pos.data.entity.SaleItem
import com.minimart.pos.data.entity.SaleStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SaleDaoTest : DbTestBase() {
    private suspend fun makeSale(receipt: String, total: Double = 50.0, at: Long = System.currentTimeMillis()): Long {
        val pid = db.productDao().insertProduct(Product(barcode = "b-$receipt", name = "Item", price = total, stock = 10))
        val sale = Sale(receiptNumber = receipt, subtotal = total, taxAmount = 0.0, totalAmount = total, amountPaid = total,
            changeGiven = 0.0, cashPortion = total, paymentMethod = PaymentMethod.CASH, cashierId = 1, createdAt = at)
        val item = SaleItem(saleId = 0, productId = pid, productBarcode = "b-$receipt", productName = "Item",
            unitPrice = total, quantity = 1, lineTotal = total)
        return db.saleDao().insertSaleWithItems(sale, listOf(item))
    }

    @Test fun saleAndItemsAreStoredTogether() = runBlocking {
        val id = makeSale("RCP-1")
        val loaded = db.saleDao().getSaleWithItems(id)!!
        assertEquals("RCP-1", loaded.sale.receiptNumber)
        assertEquals(1, loaded.items.size)
        assertEquals(id, loaded.items.first().saleId)
    }

    @Test fun aSaleCanOnlyBeVoidedOnce() = runBlocking {
        val id = makeSale("RCP-2")
        assertEquals(1, db.saleDao().voidSale(id, "wrong item"))
        assertEquals(0, db.saleDao().voidSale(id, "again"))
        assertEquals(SaleStatus.VOIDED, db.saleDao().getSaleWithItems(id)!!.sale.status)
    }

    @Test fun aVoidedSaleCannotBeRefundedAsWell() = runBlocking {
        val id = makeSale("RCP-3")
        db.saleDao().voidSale(id, "mistake")
        assertEquals(0, db.saleDao().refundSale(id, "customer returned"))
    }

    @Test fun completedRangeExcludesVoidedAndOutOfRangeSales() = runBlocking {
        val inside = makeSale("RCP-4", at = 1_000)
        val voided = makeSale("RCP-5", at = 1_500)
        makeSale("RCP-6", at = 9_000) // after the range
        db.saleDao().voidSale(voided, "oops")
        val rows = db.saleDao().getCompletedSalesByDateRange(0, 2_000).first()
        assertEquals(listOf(inside), rows.map { it.id })
    }

    @Test fun revenueTotalsOnlyCountCompletedSales() = runBlocking {
        makeSale("RCP-7", total = 100.0, at = 1_000)
        val voided = makeSale("RCP-8", total = 40.0, at = 1_100)
        db.saleDao().voidSale(voided, "oops")
        assertEquals(100.0, db.saleDao().getTotalRevenueBetween(0, 2_000).first() ?: 0.0, 1e-9)
    }
}
