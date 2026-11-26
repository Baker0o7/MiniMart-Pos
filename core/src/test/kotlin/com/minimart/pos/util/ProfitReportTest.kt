package com.minimart.pos.util

import org.junit.Assert.*
import org.junit.Test

class ProfitReportTest {
    private fun m(c: Long) = Money.fromCents(c)
    private fun pm(name: String, cat: String, rev: Long, cost: Long, costed: Long = rev) =
        ProductMargin(name, cat, 1.0, m(rev), m(cost), m(costed))

    @Test fun marginOnlyCountsLinesWithAKnownCost() {
        val p = pm("Milk", "Dairy", rev = 20000, cost = 12000, costed = 15000)
        assertEquals(3000L, p.profit.cents)
        assertEquals(20.0, p.marginPercent!!, 1e-9)
        assertFalse(p.belowCost)
    }

    @Test fun noKnownCostMeansNoMarginAndNoProfitClaim() {
        val p = pm("Mystery", "General", rev = 5000, cost = 0, costed = 0)
        assertFalse(p.costKnown); assertNull(p.marginPercent); assertFalse(p.belowCost)
    }

    @Test fun belowCostIsFlaggedAndSortedWorstFirst() {
        val items = listOf(pm("A", "x", 1000, 1500), pm("B", "x", 1000, 900), pm("C", "x", 1000, 3000))
        assertEquals(listOf("C", "A"), ProfitReport.belowCost(items).map { it.name })
    }

    @Test fun rankedPutsBestProfitFirstAndUncostedLast() {
        val items = listOf(pm("Low", "x", 1000, 900), pm("None", "x", 9000, 0, 0), pm("High", "x", 1000, 100))
        assertEquals(listOf("High", "Low", "None"), ProfitReport.ranked(items).map { it.name })
    }

    @Test fun missingCostListsBiggestSellersFirst() {
        val items = listOf(pm("Small", "x", 100, 0, 0), pm("Big", "x", 900, 0, 0), pm("Fine", "x", 500, 100))
        assertEquals(listOf("Big", "Small"), ProfitReport.missingCost(items).map { it.name })
    }

    @Test fun categoriesAddUpAndBlankBecomesGeneral() {
        val cats = ProfitReport.byCategory(listOf(pm("a", "Dairy", 1000, 600), pm("b", "Dairy", 500, 100), pm("c", " ", 300, 200)))
        val dairy = cats.first { it.category == "Dairy" }
        assertEquals(1500L, dairy.revenue.cents); assertEquals(800L, dairy.profit.cents); assertEquals(2, dairy.products)
        assertNotNull(cats.firstOrNull { it.category == "General" })
        assertEquals("Dairy", cats.first().category)
    }

    @Test fun previousRangeIsTheSameLengthJustBefore() {
        assertEquals(1000L to 2000L, ProfitReport.previousRange(2000, 3000))
        assertNull(ProfitReport.previousRange(3000, 3000))
        assertNull(ProfitReport.previousRange(3000, 2000))
        assertNull(ProfitReport.previousRange(500, 2000)) // would start before time zero
    }

    @Test fun comparisonShowsChangeAndPercent() {
        fun pnl(rev: Long, cost: Long, exp: Long): ProfitAndLoss {
            val gross = rev - cost
            return ProfitAndLoss(1, m(rev), Money.ZERO, m(rev), m(cost), m(gross), emptyMap(), m(exp), m(gross - exp), emptyMap(), true)
        }
        val d = ProfitReport.compare(pnl(12000, 6000, 1000), pnl(10000, 5000, 2000)).associateBy { it.label }
        assertEquals(2000L, d.getValue("Revenue").change.cents)
        assertEquals(20.0, d.getValue("Revenue").changePercent!!, 1e-9)
        assertEquals(5000L, d.getValue("Net profit").now.cents)
        assertEquals(3000L, d.getValue("Net profit").before.cents)
        assertEquals(-50.0, d.getValue("Expenses").changePercent!!, 1e-9)
    }

    @Test fun nothingEarlierMeansNoPercent() {
        assertNull(Delta("x", m(500), Money.ZERO).changePercent)
    }

    @Test fun grossMarginNeedsRevenue() {
        val p = ProfitAndLoss(1, m(10000), Money.ZERO, m(10000), m(6000), m(4000), emptyMap(), Money.ZERO, m(4000), emptyMap(), true)
        assertEquals(40.0, ProfitReport.grossMarginPercent(p)!!, 1e-9)
        assertNull(ProfitReport.grossMarginPercent(p.copy(revenueNet = Money.ZERO)))
    }

    @Test fun percentTextIsLocaleSafeAndSigned() {
        assertEquals("12.5%", ProfitReport.percentText(12.5))
        assertEquals("+12.5%", ProfitReport.percentText(12.5, signed = true))
        assertEquals("-3.0%", ProfitReport.percentText(-3.0, signed = true))
        assertEquals("-", ProfitReport.percentText(null))
    }

    @Test fun csvKeepsNegativeMarginsAndDefusesFormulas() {
        val csv = ProfitReport.productMarginCsv(listOf(pm("=cmd", "x", 1000, 1500), pm("Fine", "x", 1000, 500)), "Shop", "May", "KES")
        assertTrue(csv.contains("'=cmd"))
        assertTrue(csv.contains("-50.0%"))
        assertTrue(csv.contains("Sold below cost"))
        assertTrue(csv.contains("50.0%"))
    }
}
