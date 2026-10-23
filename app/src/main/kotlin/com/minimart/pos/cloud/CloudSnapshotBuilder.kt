package com.minimart.pos.cloud

import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.SaleStatus
import com.minimart.pos.data.entity.ShiftStatus
import com.minimart.pos.data.repository.CustomerRepository
import com.minimart.pos.data.repository.ExpenseRepository
import com.minimart.pos.data.repository.ProductRepository
import com.minimart.pos.data.repository.SaleRepository
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.data.repository.ShiftRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** The summary a store uploads: last 30 days of sales, stock alerts and recent shifts. */
@Singleton
class CloudSnapshotBuilder @Inject constructor(
    private val sales: SaleRepository,
    private val expenses: ExpenseRepository,
    private val products: ProductRepository,
    private val shifts: ShiftRepository,
    private val customers: CustomerRepository,
    private val settings: SettingsRepository,
    private val audit: com.minimart.pos.util.AuditLogger
) {
    companion object { const val DAYS = 30 }

    suspend fun build(): JSONObject {
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(DAYS - 1))
        }
        val start = cal.timeInMillis
        val now = System.currentTimeMillis()

        val allSales = sales.getSalesByDateRange(start, now).first()
        val allExpenses = expenses.getExpensesByDateRange(start, now).first()
        val stats = sales.getProductSalesStats(start, now).first()

        class Day { var revenue = 0.0; var tx = 0; var cash = 0.0; var mpesa = 0.0; var card = 0.0; var credit = 0.0
            var discounts = 0.0; var voids = 0; var voidTotal = 0.0; var refunds = 0; var refundTotal = 0.0; var expenses = 0.0 }
        val byDay = linkedMapOf<String, Day>()
        val c = Calendar.getInstance().apply { timeInMillis = start }
        repeat(DAYS) { byDay[dayFmt.format(c.time)] = Day(); c.add(Calendar.DAY_OF_YEAR, 1) }

        allSales.forEach { s ->
            val d = byDay[dayFmt.format(Date(s.createdAt))] ?: return@forEach
            when (s.status) {
                SaleStatus.COMPLETED -> {
                    d.revenue += s.totalAmount; d.tx++; d.discounts += s.discountAmount
                    when (s.paymentMethod) {
                        PaymentMethod.CASH -> d.cash += s.totalAmount
                        PaymentMethod.MPESA -> d.mpesa += s.totalAmount
                        PaymentMethod.CARD -> d.card += s.totalAmount
                        PaymentMethod.CREDIT -> d.credit += s.totalAmount
                        PaymentMethod.MIXED -> { d.cash += s.cashPortion; d.credit += (s.totalAmount - s.cashPortion).coerceAtLeast(0.0) }
                    }
                }
                SaleStatus.VOIDED -> { d.voids++; d.voidTotal += s.totalAmount }
                SaleStatus.REFUNDED -> { d.refunds++; d.refundTotal += s.totalAmount }
            }
        }
        allExpenses.forEach { e -> byDay[dayFmt.format(Date(e.createdAt))]?.let { it.expenses += e.amount } }

        val daily = JSONArray()
        byDay.forEach { (day, d) ->
            daily.put(JSONObject().put("day", day).put("revenue", d.revenue).put("transactions", d.tx)
                .put("cash", d.cash).put("mpesa", d.mpesa).put("card", d.card).put("credit", d.credit)
                .put("discounts", d.discounts).put("voids", d.voids).put("voidTotal", d.voidTotal)
                .put("refunds", d.refunds).put("refundTotal", d.refundTotal).put("expenses", d.expenses))
        }

        // Profit is only meaningful for products that have a cost price.
        val costedRevenue = stats.sumOf { it.costedRevenue }
        val costedCost = stats.sumOf { it.cost }
        val revenue30 = byDay.values.sumOf { it.revenue }

        val prods = products.getAllProducts().first()
        val lowStock = JSONArray()
        prods.filter { if (it.isWeighed) it.stockKg <= it.lowStockThreshold else it.stock <= it.lowStockThreshold }
            .sortedBy { if (it.isWeighed) it.stockKg else it.stock.toDouble() }.take(40).forEach {
                lowStock.put(JSONObject().put("name", it.name).put("stock", if (it.isWeighed) it.stockKg else it.stock.toDouble())
                    .put("threshold", it.lowStockThreshold).put("unit", if (it.isWeighed) "kg" else it.unit))
            }
        val expiring = JSONArray()
        val horizon = now + 90L * 24 * 60 * 60 * 1000
        prods.filter { it.expiryDate in 1..horizon && (it.stock > 0 || it.stockKg > 0) }
            .sortedBy { it.expiryDate }.take(40).forEach {
                expiring.put(JSONObject().put("name", it.name).put("expiryDate", it.expiryDate)
                    .put("stock", if (it.isWeighed) it.stockKg else it.stock.toDouble()))
            }

        val shiftArr = JSONArray()
        shifts.getRecentShifts(now - 14L * 24 * 60 * 60 * 1000).first().take(20).forEach { s ->
            val closed = s.status == ShiftStatus.CLOSED
            shiftArr.put(JSONObject().put("id", s.id).put("cashier", s.cashierName).put("clockIn", s.clockIn)
                .put("clockOut", s.clockOut ?: JSONObject.NULL).put("open", !closed)
                .put("totalSales", s.totalSales).put("transactions", s.totalTransactions)
                .put("expectedCash", if (closed) s.expectedCash else JSONObject.NULL)
                .put("countedCash", s.closingFloat ?: JSONObject.NULL)
                .put("variance", if (closed) s.cashDiscrepancy else JSONObject.NULL))
        }

        return JSONObject()
            .put("v", 1)
            .put("storeName", settings.storeName.first())
            .put("currency", settings.currency.first())
            .put("generatedAt", now)
            .put("days", DAYS)
            .put("daily", daily)
            .put("profit", JSONObject().put("costedRevenue", costedRevenue).put("costedCost", costedCost)
                .put("coverage", if (revenue30 > 0) (costedRevenue / revenue30).coerceIn(0.0, 1.0) else 0.0))
            .put("stock", JSONObject().put("productCount", prods.size)
                .put("costValue", prods.sumOf { (if (it.isWeighed) it.stockKg else it.stock.toDouble()) * it.costPrice })
                .put("retailValue", prods.sumOf { (if (it.isWeighed) it.stockKg * it.pricePerKg else it.stock * it.price) })
                .put("lowStock", lowStock).put("expiring", expiring))
            .put("shifts", shiftArr)
            // Newest activity-log hash and entry count (no content): a copy outside the phone that proves the log was not trimmed.
            .put("auditAnchor", audit.anchorText() ?: JSONObject.NULL)
            .put("customerCredit", customers.getTotalCreditOutstanding().first() ?: 0.0)
    }
}
