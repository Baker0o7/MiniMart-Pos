package com.minimart.pos.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class UserRole { CASHIER, MANAGER, OWNER }

@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)]
)
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val pinHash: String,                 // SHA-256 of 4-digit PIN
    val displayName: String,
    val role: UserRole = UserRole.CASHIER,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
