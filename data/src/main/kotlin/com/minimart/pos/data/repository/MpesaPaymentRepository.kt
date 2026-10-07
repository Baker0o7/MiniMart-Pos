package com.minimart.pos.data.repository

import com.minimart.pos.data.dao.MpesaPaymentDao
import com.minimart.pos.data.entity.MpesaPayment
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MpesaPaymentRepository @Inject constructor(private val dao: MpesaPaymentDao) {
    /** True when newly recorded, false when that transaction code was already stored. */
    suspend fun record(payment: MpesaPayment): Boolean = dao.insert(payment) != -1L
    fun getByRange(start: Long, end: Long): Flow<List<MpesaPayment>> = dao.getByRange(start, end)
    fun totalSince(start: Long): Flow<Double> = dao.totalSince(start)
    fun countSince(start: Long): Flow<Int> = dao.countSince(start)
    suspend fun findByCode(code: String): MpesaPayment? = dao.findByCode(code.trim())
}
