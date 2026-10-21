package com.minimart.pos.ui.viewmodel

import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.repository.MpesaPaymentRepository
import com.minimart.pos.data.repository.SaleRepository
import com.minimart.pos.util.MpesaReconciler
import com.minimart.pos.util.MpesaReconciliation
import com.minimart.pos.util.MpesaSaleRecord
import com.minimart.pos.util.PaymentRecord

/** Matches the M-Pesa messages received in [start, end) against the sales that name an M-Pesa code. */
internal suspend fun reconcileRange(
    payments: MpesaPaymentRepository, sales: SaleRepository, start: Long, end: Long
): MpesaReconciliation {
    val received = payments.between(start, end).map { PaymentRecord(it.code, it.amount, it.receivedAt, it.senderName) }
    val sold = sales.mpesaSalesBetween(start, end).map {
        MpesaSaleRecord(it.id, it.receiptNumber, it.mpesaRef.orEmpty(), it.totalAmount, it.paymentMethod == PaymentMethod.MPESA, it.createdAt)
    }
    return MpesaReconciler.reconcile(received, sold)
}
