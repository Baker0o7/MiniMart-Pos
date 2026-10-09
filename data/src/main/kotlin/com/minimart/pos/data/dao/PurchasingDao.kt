package com.minimart.pos.data.dao

import androidx.room.*
import com.minimart.pos.data.entity.PurchaseOrder
import com.minimart.pos.data.entity.PurchaseOrderItem
import com.minimart.pos.data.entity.Supplier
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchasingDao {

    // ── Suppliers ────────────────────────────────────────────────────────────
    @Query("SELECT * FROM suppliers WHERE isActive = 1 ORDER BY name COLLATE NOCASE ASC")
    fun getSuppliers(): Flow<List<Supplier>>

    @Query("SELECT * FROM suppliers ORDER BY name COLLATE NOCASE ASC")
    fun getAllSuppliersIncludingInactive(): Flow<List<Supplier>>

    @Query("SELECT * FROM suppliers WHERE id = :id LIMIT 1")
    suspend fun getSupplier(id: Long): Supplier?

    @Insert
    suspend fun insertSupplier(supplier: Supplier): Long

    @Update
    suspend fun updateSupplier(supplier: Supplier)

    // ── Purchase orders ──────────────────────────────────────────────────────
    @Query("SELECT * FROM purchase_orders ORDER BY createdAt DESC")
    fun getOrders(): Flow<List<PurchaseOrder>>

    @Query("SELECT * FROM purchase_orders WHERE id = :id LIMIT 1")
    suspend fun getOrder(id: Long): PurchaseOrder?

    @Insert
    suspend fun insertOrder(order: PurchaseOrder): Long

    @Update
    suspend fun updateOrder(order: PurchaseOrder)

    @Query("DELETE FROM purchase_orders WHERE id = :id")
    suspend fun deleteOrder(id: Long)

    // ── Lines ────────────────────────────────────────────────────────────────
    @Query("SELECT * FROM purchase_order_items ORDER BY id ASC")
    fun getAllItems(): Flow<List<PurchaseOrderItem>>

    @Query("SELECT * FROM purchase_order_items WHERE poId = :poId ORDER BY id ASC")
    suspend fun getItems(poId: Long): List<PurchaseOrderItem>

    @Insert
    suspend fun insertItems(items: List<PurchaseOrderItem>)

    @Update
    suspend fun updateItem(item: PurchaseOrderItem)

    @Query("DELETE FROM purchase_order_items WHERE poId = :poId")
    suspend fun deleteItems(poId: Long)
}
