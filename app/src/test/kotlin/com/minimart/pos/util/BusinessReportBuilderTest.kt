package com.minimart.pos.util

import com.minimart.pos.data.dao.TopSellerResult
import com.minimart.pos.data.entity.Expense
import com.minimart.pos.data.entity.ExpenseCategory
import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.Sale
import com.minimart.pos.data.entity.SaleStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class BusinessReportBuilderTest {

    private fun sale(total: Double, method: PaymentMethod, status: SaleStatus = SaleStatus.COMPLETED,
                     discount: Double = 0.0, tax: Double = 0.0) =
        Sale(receiptNumber = "R", subtotal = total + discount, discountAmount = discount, taxAmount = tax,
            totalAmount = total, amountPaid = total, changeGiven = 0.0, paymentMethod = method,
            status = status, cashierId = 1L)

    private fun build(sales: List<Sale>, expenses: List<Expense> = emptyList(), sellers: List<TopSellerResult> = emptyList()) =
        BusinessReportBuilder.build("Shop", "KES", "Today", 0L, sales, sellers, expenses)

    @Test
    fun `only completed sales count as revenue and reversed ones are reported separately`() {
        val r = build(listOf(
            sale(100.0, PaymentMethod.CASH),
            sale(50.0, PaymentMethod.MPESA),
            sale(70.0, PaymentMethod.CASH, SaleStatus.REFUNDED),
            sale(30.0, PaymentMethod.CARD, SaleStatus.VOIDED)
        ))
        assertEquals(150.0, r.revenue, 0.0)
        assertEquals(2, r.transactions)
        assertEquals(75.0, r.averageBasket, 0.0)
        assertEquals(2, r.reversedCount)
        assertEquals(100.0, r.reversedAmount, 0.0)
    }

    @Test
    fun `payment lines are grouped, counted and sorted by amount`() {
        val r = build(listOf(
            sale(10.0, PaymentMethod.CASH), sale(20.0, PaymentMethod.CASH),
            sale(100.0, PaymentMethod.MPESA)
        ))
        assertEquals(listOf("M-Pesa", "Cash"), r.payments.map { it.label })
        assertEquals(30.0, r.payments[1].amount, 0.0)
        assertEquals(2, r.payments[1].count)
    }

    @Test
    fun `totals are cent exact`() {
        val r = build(listOf(sale(0.1, PaymentMethod.CASH), sale(0.2, PaymentMethod.CASH)))
        assertEquals(0.3, r.revenue, 0.0)     // plain Double addition would give 0.30000000000000004
    }

    @Test
    fun `net is revenue minus expenses and expenses are grouped by category`() {
        val r = build(
            listOf(sale(1000.0, PaymentMethod.CASH)),
            listOf(Expense(title = "a", amount = 200.0, category = ExpenseCategory.RENT),
                   Expense(title = "b", amount = 50.5, category = ExpenseCategory.RENT),
                   Expense(title = "c", amount = 100.0, category = ExpenseCategory.WATER))
        )
        assertEquals(350.5, r.totalExpenses, 0.0)
        assertEquals(649.5, r.net, 0.0)
        assertEquals("Rent", r.expenses.first().category)
        assertEquals(250.5, r.expenses.first().amount, 0.0)
    }

    @Test
    fun `empty period gives zeros without dividing by zero`() {
        val r = build(emptyList())
        assertEquals(0.0, r.revenue, 0.0)
        assertEquals(0.0, r.averageBasket, 0.0)
        assertEquals(0, r.transactions)
    }
}
