package com.minimart.pos.data.repository

/** What the till can say about an M-Pesa code typed at checkout. */
enum class MpesaVerdict { EMPTY, BAD_FORMAT, ALREADY_USED, VERIFIED, UNDERPAID, NOT_RECEIVED, UNCHECKED }

data class MpesaAssessment(val verdict: MpesaVerdict, val receivedAmount: Double? = null) {
    /** Strict mode lets a sale through only on a verified code. */
    val passesStrict: Boolean get() = verdict == MpesaVerdict.VERIFIED
    /** Anything that is certainly wrong, whatever the mode. */
    val isBlocking: Boolean get() = verdict == MpesaVerdict.BAD_FORMAT ||
        verdict == MpesaVerdict.ALREADY_USED || verdict == MpesaVerdict.UNDERPAID
}

object MpesaVerifier {
    /**
     * @param receivedAmount amount of the SMS-recorded payment with this code, or null if none seen
     * @param timesUsed completed sales already carrying this code
     * @param trackingOn whether SMS tracking is on (if off, "not received" is unknowable)
     */
    fun assess(ref: String, due: Double, receivedAmount: Double?, timesUsed: Int, trackingOn: Boolean): MpesaAssessment {
        val code = ref.trim()
        if (code.isEmpty()) return MpesaAssessment(MpesaVerdict.EMPTY)
        if (!PaymentValidator.isWellFormedMpesaRef(code)) return MpesaAssessment(MpesaVerdict.BAD_FORMAT)
        if (timesUsed > 0) return MpesaAssessment(MpesaVerdict.ALREADY_USED, receivedAmount)
        if (receivedAmount != null)
            return if (receivedAmount + 0.011 >= due) MpesaAssessment(MpesaVerdict.VERIFIED, receivedAmount)
                   else MpesaAssessment(MpesaVerdict.UNDERPAID, receivedAmount)
        return if (trackingOn) MpesaAssessment(MpesaVerdict.NOT_RECEIVED) else MpesaAssessment(MpesaVerdict.UNCHECKED)
    }
}
