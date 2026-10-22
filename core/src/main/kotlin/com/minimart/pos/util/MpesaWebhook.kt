package com.minimart.pos.util

/** One payment waiting in the owner's cloud inbox, as the app received it. */
data class InboxPayment(val id: Long, val code: String, val amount: Double, val name: String, val phone: String, val paidAtMs: Long)

/** Rules for payments arriving from the Daraja webhook. Everything from the network is treated as untrusted. */
object MpesaWebhook {
    private val CODE = Regex("^[A-Z0-9]{8,12}$")
    const val MAX_AMOUNT = 1_000_000.0
    const val FUNCTION_NAME = "till-payments"   // Safaricom refuses addresses containing "mpesa", "safaricom", "sql", "exe"…

    /** The address to register with Daraja. Null when the project address is not a plain https URL. */
    fun callbackUrl(projectUrl: String, token: String): String? {
        val base = projectUrl.trim().trimEnd('/')
        if (!base.startsWith("https://") || token.length < 32 || !token.all { it.isLetterOrDigit() }) return null
        return "$base/functions/v1/$FUNCTION_NAME?t=$token"
    }

    /** Returns a cleaned payment, or null when it is not a believable M-Pesa payment. */
    fun sanitize(p: InboxPayment, nowMs: Long): InboxPayment? {
        val code = p.code.trim().uppercase()
        if (!CODE.matches(code)) return null
        if (!p.amount.isFinite() || p.amount <= 0.0 || p.amount > MAX_AMOUNT) return null
        // A time far in the future is a clock or data fault; use arrival time instead.
        val paidAt = if (p.paidAtMs <= 0L || p.paidAtMs > nowMs + 24L * 60 * 60 * 1000) nowMs else p.paidAtMs
        val phone = p.phone.filter { it.isDigit() || it == '+' }.take(15)
        return p.copy(code = code, name = p.name.trim().take(60), phone = phone, paidAtMs = paidAt)
    }

    /** Plain-language summary of a fetch, shown in Settings. */
    fun summary(recorded: Int, duplicates: Int, rejected: Int): String = when {
        recorded == 0 && duplicates == 0 && rejected == 0 -> "No new payments."
        else -> buildString {
            append("$recorded new payment(s) recorded")
            if (duplicates > 0) append(", $duplicates already known")
            if (rejected > 0) append(", $rejected ignored as invalid")
            append(".")
        }
    }
}
