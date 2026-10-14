package com.minimart.pos.mpesa

import com.minimart.pos.data.repository.PaymentValidator
import java.security.MessageDigest

/**
 * The reference recorded on a sale paid by STK Push.
 *
 * Daraja's status query confirms a payment but does not return the M-Pesa receipt number (only the
 * callback does, and this app has no callback server). The sale validator accepts only 6–20 capital
 * letters/digits, and the raw CheckoutRequestID ("ws_CO_...") fails that, so a confirmed payment
 * could not be completed. A short stable code derived from the CheckoutRequestID fills the gap:
 * unique per push, so the duplicate-reference check still catches a reused one.
 */
object StkReference {
    fun forPayment(receiptFromDaraja: String?, checkoutRequestId: String): String {
        receiptFromDaraja?.trim()?.uppercase()
            ?.takeIf { PaymentValidator.isWellFormedMpesaRef(it) }
            ?.let { return it }
        val digest = MessageDigest.getInstance("SHA-256").digest(checkoutRequestId.toByteArray(Charsets.UTF_8))
        return "STK" + digest.joinToString("") { "%02X".format(it) }.take(12)
    }
}
