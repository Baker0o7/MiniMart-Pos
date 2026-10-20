package com.minimart.pos.util

import kotlin.math.abs

data class PaymentRecord(val code: String, val amount: Double, val receivedAt: Long, val sender: String = "")
data class MpesaSaleRecord(
    val saleId: Long, val receipt: String, val code: String, val saleTotal: Double,
    /** True when the whole sale was paid by M-Pesa, so the payment should equal the total. */
    val mpesaOnly: Boolean, val createdAt: Long
)

data class AmountMismatch(val sale: MpesaSaleRecord, val payment: PaymentRecord) {
    val difference: Double get() = payment.amount - sale.saleTotal
}

data class MpesaReconciliation(
    val matched: List<Pair<MpesaSaleRecord, PaymentRecord>>,
    /** Money arrived but no sale uses the code: a customer paid and was not rung up, or a code was mistyped. */
    val unclaimedPayments: List<PaymentRecord>,
    /** A sale says it was paid by M-Pesa, but no message from MPESA exists for that code. */
    val unverifiedSales: List<MpesaSaleRecord>,
    val mismatches: List<AmountMismatch>,
    /** The same code is on more than one live sale. */
    val reusedCodes: Map<String, List<MpesaSaleRecord>>
) {
    val unclaimedTotal: Double get() = unclaimedPayments.sumOf { it.amount }
    val unverifiedTotal: Double get() = unverifiedSales.sumOf { it.saleTotal }
    val needsAttention: Boolean get() =
        unclaimedPayments.isNotEmpty() || unverifiedSales.isNotEmpty() || mismatches.isNotEmpty() || reusedCodes.isNotEmpty()
    val problemCount: Int get() = unclaimedPayments.size + unverifiedSales.size + mismatches.size + reusedCodes.size
}

/** Lines up M-Pesa messages received on this phone against the sales that say they were paid by M-Pesa. */
object MpesaReconciler {
    private fun norm(code: String) = code.trim().uppercase()

    fun reconcile(payments: List<PaymentRecord>, sales: List<MpesaSaleRecord>, tolerance: Double = 0.5): MpesaReconciliation {
        val byCode = payments.associateBy { norm(it.code) }
        val salesByCode = sales.filter { norm(it.code).isNotEmpty() }.groupBy { norm(it.code) }

        val matched = mutableListOf<Pair<MpesaSaleRecord, PaymentRecord>>()
        val unverified = mutableListOf<MpesaSaleRecord>()
        val mismatches = mutableListOf<AmountMismatch>()

        for ((code, group) in salesByCode) {
            val pay = byCode[code]
            if (pay == null) { unverified += group; continue }
            for (sale in group) {
                matched += sale to pay
                // A split sale only needs the payment not to exceed its total; a pure M-Pesa sale must match it.
                val off = if (sale.mpesaOnly) abs(pay.amount - sale.saleTotal) > tolerance else pay.amount > sale.saleTotal + tolerance
                if (off && group.size == 1) mismatches += AmountMismatch(sale, pay)
            }
        }
        val unclaimed = payments.filter { norm(it.code) !in salesByCode }.sortedByDescending { it.receivedAt }
        val reused = salesByCode.filterValues { it.size > 1 }
        return MpesaReconciliation(matched, unclaimed, unverified.sortedByDescending { it.createdAt }, mismatches, reused)
    }
}
