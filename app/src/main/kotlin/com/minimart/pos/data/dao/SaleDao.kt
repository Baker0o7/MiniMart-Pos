package com.minimart.pos.data.dao

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

    @Query("SELECT * FROM sales WHERE createdAt >= :startMs ORDER BY createdAt DESC")
    fun getSalesToday(startMs: Long): Flow<List<Sale>>

    @Query("SELECT SUM(totalAmount) FROM sales WHERE createdAt >= :startMs AND status = 'COMPLETED'")
    fun getTotalRevenueToday(startMs: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM sales WHERE createdAt >= :startMs AND status = 'COMPLETED'")
    fun getSaleCountToday(startMs: Long): Flow<Int>

    @Query("""
        SELECT si.productId, si.productName, SUM(si.quantity) as totalQty, SUM(si.lineTotal) as totalRevenue
        FROM sale_items si 
        INNER JOIN sales s ON si.saleId = s.id
        WHERE s.createdAt >= :startMs AND s.status = 'COMPLETED'
        GROUP BY si.productId
        ORDER BY totalQty DESC
        LIMIT :limit
    """)
    fun getTopSellingProducts(startMs: Long, limit: Int = 10): Flow<List<TopSellerResult>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSale(sale: Sale): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSaleItems(items: List<SaleItem>)

    @Query("UPDATE sales SET status = 'VOIDED' WHERE id = :saleId")
    suspend fun voidSale(saleId: Long)

    @Transaction
    suspend fun insertSaleWithItems(sale: Sale, items: List<SaleItem>): Long {
        val saleId = insertSale(sale)
        val itemsWithSaleId = items.map { it.copy(saleId = saleId) }
        insertSaleItems(itemsWithSaleId)
        return saleId
    }
}

data class TopSellerResult(
    val productId: Long,
    val productName: String,
    val totalQty: Int,
    val totalRevenue: Double
)

// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE username = :username AND isActive = 1 LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Long): User?

    @Query("SELECT * FROM users WHERE isActive = 1 ORDER BY displayName ASC")
    fun getAllUsers(): Flow<List<User>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: User): Long

    @Update
    suspend fun updateUser(user: User)

    @Query("SELECT COUNT(*) FROM users WHERE isActive = 1")
    suspend fun getUserCount(): Int
}
