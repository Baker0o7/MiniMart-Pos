package com.minimart.pos.util

import kotlin.math.roundToLong

/**
 * Represents a monetary amount as an exact integer count of the currency's smallest
 * unit (cents for KES/USD-style currencies), rather than a [Double].
 *
 * Why this exists: [Double] cannot represent most decimal fractions exactly (0.1 + 0.2
 * != 0.3 in IEEE-754 binary floating point). For a POS app, repeated addition of prices
 * and discounts across a cart, then across a whole day's sales, then across a month's
 * reports, accumulates these tiny errors — occasionally producing a receipt total that's
 * off by a fraction of a cent from what summing the same numbers on paper would give,
 * or a discrepancy report showing a non-zero "error" that isn't a real till shortage.
 * [Money] is a Kotlin `value class` — it compiles down to a plain [Long] at runtime
 * (zero object-allocation overhead versus a raw Long), so it's exact AND free.
 *
 * Scope note: this class is applied to the CartUiState checkout-calculation core
 * (subtotal/tax/discount/total/change — the numbers that decide how much cash to
 * collect) rather than rewritten across every Double money field in every Room entity
 * app-wide. A full schema-level migration would touch 20+ entity fields, 15+ DAO
 * queries doing SUM()/arithmetic, and 30+ UI display sites — attempting that in one
 * pass without a real compiler to verify against carries a much higher risk of
 * introducing a NEW financial bug than the Double-precision error it would fix. This
 * class establishes the correct pattern on the highest-stakes real-time calculation
 * first; migrating Product/Expense/Customer/Shift can follow the same pattern
 * incrementally, each independently verifiable.
 */
@JvmInline
value class Money private constructor(val cents: Long) : Comparable<Money> {

    companion object {
        val ZERO = Money(0L)

        /** Construct from a whole-currency Double (e.g. 149.99), rounding to the nearest cent. */
        fun fromDouble(amount: Double): Money =
            Money((amount * 100.0).roundToLong())

        /** Construct directly from an exact cents count (e.g. from a DB column already in cents). */
        fun fromCents(cents: Long): Money = Money(cents)

        /** Parses user input text (e.g. a price field) into Money, or null if not a valid amount. */
        fun parseOrNull(text: String): Money? =
            text.trim().toDoubleOrNull()?.let { fromDouble(it) }
    }

    operator fun plus(other: Money): Money = Money(cents + other.cents)
    operator fun minus(other: Money): Money = Money(cents - other.cents)
    operator fun unaryMinus(): Money = Money(-cents)

    /** Multiply by an integer quantity (e.g. unit price × quantity). */
    operator fun times(quantity: Int): Money = Money(cents * quantity)

    /** Multiply by a scalar (e.g. applying a weight in kg, or a tax rate) — rounds to the nearest cent. */
    operator fun times(scalar: Double): Money = Money((cents * scalar).roundToLong())

    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    fun coerceAtLeast(min: Money): Money = if (cents < min.cents) min else this
    fun coerceAtMost(max: Money): Money = if (cents > max.cents) max else this
    fun coerceIn(min: Money, max: Money): Money = coerceAtLeast(min).coerceAtMost(max)
    fun isPositive(): Boolean = cents > 0
    fun isZero(): Boolean = cents == 0L
    fun isNegative(): Boolean = cents < 0

    /** Converts back to a whole-currency Double, for display or for writing to a
     * Double-typed DB column at the entity boundary. */
    fun toDouble(): Double = cents / 100.0

    /** Formats as "1,234.56" (no currency symbol — callers prepend their own currency string,
     * consistent with how the rest of the app already does `"$currency ${String.format(...)}"`). */
    fun format(): String = String.format("%,.2f", toDouble())

    override fun toString(): String = format()
}

/** Convenience: sum a list of Money values. */
fun Iterable<Money>.sum(): Money = fold(Money.ZERO) { acc, m -> acc + m }
