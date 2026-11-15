package com.minimart.pos.util

import java.text.SimpleDateFormat
import java.util.Locale

/** One incoming M-Pesa payment read from a Safaricom confirmation SMS. */
data class ParsedMpesa(
    val code: String,
    val amount: Double,
    val senderName: String,
    val senderPhone: String,
    /** Time stated in the message, or null if it could not be read. */
    val timestamp: Long?
)

/**
 * Reads Safaricom "you have received" confirmations (personal, Till and Paybill wording).
 * Anything else — sent money, airtime, withdrawals, balance or promo texts — returns null.
 */
object MpesaSmsParser {
    /** The only sender ID accepted. Texts from ordinary phone numbers are never trusted. */
    const val TRUSTED_SENDER = "MPESA"

    fun isTrustedSender(address: String?): Boolean = address?.trim().equals(TRUSTED_SENDER, ignoreCase = true)

    private val head = Regex("""^\s*([A-Z0-9]{10})\s+Confirmed\.?\s*(.*)$""", RegexOption.DOT_MATCHES_ALL)
    private const val AMT = """(?:Ksh|KES)\.?\s?([\d,]+(?:\.\d{1,2})?)"""
    private const val TAIL = """\s+from\s+(.+?)\s+on\s+(\d{1,2}/\d{1,2}/\d{2,4})\s+at\s+(\d{1,2}:\d{2}\s*[AaPp][Mm])"""
    private val personal = Regex("""received\s+$AMT$TAIL""", RegexOption.IGNORE_CASE)
    private val till = Regex("""$AMT\s+received$TAIL""", RegexOption.IGNORE_CASE)
    // Safaricom now hides the middle of the sender's number ("0712***456"), so stars count as part of the number.
    private val phone = Regex("""\+?(?=[\d*]*\d)[\d*]{9,13}""")
    private val account = Regex("""\s*Account\s+Number.*$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    fun parse(body: String): ParsedMpesa? {
        val text = body.replace('\n', ' ').replace(Regex("""\s+"""), " ").trim()
        val h = head.find(text) ?: return null
        val code = h.groupValues[1]
        val rest = h.groupValues[2]
        val m = personal.find(rest) ?: till.find(rest) ?: return null
        val amount = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null
        val middle = m.groupValues[2]
        val phoneNo = phone.find(middle)?.value ?: ""
        val name = middle.replace(phoneNo, " ").replace(account, "").replace(Regex("""\s+"""), " ").trim()
        return ParsedMpesa(code, amount, name, phoneNo, parseTime(m.groupValues[3], m.groupValues[4]))
    }

    /**
     * When the payment happened. The time written in the text is used unless it is more than a day ahead of this
     * phone's clock (a wrong date in the text must not hide a payment from today's reports); then the time the
     * network stamped on the message is used.
     */
    fun paidAt(textTime: Long?, smsTimeMs: Long, nowMs: Long): Long =
        if (textTime != null && textTime <= nowMs + 24L * 60 * 60 * 1000) textTime else smsTimeMs

    private fun parseTime(date: String, time: String): Long? {
        val pattern = if (date.substringAfterLast('/').length == 4) "d/M/yyyy h:mm a" else "d/M/yy h:mm a"
        return try {
            SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                .parse("$date ${time.uppercase().replace(Regex("""\s*([AP]M)"""), " $1")}")?.time
        } catch (_: Exception) { null }
    }
}
