package com.minimart.pos.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    indices = [Index(value = ["barcode"], unique = true), Index(value = ["category"]), Index(value = ["sku"]), Index(value = ["pluCode"])]
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String,
    val sku: String = "",
    val name: String,
    val description: String = "",
    val price: Double,
    val costPrice: Double = 0.0,
    val stock: Int,
    val lowStockThreshold: Int = 5,
    val category: String = "General",
    val unit: String = "pcs",
    val imageUri: String? = null,
    val taxRate: Double = 0.0,
    @androidx.room.ColumnInfo(defaultValue = "") val supplierName: String = "",
    @androidx.room.ColumnInfo(defaultValue = "") val supplierPhone: String = "",
    @androidx.room.ColumnInfo(defaultValue = "0") val reorderQuantity: Int = 0,
    @androidx.room.ColumnInfo(defaultValue = "") val batchNumber: String = "",
    @androidx.room.ColumnInfo(defaultValue = "0") val expiryDate: Long = 0L,  // 0 = no expiry
    // ── Weighing scale / PLU ─────────────────────────────────────────────
    @androidx.room.ColumnInfo(defaultValue = "") val pluCode: String = "",
    @androidx.room.ColumnInfo(defaultValue = "0") val isWeighed: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "0.0") val pricePerKg: Double = 0.0,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
