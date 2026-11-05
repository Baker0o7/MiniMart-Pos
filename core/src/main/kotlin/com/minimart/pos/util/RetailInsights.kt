package com.minimart.pos.util

/** Basket totals for a period, counted over completed sales only. */
data class BasketTotals(val baskets: Int, val lines: Int, val multiLineBaskets: Int)
data class BasketSummary(val avgItemsPerBasket: Double, val multiItemShare: Double, val baskets: Int)

/** Two products that were bought in the same sale [times] times. */
data class ProductPair(val first: String, val second: String, val times: Int)

/** One cashier's sales and reversals (refunds and voids) for a period. */
data class StaffRow(
    val cashierId: Long, val name: String, val sales: Int, val revenue: Double,
    val voids: Int, val refunds: Int, val reversedValue: Double
)

data class StaffInsight(val row: StaffRow, val reversalRate: Double, val flagged: Boolean)

/** Where money went without a sale: discounts given and sales taken back. */
data class Leakage(val discounts: Double, val discountShare: Double, val reversedValue: Double, val reversedCount: Int, val reversalRate: Double)

/**
 * Retail questions a shop owner actually asks, answered from totals the database already holds. Pure and
 * cent-exact. Why the wording is careful: a high refund or void rate has innocent causes (a new cashier, a
 * busy day, a supplier recall), so it is flagged as "worth a conversation", never as a verdict.
 */
object RetailInsights {
    /** Needs a few baskets before a share means anything. */
    const val MIN_BASKETS = 5
    /** A cashier needs this many transactions before their rate is judged. */
    const val MIN_STAFF_TRANSACTIONS = 10
    private const val FLAG_RATE = 0.10
    private const val FLAG_MULTIPLE = 2.0

    fun basket(t: BasketTotals): BasketSummary? {
        if (t.baskets < MIN_BASKETS) return null
        return BasketSummary(
            avgItemsPerBasket = t.lines.toDouble() / t.baskets,
            multiItemShare = t.multiLineBaskets.toDouble() / t.baskets,
            baskets = t.baskets
        )
    }

    /** The commonest pairs, only ones seen at least [minTimes] times so a coincidence is not reported as a habit. */
    fun pairs(pairs: List<ProductPair>, minTimes: Int = 3, limit: Int = 5): List<ProductPair> =
        pairs.filter { it.times >= minTimes && it.first != it.second }.sortedByDescending { it.times }.take(limit)

    fun staff(rows: List<StaffRow>): List<StaffInsight> {
        val all = rows.sumOf { it.sales + it.voids + it.refunds }
        val reversed = rows.sumOf { it.voids + it.refunds }
        val overall = if (all > 0) reversed.toDouble() / all else 0.0
        return rows.map { r ->
            val n = r.sales + r.voids + r.refunds
            val rate = if (n > 0) (r.voids + r.refunds).toDouble() / n else 0.0
            StaffInsight(r, rate, n >= MIN_STAFF_TRANSACTIONS && rate >= FLAG_RATE && rate >= overall * FLAG_MULTIPLE)
        }.sortedWith(compareByDescending<StaffInsight> { it.row.revenue }.thenBy { it.row.name })
    }

    fun leakage(revenue: Double, discounts: Double, rows: List<StaffRow>): Leakage {
        val reversedValue = rows.sumOfMoney { it.reversedValue }
        val reversedCount = rows.sumOf { it.voids + it.refunds }
        val completed = rows.sumOf { it.sales }
        val gross = Money.fromDouble(revenue) + Money.fromDouble(discounts)
        return Leakage(
            discounts = discounts,
            discountShare = if (gross.isPositive()) discounts / gross.toDouble() else 0.0,
            reversedValue = reversedValue,
            reversedCount = reversedCount,
            reversalRate = if (completed + reversedCount > 0) reversedCount.toDouble() / (completed + reversedCount) else 0.0
        )
    }
}
