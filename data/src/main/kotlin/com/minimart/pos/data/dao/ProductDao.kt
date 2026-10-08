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

    @Query("SELECT * FROM products WHERE pluCode = :plu AND isWeighed = 1 AND isActive = 1 LIMIT 1")
    suspend fun getProductByPlu(plu: String): Product?

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Long): Product?

    @Query("""
        SELECT * FROM products 
        WHERE isActive = 1 AND (
            name LIKE '%' || :query || '%' OR 
            barcode LIKE '%' || :query || '%' OR
            category LIKE '%' || :query || '%' OR
            sku LIKE '%' || :query || '%'
        ) ORDER BY
            CASE
                WHEN barcode = :query OR sku = :query THEN 0
                WHEN name LIKE :query || '%' THEN 1
                WHEN name LIKE '% ' || :query || '%' THEN 2
                WHEN name LIKE '%' || :query || '%' THEN 3
                ELSE 4
            END, name ASC
        LIMIT 40
    """)
    fun searchProducts(query: String): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE isActive = 1 AND stock <= lowStockThreshold ORDER BY stock ASC")
    fun getLowStockProducts(): Flow<List<Product>>

    /** Only products that can expire and still have stock — a small set, unlike every product. */
    @Query("SELECT * FROM products WHERE isActive = 1 AND expiryDate > 0 AND (stock > 0 OR stockKg > 0) ORDER BY expiryDate ASC")
    fun getExpiryCandidates(): Flow<List<Product>>

    @Query("SELECT DISTINCT category FROM products WHERE isActive = 1 ORDER BY category ASC")
    fun getCategories(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM products WHERE isActive = 1")
    suspend fun getProductCount(): Int

    // ── Mutations ─────────────────────────────────────────────────────────────

    // ABORT, not REPLACE: REPLACE deletes the conflicting row, so a duplicate barcode would
    // silently wipe another product (or fail on its sale history via the RESTRICT FK).
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProduct(product: Product): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<Product>)

    @Update
    suspend fun updateProduct(product: Product)

    /** Returns the number of rows updated: 0 means there was not enough stock (nothing changed). */
    @Query("UPDATE products SET stock = stock - :quantity, updatedAt = :now WHERE id = :productId AND stock >= :quantity")
    suspend fun decrementStock(productId: Long, quantity: Int, now: Long = System.currentTimeMillis()): Int

    @Query("UPDATE products SET stock = stock + :quantity, updatedAt = :now WHERE id = :productId")
    suspend fun incrementStock(productId: Long, quantity: Int, now: Long = System.currentTimeMillis())

    /** Weighed products: take [kg] off exact stock (never below 0) and keep the integer
     *  [stock] mirror in sync. SQLite evaluates every right-hand side against the old row. */
    @Query("UPDATE products SET stockKg = MAX(stockKg - :kg, 0.0), stock = CAST(MAX(stockKg - :kg, 0.0) AS INTEGER), updatedAt = :now WHERE id = :productId")
    suspend fun decrementStockKg(productId: Long, kg: Double, now: Long = System.currentTimeMillis()): Int

    @Query("UPDATE products SET stockKg = stockKg + :kg, stock = CAST(stockKg + :kg AS INTEGER), updatedAt = :now WHERE id = :productId")
    suspend fun incrementStockKg(productId: Long, kg: Double, now: Long = System.currentTimeMillis()): Int

    /** A deleted product keeps its row (sale history points at it) and so kept its UNIQUE barcode too,
     *  which made that barcode impossible to add again. Renaming the deleted row frees it. */
    @Query("UPDATE products SET barcode = barcode || '~del' || id WHERE barcode = :barcode AND isActive = 0")
    suspend fun releaseDeletedBarcode(barcode: String): Int

    @Query("UPDATE products SET isActive = 0, updatedAt = :now WHERE id = :productId")
    suspend fun softDeleteProduct(productId: Long, now: Long = System.currentTimeMillis())
}
