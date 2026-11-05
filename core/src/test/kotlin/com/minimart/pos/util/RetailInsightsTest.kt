package com.minimart.pos.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RetailInsightsTest {
    private fun row(id: Long, name: String, sales: Int, voids: Int = 0, refunds: Int = 0, revenue: Double = sales * 100.0, reversed: Double = 0.0) =
        StaffRow(id, name, sales, revenue, voids, refunds, reversed)

    @Test fun basketNeedsAFewSalesBeforeItSaysAnything() {
        assertNull(RetailInsights.basket(BasketTotals(baskets = 4, lines = 10, multiLineBaskets = 3)))
    }

    @Test fun basketAveragesAreWorkedOutPerSale() {
        val b = RetailInsights.basket(BasketTotals(baskets = 20, lines = 50, multiLineBaskets = 15))!!
        assertEquals(2.5, b.avgItemsPerBasket, 1e-9); assertEquals(0.75, b.multiItemShare, 1e-9)
    }

    @Test fun onlyRepeatedPairsAreReportedAndStrongestFirst() {
        val out = RetailInsights.pairs(listOf(ProductPair("Bread", "Milk", 9), ProductPair("Soda", "Chips", 2), ProductPair("Sugar", "Tea", 5)))
        assertEquals(listOf("Bread", "Sugar"), out.map { it.first })
    }

    @Test fun aCashierWithManyReversalsAndEnoughSalesIsFlagged() {
        val s = RetailInsights.staff(listOf(row(1, "Amina", sales = 200), row(2, "Brian", sales = 70, voids = 20, refunds = 10, revenue = 7000.0)))
        assertFalse(s.first { it.row.name == "Amina" }.flagged)
        assertTrue(s.first { it.row.name == "Brian" }.flagged)
    }

    @Test fun aNewCashierWithFewSalesIsNeverFlaggedWhateverTheirRate() {
        val s = RetailInsights.staff(listOf(row(1, "Amina", sales = 100), row(2, "New", sales = 3, voids = 3)))
        assertFalse(s.first { it.row.name == "New" }.flagged)
    }

    @Test fun whenEveryoneReversesALotNobodyIsSingledOut() {
        val s = RetailInsights.staff(listOf(row(1, "A", sales = 80, voids = 20), row(2, "B", sales = 80, voids = 20)))
        assertTrue(s.none { it.flagged })
    }

    @Test fun staffAreListedByRevenue() {
        val s = RetailInsights.staff(listOf(row(1, "Low", 10, revenue = 100.0), row(2, "High", 10, revenue = 900.0)))
        assertEquals(listOf("High", "Low"), s.map { it.row.name })
    }

    @Test fun leakageAddsDiscountsAndReversalsInExactCents() {
        val l = RetailInsights.leakage(revenue = 9_000.0, discounts = 1_000.0,
            rows = listOf(row(1, "A", sales = 95, voids = 3, refunds = 2, reversed = 100.1), row(2, "B", sales = 0, voids = 0, refunds = 1, reversed = 0.2)))
        assertEquals(0.10, l.discountShare, 1e-9)
        assertEquals(100.3, l.reversedValue, 0.0)
        assertEquals(6, l.reversedCount)
        assertEquals(6.0 / 101.0, l.reversalRate, 1e-9)
    }

    @Test fun noSalesGivesZeroNotADivideByZero() {
        val l = RetailInsights.leakage(0.0, 0.0, emptyList())
        assertEquals(0.0, l.discountShare, 0.0); assertEquals(0.0, l.reversalRate, 0.0)
    }
}
