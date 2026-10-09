package com.minimart.pos.util

import com.minimart.pos.data.repository.ZReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Plain-text end-of-shift report, laid out for a 32-column receipt printer. The same text is
 * printed, shared and previewed, so what the manager reads is what came out of the printer.
 *
 * With [showVariance] false (blind close, viewed by a cashier) the expected cash and the over/short
 * figure are left out; the counted cash is always shown.
 */
object ZReportFormatter {
    private const val W = 32

    private fun money(v: Double) = String.format(Locale.US, "%,.2f", v)
    private fun center(s: String) = if (s.length >= W) s.take(W) else " ".repeat((W - s.length) / 2) + s
    private fun lr(left: String, right: String): String {
        val gap = W - left.length - right.length
        return if (gap >= 1) left + " ".repeat(gap) + right else left.take(W - right.length - 1) + " " + right
    }

    fun format(z: ZReport, storeName: String, currency: String, showVariance: Boolean): String {
        val s = z.shift
        val df = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
        val rule = "-".repeat(W)
        val dbl = "=".repeat(W)
        val sb = StringBuilder()
        fun line(t: String = "") { sb.append(t).append('\n') }
        fun amt(label: String, v: Double) = line(lr(label, "$currency ${money(v)}"))

        line(center(storeName.uppercase().take(W)))
        line(center(if (s.clockOut == null) "X-REPORT (SHIFT OPEN)" else "Z-REPORT  ${z.zNumber}"))
        line(dbl)
        line("Cashier: ${s.cashierName}")
        line("Opened:  ${df.format(Date(s.clockIn))}")
        s.clockOut?.let { line("Closed:  ${df.format(Date(it))}") }
        line(rule)
        line(lr("Transactions", s.totalTransactions.toString()))
        amt("Cash sales", s.totalCashSales)
        amt("M-Pesa sales", s.totalMpesaSales)
        if (s.totalCardSales > 0) amt("Card sales", s.totalCardSales)
        if (z.creditSales > 0) amt("On credit", z.creditSales)
        line(rule)
        amt("TOTAL SALES", s.totalSales)
        if (s.totalDiscounts > 0) amt("Discounts given", s.totalDiscounts)
        if (z.voidCount > 0) amt("Voids (${z.voidCount})", z.voidTotal)
        if (z.refundCount > 0) amt("Refunds (${z.refundCount})", z.refundTotal)
        line(rule)
        line("CASH DRAWER")
        amt("Opening float", s.openingFloat)
        amt("+ Cash sales", s.totalCashSales)
        val counted = s.closingFloat
        if (counted != null) {
            amt("Counted cash", counted)
            if (showVariance) {
                amt("Expected cash", s.expectedCash)
                val d = s.cashDiscrepancy
                val tag = when { d > 0.005 -> "OVER"; d < -0.005 -> "SHORT"; else -> "BALANCED" }
                line(lr("Variance ($tag)", "${if (d > 0) "+" else ""}${money(d)}"))
            } else {
                line("Variance withheld (blind close)")
            }
        }
        if (z.noSaleOpens > 0) line(lr("No-sale drawer opens", z.noSaleOpens.toString()))
        if (s.notes.isNotBlank()) { line(rule); line("Notes: ${s.notes}") }
        line(dbl)
        line()
        line("Cashier: ________________")
        line()
        line("Manager: ________________")
        line()
        line(center("Printed ${df.format(Date())}"))
        return sb.toString().trimEnd()
    }
}
