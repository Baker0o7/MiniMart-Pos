package com.minimart.pos.data.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

// ─── Sale (header) ────────────────────────────────────────────────────────────

enum class PaymentMethod { CASH, MPESA, CARD, MIXED, CREDIT }
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
    // Bug fix: for MIXED (split cash+credit) payments, amountPaid stores
    // creditAmount + cashAmount COMBINED — there was no way to recover just the
    // cash portion for end-of-shift till reconciliation. This field always holds
    // the actual cash physically collected for this sale (0.0 for MPESA/CARD/CREDIT,
    // the full totalAmount for CASH, and just the cash share for MIXED).
    @ColumnInfo(defaultValue = "0.0") val cashPortion: Double = 0.0,
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
    var discount: Double = 0.0,
    // For weighed products: actual weight from PLU barcode
    val weightKg: Double = 0.0
) {
    // ── Inclusive VAT (tax is already inside the selling price) ───────────────
    val lineSubtotal: Double get() = if (product.isWeighed && weightKg > 0)
        com.minimart.pos.util.PluDecoder.calculatePrice(product.pricePerKg, weightKg)
    else product.price * quantity
    val lineTax: Double get() = if (product.taxRate > 0)
        lineSubtotal - (lineSubtotal / (1.0 + product.taxRate)) else 0.0
    val lineNet: Double get() = lineSubtotal - lineTax
    val lineDiscount: Double get() = discount
    val lineTotal: Double get() = lineSubtotal - lineDiscount
    // Display helpers
    val displayWeight: String get() = if (product.isWeighed && weightKg > 0)
        "${String.format("%.3f", weightKg)} kg" else "${quantity} pcs"
}
