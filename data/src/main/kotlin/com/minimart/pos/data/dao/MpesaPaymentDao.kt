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

    @Query("SELECT * FROM mpesa_payments WHERE receivedAt >= :start AND receivedAt < :end ORDER BY receivedAt DESC")
    suspend fun between(start: Long, end: Long): List<MpesaPayment>

    @Query("SELECT ROUND(COALESCE(SUM(amount), 0.0), 2) FROM mpesa_payments WHERE receivedAt >= :start")
    fun totalSince(start: Long): Flow<Double>

    @Query("SELECT COUNT(*) FROM mpesa_payments WHERE receivedAt >= :start")
    fun countSince(start: Long): Flow<Int>

    @Query("SELECT * FROM mpesa_payments WHERE code = :code COLLATE NOCASE LIMIT 1")
    suspend fun findByCode(code: String): MpesaPayment?

    /** Rows that still hold readable sender details from before field encryption (see FieldCipher). */
    @Query("""
        SELECT * FROM mpesa_payments
        WHERE (senderName <> '' AND senderName NOT LIKE 'enc1:%')
           OR (senderPhone <> '' AND senderPhone NOT LIKE 'enc1:%')
           OR (rawMessage <> '' AND rawMessage NOT LIKE 'enc1:%')
        LIMIT :limit
    """)
    suspend fun unsealedRows(limit: Int): List<MpesaPayment>

    @Query("UPDATE mpesa_payments SET senderName = :name, senderPhone = :phone, rawMessage = :raw WHERE id = :id")
    suspend fun updateSensitive(id: Long, name: String, phone: String, raw: String)
}
