package com.minimart.pos.data.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

// ─── Sale (header) ────────────────────────────────────────────────────────────

enum class PaymentMethod { CASH, MPESA, CARD, MIXED }
enum class SaleStatus { COMPLETED, REFUNDED, VOIDED }

@Entity(tableName = "sales")
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptNumber: String,           // e.g. "RCP-20250412-0001"
    val subtotal: Double,
    val taxAmount: Double,
    val discountAmount: Double = 0.0,
    val totalAmount: Double,
    val amountPaid: Double,
    val changeGiven: Double,
    val paymentMethod: PaymentMethod,
    val mpesaRef: String? = null,        // M-Pesa transaction reference
    val status: SaleStatus = SaleStatus.COMPLETED,
    val cashierId: Long,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

// ─── Sale Line Item ───────────────────────────────────────────────────────────

@Entity(
    tableName = "sale_items",
    foreignKeys = [
        ForeignKey(entity = Sale::class, parentColumns = ["id"], childColumns = ["saleId"],
            onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Product::class, parentColumns = ["id"], childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("saleId"), Index("productId")]
)
data class SaleItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val productId: Long,
    val productBarcode: String,
    val productName: String,             // snapshot at time of sale
    val unitPrice: Double,
    val quantity: Int,
    val discountAmount: Double = 0.0,
    val taxAmount: Double = 0.0,
    val lineTotal: Double                // (unitPrice * quantity) - discount + tax
)

// ─── Sale with items (Room relation) ─────────────────────────────────────────

data class SaleWithItems(
    @Embedded val sale: Sale,
    @Relation(parentColumn = "id", entityColumn = "saleId")
    val items: List<SaleItem>
)

// ─── Cart Item (in-memory, not persisted) ─────────────────────────────────────

data class CartItem(
    val product: Product,
    var quantity: Int = 1,
    var discount: Double = 0.0
) {
    val lineSubtotal: Double get() = product.price * quantity
    val lineTax: Double get() = lineSubtotal * product.taxRate
    val lineDiscount: Double get() = discount
    val lineTotal: Double get() = lineSubtotal + lineTax - lineDiscount
}
