package com.minimart.pos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.minimart.pos.data.entity.SaleItemCost
import com.minimart.pos.data.entity.StockBatch
import kotlinx.coroutines.flow.Flow

@Dao
interface BatchDao {
    @Insert suspend fun insertBatch(batch: StockBatch): Long
    @Insert suspend fun insertCosts(costs: List<SaleItemCost>)

    /** Batches with stock left, oldest first. */
    @Query("SELECT * FROM stock_batches WHERE productId = :productId AND remaining > 0.000001 ORDER BY receivedAt ASC, id ASC")
    suspend fun openBatches(productId: Long): List<StockBatch>

    @Query("UPDATE stock_batches SET remaining = MAX(0.0, remaining - :qty) WHERE id = :id")
    suspend fun consume(id: Long, qty: Double)

    /** Puts stock back into a batch, never beyond what was originally received. */
    @Query("UPDATE stock_batches SET remaining = MIN(quantity, remaining + :qty) WHERE id = :id")
    suspend fun restore(id: Long, qty: Double)

    @Query("SELECT * FROM sale_item_costs WHERE saleItemId IN (SELECT id FROM sale_items WHERE saleId = :saleId)")
    suspend fun costsForSale(saleId: Long): List<SaleItemCost>

    @Query("DELETE FROM sale_item_costs WHERE saleItemId IN (SELECT id FROM sale_items WHERE saleId = :saleId)")
    suspend fun deleteCostsForSale(saleId: Long)

    @Query("SELECT * FROM stock_batches WHERE productId = :productId ORDER BY receivedAt DESC, id DESC LIMIT :limit")
    fun recentBatches(productId: Long, limit: Int = 20): Flow<List<StockBatch>>

    /** Money tied up in batches that still have stock. */
    @Query("SELECT COALESCE(SUM(remaining * unitCost), 0.0) FROM stock_batches WHERE remaining > 0.000001")
    fun batchStockValue(): Flow<Double>
}
