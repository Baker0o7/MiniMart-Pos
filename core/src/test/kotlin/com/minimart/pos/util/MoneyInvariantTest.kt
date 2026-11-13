package com.minimart.pos.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** Rules that must hold for every amount, not just the few a hand-written test would try. */
class MoneyInvariantTest {
    @Test fun `every whole number of cents survives the trip through a decimal amount`() {
        // This is the property the cents columns rely on: amount = cents / 100.0 and back again loses nothing.
        for (c in 0L..3_000_000L) assertEquals(c, Money.fromDouble(c / 100.0).cents, "cents $c")
    }

    @Test fun `a decimal with a little arithmetic noise still lands on the right cent`() {
        val r = Random(7)
        repeat(20_000) {
            val c = r.nextLong(1, 10_000_000)
            assertEquals(c, Money.fromDouble(c / 100.0 + 1e-9).cents)
            assertEquals(c, Money.fromDouble(c / 100.0 - 1e-9).cents)
        }
    }

    @Test fun `adding in any order gives the same total`() {
        val r = Random(11)
        repeat(200) {
            val xs = List(r.nextInt(1, 60)) { Money.fromCents(r.nextLong(0, 500_000)) }
            assertEquals(xs.sum(), xs.shuffled(r).sum())
            assertEquals(xs.sum().cents, xs.sumOf { it.cents })
        }
    }

    @Test fun `adding then taking away gives back the start`() {
        val r = Random(3)
        repeat(5_000) {
            val a = Money.fromCents(r.nextLong(-1_000_000, 1_000_000)); val b = Money.fromCents(r.nextLong(-1_000_000, 1_000_000))
            assertEquals(a, (a + b) - b)
            assertEquals(Money.ZERO, a + (-a))
        }
    }

    @Test fun `ten thousand ten-cent sales add up to exactly a thousand`() {
        assertEquals(1000.0, List(10_000) { 0.1 }.sumOfMoney { it }, 0.0)
        assertEquals(100_000L, List(10_000) { Money.fromDouble(0.1) }.sum().cents)
    }

    @Test fun `rounding to cents twice changes nothing`() {
        val r = Random(5)
        repeat(10_000) {
            val x = r.nextDouble(0.0, 100_000.0)
            assertEquals(x.roundedToCents(), x.roundedToCents().roundedToCents(), 0.0)
        }
    }
}
