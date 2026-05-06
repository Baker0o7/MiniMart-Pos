package com.minimart.pos.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    indices = [Index(value = ["barcode"], unique = true), Index(value = ["category"]), Index(value = ["sku"])]
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
    val supplierName: String = "",       // supplier / vendor name
    val supplierPhone: String = "",      // supplier contact number
    val reorderQuantity: Int = 0,        // how many to order when restocking
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
