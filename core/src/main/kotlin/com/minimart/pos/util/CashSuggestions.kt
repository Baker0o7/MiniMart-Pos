package com.minimart.pos.util

import java.util.Locale

/**
 * The one-tap "cash received" buttons at checkout. Kept here (not in the screen) so the rules can be tested:
 * the buttons must always cover the bill, so the cashier never taps one that leaves the customer short.
 */
object CashSuggestions {
    private val NOTE_STEPS = listOf(50L, 100L, 500L, 1000L)
    private val NO_BILL_YET = listOf(100L, 200L, 500L, 1000L)

    /** The exact total as typed text: "250" for whole shillings, "250.50" otherwise. */
    fun exactText(total: Double): String =
        if (total % 1.0 == 0.0) total.toLong().toString() else String.format(Locale.US, "%.2f", total)

    /**
     * Up to three round amounts that each cover [total] (next 50/100/500/1000). When the bill sits just under one big
     * round number only one distinct amount exists, so the list continues upwards (1000, 1500, 2000) instead of
     * showing a single button. With no bill yet, fixed common notes are offered.
     */
    fun roundUps(total: Double): List<Long> {
        if (!total.isFinite() || total <= 0.0) return NO_BILL_YET.take(3)
        val t = kotlin.math.ceil(total).toLong()
        val ups = NOTE_STEPS.map { step -> ((t + step - 1) / step) * step }
            .filter { it > total }
            .distinct()
            .take(3)
            .toMutableList()
        var next = ups.lastOrNull() ?: t
        while (ups.size < 3) { next += if (next < 1000L) 100L else 500L; ups.add(next) }
        return ups
    }

    /** Thousands separators for the button label: 1500 -> "1,500". */
    fun label(amount: Long): String = String.format(Locale.US, "%,d", amount)
}
