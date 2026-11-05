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

/** The new analytics queries return what the shop actually sold, and leave refunded and voided sales out of baskets. */
class RetailAnalyticsQueryTest : DbTestBase() {
    private suspend fun product(code: String) =
        db.productDao().insertProduct(Product(barcode = code, name = "Item $code", price = 10.0, stock = 100))

    private suspend fun sale(receipt: String, cashier: Long, status: SaleStatus, total: Double, vararg products: Long) {
        val s = Sale(receiptNumber = receipt, subtotal = total, taxAmount = 0.0, totalAmount = total, amountPaid = total, changeGiven = 0.0,
            cashPortion = total, paymentMethod = PaymentMethod.CASH, status = status, cashierId = cashier, createdAt = 1_000L)
        val items = products.map { SaleItem(saleId = 0, productId = it, productBarcode = "b$it", productName = "Item $it", unitPrice = 10.0, quantity = 1, lineTotal = 10.0) }
        db.saleDao().insertSaleWithItems(s, items)
    }

    @Test fun basketsCountOnlyCompletedSalesAndTheirLines() = runBlocking {
        val a = product("A"); val b = product("B")
        sale("R1", 1, SaleStatus.COMPLETED, 20.0, a, b)
        sale("R2", 1, SaleStatus.COMPLETED, 10.0, a)
        sale("R3", 1, SaleStatus.VOIDED, 20.0, a, b)
        val t = db.saleDao().basketTotals(0L, 10_000L).first()
        assertEquals(2, t.baskets); assertEquals(3, t.lines); assertEquals(1, t.multiLineBaskets)
    }

    @Test fun anEmptyPeriodGivesZerosNotNulls() = runBlocking {
        val t = db.saleDao().basketTotals(0L, 10_000L).first()
        assertEquals(0, t.baskets); assertEquals(0, t.lines); assertEquals(0, t.multiLineBaskets)
    }

    @Test fun pairsAreCountedOncePerSaleAndNeedTwoSales() = runBlocking {
        val a = product("A"); val b = product("B"); val c = product("C")
        sale("R1", 1, SaleStatus.COMPLETED, 20.0, a, b)
        sale("R2", 1, SaleStatus.COMPLETED, 30.0, a, b, c)
        sale("R3", 1, SaleStatus.COMPLETED, 10.0, c)
        sale("R4", 1, SaleStatus.REFUNDED, 20.0, a, b)
        val pairs = db.saleDao().productPairs(0L, 10_000L).first()
        assertEquals(1, pairs.size)
        assertEquals(2, pairs[0].times)
        assertEquals(setOf("Item $a", "Item $b"), setOf(pairs[0].first, pairs[0].second))
    }

    @Test fun staffActivitySeparatesSalesFromRefundsAndVoids() = runBlocking {
        val a = product("A")
        sale("R1", 7, SaleStatus.COMPLETED, 10.0, a)
        sale("R2", 7, SaleStatus.COMPLETED, 10.5, a)
        sale("R3", 7, SaleStatus.VOIDED, 10.0, a)
        sale("R4", 7, SaleStatus.REFUNDED, 10.0, a)
        sale("R5", 8, SaleStatus.COMPLETED, 10.0, a)
        val rows = db.saleDao().staffActivity(0L, 10_000L).first().associateBy { it.cashierId }
        val r = rows.getValue(7L)
        assertEquals(2, r.sales); assertEquals(20.5, r.revenue, 0.0)
        assertEquals(1, r.voids); assertEquals(1, r.refunds); assertEquals(20.0, r.reversedValue, 0.0)
        assertEquals(1, rows.getValue(8L).sales)
    }
}
