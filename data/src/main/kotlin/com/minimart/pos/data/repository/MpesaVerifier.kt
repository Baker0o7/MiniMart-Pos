package com.minimart.pos.data.repository

/** What the till can say about an M-Pesa code typed at checkout. */
enum class MpesaVerdict { EMPTY, BAD_FORMAT, ALREADY_USED, VERIFIED, UNDERPAID, NOT_RECEIVED, UNCHECKED,
    /** The payment is real but arrived long before this sale: ask whether it belongs to an earlier customer. */
    STALE }

data class MpesaAssessment(val verdict: MpesaVerdict, val receivedAmount: Double? = null) {
    /** Strict mode lets a sale through only on a verified code. */
    /** A stale payment is a warning, not a stop: a customer may really have paid ahead. */
    val passesStrict: Boolean get() = verdict == MpesaVerdict.VERIFIED || verdict == MpesaVerdict.STALE
    /** Anything that is certainly wrong, whatever the mode. */
    val isBlocking: Boolean get() = verdict == MpesaVerdict.BAD_FORMAT ||
        verdict == MpesaVerdict.ALREADY_USED || verdict == MpesaVerdict.UNDERPAID
}

object MpesaVerifier {
    /** A payment older than this when the sale is rung up looks like one left over from earlier. */
    const val STALE_AFTER_MS = 6 * 60 * 60 * 1000L

    private fun isStale(receivedAt: Long?, now: Long) =
        receivedAt != null && receivedAt > 0L && now > 0L && now - receivedAt > STALE_AFTER_MS

    /**
     * @param receivedAmount amount of the SMS-recorded payment with this code, or null if none seen
     * @param timesUsed completed sales already carrying this code
     * @param trackingOn whether SMS tracking is on (if off, "not received" is unknowable)
     * @param receivedAt when the payment message arrived (0 or null when unknown); with [now] it spots an old payment
     *   being shown for a new sale, the usual way a code is reused by someone who did not just pay
     */
    fun assess(
        ref: String, due: Double, receivedAmount: Double?, timesUsed: Int, trackingOn: Boolean,
        receivedAt: Long? = null, now: Long = 0L
    ): MpesaAssessment {
        val code = ref.trim()
        if (code.isEmpty()) return MpesaAssessment(MpesaVerdict.EMPTY)
        if (!PaymentValidator.isWellFormedMpesaRef(code)) return MpesaAssessment(MpesaVerdict.BAD_FORMAT)
        if (timesUsed > 0) return MpesaAssessment(MpesaVerdict.ALREADY_USED, receivedAmount)
        if (receivedAmount != null)
            return when {
                receivedAmount + 0.011 < due -> MpesaAssessment(MpesaVerdict.UNDERPAID, receivedAmount)
                isStale(receivedAt, now) -> MpesaAssessment(MpesaVerdict.STALE, receivedAmount)
                else -> MpesaAssessment(MpesaVerdict.VERIFIED, receivedAmount)
            }
        return if (trackingOn) MpesaAssessment(MpesaVerdict.NOT_RECEIVED) else MpesaAssessment(MpesaVerdict.UNCHECKED)
    }
}
