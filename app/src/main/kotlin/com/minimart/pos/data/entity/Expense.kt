package com.minimart.pos.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ExpenseCategory {
    SUPPLIER, ELECTRICITY, WATER, RENT, SALARY, TRANSPORT,
    PACKAGING, CLEANING, MAINTENANCE, TAXES, OTHER
}

@Entity(tableName = "expenses", indices = [Index("createdAt"), Index("category")])
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Double,
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val supplierName: String = "",       // for SUPPLIER category
    val notes: String = "",
    val receiptRef: String = "",         // receipt/invoice number
    val cashierId: Long = 1L,
    val createdAt: Long = System.currentTimeMillis()
)
