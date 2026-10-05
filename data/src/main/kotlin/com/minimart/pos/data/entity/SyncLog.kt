package com.minimart.pos.data.entity

import androidx.room.*

enum class SyncEntityType { PRODUCT, SALE, CUSTOMER, EXPENSE, CREDIT_TX }
enum class SyncOperation  { CREATE, UPDATE, DELETE }
enum class SyncStatus     { PENDING, SYNCED, CONFLICT }

@Entity(tableName = "sync_log", indices = [Index("status"), Index("createdAt")])
data class SyncLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: SyncEntityType,
    val entityId:   Long,
    val operation:  SyncOperation,
    val deviceId:   String,        // UUID of the originating device
    val payload:    String,        // JSON of the changed record
    val status:     SyncStatus = SyncStatus.PENDING,
    val createdAt:  Long = System.currentTimeMillis()
)
