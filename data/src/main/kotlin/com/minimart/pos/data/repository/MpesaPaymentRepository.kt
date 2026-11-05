package com.minimart.pos.data.repository

import com.minimart.pos.data.dao.MpesaPaymentDao
import com.minimart.pos.data.entity.MpesaPayment
import com.minimart.pos.util.FieldCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M-Pesa payments. Who paid (name, phone) and the raw SMS are sealed with [FieldCipher] before they reach the
 * database and opened on the way out; the code and amount stay readable because matching and totals need them.
 */
@Singleton
class MpesaPaymentRepository @Inject constructor(
    private val dao: MpesaPaymentDao,
    private val cipher: FieldCipher = FieldCipher.NONE
) {
    private fun seal(p: MpesaPayment) = p.copy(
        senderName = cipher.protect(NAME, p.senderName),
        senderPhone = cipher.protect(PHONE, p.senderPhone),
        rawMessage = cipher.protect(RAW, p.rawMessage)
    )

    private fun open(p: MpesaPayment) = p.copy(
        senderName = cipher.reveal(NAME, p.senderName),
        senderPhone = cipher.reveal(PHONE, p.senderPhone),
        rawMessage = cipher.reveal(RAW, p.rawMessage)
    )

    /** True when newly recorded, false when that transaction code was already stored. */
    suspend fun record(payment: MpesaPayment): Boolean =
        dao.insert(seal(payment.copy(code = payment.code.trim().uppercase()))) != -1L   // "ab12" and "AB12" are one payment
    fun getByRange(start: Long, end: Long): Flow<List<MpesaPayment>> = dao.getByRange(start, end).map { l -> l.map(::open) }
    fun totalSince(start: Long): Flow<Double> = dao.totalSince(start)
    fun countSince(start: Long): Flow<Int> = dao.countSince(start)
    suspend fun between(start: Long, end: Long): List<MpesaPayment> = dao.between(start, end).map(::open)
    suspend fun findByCode(code: String): MpesaPayment? = dao.findByCode(code.trim().uppercase())?.let(::open)

    /**
     * Seals rows saved before field encryption existed. Safe to repeat: it only touches rows that still hold
     * readable text, and writes a row only after checking the sealed text opens back to the original.
     * Returns how many rows were sealed.
     */
    suspend fun sealLegacyRows(): Int {
        var done = 0
        while (true) {
            val rows = dao.unsealedRows(200).filter {
                cipher.needsSealing(it.senderName) || cipher.needsSealing(it.senderPhone) || cipher.needsSealing(it.rawMessage)
            }
            if (rows.isEmpty()) return done
            var progressed = false
            rows.forEach { r ->
                val sealed = seal(r)
                val sameWhenOpened = open(sealed).let { it.senderName == r.senderName && it.senderPhone == r.senderPhone && it.rawMessage == r.rawMessage }
                if (sameWhenOpened) {
                    dao.updateSensitive(r.id, sealed.senderName, sealed.senderPhone, sealed.rawMessage)
                    done++; progressed = true
                }
            }
            if (!progressed) return done   // the key does not round-trip: leave the rows readable rather than risk them
        }
    }

    private companion object {
        const val NAME = "mpesa.senderName"
        const val PHONE = "mpesa.senderPhone"
        const val RAW = "mpesa.rawMessage"
    }
}
