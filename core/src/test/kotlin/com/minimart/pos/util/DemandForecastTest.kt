package com.minimart.pos.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DemandForecastTest {
    @Test fun `no sales means no forecast and no run-out date`() {
        val f = DemandForecast.forecast(List(28) { 0.0 }, 0, 50.0)
        assertEquals(0.0, f.avgPerDay, 0.0); assertNull(f.daysUntilEmpty)
    }
    @Test fun `steady sales give that rate and the right run-out day`() {
        val f = DemandForecast.forecast(List(14) { 5.0 }, 0, 20.0)
        assertEquals(5.0, f.avgPerDay, 1e-9)
        assertEquals(4, f.daysUntilEmpty)
    }
    @Test fun `recent days weigh more than old ones`() {
        val rising = List(10) { 1.0 } + List(10) { 9.0 }
        assertTrue(DemandForecast.forecast(rising, 0, 100.0).avgPerDay > 5.0)
    }
    @Test fun `a busy weekday is forecast higher once there are weeks of history`() {
        // 5 weeks starting Monday(0): Saturday(5) sells 20, other days 4.
        val h = List(35) { i -> if (i % 7 == 5) 20.0 else 4.0 }
        val f = DemandForecast.forecast(h, 0, 1000.0)
        // history ends on Sunday, so tomorrow is Monday; Saturday is index 5 of the forecast.
        assertTrue(f.daily[5] > f.daily[0] * 1.5)
    }
    @Test fun `pattern does not change the weekly total`() {
        val h = List(35) { i -> if (i % 7 == 5) 20.0 else 4.0 }
        val f = DemandForecast.forecast(h, 0, 1000.0, horizon = 7)
        val flat = DemandForecast.forecast(h.map { 7.0 }, 0, 1000.0, horizon = 7)
        assertTrue(f.daily.sum() > 0 && flat.daily.sum() > 0)
        assertEquals(f.daily.sum() / 7, f.avgPerDay, 1e-9)
    }
    @Test fun `short history is a flat average with low confidence`() {
        val f = DemandForecast.forecast(listOf(3.0, 0.0, 6.0), 2, 10.0)
        assertTrue(f.confidence < 0.2)
        assertEquals(f.daily.first(), f.daily.last(), 1e-9)
    }
    @Test fun `out of stock already runs out tomorrow`() =
        assertEquals(1, DemandForecast.forecast(List(14) { 2.0 }, 0, 0.0).daysUntilEmpty)
    @Test fun `stock that outlasts the horizon has no run-out date`() =
        assertNull(DemandForecast.forecast(List(14) { 1.0 }, 0, 10_000.0).daysUntilEmpty)
    @Test fun `order quantity covers lead time plus cover days`() {
        val f = DemandForecast.forecast(List(14) { 5.0 }, 0, 20.0)
        assertEquals(5 * (3 + 14) - 20, DemandForecast.orderQuantity(f, 20.0, 3, 14))
        assertEquals(0, DemandForecast.orderQuantity(f, 1000.0, 3, 14))
    }
    @Test fun `negative history is treated as zero`() =
        assertEquals(0.0, DemandForecast.forecast(listOf(-5.0, -1.0), 0, 5.0).avgPerDay, 0.0)
    @Test fun `dense fills missing days with zero`() =
        assertEquals(listOf(2.0, 0.0, 3.0), DemandForecast.dense(mapOf(10L to 2.0, 12L to 3.0), 10, 12))
}
