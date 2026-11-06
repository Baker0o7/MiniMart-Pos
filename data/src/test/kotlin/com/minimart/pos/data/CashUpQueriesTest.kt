package com.minimart.pos.data

import com.minimart.pos.data.entity.Expense
import com.minimart.pos.data.entity.ExpenseCategory
import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.Sale
import com.minimart.pos.data.entity.SaleStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The two queries the cash-up uses to find cash that left the drawer without being a sale. */
class CashUpQueriesTest : DbTestBase() {
    private val now = System.currentTimeMillis()
    private val shiftStart = now - 60 * 60 * 1000L
    private val shiftEnd = now + 60 * 1000L
    private var n = 0

    private fun sale(method: PaymentMethod, at: Long, cashier: Long = 1, total: Double = 100.0, cash: Double = if (method == PaymentMethod.CASH) total else 0.0) = Sale(
        receiptNumber = "R${++n}", subtotal = total, taxAmount = 0.0, totalAmount = total, amountPaid = total, changeGiven = 0.0,
        cashPortion = cash, paymentMethod = method, cashierId = cashier, createdAt = at)

    private fun expense(amount: Double, drawer: Boolean, at: Long = now, cashier: Long = 1) =
        Expense(title = "e$amount", amount = amount, category = ExpenseCategory.OTHER, cashierId = cashier, paidFromDrawer = drawer, createdAt = at)

    @Test fun onlyDrawerExpensesInTheWindowForThatCashierAreFound() = runBlocking<Unit> {
        val dao = db.expenseDao()
        dao.insertExpense(expense(150.0, drawer = true))
        dao.insertExpense(expense(70.0, drawer = false))                       // paid another way
        dao.insertExpense(expense(30.0, drawer = true, cashier = 2))           // someone else's drawer
        dao.insertExpense(expense(20.0, drawer = true, at = shiftStart - 1))   // before this shift
        val found = dao.drawerPaidBetween(shiftStart, shiftEnd, cashierId = 1)
        assertEquals(listOf(150.0), found.map { it.amount })
    }

    @Test fun anOldCashSaleRefundedInTheShiftIsFoundWithItsRefundTime() = runBlocking<Unit> {
        val dao = db.saleDao()
        val id = dao.insertSale(sale(PaymentMethod.CASH, at = shiftStart - 5 * 3_600_000L))
        assertEquals(1, dao.refundSale(id, "wrong item"))
        val s = dao.getSaleWithItems(id)!!.sale
        assertEquals(SaleStatus.REFUNDED, s.status)
        assertTrue("refund time recorded", s.statusChangedAt in (now - 60_000)..(now + 60_000))
        assertEquals(listOf(id), dao.cashReversedFromEarlier(shiftStart, shiftEnd, 1).map { it.id })
    }

    @Test fun onlyCashThatReallyWentBackOutIsCounted() = runBlocking<Unit> {
        val dao = db.saleDao(); val old = shiftStart - 5 * 3_600_000L
        val card = dao.insertSale(sale(PaymentMethod.CARD, old)); dao.refundSale(card, "x")             // never in the drawer
        val inShift = dao.insertSale(sale(PaymentMethod.CASH, shiftStart + 1000)); dao.refundSale(inShift, "x")   // already excluded from this shift's sales
        val other = dao.insertSale(sale(PaymentMethod.CASH, old, cashier = 2)); dao.refundSale(other, "x")        // another drawer
        val still = dao.insertSale(sale(PaymentMethod.CASH, old))                                        // not reversed
        val mixed = dao.insertSale(sale(PaymentMethod.MIXED, old, total = 200.0, cash = 80.0)); dao.voidSale(mixed, "mistake")
        val found = dao.cashReversedFromEarlier(shiftStart, shiftEnd, 1)
        assertEquals(listOf(mixed), found.map { it.id })
        assertEquals(80.0, found.single().cashPortion, 0.001)
        assertTrue(still !in found.map { it.id })
    }

    @Test fun reversalsBeforeTheShiftWindowAreNotCountedTwice() = runBlocking<Unit> {
        val dao = db.saleDao()
        val id = dao.insertSale(sale(PaymentMethod.CASH, shiftStart - 3_600_000L)); dao.refundSale(id, "x")
        // A later shift that started after the refund must not subtract it again.
        assertTrue(dao.cashReversedFromEarlier(now + 120_000, now + 3_600_000, 1).isEmpty())
    }
}
