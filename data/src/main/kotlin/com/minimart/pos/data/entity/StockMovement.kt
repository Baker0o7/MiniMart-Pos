package com.minimart.pos.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One change to a product's stock, written by a database trigger (see StockTriggers) so no code path can change stock
 * without leaving a line. [reason] says why when the code that made the change said so; a change nobody explained is
 * [Reasons.OTHER], which Stock reconciliation shows as "unexplained".
 *
 * [delta] and [stockAfter] are in the product's own measure: whole units for counted products, kilograms for weighed ones.
 */
@Entity(tableName = "stock_movements", indices = [Index("productId", "at"), Index("reason")])
data class StockMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val at: Long,
    val delta: Double,
    val reason: String,
    /** The sale, purchase order, count or loss the change belongs to (0 when none). */
    val ref: Long = 0,
    val stockAfter: Double
) {
    object Reasons {
        const val SALE = "SALE"
        const val REFUND = "REFUND"
        const val VOID = "VOID"
        const val RECEIVE = "RECEIVE"
        const val ADJUST = "ADJUST"
        const val COUNT = "COUNT"
        const val NEW_PRODUCT = "NEW_PRODUCT"
        const val OPENING = "OPENING"
        const val LOSS_EXPIRED = "LOSS_EXPIRED"
        const val LOSS_DAMAGED = "LOSS_DAMAGED"
        const val LOSS_THEFT = "LOSS_THEFT"
        const val LOSS_WASTAGE = "LOSS_WASTAGE"
        const val OTHER = "OTHER"
    }
}

/** A single row (id 1) naming the reason for the stock changes made in the current transaction. Rolled back with it. */
@Entity(tableName = "stock_context")
data class StockContext(
    @PrimaryKey val id: Int = 1,
    val reason: String = "",
    val ref: Long = 0
)
