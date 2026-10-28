package com.minimart.pos.util

/**
 * When the Complete Sale button may be pressed. Kept out of the screen so the rule that decides whether a
 * customer has paid enough can be tested without a phone. Amounts are compared in cents ([Money]), so
 * KES 99.99 paid against a 99.99 total is never refused by a rounding hair.
 */
object CheckoutRules {
    enum class Kind { CASH, MPESA, CREDIT, OTHER }

    /**
     * @param mpesaAccepted already decided by the M-Pesa checks (code looks right, not a repeat, no prompt still pending)
     * @param split credit amount used in a split payment, or null when not splitting
     */
    fun canComplete(
        total: Double, kind: Kind, cashGiven: Double,
        hasCustomer: Boolean = false, mpesaAccepted: Boolean = false, split: Double? = null
    ): Boolean {
        val due = Money.fromDouble(total)
        if (due <= Money.ZERO) return false
        val cash = Money.fromDouble(cashGiven)
        if (split != null) {
            val credit = Money.fromDouble(split)
            return hasCustomer && credit > Money.ZERO && cash >= (due - credit).coerceAtLeast(Money.ZERO)
        }
        return when (kind) {
            Kind.CASH -> cash >= due
            Kind.MPESA -> mpesaAccepted
            Kind.CREDIT -> hasCustomer
            Kind.OTHER -> true
        }
    }

    /** Change to hand back; never negative. */
    fun change(total: Double, cashGiven: Double): Double =
        (Money.fromDouble(cashGiven) - Money.fromDouble(total)).coerceAtLeast(Money.ZERO).toDouble()
}
