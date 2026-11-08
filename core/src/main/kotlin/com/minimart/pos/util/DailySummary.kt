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
        if (i.salesCount == 0) Lang.t("${i.storeName}: no sales today", "${i.storeName}: لا مبيعات اليوم")
        else Lang.t("${i.storeName}: ${i.currency} ${money(i.revenue)} from ${i.salesCount} sale${if (i.salesCount == 1) "" else "s"}",
            "${i.storeName}: ${i.currency} ${money(i.revenue)} من ${i.salesCount} عملية بيع")

    fun body(i: DailySummaryInput): String = buildString {
        line("${i.storeName} - ${i.dateLabel}")
        if (i.salesCount == 0) {
            line(Lang.t("No sales were recorded today.", "لم تُسجَّل أي مبيعات اليوم."))
        } else {
            line(Lang.t("Sales", "المبيعات") + ": ${i.currency} ${money(i.revenue)} (${i.salesCount})")
            val parts = listOf(Lang.t("Cash", "نقدًا") to i.cash, "M-Pesa" to i.mpesa, Lang.t("Card", "بطاقة") to i.card, Lang.t("On credit", "بالآجل") to i.onCredit)
                .filter { it.second.isPositive() }
            parts.forEach { (name, amount) -> line("  $name: ${i.currency} ${money(amount)}") }
        }
        if (i.refundsCount > 0 || i.voidsCount > 0) {
            line(Lang.t("Refunded: ${i.refundsCount}, voided: ${i.voidsCount}", "مستردة: ${i.refundsCount}، ملغاة: ${i.voidsCount}"))
        }
        if (i.expenses.isPositive()) line(Lang.t("Expenses", "المصروفات") + ": ${i.currency} ${money(i.expenses)}")
        i.profit?.let { p ->
            line(Lang.t("Profit (items with a cost)", "الربح (للمنتجات ذات التكلفة)") + ": ${i.currency} ${money(p)}")
            if (i.expenses.isPositive()) line(Lang.t("After expenses", "بعد المصروفات") + ": ${i.currency} ${money(p - i.expenses)}")
        }
        if (i.topProducts.isNotEmpty()) {
            line(Lang.t("Top sellers", "الأكثر مبيعًا") + ": " + i.topProducts.take(3).joinToString(", ") { "${it.first} x${it.second}" })
        }
        val alerts = buildList {
            if (i.unclaimedMpesaCount > 0)
                add(Lang.t("${i.unclaimedMpesaCount} M-Pesa payment${if (i.unclaimedMpesaCount == 1) "" else "s"} received but not matched to a sale", "${i.unclaimedMpesaCount} دفعة M-Pesa مستلمة غير مربوطة بعملية بيع"))
            if (i.lowStockCount > 0) add(Lang.t("${i.lowStockCount} product${if (i.lowStockCount == 1) "" else "s"} low on stock", "${i.lowStockCount} منتج منخفض المخزون"))
            if (i.pendingSync > 0) add(Lang.t("${i.pendingSync} sale record${if (i.pendingSync == 1) "" else "s"} not yet synced", "${i.pendingSync} سجل بيع لم يُزامن بعد"))
        }
        if (alerts.isNotEmpty()) {
            line(Lang.t("Needs attention:", "يحتاج إلى اهتمام:"))
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
