package com.minimart.pos.util

data class SaleLite(val createdAt: Long, val total: Double, val method: String, val cashPortion: Double = 0.0)

data class TodaySummary(
    val revenue: Double,
    val count: Int,
    val averageBasket: Double,
    val profit: Double,
    val expenses: Double,
    val netProfit: Double,
    val cash: Double,
    val mpesa: Double,
    val card: Double,
    val credit: Double,
    /** 24 buckets, 0 = midnight to 1 AM. */
    val hourly: List<Double>,
    val busiestHour: Int?,
    val lastWeekSameTime: Double,
    /** Percent change against the same time last week; null when last week had nothing to compare. */
    val vsLastWeekPercent: Int?
)

object TodaySummaryBuilder {
    /** [method] strings are PaymentMethod names: CASH, MPESA, CARD, CREDIT, MIXED. For MIXED, [SaleLite.cashPortion] is cash and the rest is credit. */
    fun build(
        sales: List<SaleLite>, profit: Double, expenses: Double, lastWeekSameTime: Double,
        hourOf: (Long) -> Int
    ): TodaySummary {
        val revenue = sales.sumOf { it.total }
        var cash = 0.0; var mpesa = 0.0; var card = 0.0; var credit = 0.0
        val hourly = DoubleArray(24)
        for (s in sales) {
            hourly[hourOf(s.createdAt).coerceIn(0, 23)] += s.total
            when (s.method) {
                "CASH" -> cash += s.total
                "MPESA" -> mpesa += s.total
                "CARD" -> card += s.total
                "CREDIT" -> credit += s.total
                "MIXED" -> { val c = s.cashPortion.coerceIn(0.0, s.total); cash += c; credit += s.total - c }
                else -> cash += s.total
            }
        }
        val peak = hourly.indices.maxByOrNull { hourly[it] }?.takeIf { hourly[it] > 0.0 }
        val vs = if (lastWeekSameTime > 0.0) Math.round((revenue - lastWeekSameTime) / lastWeekSameTime * 100.0).toInt() else null
        return TodaySummary(
            revenue = revenue, count = sales.size,
            averageBasket = if (sales.isEmpty()) 0.0 else revenue / sales.size,
            profit = profit, expenses = expenses, netProfit = profit - expenses,
            cash = cash, mpesa = mpesa, card = card, credit = credit,
            hourly = hourly.toList(), busiestHour = peak,
            lastWeekSameTime = lastWeekSameTime, vsLastWeekPercent = vs
        )
    }
}
