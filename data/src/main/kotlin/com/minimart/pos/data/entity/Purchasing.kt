package com.minimart.pos.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A company the shop buys stock from. Never hard-deleted — purchase orders keep pointing at it. */
@Entity(tableName = "suppliers", indices = [Index("name")])
data class Supplier(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val notes: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

enum class PurchaseOrderStatus { DRAFT, ORDERED, PARTIAL, RECEIVED, CANCELLED }

/** Header of an order placed with a supplier. Its lines are [PurchaseOrderItem]s. */
@Entity(tableName = "purchase_orders", indices = [Index("supplierId"), Index("status")])
data class PurchaseOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplierId: Long,
    val status: PurchaseOrderStatus = PurchaseOrderStatus.DRAFT,
    val notes: String = "",
    /** Money already paid to the supplier against this order. */
    val amountPaid: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val orderedAt: Long = 0L,     // 0 = not sent yet
    val receivedAt: Long = 0L     // 0 = not (fully) received
)

/** One product line. Quantities are Double so weighed products can be ordered in kg. */
@Entity(tableName = "purchase_order_items", indices = [Index("poId")])
data class PurchaseOrderItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val poId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Double,
    val receivedQty: Double = 0.0,
    val unitCost: Double = 0.0
) {
    val lineTotal: Double get() = quantity * unitCost
    val receivedValue: Double get() = receivedQty * unitCost
    val remaining: Double get() = (quantity - receivedQty).coerceAtLeast(0.0)
}

/** "PO-0007" — derived from the id, so there is no separate number to keep unique. */
fun poLabel(id: Long): String = "PO-" + id.toString().padStart(4, '0')
