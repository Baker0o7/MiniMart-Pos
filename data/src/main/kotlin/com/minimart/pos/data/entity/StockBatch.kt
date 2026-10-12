package com.minimart.pos.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One delivery (or manual stock-in) of a product at its own unit cost. Sales use up the oldest batch
 * first (FIFO), so profit reflects what the goods actually cost. [remaining] is in units, or kg for
 * weighed products.
 */
@Entity(
    tableName = "stock_batches",
    indices = [Index("productId"), Index(value = ["productId", "receivedAt"])]
)
data class StockBatch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val receivedAt: Long = System.currentTimeMillis(),
    val quantity: Double,
    val remaining: Double,
    val unitCost: Double,
    val source: String = "",
    val batchNumber: String = "",
    val expiryDate: Long = 0L
)

/** Which batch(es) a sale line was taken from. [batchId] 0 means stock that had no batch record. */
@Entity(
    tableName = "sale_item_costs",
    indices = [Index("saleItemId"), Index("batchId")]
)
data class SaleItemCost(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleItemId: Long,
    val batchId: Long,
    val quantity: Double,
    val unitCost: Double
)
