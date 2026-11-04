package com.minimart.pos.data.repository

import com.minimart.pos.data.db.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class IntegrityReport(val salesChecked: Int, val issues: List<String>) {
    val ok: Boolean get() = issues.isEmpty()
}

/**
 * Looks for money and stock records that no longer add up: sale headers that disagree with their lines,
 * negative stock, a customer balance that differs from the credit ledger, one M-Pesa code used twice,
 * an M-Pesa sale larger than its payment, and stock batches that disagree with the sales that used them. It only reads; it never changes anything.
 */
@Singleton
class IntegrityChecker @Inject constructor(private val db: AppDatabase) {

    fun check(): IntegrityReport {
        val issues = mutableListOf<String>()
        val sdb = db.openHelper.readableDatabase
        var checked = 0

        sdb.query("""
            SELECT s.id, s.receiptNumber, s.totalAmount, s.discountAmount,
                   COALESCE(SUM(si.lineTotal), 0.0), COALESCE(SUM(si.discountAmount), 0.0), COUNT(si.id)
            FROM sales s LEFT JOIN sale_items si ON si.saleId = s.id
            WHERE s.status = 'COMPLETED' GROUP BY s.id
        """.trimIndent()).use { c ->
            while (c.moveToNext()) {
                checked++
                val receipt = c.getString(1)
                val total = c.getDouble(2); val discount = c.getDouble(3)
                val lines = c.getDouble(4); val lineDiscount = c.getDouble(5); val count = c.getInt(6)
                if (count == 0) { issues += "Sale $receipt has no items"; continue }
                val expected = lines - (discount - lineDiscount)
                if (abs(total - expected) > 0.02 && issues.size < 40)
                    issues += "Sale $receipt: total ${fmt(total)} but its items add up to ${fmt(expected)}"
            }
        }

        sdb.query("SELECT name, stock, stockKg FROM products WHERE isActive = 1 AND (stock < 0 OR stockKg < -0.0001)").use { c ->
            while (c.moveToNext()) issues += "${c.getString(0)} has negative stock"
        }

        sdb.query("""
            SELECT c.name, c.creditBalance,
                   (SELECT t.balanceAfter FROM credit_transactions t WHERE t.customerId = c.id ORDER BY t.id DESC LIMIT 1)
            FROM customers c
        """.trimIndent()).use { c ->
            while (c.moveToNext()) {
                if (c.isNull(2)) continue
                if (abs(c.getDouble(1) - c.getDouble(2)) > 0.01 && issues.size < 60)
                    issues += "${c.getString(0)}: balance ${fmt(c.getDouble(1))} differs from the ledger (${fmt(c.getDouble(2))})"
            }
        }

        sdb.query("""
            SELECT mpesaRef, COUNT(*) FROM sales
            WHERE status IN ('COMPLETED', 'REFUNDED') AND mpesaRef IS NOT NULL AND mpesaRef <> ''
            GROUP BY UPPER(mpesaRef) HAVING COUNT(*) > 1
        """.trimIndent()).use { c ->
            while (c.moveToNext()) issues += "M-Pesa code ${c.getString(0)} is used on ${c.getInt(1)} sales"
        }

        sdb.query("SELECT COUNT(*) FROM stock_batches WHERE remaining < -0.0001 OR remaining > quantity + 0.0001").use { c ->
            if (c.moveToFirst() && c.getInt(0) > 0) issues += "${c.getInt(0)} stock batch record(s) are damaged"
        }

        // An M-Pesa sale for more than the payment that was recorded under its code.
        sdb.query("""
            SELECT s.receiptNumber, s.totalAmount, p.amount
            FROM sales s JOIN mpesa_payments p ON p.code = s.mpesaRef COLLATE NOCASE
            WHERE s.status = 'COMPLETED' AND s.paymentMethod = 'MPESA' AND p.amount + 0.011 < s.totalAmount
        """.trimIndent()).use { c ->
            while (c.moveToNext() && issues.size < 80)
                issues += "Sale ${c.getString(0)} is ${fmt(c.getDouble(1))} but its M-Pesa payment was only ${fmt(c.getDouble(2))}"
        }

        // Stock records: a batch cannot hold more than it received minus what live sales took from it,
        // and a refunded or voided sale must hold no stock any more.
        sdb.query("""
            SELECT COUNT(*) FROM stock_batches b
            WHERE b.remaining > b.quantity - COALESCE((SELECT SUM(c.quantity) FROM sale_item_costs c WHERE c.batchId = b.id), 0.0) + 0.0001
        """.trimIndent()).use { c ->
            if (c.moveToFirst() && c.getInt(0) > 0) issues += "${c.getInt(0)} stock batch(es) hold more than was received less what was sold"
        }
        sdb.query("""
            SELECT COUNT(*) FROM sale_item_costs c
            JOIN sale_items si ON si.id = c.saleItemId JOIN sales s ON s.id = si.saleId
            WHERE s.status <> 'COMPLETED'
        """.trimIndent()).use { c ->
            if (c.moveToFirst() && c.getInt(0) > 0) issues += "${c.getInt(0)} stock record(s) are still held by refunded or voided sales"
        }
        sdb.query("""
            SELECT s.receiptNumber, si.productName, CASE WHEN si.weightKg > 0 THEN si.weightKg ELSE si.quantity END, SUM(c.quantity)
            FROM sale_items si JOIN sales s ON s.id = si.saleId JOIN sale_item_costs c ON c.saleItemId = si.id
            WHERE s.status = 'COMPLETED'
            GROUP BY si.id
            HAVING ABS(SUM(c.quantity) - CASE WHEN si.weightKg > 0 THEN si.weightKg ELSE si.quantity END) > 0.001
        """.trimIndent()).use { c ->
            while (c.moveToNext() && issues.size < 100)
                issues += "Sale ${c.getString(0)}: ${c.getString(1)} sold ${c.getDouble(2)} but stock was taken for ${c.getDouble(3)}"
        }

        return IntegrityReport(checked, issues)
    }

    private fun fmt(v: Double) = String.format(java.util.Locale.US, "%.2f", v)
}
