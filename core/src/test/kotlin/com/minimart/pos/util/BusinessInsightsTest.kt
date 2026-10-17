package com.minimart.pos.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessInsightsTest {
    private fun p(name: String, revenue: Double, cost: Double = 0.0, costed: Double = revenue) =
        ProductPerformance(name.hashCode().toLong(), name, "General", 1.0, revenue, cost, costed)

    @Test fun abcSplitsTheLongTailFromTheProductsThatMakeTheMoney() {
        // 1000 total: milk 500, bread 300 (cumulative 80%), then 100, 50, 30, 20.
        val r = BusinessInsights.abc(listOf(p("milk", 500.0), p("bread", 300.0), p("a", 100.0), p("b", 50.0), p("c", 30.0), p("d", 20.0)))!!
        assertEquals(listOf("milk", "bread"), r.aProducts)
        assertEquals(2, r.aCount); assertEquals(0.8, r.aShare, 1e-9)
        assertEquals(6, r.total)
        assertEquals(1.0, r.aShare + r.bShare + r.cShare, 1e-9)
    }

    @Test fun theProductThatCrossesEightyPercentIsStillAnA() {
        val r = BusinessInsights.abc(listOf(p("big", 90.0), p("small", 10.0)))!!
        assertEquals(listOf("big"), r.aProducts)
    }

    @Test fun abcIgnoresProductsThatDidNotSellAndHandlesNoSales() {
        assertNull(BusinessInsights.abc(emptyList()))
        assertNull(BusinessInsights.abc(listOf(p("idle", 0.0))))
        assertEquals(1, BusinessInsights.abc(listOf(p("idle", 0.0), p("sold", 5.0)))!!.total)
    }

    @Test fun lossMakersAreProductsSoldBelowCost() {
        val m = BusinessInsights.margins(listOf(p("ok", 100.0, 60.0), p("loss", 100.0, 130.0), p("worse", 50.0, 90.0)))
        assertEquals(listOf("worse", "loss"), m.lossMakers.map { it.name })
        assertEquals(listOf("ok"), m.best.map { it.name })
    }

    @Test fun thinMarginMeansProfitableButUnderTenPercent() {
        val m = BusinessInsights.margins(listOf(p("thin", 100.0, 95.0), p("fat", 100.0, 50.0), p("loss", 100.0, 110.0)))
        assertEquals(listOf("thin"), m.thin.map { it.name })
    }

    @Test fun productsWithoutACostPriceAreNeverShownAsProfit() {
        val m = BusinessInsights.margins(listOf(p("nocost", 500.0, cost = 0.0, costed = 0.0)))
        assertTrue(m.best.isEmpty() && m.lossMakers.isEmpty() && m.thin.isEmpty())
        assertNull(p("nocost", 500.0, 0.0, 0.0).margin)
    }

    @Test fun bestEarnersAreRankedByProfitNotRevenue() {
        val m = BusinessInsights.margins(listOf(p("highRevLowProfit", 1000.0, 950.0), p("lowRevHighProfit", 200.0, 50.0)), best = 2)
        assertEquals("lowRevHighProfit", m.best.first().name)
    }

    @Test fun typicalWeekDividesByHowManyOfEachWeekdayFellInThePeriod() {
        // 28 days = exactly 4 of each weekday.
        val week = BusinessInsights.typicalWeek(listOf(400.0, 0.0, 0.0, 0.0, 0.0, 800.0, 0.0), 28, todayIndex = 2)!!
        assertEquals(100.0, week[0], 1e-9)
        assertEquals(200.0, week[5], 1e-9)
    }

    @Test fun typicalWeekAccountsForPartialWeeks() {
        // 30 days ending on a Wednesday (index 2): Mon, Tue, Wed occur 5 times, the rest 4.
        val week = BusinessInsights.typicalWeek(List(7) { 100.0 }, 30, todayIndex = 2)!!
        assertEquals(20.0, week[0], 1e-9)
        assertEquals(25.0, week[3], 1e-9)
    }

    @Test fun typicalWeekNeedsAtLeastTwoWeeksOfHistory() {
        assertNull(BusinessInsights.typicalWeek(List(7) { 1.0 }, 7, 0))
        assertNotNull(BusinessInsights.typicalWeek(List(7) { 1.0 }, 14, 0))
    }
}
