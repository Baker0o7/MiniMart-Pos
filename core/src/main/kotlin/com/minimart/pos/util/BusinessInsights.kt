package com.minimart.pos.util

/** What one product sold in a period. [costedRevenue] is the part of [revenue] whose cost is known. */
data class ProductPerformance(
    val id: Long, val name: String, val category: String,
    val qty: Double, val revenue: Double, val cost: Double, val costedRevenue: Double
) {
    /** Profit on the costed part of the sales only, so products with no cost price are not shown as pure profit. */
    val profit: Double get() = costedRevenue - cost
    val margin: Double? get() = if (costedRevenue > 0.0) profit / costedRevenue else null
}

/** Pareto view of sales: a few products usually make most of the money. */
data class AbcResult(
    val aCount: Int, val bCount: Int, val cCount: Int,
    val aShare: Double, val bShare: Double, val cShare: Double,
    /** Names of the "A" products, biggest first. */
    val aProducts: List<String>
) {
    val total: Int get() = aCount + bCount + cCount
}

data class MarginReport(
    /** Biggest profit earners (costed products only). */
    val best: List<ProductPerformance>,
    /** Sold for less than they cost. */
    val lossMakers: List<ProductPerformance>,
    /** Profitable but under [BusinessInsights.THIN_MARGIN]. */
    val thin: List<ProductPerformance>
)

object BusinessInsights {
    const val A_SHARE = 0.80
    const val B_SHARE = 0.95
    const val THIN_MARGIN = 0.10

    /** A = the products that together make the first 80% of revenue, B = up to 95%, C = the long tail. */
    fun abc(items: List<ProductPerformance>): AbcResult? {
        val sold = items.filter { it.revenue > 0.0 }.sortedByDescending { it.revenue }
        val total = sold.sumOf { it.revenue }
        if (sold.isEmpty() || total <= 0.0) return null
        var running = 0.0
        val a = mutableListOf<ProductPerformance>(); val b = mutableListOf<ProductPerformance>(); val c = mutableListOf<ProductPerformance>()
        for (p in sold) {
            val before = running / total
            running += p.revenue
            // A product belongs to the class that its own sales start in, so the one that crosses 80% is still an "A".
            when {
                before < A_SHARE -> a += p
                before < B_SHARE -> b += p
                else -> c += p
            }
        }
        fun share(l: List<ProductPerformance>) = l.sumOf { it.revenue } / total
        return AbcResult(a.size, b.size, c.size, share(a), share(b), share(c), a.map { it.name })
    }

    fun margins(items: List<ProductPerformance>, best: Int = 5): MarginReport {
        val costed = items.filter { it.costedRevenue > 0.0 }
        return MarginReport(
            best = costed.filter { it.profit > 0.0 }.sortedByDescending { it.profit }.take(best),
            lossMakers = costed.filter { it.profit < 0.0 }.sortedBy { it.profit },
            thin = costed.filter { (it.margin ?: 1.0) in 0.0..THIN_MARGIN && it.profit >= 0.0 }.sortedBy { it.margin }
        )
    }

    /**
     * Typical takings for each weekday (Monday first): that weekday's revenue divided by how many of that
     * weekday fell in the period. [todayIndex] is today's weekday, Monday = 0; the period is the last
     * [days] days ending today. Returns null when the period is too short to say anything.
     */
    fun typicalWeek(weekdayRevenue: List<Double>, days: Int, todayIndex: Int): List<Double>? {
        if (weekdayRevenue.size != 7 || days < 14) return null
        val occurrences = IntArray(7)
        for (back in 0 until days) occurrences[Math.floorMod(todayIndex - back, 7)]++
        return List(7) { i -> if (occurrences[i] == 0) 0.0 else weekdayRevenue[i] / occurrences[i] }
    }
}
