package com.minimart.pos.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.TimeZone

class DailySummaryTest {
    private fun input(
        sales: Int = 3, revenue: Double = 1234.5, expenses: Double = 0.0, profit: Double? = null,
        refunds: Int = 0, unclaimed: Int = 0, low: Int = 0, pending: Int = 0
    ) = DailySummaryInput(
        storeName = "Mama Duka", currency = "KES", dateLabel = "Tue 3 Nov", salesCount = sales,
        revenue = Money.fromDouble(revenue), cash = Money.fromDouble(1000.0), mpesa = Money.fromDouble(234.5),
        card = Money.ZERO, onCredit = Money.ZERO, refundsCount = refunds, voidsCount = 0,
        expenses = Money.fromDouble(expenses), profit = profit?.let { Money.fromDouble(it) },
        topProducts = listOf("Sugar" to 12, "Milk" to 9), lowStockCount = low,
        unclaimedMpesaCount = unclaimed, pendingSync = pending
    )

    @Test fun `title says how much came in`() =
        assertEquals("Mama Duka: KES 1,234.50 from 3 sales", DailySummary.title(input()))

    @Test fun `a day with no sales says so`() {
        val i = input(sales = 0, revenue = 0.0)
        assertEquals("Mama Duka: no sales today", DailySummary.title(i))
        assertTrue("No sales were recorded today." in DailySummary.body(i))
    }

    @Test fun `payment methods with nothing are left out`() {
        val body = DailySummary.body(input())
        assertTrue("Cash: KES 1,000.00" in body)
        assertTrue("M-Pesa: KES 234.50" in body)
        assertFalse("Card" in body)
    }

    @Test fun `profit after expenses is shown only when there is a profit figure`() {
        assertFalse("Profit" in DailySummary.body(input(expenses = 100.0)))
        val body = DailySummary.body(input(expenses = 100.0, profit = 400.0))
        assertTrue("After expenses: KES 300.00" in body)
    }

    @Test fun `things that need action come last`() {
        val body = DailySummary.body(input(unclaimed = 2, low = 1, pending = 4))
        assertTrue(body.endsWith("- 4 sale records not yet synced"))
        assertTrue("2 M-Pesa payments received but not matched to a sale" in body)
        assertTrue("1 product low on stock" in body)
    }

    @Test fun `no attention section when all is well`() = assertFalse("Needs attention" in DailySummary.body(input()))

    @Test fun `next run is today when the hour is still ahead and tomorrow when it has passed`() {
        val nairobi = TimeZone.getTimeZone("Africa/Nairobi")
        val sixPm = java.util.Calendar.getInstance(nairobi).apply { clear(); set(2026, 10, 3, 18, 0, 0) }.timeInMillis
        assertEquals(2 * 3_600_000L, DailySummary.delayUntilHour(sixPm, 20, nairobi))
        assertEquals(22 * 3_600_000L, DailySummary.delayUntilHour(sixPm, 16, nairobi))
        assertEquals(24 * 3_600_000L, DailySummary.delayUntilHour(sixPm, 18, nairobi))
    }
}
