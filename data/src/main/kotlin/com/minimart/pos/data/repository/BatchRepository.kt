package com.minimart.pos.data.repository

import com.minimart.pos.data.dao.BatchDao
import com.minimart.pos.data.dao.ProductDao
import com.minimart.pos.data.entity.SaleItem
import com.minimart.pos.data.entity.SaleItemCost
import com.minimart.pos.data.entity.StockBatch
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FIFO cost layers. Every method that changes batches is meant to run inside the caller's database
 * transaction (sale, delivery, stock adjustment), so batches and stock cannot disagree after a crash.
 */
@Singleton
class BatchRepository @Inject constructor(
    private val dao: BatchDao,
    private val productDao: ProductDao
) {
    fun recentBatches(productId: Long): Flow<List<StockBatch>> = dao.recentBatches(productId)
    fun batchStockValue(): Flow<Double> = dao.batchStockValue()

    /** Records stock coming in at [unitCost]. A zero or negative quantity is ignored. */
    suspend fun addBatch(
        productId: Long, qty: Double, unitCost: Double, source: String,
        batchNumber: String = "", expiryDate: Long = 0L
    ) {
        if (qty <= 0.0 || !qty.isFinite()) return
        dao.insertBatch(StockBatch(
            productId = productId, quantity = qty, remaining = qty,
            unitCost = unitCost.coerceAtLeast(0.0), source = source,
            batchNumber = batchNumber, expiryDate = expiryDate
        ))
    }

    /** A manual stock change: stock-in becomes a batch at [unitCost]; stock-out (loss, correction) uses up the oldest batches. */
    suspend fun recordManualChange(productId: Long, delta: Double, unitCost: Double, source: String) {
        if (!delta.isFinite()) return
        if (delta > 0.0) addBatch(productId, delta, unitCost, source) else if (delta < 0.0) consume(productId, -delta)
    }

    /** Takes [qty] out of the oldest batches and returns what it cost. Nothing is left uncosted. */
    suspend fun consume(productId: Long, qty: Double): List<CostPart> {
        val fallback = productDao.getProductById(productId)?.costPrice ?: 0.0
        val parts = FifoCosting.allocate(
            dao.openBatches(productId).map { BatchSlice(it.id, it.remaining, it.unitCost) }, qty, fallback
        )
        parts.filter { it.batchId != 0L }.forEach { dao.consume(it.batchId, it.qty) }
        return parts
    }

    /**
     * Costs every line of a new sale, takes the stock out of the batches and returns the lines with
     * their cost filled in, plus the per-line allocation to store once the line ids are known.
     */
    suspend fun costLines(items: List<SaleItem>): Pair<List<SaleItem>, List<List<CostPart>>> {
        val allocations = items.map { consume(it.productId, if (it.weightKg > 0.0) it.weightKg else it.quantity.toDouble()) }
        val costed = items.mapIndexed { i, item -> item.copy(costTotal = FifoCosting.total(allocations[i])) }
        return costed to allocations
    }

    /** Stores which batches each (now numbered) sale line came from. */
    suspend fun saveAllocations(itemIds: List<Long>, allocations: List<List<CostPart>>) {
        val rows = itemIds.zip(allocations).flatMap { (itemId, parts) ->
            parts.map { SaleItemCost(saleItemId = itemId, batchId = it.batchId, quantity = it.qty, unitCost = it.unitCost) }
        }
        if (rows.isNotEmpty()) dao.insertCosts(rows)
    }

    /** A voided or refunded sale puts its goods back into the batches they came from. */
    suspend fun restoreForSale(saleId: Long) {
        dao.costsForSale(saleId).filter { it.batchId != 0L }.forEach { dao.restore(it.batchId, it.quantity) }
        dao.deleteCostsForSale(saleId)
    }
}
