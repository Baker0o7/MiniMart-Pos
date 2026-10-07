package com.minimart.pos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.minimart.pos.data.entity.MpesaPayment
import kotlinx.coroutines.flow.Flow

@Dao
interface MpesaPaymentDao {
    /** Returns -1 when the transaction code is already recorded. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(payment: MpesaPayment): Long

    @Query("SELECT * FROM mpesa_payments WHERE receivedAt >= :start AND receivedAt <= :end ORDER BY receivedAt DESC")
    fun getByRange(start: Long, end: Long): Flow<List<MpesaPayment>>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM mpesa_payments WHERE receivedAt >= :start")
    fun totalSince(start: Long): Flow<Double>

    @Query("SELECT COUNT(*) FROM mpesa_payments WHERE receivedAt >= :start")
    fun countSince(start: Long): Flow<Int>

    @Query("SELECT * FROM mpesa_payments WHERE code = :code COLLATE NOCASE LIMIT 1")
    suspend fun findByCode(code: String): MpesaPayment?
}
