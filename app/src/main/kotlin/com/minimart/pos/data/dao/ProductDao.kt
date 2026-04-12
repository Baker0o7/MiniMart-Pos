package com.minimart.pos.data.dao

import androidx.room.*
import com.minimart.pos.data.entity.Product
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    // ── Queries ──────────────────────────────────────────────────────────────

    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY name ASC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE isActive = 1 AND category = :category ORDER BY name ASC")
    fun getProductsByCategory(category: String): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE barcode = :barcode AND isActive = 1 LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): Product?

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Long): Product?

    @Query("""
        SELECT * FROM products 
        WHERE isActive = 1 AND (
            name LIKE '%' || :query || '%' OR 
            barcode LIKE '%' || :query || '%' OR
            category LIKE '%' || :query || '%'
        ) ORDER BY name ASC
    """)
    fun searchProducts(query: String): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE isActive = 1 AND stock <= lowStockThreshold ORDER BY stock ASC")
    fun getLowStockProducts(): Flow<List<Product>>

    @Query("SELECT DISTINCT category FROM products WHERE isActive = 1 ORDER BY category ASC")
    fun getCategories(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM products WHERE isActive = 1")
    suspend fun getProductCount(): Int

    // ── Mutations ─────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<Product>)

    @Update
    suspend fun updateProduct(product: Product)

    @Query("UPDATE products SET stock = stock - :quantity, updatedAt = :now WHERE id = :productId")
    suspend fun decrementStock(productId: Long, quantity: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE products SET stock = stock + :quantity, updatedAt = :now WHERE id = :productId")
    suspend fun incrementStock(productId: Long, quantity: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE products SET isActive = 0, updatedAt = :now WHERE id = :productId")
    suspend fun softDeleteProduct(productId: Long, now: Long = System.currentTimeMillis())
}
