package com.minimart.pos.data

import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.Sale
import com.minimart.pos.data.entity.SaleStatus
import com.minimart.pos.data.entity.cashMoney
import com.minimart.pos.data.entity.totalMoney
import com.minimart.pos.data.entity.totalOf
import com.minimart.pos.data.repository.IntegrityChecker
import com.minimart.pos.util.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Phase 2 of whole-cent money: reports and cash-up add the cents columns. These tests check, over hundreds of
 * sales of every shape, that the cents always equal the decimal amounts and that what the database adds up in SQL is
 * exactly what adding the same sales with Money gives.
 */
class SaleCentsInvariantTest : DbTestBase() {
    private val methods = PaymentMethod.entries

    private fun sale(i: Int, total: Double, cash: Double, method: PaymentMethod, at: Long) = Sale(
        receiptNumber = "INV-$i", subtotal = total, taxAmount = 0.0, totalAmount = total, amountPaid = total, changeGiven = 0.0,
        cashPortion = cash, paymentMethod = method, cashierId = 1L, createdAt = at)

    /** Amounts a till really sees: whole shillings, cents, long sums, and values carrying float noise. */
    private fun amounts(r: Random, n: Int) = List(n) {
        val c = r.nextLong(1, 5_000_000)
        when (r.nextInt(3)) { 0 -> c / 100.0; 1 -> c / 100.0 + 1e-9; else -> (c / 100) * 1.0 }
    }

    private fun insertMany(n: Int, seed: Int): List<Sale> = runBlocking {
        val r = Random(seed)
        val totals = amounts(r, n)
        totals.mapIndexed { i, t ->
            val method = methods[r.nextInt(methods.size)]
            val cash = if (method == PaymentMethod.MIXED) Money.fromDouble(t * r.nextDouble(0.0, 1.0)).toDouble() else if (method == PaymentMethod.CASH) t else 0.0
            val s = sale(i, t, cash, method, 1_000L + i)
            val id = db.saleDao().insertSaleWithItems(s, emptyList())
            db.saleDao().getSaleWithItems(id)!!.sale
        }
    }

    @Test fun everySavedSaleHasCentsEqualToItsAmount() {
        insertMany(400, 1).forEach {
            assertEquals(Money.fromDouble(it.totalAmount).cents, it.totalCents)
            assertEquals(Money.fromDouble(it.cashPortion).cents, it.cashPortionCents)
            assertEquals(Money.fromDouble(it.totalAmount), it.totalMoney())
            assertEquals(Money.fromDouble(it.cashPortion), it.cashMoney())
        }
    }

    @Test fun aSaleInsertedWithoutGoingThroughInsertSaleWithItemsStillGetsItsCents() = runBlocking {
        val id = db.saleDao().insertSale(sale(1, 0.1 + 0.2, 0.0, PaymentMethod.CARD, 5L))
        assertEquals(30L, db.saleDao().getSaleWithItems(id)!!.sale.totalCents)
    }

    @Test fun databaseRevenueEqualsMoneyAdditionOfTheSameSales() = runBlocking {
        val sales = insertMany(500, 2)
        val expected = sales.filter { it.status == SaleStatus.COMPLETED }.fold(Money.ZERO) { a, s -> a + Money.fromDouble(s.totalAmount) }.toDouble()
        assertEquals(expected, db.saleDao().getTotalRevenueBetween(0L, Long.MAX_VALUE).first() ?: 0.0, 0.0)
        assertEquals(expected, sales.totalOf(), 0.0)
        val stats = db.saleDao().completedSalesStats("").first()
        assertEquals(500, stats.count)
        assertEquals(expected, stats.total, 0.0)
    }

    @Test fun refundsAndVoidsLeaveRevenueAndMoveToReversedValue() = runBlocking {
        val sales = insertMany(120, 3)
        val refunded = sales.filterIndexed { i, _ -> i % 5 == 0 }
        val voided = sales.filterIndexed { i, _ -> i % 7 == 3 }.filter { it !in refunded }
        refunded.forEach { db.saleDao().refundSale(it.id, "test") }
        voided.forEach { db.saleDao().voidSale(it.id, "test") }
        val live = sales.filter { it !in refunded && it !in voided }
        val money = { l: List<Sale> -> l.fold(Money.ZERO) { a, s -> a + Money.fromDouble(s.totalAmount) }.toDouble() }
        val row = db.saleDao().staffActivity(0L, Long.MAX_VALUE).first().single()
        assertEquals(money(live), row.revenue, 0.0)
        assertEquals(money(refunded + voided), row.reversedValue, 0.0)
        assertEquals(money(live), db.saleDao().getTotalRevenueBetween(0L, Long.MAX_VALUE).first() ?: 0.0, 0.0)
    }

    @Test fun theIntegrityCheckFindsDriftedCentsAndRepairFixesThemOnce() = runBlocking {
        insertMany(50, 4)
        val checker = IntegrityChecker(db)
        assertEquals(0, checker.repairSaleCents())
        assertTrue(checker.check().issues.none { it.contains("whole-cent") })

        db.openHelper.writableDatabase.execSQL("UPDATE sales SET totalCents = totalCents + 1 WHERE id % 10 = 0")
        db.openHelper.writableDatabase.execSQL("UPDATE sales SET cashPortionCents = 99 WHERE id = 3")
        assertTrue(checker.check().issues.any { it.contains("whole-cent") })
        val fixed = checker.repairSaleCents()
        assertEquals(6, fixed) // ids 10, 20, 30, 40, 50 and 3
        assertEquals(0, checker.repairSaleCents())
        assertTrue(checker.check().issues.none { it.contains("whole-cent") })
    }
}
