package com.minimart.pos.util

/** A way to count one product besides its base unit: 1 [code] = [factor] base units (1 carton = 24 pcs). */
data class PackSize(val code: String, val factor: Int)

/**
 * Units of measure, kept deliberately small: stock, sales, batches and refunds always count the product's BASE unit, and a
 * pack is only a convenient way to type or show a quantity. These pure functions do all the conversion so it can be tested.
 */
object UnitMath {
    /** Most base units one pack may hold; stops a typo from turning one tap into millions of units. */
    const val MAX_FACTOR = 100_000

    /** A unit code is short, lower case, and made of letters, digits, spaces or "-" ("carton", "6-pack"). Null = not usable. */
    fun normalizeCode(raw: String): String? {
        val t = raw.trim().lowercase().replace(Regex("\\s+"), " ")
        if (t.isEmpty() || t.length > 20) return null
        if (!t.all { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '.' }) return null
        return t
    }

    /** A pack must hold at least two base units (one is the base unit itself) and a sane number of them. */
    fun validFactor(factor: Int): Boolean = factor in 2..MAX_FACTOR

    /** Parses what the owner typed into the "how many" field; null for blanks, decimals, zero, one or absurd values. */
    fun parseFactor(text: String): Int? = text.trim().toIntOrNull()?.takeIf(::validFactor)

    /** Base units for [count] packs of [factor]; null if it would overflow an Int (stock is a whole-number count). */
    fun toBase(count: Int, factor: Int): Int? {
        if (count < 0 || factor < 1) return null
        val v = count.toLong() * factor.toLong()
        return if (v > Int.MAX_VALUE) null else v.toInt()
    }

    /** Whole packs and the loose rest, e.g. 75 with a 24-pack = 3 packs and 3 left over. */
    fun split(base: Int, factor: Int): Pair<Int, Int> =
        if (base <= 0 || factor < 1) 0 to base.coerceAtLeast(0) else (base / factor) to (base % factor)

    /**
     * "3 carton + 3 pcs" for [base] units: biggest pack first, the remainder in the next pack that fits, the rest in
     * [baseCode]. Stock at or below zero reads "0 pcs". With no packs it is just "75 pcs".
     */
    fun describe(base: Int, baseCode: String, packs: List<PackSize>): String {
        if (base <= 0) return "0 $baseCode"
        var left = base
        val parts = ArrayList<String>()
        for (p in packs.filter { validFactor(it.factor) }.distinctBy { it.factor }.sortedByDescending { it.factor }) {
            val n = left / p.factor
            if (n > 0) { parts += "$n ${p.code}"; left -= n * p.factor }
        }
        if (left > 0 || parts.isEmpty()) parts += "$left $baseCode"
        return parts.joinToString(" + ")
    }

    /** The unit price of a pack: the base price times the factor, rounded to whole cents like every other price. */
    fun packPrice(basePrice: Double, factor: Int): Double = (Money.fromDouble(basePrice) * factor).toDouble()
}
