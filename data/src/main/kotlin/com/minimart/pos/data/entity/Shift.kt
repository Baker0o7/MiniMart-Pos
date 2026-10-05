package com.minimart.pos.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ShiftStatus { OPEN, CLOSED }

@Entity(
    tableName = "shifts",
    foreignKeys = [ForeignKey(entity = User::class, parentColumns = ["id"], childColumns = ["cashierId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("cashierId"), Index("clockIn"), Index("status")]
)
data class Shift(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cashierId: Long,
    val cashierName: String,
    val clockIn: Long = System.currentTimeMillis(),
    val clockOut: Long? = null,
    val openingFloat: Double = 0.0,     // cash in drawer at start
    val closingFloat: Double? = null,   // cash in drawer at end
    val totalCashSales: Double = 0.0,
    val totalMpesaSales: Double = 0.0,
    val totalCardSales: Double = 0.0,
    val totalSales: Double = 0.0,
    val totalTransactions: Int = 0,
    val totalDiscounts: Double = 0.0,
    val totalItemsSold: Int = 0,
    val expectedCash: Double = 0.0,     // openingFloat + cashSales
    val cashDiscrepancy: Double = 0.0,  // closingFloat - expectedCash
    val notes: String = "",
    val status: ShiftStatus = ShiftStatus.OPEN
)
