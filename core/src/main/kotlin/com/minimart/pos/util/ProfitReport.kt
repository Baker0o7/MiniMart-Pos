package com.minimart.pos.util

/**
 * One product's sales over a period. [cost] and [costedRevenue] only cover lines whose cost is known, so
 * [profit] and [marginPercent] are never flattered by items nobody recorded a cost for.
 */
data class ProductMargin(
    val name: String, val category: String, val qty: Double,
    val revenue: Money, val cost: Money, val costedRevenue: Money
) {
    val costKnown: Boolean get() = costedRevenue.cents > 0
    val profit: Money get() = costedRevenue - cost

    /** Profit as a percentage of the revenue it came from; null when no cost is known. */
    val marginPercent: Double? get() = if (costKnown) profit.cents * 100.0 / costedRevenue.cents else null

    /** Sold for less than it cost. */
    val belowCost: Boolean get() = costKnown && profit.isNegative()
}

/** A category's total, built from its products. */
data class CategoryMargin(val category: String, val revenue: Money, val profit: Money, val costedRevenue: Money, val products: Int) {
    val marginPercent: Double? get() = if (costedRevenue.cents > 0) profit.cents * 100.0 / costedRevenue.cents else null
}

/** One line of a period comparison: this period against the one before it. */
data class Delta(val label: String, val now: Money, val before: Money) {
    val change: Money get() = now - before

    /** Change as a percentage of the earlier figure; null when there is nothing earlier to compare with. */
    val changePercent: Double? get() = if (before.cents == 0L) null else change.cents * 100.0 / kotlin.math.abs(before.cents)
}

/**
 * Pure helpers for the Profit & Loss screen: product and category margins, the same-length period before a
 * range, and a this-versus-last comparison. Kept in :core so the arithmetic is tested without a phone.
 */
object ProfitReport {
    /** Best earners first (by profit); products with no known cost go last, so they do not look like zero-profit items. */
    fun ranked(items: List<ProductMargin>): List<ProductMargin> =
        items.sortedWith(compareByDescending<ProductMargin> { it.costKnown }.thenByDescending { it.profit.cents }.thenBy { it.name.lowercase() })

    /** Products sold for less than they cost, worst loss first. */
    fun belowCost(items: List<ProductMargin>): List<ProductMargin> =
        items.filter { it.belowCost }.sortedBy { it.profit.cents }

    /** Products with sales but no recorded cost, biggest sellers first: the ones to fix so profit is trustworthy. */
    fun missingCost(items: List<ProductMargin>): List<ProductMargin> =
        items.filter { !it.costKnown && it.revenue.isPositive() }.sortedByDescending { it.revenue.cents }

    fun byCategory(items: List<ProductMargin>): List<CategoryMargin> =
        items.groupBy { it.category.ifBlank { "General" } }.map { (cat, list) ->
            CategoryMargin(cat, list.map { it.revenue }.sum(), list.map { it.profit }.sum(), list.map { it.costedRevenue }.sum(), list.size)
        }.sortedByDescending { it.profit.cents }

    /**
     * The stretch of time of equal length that ends where [startMs] begins, for "compared with last period".
     * Returns null for an empty or backwards range.
     */
    fun previousRange(startMs: Long, endMs: Long): Pair<Long, Long>? {
        val length = endMs - startMs
        if (length <= 0L || startMs - length < 0L) return null
        return (startMs - length) to startMs
    }

    /** Revenue, cost of goods, gross profit, expenses and net profit, now against before. */
    fun compare(now: ProfitAndLoss, before: ProfitAndLoss): List<Delta> = listOf(
        Delta("Revenue", now.revenueNet, before.revenueNet),
        Delta("Cost of goods", now.costOfGoods, before.costOfGoods),
        Delta("Gross profit", now.grossProfit, before.grossProfit),
        Delta("Expenses", now.totalExpenses, before.totalExpenses),
        Delta("Net profit", now.netProfit, before.netProfit)
    )

    /** Gross margin: gross profit as a percentage of revenue excluding VAT; null when there was no revenue. */
    fun grossMarginPercent(p: ProfitAndLoss): Double? = if (p.revenueNet.cents <= 0) null else p.grossProfit.cents * 100.0 / p.revenueNet.cents

    /** "12.5%" with the sign for changes ("+12.5%"); "-" when there is nothing to compare. */
    fun percentText(value: Double?, signed: Boolean = false): String {
        if (value == null || value.isNaN() || value.isInfinite()) return "-"
        val s = String.format(java.util.Locale.US, "%.1f", value)
        return (if (signed && value > 0) "+" else "") + s + "%"
    }

    /** The product margin table as CSV for an accountant, with the same formula-safe cells as the other exports. */
    fun productMarginCsv(items: List<ProductMargin>, storeName: String, period: String, currency: String): String = buildString {
        fun row(vararg c: String) = append(c.joinToString(",") { AccountingExport.cell(it) }).append("\r\n")
        row(storeName); row("Profit by product", period); row("All amounts in $currency"); row("")
        row("Product", "Category", "Quantity", "Revenue", "Cost of goods", "Profit", "Margin %", "Note")
        for (p in ranked(items)) {
            row(
                p.name, p.category, String.format(java.util.Locale.US, "%.2f", p.qty),
                AccountingExport.amount(p.revenue), AccountingExport.amount(p.cost),
                if (p.costKnown) AccountingExport.amount(p.profit) else "", p.marginPercent?.let { percentText(it) } ?: "",
                when { p.belowCost -> "Sold below cost"; !p.costKnown -> "No cost recorded"; else -> "" }
            )
        }
    }
}
