package com.minimart.pos.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.minimart.pos.data.entity.StockMovement
import kotlinx.coroutines.flow.Flow

/** What changed in one product's stock for one reason over a period. */
data class ReasonTotal(val productId: Long, val reason: String, val total: Double, val lines: Int)

@Dao
interface StockMovementDao {
    @Query("UPDATE stock_context SET reason = :reason, ref = :ref WHERE id = 1")
    suspend fun setContext(reason: String, ref: Long): Int

    @Query("UPDATE stock_context SET reason = '', ref = 0 WHERE id = 1")
    suspend fun clearContext(): Int

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY at DESC, id DESC LIMIT :limit")
    fun recentFor(productId: Long, limit: Int = 100): Flow<List<StockMovement>>

    @Query("SELECT * FROM stock_movements WHERE at >= :since ORDER BY productId, at, id")
    suspend fun since(since: Long): List<StockMovement>

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY at, id")
    suspend fun allFor(productId: Long): List<StockMovement>

    @Query("SELECT MIN(at) FROM stock_movements")
    suspend fun firstMovementAt(): Long?

    /** Net change per product and reason from [since]; the base of the reconciliation view. */
    @Query("SELECT productId, reason, SUM(delta) AS total, COUNT(*) AS lines FROM stock_movements WHERE at >= :since GROUP BY productId, reason")
    suspend fun totalsSince(since: Long): List<ReasonTotal>
}
