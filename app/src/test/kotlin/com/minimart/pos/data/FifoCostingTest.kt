package com.minimart.pos.data

import com.minimart.pos.data.repository.BatchSlice
import com.minimart.pos.data.repository.FifoCosting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FifoCostingTest {
    private val batches = listOf(BatchSlice(1, 10.0, 50.0), BatchSlice(2, 10.0, 60.0))

    @Test fun takesFromOldestBatchFirst() {
        val parts = FifoCosting.allocate(batches, 4.0, 99.0)
        assertEquals(1, parts.size)
        assertEquals(1L, parts[0].batchId)
        assertEquals(200.0, FifoCosting.total(parts), 0.001)
    }

    @Test fun spansBatchesWhenFirstRunsOut() {
        val parts = FifoCosting.allocate(batches, 15.0, 99.0)
        assertEquals(listOf(1L, 2L), parts.map { it.batchId })
        assertEquals(10.0, parts[0].qty, 1e-9)
        assertEquals(5.0, parts[1].qty, 1e-9)
        assertEquals(10 * 50.0 + 5 * 60.0, FifoCosting.total(parts), 0.001)
    }

    @Test fun shortfallUsesFallbackCostUnderBatchZero() {
        val parts = FifoCosting.allocate(batches, 25.0, 70.0)
        val last = parts.last()
        assertEquals(0L, last.batchId)
        assertEquals(5.0, last.qty, 1e-9)
        assertEquals(70.0, last.unitCost, 1e-9)
        assertEquals(500.0 + 600.0 + 350.0, FifoCosting.total(parts), 0.001)
    }

    @Test fun noBatchesMeansWholeLineAtFallback() {
        val parts = FifoCosting.allocate(emptyList(), 3.0, 12.5)
        assertEquals(1, parts.size)
        assertEquals(37.5, FifoCosting.total(parts), 0.001)
    }

    @Test fun zeroOrNegativeNeedGivesNothing() {
        assertTrue(FifoCosting.allocate(batches, 0.0, 10.0).isEmpty())
        assertTrue(FifoCosting.allocate(batches, -2.0, 10.0).isEmpty())
    }

    @Test fun skipsEmptiedBatches() {
        val parts = FifoCosting.allocate(listOf(BatchSlice(1, 0.0, 50.0), BatchSlice(2, 5.0, 60.0)), 2.0, 99.0)
        assertEquals(listOf(2L), parts.map { it.batchId })
    }

    @Test fun fractionalKgQuantities() {
        val parts = FifoCosting.allocate(listOf(BatchSlice(1, 2.5, 120.0)), 1.25, 0.0)
        assertEquals(150.0, FifoCosting.total(parts), 0.001)
    }

    @Test fun negativeFallbackIsClampedToZero() {
        val parts = FifoCosting.allocate(emptyList(), 2.0, -5.0)
        assertEquals(0.0, FifoCosting.total(parts), 0.0)
    }

    @Test fun allocationNeverExceedsNeed() {
        val parts = FifoCosting.allocate(batches, 13.7, 1.0)
        assertEquals(13.7, parts.sumOf { it.qty }, 1e-9)
    }

    @Test fun exactlyEmptyingABatchDoesNotTouchTheNext() {
        val parts = FifoCosting.allocate(batches, 10.0, 99.0)
        assertEquals(listOf(1L), parts.map { it.batchId })
    }

    @Test fun batchesAreUsedInTheOrderGiven() {
        val parts = FifoCosting.allocate(listOf(BatchSlice(5, 1.0, 10.0), BatchSlice(2, 1.0, 20.0)), 2.0, 0.0)
        assertEquals(listOf(5L, 2L), parts.map { it.batchId })
    }

    @Test fun negativeRemainingIsSkipped() {
        val parts = FifoCosting.allocate(listOf(BatchSlice(1, -3.0, 50.0), BatchSlice(2, 4.0, 60.0)), 2.0, 99.0)
        assertEquals(listOf(2L), parts.map { it.batchId })
    }

    @Test fun dustLeftoverDoesNotCreateAFallbackLine() {
        val parts = FifoCosting.allocate(listOf(BatchSlice(1, 10.0, 5.0)), 10.0 + 1e-10, 99.0)
        assertEquals(1, parts.size)
    }

    @Test fun totalIsRoundedToCents() {
        assertEquals(0.33, FifoCosting.total(FifoCosting.allocate(listOf(BatchSlice(1, 1.0, 0.3349)), 1.0, 0.0)), 1e-9)
        assertEquals(0.0, FifoCosting.total(emptyList()), 0.0)
    }

    @Test fun costPartMultipliesQuantityByUnitCost() {
        assertEquals(37.5, com.minimart.pos.data.repository.CostPart(1, 2.5, 15.0).cost, 1e-9)
    }

    @Test fun partsAlwaysAddUpToTheNeedAcrossManyShapes() {
        for (needed in listOf(0.5, 1.0, 9.99, 10.0, 10.01, 19.5, 20.0, 35.25)) {
            val parts = FifoCosting.allocate(batches, needed, 1.0)
            assertEquals("need $needed", needed, parts.sumOf { it.qty }, 1e-9)
            assertTrue(parts.none { it.qty <= 0.0 })
        }
    }

    @Test fun costNeverDropsBelowTheBatchCostForCoveredStock() {
        val parts = FifoCosting.allocate(batches, 12.0, 0.0)
        assertEquals(10 * 50.0 + 2 * 60.0, FifoCosting.total(parts), 0.001)
    }
}
