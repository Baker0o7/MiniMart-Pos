package com.minimart.pos.util

enum class CashVerdict { BALANCED, OVER, SHORT }

/** One drawer count: what should be there, what was counted, and the difference (counted minus expected). */
data class CashUpResult(val expected: Money, val counted: Money, val variance: Money, val verdict: CashVerdict)

/** What one shift contributes to the day. [counted] is null while the shift is still open. */
data class ShiftCash(val label: String, val expected: Money, val counted: Money?)

/** The whole day: closed shifts add up, open shifts are listed because their drawer has not been counted yet. */
data class DayCashUp(
    val expected: Money, val counted: Money, val variance: Money, val verdict: CashVerdict,
    val closedShifts: Int, val openShifts: List<String>
)

/**
 * Cash-up rules, all cent-exact. Why: the drawer should hold the opening float plus the cash actually
 * collected (the cash share of a split sale, not the credit share), and a difference of under
 * [TOLERANCE] is rounding or a coin, not a shortage worth chasing.
 */
object CashUp {
    /** Differences smaller than this (KSh 1) count as balanced. */
    val TOLERANCE: Money = Money.fromCents(100)

    /** Cash a completed sale put into the drawer. */
    fun cashFromSale(method: String, total: Money, cashPortion: Money): Money = when (method) {
        "CASH" -> total
        "MIXED" -> cashPortion.coerceIn(Money.ZERO, total)
        else -> Money.ZERO   // M-Pesa, card and credit never touch the drawer
    }

    fun reconcile(expected: Money, counted: Money): CashUpResult {
        val diff = counted - expected
        val verdict = when {
            diff.cents >= TOLERANCE.cents -> CashVerdict.OVER
            diff.cents <= -TOLERANCE.cents -> CashVerdict.SHORT
            else -> CashVerdict.BALANCED
        }
        return CashUpResult(expected, counted, diff, verdict)
    }

    fun day(shifts: List<ShiftCash>): DayCashUp {
        val closed = shifts.filter { it.counted != null }
        val expected = closed.map { it.expected }.sum()
        val counted = closed.mapNotNull { it.counted }.sum()
        val r = reconcile(expected, counted)
        return DayCashUp(expected, counted, r.variance, r.verdict, closed.size, shifts.filter { it.counted == null }.map { it.label })
    }
}
