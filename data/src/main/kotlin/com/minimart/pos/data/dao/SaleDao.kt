package com.minimart.pos.data.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.minimart.pos.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {

    @Transaction
    @Query("SELECT * FROM sales ORDER BY createdAt DESC")
    fun getAllSalesWithItems(): Flow<List<SaleWithItems>>

    @Transaction
    @Query("SELECT * FROM sales WHERE id = :saleId LIMIT 1")
    suspend fun getSaleWithItems(saleId: Long): SaleWithItems?

    @Query("""
        SELECT * FROM sales 
        WHERE createdAt >= :startMs AND createdAt <= :endMs 
        ORDER BY createdAt DESC
    """)
    fun getSalesByDateRange(startMs: Long, endMs: Long): Flow<List<Sale>>

    /** Bug fix: getSalesByDateRange returns ALL statuses, so callers had to filter
     * COMPLETED in Kotlin — loading every voided/refunded sale into memory first.
     * This query filters in SQL so only revenue-counted sales are loaded. */
    @Query("""
        SELECT * FROM sales 
        WHERE createdAt >= :startMs AND createdAt <= :endMs AND status = 'COMPLETED'
        ORDER BY createdAt DESC
    """)
    fun getCompletedSalesByDateRange(startMs: Long, endMs: Long): Flow<List<Sale>>

    @Query("SELECT * FROM sales WHERE createdAt >= :startMs ORDER BY createdAt DESC")
    fun getSalesToday(startMs: Long): Flow<List<Sale>>

    @Query("SELECT SUM(totalAmount) FROM sales WHERE createdAt >= :startMs AND status = 'COMPLETED'")
    fun getTotalRevenueToday(startMs: Long): Flow<Double?>

    @Query("SELECT SUM(totalAmount) FROM sales WHERE createdAt >= :startMs AND createdAt < :endMs AND status = 'COMPLETED'")
    fun getTotalRevenueBetween(startMs: Long, endMs: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM sales WHERE createdAt >= :startMs AND status = 'COMPLETED'")
    fun getSaleCountToday(startMs: Long): Flow<Int>

    @Query("""
        SELECT si.productId, si.productName, SUM(si.quantity) as totalQty, SUM(si.lineTotal) as totalRevenue
        FROM sale_items si 
        INNER JOIN sales s ON si.saleId = s.id
        WHERE s.createdAt >= :startMs AND s.createdAt <= :endMs AND s.status = 'COMPLETED'
        GROUP BY si.productId
        ORDER BY totalQty DESC
        LIMIT :limit
    """)
    fun getTopSellingProducts(startMs: Long, endMs: Long = Long.MAX_VALUE, limit: Int = 10): Flow<List<TopSellerResult>>

    /** Per-product sales over a range (completed sales only) — units/kg, revenue, cost of goods and the
     *  revenue that has a known cost price, for analytics and stock insights. Weighed lines count kg. */
    @Query("""
        SELECT si.productId AS productId,
               si.productName AS productName,
               COALESCE(p.category, 'General') AS category,
               COALESCE(SUM(CASE WHEN si.weightKg > 0 THEN si.weightKg ELSE si.quantity END), 0.0) AS qty,
               COALESCE(SUM(si.lineTotal), 0.0) AS revenue,
               COALESCE(SUM((CASE WHEN si.weightKg > 0 THEN si.weightKg ELSE si.quantity END) * COALESCE(p.costPrice, 0.0)), 0.0) AS cost,
               COALESCE(SUM(CASE WHEN COALESCE(p.costPrice, 0.0) > 0 THEN si.lineTotal ELSE 0.0 END), 0.0) AS costedRevenue
        FROM sale_items si
        INNER JOIN sales s ON si.saleId = s.id
        LEFT JOIN products p ON p.id = si.productId
        WHERE s.createdAt >= :startMs AND s.createdAt <= :endMs AND s.status = 'COMPLETED'
        GROUP BY si.productId
    """)
    fun getProductSalesStats(startMs: Long, endMs: Long = Long.MAX_VALUE): Flow<List<ProductSalesStat>>

    /** When each product last sold (completed sales), for spotting dead stock. */
    @Query("""
        SELECT si.productId AS productId, MAX(s.createdAt) AS lastSoldAt
        FROM sale_items si
        INNER JOIN sales s ON si.saleId = s.id
        WHERE s.status = 'COMPLETED'
        GROUP BY si.productId
    """)
    fun getLastSoldTimes(): Flow<List<LastSold>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSale(sale: Sale): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSaleItems(items: List<SaleItem>)


    @Transaction
    @Query("""
        SELECT * FROM sales 
        WHERE receiptNumber LIKE '%' || :query || '%'
           OR notes LIKE '%' || :query || '%'
           OR mpesaRef LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
        LIMIT 100
    """)
    fun searchSales(query: String): Flow<List<SaleWithItems>>

    @Transaction
    @Query("SELECT * FROM sales WHERE status = 'COMPLETED' ORDER BY createdAt DESC")
    fun getCompletedSales(): Flow<List<SaleWithItems>>

    /** Paged Sales History: COMPLETED sales only; a blank [query] matches everything. */
    @Transaction
    @Query("""
        SELECT * FROM sales
        WHERE status = 'COMPLETED' AND (:query = ''
           OR receiptNumber LIKE '%' || :query || '%'
           OR notes LIKE '%' || :query || '%'
           OR mpesaRef LIKE '%' || :query || '%')
        ORDER BY createdAt DESC
    """)
    fun pagedCompletedSales(query: String): PagingSource<Int, SaleWithItems>

    /** Record count and revenue for the same filter as [pagedCompletedSales] (computed in SQL,
     *  so the totals stay correct even though only one page is loaded at a time). */
    @Query("""
        SELECT COUNT(*) AS count, COALESCE(SUM(totalAmount), 0.0) AS total FROM sales
        WHERE status = 'COMPLETED' AND (:query = ''
           OR receiptNumber LIKE '%' || :query || '%'
           OR notes LIKE '%' || :query || '%'
           OR mpesaRef LIKE '%' || :query || '%')
    """)
    fun completedSalesStats(query: String): Flow<SaleStats>

    /** Only a COMPLETED sale can be refunded; returns rows changed (0 = already refunded/voided).
     *  Existing sale notes are kept and the reason is appended. */
    @Query("UPDATE sales SET status = 'REFUNDED', notes = CASE WHEN notes = '' THEN :reason ELSE notes || ' | ' || :reason END WHERE id = :saleId AND status = 'COMPLETED'")
    suspend fun refundSale(saleId: Long, reason: String): Int

    @Query("UPDATE sales SET status = 'VOIDED', notes = CASE WHEN notes = '' THEN :reason ELSE notes || ' | ' || :reason END WHERE id = :saleId AND status = 'COMPLETED'")
    suspend fun voidSale(saleId: Long, reason: String): Int

    /** How many live (not refunded/voided) sales already claim this M-Pesa reference. */
    @Query("SELECT COUNT(*) FROM sales WHERE mpesaRef = :ref COLLATE NOCASE AND status = 'COMPLETED'")
    suspend fun countCompletedWithMpesaRef(ref: String): Int

    /** Finds a sale by receipt number and creation time (how a synced sale is matched on the main device). */
    @Query("SELECT * FROM sales WHERE receiptNumber = :receipt AND createdAt = :createdAt LIMIT 1")
    suspend fun findByReceiptAndTime(receipt: String, createdAt: Long): Sale?

    @Transaction
    suspend fun insertSaleWithItems(sale: Sale, items: List<SaleItem>): Long {
        val saleId = insertSale(sale)
        val itemsWithSaleId = items.map { it.copy(saleId = saleId) }
        insertSaleItems(itemsWithSaleId)
        return saleId
    }
}

data class ProductSalesStat(
    val productId: Long,
    val productName: String,
    val category: String,
    val qty: Double,
    val revenue: Double,
    val cost: Double,
    val costedRevenue: Double
)

data class LastSold(val productId: Long, val lastSoldAt: Long)

data class SaleStats(val count: Int, val total: Double)

data class TopSellerResult(
    val productId: Long,
    val productName: String,
    val totalQty: Int,
    val totalRevenue: Double
)

// Search extension added at end — actually must go inside interface
// Will patch inline instead
