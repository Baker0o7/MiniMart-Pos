package com.minimart.pos.data.entity

import androidx.room.*

@Entity(tableName = "customers", indices = [Index("phone")])
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val email: String = "",
    @ColumnInfo(defaultValue = "0.0") val creditBalance: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val totalPurchases: Double = 0.0,
    @ColumnInfo(defaultValue = "0")   val visitCount: Int = 0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

enum class CreditTxType { CREDIT_ADDED, CREDIT_USED, REFUND, ADJUSTMENT }

@Entity(
    tableName = "credit_transactions",
    foreignKeys = [ForeignKey(
        entity = Customer::class,
        parentColumns = ["id"], childColumns = ["customerId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("customerId")]
)
data class CreditTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val amount: Double,              // positive = credit added, negative = credit used
    val type: CreditTxType,
    val saleId: Long? = null,
    val notes: String = "",
    val balanceAfter: Double,
    val createdAt: Long = System.currentTimeMillis()
)
