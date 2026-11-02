package com.minimart.pos.util

import java.util.Locale

/** Everything the owner's end-of-day message needs, already added up (see [DailySummary]). */
data class DailySummaryInput(
    val storeName: String,
    val currency: String,
    val dateLabel: String,
    val salesCount: Int,
    val revenue: Money,
    val cash: Money,
    val mpesa: Money,
    val card: Money,
    val onCredit: Money,
    val refundsCount: Int,
    val voidsCount: Int,
    val expenses: Money,
    /** Profit on lines whose cost is known; null when no cost was recorded today. */
    val profit: Money?,
    val topProducts: List<Pair<String, Int>>,
    val lowStockCount: Int,
    val unclaimedMpesaCount: Int,
    val pendingSync: Int
)

/**
 * The owner's daily message. Plain text on purpose: it is shown in a notification and can be sent by SMS
 * or WhatsApp, so no markup, and the numbers that need action come last under "Needs attention".
 */
object DailySummary {
    fun title(i: DailySummaryInput): String =
        if (i.salesCount == 0) "${i.storeName}: no sales today"
        else "${i.storeName}: ${i.currency} ${money(i.revenue)} from ${i.salesCount} sale${if (i.salesCount == 1) "" else "s"}"

    fun body(i: DailySummaryInput): String = buildString {
        line("${i.storeName} - ${i.dateLabel}")
        if (i.salesCount == 0) {
            line("No sales were recorded today.")
        } else {
            line("Sales: ${i.currency} ${money(i.revenue)} (${i.salesCount})")
            val parts = listOf("Cash" to i.cash, "M-Pesa" to i.mpesa, "Card" to i.card, "On credit" to i.onCredit)
                .filter { it.second.isPositive() }
            parts.forEach { (name, amount) -> line("  $name: ${i.currency} ${money(amount)}") }
        }
        if (i.refundsCount > 0 || i.voidsCount > 0) {
            line("Refunded: ${i.refundsCount}, voided: ${i.voidsCount}")
        }
        if (i.expenses.isPositive()) line("Expenses: ${i.currency} ${money(i.expenses)}")
        i.profit?.let { p ->
            line("Profit (items with a cost): ${i.currency} ${money(p)}")
            if (i.expenses.isPositive()) line("After expenses: ${i.currency} ${money(p - i.expenses)}")
        }
        if (i.topProducts.isNotEmpty()) {
            line("Top sellers: " + i.topProducts.take(3).joinToString(", ") { "${it.first} x${it.second}" })
        }
        val alerts = buildList {
            if (i.unclaimedMpesaCount > 0)
                add("${i.unclaimedMpesaCount} M-Pesa payment${if (i.unclaimedMpesaCount == 1) "" else "s"} received but not matched to a sale")
            if (i.lowStockCount > 0) add("${i.lowStockCount} product${if (i.lowStockCount == 1) "" else "s"} low on stock")
            if (i.pendingSync > 0) add("${i.pendingSync} sale record${if (i.pendingSync == 1) "" else "s"} not yet synced")
        }
        if (alerts.isNotEmpty()) {
            line("Needs attention:")
            alerts.forEach { line("  - $it") }
        }
    }.trimEnd()

    private fun StringBuilder.line(s: String) { append(s).append('\n') }
    private fun money(m: Money): String = String.format(Locale.US, "%,.2f", m.toDouble())

    /** Milliseconds from [nowMs] until the next [hour]:00 in the phone's time zone (never zero, never past 24 h). */
    fun delayUntilHour(nowMs: Long, hour: Int, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): Long {
        val cal = java.util.Calendar.getInstance(zone).apply {
            timeInMillis = nowMs
            set(java.util.Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23)); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= nowMs) cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis - nowMs
    }
}
