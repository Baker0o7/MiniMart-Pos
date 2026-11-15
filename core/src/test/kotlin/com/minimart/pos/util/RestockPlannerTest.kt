package com.minimart.pos.util

import org.junit.Assert.*
import org.junit.Test

class RestockPlannerTest {
    @Test fun fastSellerWithShortCoverIsDue() {
        // 10/day, 40 on hand -> 4 days; reorder point 10*(3+2)=50
        val p = RestockPlanner.plan(40.0, 300.0, 30, 5.0, 1)
        assertTrue(p.needsReorder)
        assertEquals(4.0, p.daysCover!!, 1e-9)
        assertEquals(10 * (3 + 14) - 40, p.suggestedQty)
    }

    @Test fun plentyOfStockIsNotDue() {
        val p = RestockPlanner.plan(200.0, 300.0, 30, 5.0, 1)
        assertFalse(p.needsReorder)
        assertEquals(0, p.suggestedQty)
    }

    @Test fun deadItemAboveThresholdIsNotDue() {
        val p = RestockPlanner.plan(20.0, 0.0, 30, 5.0, 1)
        assertFalse(p.needsReorder)
        assertNull(p.daysCover)
    }

    @Test fun nonSellerAtOrBelowThresholdIsDue() {
        val p = RestockPlanner.plan(3.0, 0.0, 30, 5.0, 6)
        assertTrue(p.needsReorder)
        assertEquals(6, p.suggestedQty)
    }

    @Test fun minimumOrderQuantityRespected() {
        val p = RestockPlanner.plan(10.0, 30.0, 30, 2.0, 24)
        // 1/day, reorder point 5 -> not due yet
        assertFalse(p.needsReorder)
        val q = RestockPlanner.plan(4.0, 30.0, 30, 2.0, 24)
        assertTrue(q.needsReorder)
        assertEquals(24, q.suggestedQty)
    }

    @Test fun newProductUsesItsOwnAge() {
        // sold 10 in 2 days = 5/day, 12 on hand -> due (point 25)
        val p = RestockPlanner.plan(12.0, 10.0, 2, 2.0, 1)
        assertTrue(p.needsReorder)
        assertEquals(5.0, p.avgDaily, 1e-9)
    }

    @Test fun negativeStockTreatedAsZero() {
        val p = RestockPlanner.plan(-4.0, 30.0, 30, 2.0, 1)
        assertTrue(p.needsReorder)
        assertEquals(1 * 17, p.suggestedQty)
    }
}
