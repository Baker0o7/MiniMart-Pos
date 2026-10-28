package com.minimart.pos.data.repository

import com.minimart.pos.data.entity.MpesaPayment
import com.minimart.pos.util.InboxPayment
import com.minimart.pos.util.MpesaWebhook
import org.json.JSONArray

/** What a batch from the cloud inbox did, and which rows may now be confirmed (acknowledged) to the cloud. */
data class InboxOutcome(val recorded: Int, val duplicates: Int, val rejected: Int, val ackIds: List<Long>)

/**
 * Turns one batch of cloud-inbox rows into stored payments, safely to repeat. Safaricom may deliver a payment
 * more than once, the cloud hands the same rows out again until they are acknowledged, and a phone can stop
 * half way: so a row is acknowledged only once it is stored or known to be invalid, and storing the same M-Pesa
 * code again changes nothing. Kept apart from the network code so it can be tested with a real database.
 */
class MpesaInboxSync(private val payments: MpesaPaymentRepository) {
    suspend fun process(rowsJson: String, nowMs: Long): InboxOutcome {
        val rows = JSONArray(rowsJson)
        var recorded = 0; var duplicates = 0; var rejected = 0
        val ack = mutableListOf<Long>()
        for (i in 0 until rows.length()) {
            val r = rows.optJSONObject(i) ?: continue
            if (!r.has("id")) continue
            val id = r.optLong("id", -1L)
            if (id < 0) continue
            val clean = MpesaWebhook.sanitize(
                InboxPayment(id, r.optString("code"), r.optDouble("amount", Double.NaN), r.optString("name"),
                    r.optString("phone"), r.optLong("paidAtMs")), nowMs)
            if (clean == null) { rejected++; ack += id; continue }   // never valid: do not fetch it forever
            val isNew = payments.record(MpesaPayment(
                code = clean.code, amount = clean.amount, senderName = clean.name, senderPhone = clean.phone,
                receivedAt = clean.paidAtMs, rawMessage = "Instant M-Pesa (Daraja): ${clean.code}"))
            if (isNew) recorded++ else duplicates++
            ack += id
        }
        return InboxOutcome(recorded, duplicates, rejected, ack)
    }
}
