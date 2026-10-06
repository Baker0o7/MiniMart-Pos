package com.minimart.pos.util

import com.minimart.pos.data.dao.TopSellerResult
import com.minimart.pos.data.entity.Expense
import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.Sale
import com.minimart.pos.data.entity.SaleStatus

data class PaymentLine(val label: String, val count: Int, val amount: Double)
data class SellerLine(val name: String, val quantity: Int, val revenue: Double)
data class ExpenseLine(val category: String, val amount: Double)

/** Everything the PDF business report shows — plain data, no Android types. */
data class BusinessReportData(
    val storeName: String,
    val currency: String,
    val periodLabel: String,
    val generatedAt: Long,
    val revenue: Double,
    val transactions: Int,
    val averageBasket: Double,
    val discounts: Double,
    val vatIncluded: Double,
    val payments: List<PaymentLine>,
    val reversedCount: Int,        // refunded + voided sales (excluded from revenue)
    val reversedAmount: Double,
    val topSellers: List<SellerLine>,
    val expenses: List<ExpenseLine>,
    val totalExpenses: Double
) {
    /** Revenue minus recorded expenses (not cost-of-goods profit). */
    val net: Double get() = (Money.fromDouble(revenue) - Money.fromDouble(totalExpenses)).toDouble()
}

object BusinessReportBuilder {

    private fun List<Double>.sumExact(): Double =
        fold(Money.ZERO) { acc, v -> acc + Money.fromDouble(v) }.toDouble()

    private fun label(m: PaymentMethod) = when (m) {
        PaymentMethod.CASH -> "Cash"
        PaymentMethod.MPESA -> "M-Pesa"
        PaymentMethod.CARD -> "Card"
        PaymentMethod.CREDIT -> "Credit (on account)"
        PaymentMethod.MIXED -> "Split payment"
    }

    /** [allSales] may include refunded/voided sales; only COMPLETED ones count as revenue. */
    fun build(
        storeName: String,
        currency: String,
        periodLabel: String,
        generatedAt: Long,
        allSales: List<Sale>,
        topSellers: List<TopSellerResult>,
        expenses: List<Expense>
    ): BusinessReportData {
        val completed = allSales.filter { it.status == SaleStatus.COMPLETED }
        val reversed = allSales.filter { it.status == SaleStatus.REFUNDED || it.status == SaleStatus.VOIDED }
        val revenue = completed.map { it.totalAmount }.sumExact()
        val payments = completed.groupBy { it.paymentMethod }
            .map { (m, list) -> PaymentLine(label(m), list.size, list.map { it.totalAmount }.sumExact()) }
            .sortedByDescending { it.amount }
        val expenseLines = expenses.groupBy { it.category }
            .map { (c, list) -> ExpenseLine(c.name.lowercase().replaceFirstChar { it.uppercase() }, list.map { it.amount }.sumExact()) }
            .sortedByDescending { it.amount }
        return BusinessReportData(
            storeName = storeName,
            currency = currency,
            periodLabel = periodLabel,
            generatedAt = generatedAt,
            revenue = revenue,
            transactions = completed.size,
            averageBasket = if (completed.isEmpty()) 0.0 else revenue / completed.size,
            discounts = completed.map { it.discountAmount }.sumExact(),
            vatIncluded = completed.map { it.taxAmount }.sumExact(),
            payments = payments,
            reversedCount = reversed.size,
            reversedAmount = reversed.map { it.totalAmount }.sumExact(),
            topSellers = topSellers.map { SellerLine(it.productName, it.totalQty, it.totalRevenue) },
            expenses = expenseLines,
            totalExpenses = expenses.map { it.amount }.sumExact()
        )
    }
}
