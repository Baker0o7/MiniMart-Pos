package com.minimart.pos.util

import org.junit.Assert.*
import org.junit.Test

class MoneyTest {

    @Test
    fun `fromDouble and toDouble round-trip exactly`() {
        assertEquals(149.99, Money.fromDouble(149.99).toDouble(), 0.0)
        assertEquals(0.0, Money.fromDouble(0.0).toDouble(), 0.0)
        assertEquals(1.0, Money.fromDouble(1.0).toDouble(), 0.0)
    }

    @Test
    fun `fromDouble rounds to the nearest cent`() {
        assertEquals(1050L, Money.fromDouble(10.499999).cents) // rounds up to the nearest cent
        assertEquals(1000L, Money.fromDouble(10.001).cents)    // rounds down, well clear of the boundary
        assertEquals(1001L, Money.fromDouble(10.006).cents)    // rounds up, well clear of the boundary
        assertEquals(999L, Money.fromDouble(9.994).cents)      // rounds down
    }

    @Test
    fun `the classic Double precision failure case is exact in Money`() {
        // The textbook IEEE-754 failure: 0.1 + 0.2 != 0.3 in raw Double arithmetic.
        assertNotEquals(0.3, 0.1 + 0.2, 0.0) // sanity check: confirms Double really does fail here

        // Money must NOT reproduce this error.
        val result = Money.fromDouble(0.1) + Money.fromDouble(0.2)
        assertEquals(Money.fromDouble(0.3), result)
        assertEquals(0.3, result.toDouble(), 0.0)
    }

    @Test
    fun `summing many small cart lines stays exact`() {
        // Regression scenario: a cart with many lines at prices that are individually
        // fine in Double but compound error when summed repeatedly.
        val prices = listOf(19.99, 49.50, 5.25, 100.00, 0.99, 12.49, 7.77, 3.33)
        val doubleSum = prices.sum()
        val moneySum = prices.fold(Money.ZERO) { acc, p -> acc + Money.fromDouble(p) }.toDouble()
        // Both should agree here since these particular values happen to sum cleanly,
        // but the point is Money.fromCents is doing exact integer addition throughout —
        // verified by checking it matches the expected exact cents total independently.
        val expectedCents = prices.sumOf { Money.fromDouble(it).cents }
        assertEquals(expectedCents, Money.fromDouble(moneySum).cents)
        assertEquals(199.32, moneySum, 0.001)
    }

    @Test
    fun `plus and minus`() {
        val a = Money.fromDouble(100.50)
        val b = Money.fromDouble(30.25)
        assertEquals(130.75, (a + b).toDouble(), 0.0)
        assertEquals(70.25, (a - b).toDouble(), 0.0)
    }

    @Test
    fun `times quantity`() {
        val unitPrice = Money.fromDouble(49.99)
        assertEquals(149.97, (unitPrice * 3).toDouble(), 0.0)
    }

    @Test
    fun `times scalar for weighed items rounds to nearest cent`() {
        val pricePerKg = Money.fromDouble(300.00)
        val weightKg = 0.537
        // 300.00 * 0.537 = 161.10 exactly
        assertEquals(161.10, (pricePerKg * weightKg).toDouble(), 0.01)
    }

    @Test
    fun `coerceAtLeast floors correctly`() {
        val negative = Money.fromDouble(-50.0)
        assertEquals(Money.ZERO, negative.coerceAtLeast(Money.ZERO))
        assertTrue(Money.fromDouble(10.0).coerceAtLeast(Money.ZERO).isPositive())
    }

    @Test
    fun `coerceIn clamps a discount to the line subtotal`() {
        val lineSubtotal = Money.fromDouble(50.0)
        val tooLargeDiscount = Money.fromDouble(999.0)
        val clamped = tooLargeDiscount.coerceIn(Money.ZERO, lineSubtotal)
        assertEquals(lineSubtotal, clamped)
    }

    @Test
    fun `isZero isPositive isNegative`() {
        assertTrue(Money.ZERO.isZero())
        assertTrue(Money.fromDouble(1.0).isPositive())
        assertTrue(Money.fromDouble(-1.0).isNegative())
        assertFalse(Money.fromDouble(1.0).isNegative())
    }

    @Test
    fun `parseOrNull accepts valid amounts and rejects garbage`() {
        assertEquals(Money.fromDouble(42.50), Money.parseOrNull("42.50"))
        assertEquals(Money.fromDouble(42.50), Money.parseOrNull("  42.50  ")) // trims whitespace
        assertNull(Money.parseOrNull("not a number"))
        assertNull(Money.parseOrNull(""))
    }

    @Test
    fun `format produces comma-separated two-decimal string`() {
        assertEquals("1,234.56", Money.fromDouble(1234.56).format())
        assertEquals("0.00", Money.ZERO.format())
    }

    @Test
    fun `comparison operators work via Comparable`() {
        val small = Money.fromDouble(10.0)
        val large = Money.fromDouble(20.0)
        assertTrue(small < large)
        assertTrue(large > small)
        assertEquals(0, small.compareTo(Money.fromDouble(10.0)))
    }
}
