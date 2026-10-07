package com.minimart.pos.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** An incoming M-Pesa payment read from a Safaricom SMS. [code] is unique, so a repeated SMS is ignored. */
@Entity(
    tableName = "mpesa_payments",
    indices = [Index(value = ["code"], unique = true), Index(value = ["receivedAt"])]
)
data class MpesaPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val amount: Double,
    val senderName: String,
    val senderPhone: String,
    val receivedAt: Long,
    val rawMessage: String,
    val createdAt: Long = System.currentTimeMillis()
)
