package com.minimart.pos.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A unit the shop can count in: pcs, kg, carton... [code] is what products store and receipts print. */
@Entity(tableName = "uoms", indices = [Index(value = ["code"], unique = true)])
data class Uom(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val name: String,
    val isActive: Boolean = true,
    val sortOrder: Int = 0
)

/**
 * One extra way to count a product: 1 [unitCode] = [factor] of the product's base unit. Stock, sales and orders are always
 * kept in the base unit; this only drives the "sell/order a carton" shortcuts and the "3 cartons + 3 pcs" display.
 */
@Entity(
    tableName = "product_units",
    foreignKeys = [ForeignKey(entity = Product::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("productId"), Index(value = ["productId", "unitCode"], unique = true), Index("barcode")]
)
data class ProductUnit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val unitCode: String,
    val factor: Int,
    val sellable: Boolean = true,
    val purchasable: Boolean = true,
    /** The shop's own price for one pack (a carton deal). 0 = not set: the price is the unit price times the factor. */
    @androidx.room.ColumnInfo(defaultValue = "0.0") val price: Double = 0.0,
    /** Barcode printed on the pack. Empty = none. Kept unique across products and packs by the repository. */
    @androidx.room.ColumnInfo(defaultValue = "''") val barcode: String = "",
    /** What one pack costs when bought. 0 = not set: the unit cost times the factor. */
    @androidx.room.ColumnInfo(defaultValue = "0.0") val cost: Double = 0.0
)
