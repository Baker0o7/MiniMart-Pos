package com.minimart.pos.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodaySummaryTest {
    private fun hour(ms: Long) = ms.toInt()

    @Test fun splitsTakingsByHowTheyWerePaid() {
        val s = TodaySummaryBuilder.build(listOf(
            SaleLite(9, 100.0, "CASH"), SaleLite(9, 200.0, "MPESA"), SaleLite(10, 50.0, "CARD"),
            SaleLite(11, 300.0, "CREDIT"), SaleLite(11, 400.0, "MIXED", cashPortion = 150.0)
        ), profit = 300.0, expenses = 100.0, lastWeekSameTime = 0.0, hourOf = ::hour)
        assertEquals(1050.0, s.revenue, 0.001)
        assertEquals(250.0, s.cash, 0.001)
        assertEquals(200.0, s.mpesa, 0.001)
        assertEquals(50.0, s.card, 0.001)
        assertEquals(550.0, s.credit, 0.001)
        assertEquals(200.0, s.netProfit, 0.001)
        assertEquals(210.0, s.averageBasket, 0.001)
    }

    @Test fun findsTheBusiestHour() {
        val s = TodaySummaryBuilder.build(listOf(SaleLite(8, 10.0, "CASH"), SaleLite(15, 90.0, "CASH")), 0.0, 0.0, 0.0, ::hour)
        assertEquals(15, s.busiestHour)
    }

    @Test fun comparesWithSameTimeLastWeek() {
        val s = TodaySummaryBuilder.build(listOf(SaleLite(9, 1200.0, "CASH")), 0.0, 0.0, lastWeekSameTime = 1000.0, hourOf = ::hour)
        assertEquals(20, s.vsLastWeekPercent)
        assertNull(TodaySummaryBuilder.build(emptyList(), 0.0, 0.0, 0.0, ::hour).vsLastWeekPercent)
    }

    @Test fun emptyDayIsAllZero() {
        val s = TodaySummaryBuilder.build(emptyList(), 0.0, 0.0, 0.0, ::hour)
        assertEquals(0.0, s.revenue, 0.0); assertEquals(0.0, s.averageBasket, 0.0); assertNull(s.busiestHour)
    }
}
