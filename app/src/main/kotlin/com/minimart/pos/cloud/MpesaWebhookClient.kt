package com.minimart.pos.cloud

import com.minimart.pos.data.entity.MpesaPayment
import com.minimart.pos.data.repository.MpesaPaymentRepository
import com.minimart.pos.util.ErrorAdvice
import com.minimart.pos.util.InboxPayment
import com.minimart.pos.util.MpesaWebhook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

sealed class WebhookResult {
    data class Enabled(val callbackUrl: String) : WebhookResult()
    data class Pulled(val recorded: Int, val duplicates: Int, val rejected: Int) : WebhookResult()
    data class Failed(val message: String) : WebhookResult()
}

/**
 * Brings payments that Safaricom pushed to the owner's cloud inbox into the app. Uses the same store login as the
 * cloud dashboard. A payment is confirmed to the cloud only after it is safely stored here, so a failure part-way
 * means it is simply fetched again next time (the M-Pesa code is unique, so nothing is recorded twice).
 */
@Singleton
class MpesaWebhookClient @Inject constructor(
    private val config: CloudConfig,
    private val payments: MpesaPaymentRepository
) {
    private val lock = Mutex()

    val available: Boolean get() = config.isConfigured
    val enabled: Boolean get() = config.isConfigured && config.mpesaWebhookToken.isNotBlank()
    fun callbackUrl(): String? = MpesaWebhook.callbackUrl(config.url, config.mpesaWebhookToken)

    /** Creates (or replaces) this store's webhook address. Replacing it means the old address stops working. */
    suspend fun enable(): WebhookResult = withContext(Dispatchers.IO) {
        if (!config.isConfigured) return@withContext WebhookResult.Failed("Connect the cloud dashboard first (Settings → Cloud dashboard).")
        try {
            val body = JSONObject().put("p_store_id", config.storeId).put("p_secret", config.storeSecret)
            val (code, text) = post("enable_mpesa_webhook", body)
            if (code !in 200..299) return@withContext WebhookResult.Failed(serverMessage(text, code))
            val token = text.trim().trim('"')
            val url = MpesaWebhook.callbackUrl(config.url, token) ?: return@withContext WebhookResult.Failed("The server sent an address the app does not trust.")
            config.mpesaWebhookToken = token
            WebhookResult.Enabled(url)
        } catch (e: Exception) { WebhookResult.Failed(ErrorAdvice.explain(e, "Turning on instant M-Pesa")) }
    }

    fun disable() { config.mpesaWebhookToken = "" }

    suspend fun pull(nowMs: Long = System.currentTimeMillis()): WebhookResult = lock.withLock {
        if (!enabled) return WebhookResult.Failed("Instant M-Pesa is not turned on.")
        withContext(Dispatchers.IO) {
            try {
                val auth = JSONObject().put("p_store_id", config.storeId).put("p_secret", config.storeSecret)
                val (code, text) = post("claim_mpesa_payments", auth)
                if (code !in 200..299) return@withContext WebhookResult.Failed(serverMessage(text, code))
                val rows = JSONArray(text)
                var recorded = 0; var duplicates = 0; var rejected = 0
                val done = JSONArray()
                for (i in 0 until rows.length()) {
                    val r = rows.getJSONObject(i)
                    val id = r.getLong("id")
                    val clean = MpesaWebhook.sanitize(
                        InboxPayment(id, r.optString("code"), r.optDouble("amount", Double.NaN), r.optString("name"),
                            r.optString("phone"), r.optLong("paidAtMs")), nowMs)
                    if (clean == null) { rejected++; done.put(id); continue }   // never valid: do not fetch it forever
                    val isNew = payments.record(MpesaPayment(
                        code = clean.code, amount = clean.amount, senderName = clean.name, senderPhone = clean.phone,
                        receivedAt = clean.paidAtMs, rawMessage = "Instant M-Pesa (Daraja): ${clean.code}"))
                    if (isNew) recorded++ else duplicates++
                    done.put(id)
                }
                if (done.length() > 0) {
                    val (ackCode, ackText) = post("ack_mpesa_payments", JSONObject(auth.toString()).put("p_ids", done))
                    if (ackCode !in 200..299) return@withContext WebhookResult.Failed(serverMessage(ackText, ackCode))
                }
                WebhookResult.Pulled(recorded, duplicates, rejected)
            } catch (e: Exception) { WebhookResult.Failed(ErrorAdvice.explain(e, "Fetching M-Pesa payments")) }
        }
    }

    private fun post(fn: String, body: JSONObject): Pair<Int, String> {
        val conn = URL("${config.url}/rest/v1/rpc/$fn").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000; conn.readTimeout = 30_000
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", config.anonKey)
            conn.setRequestProperty("Authorization", "Bearer ${config.anonKey}")
            conn.setRequestProperty("Content-Type", "application/json")
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return code to (stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally { conn.disconnect() }
    }

    private fun serverMessage(text: String, code: Int): String {
        val msg = try { JSONObject(text).optString("message", "") } catch (_: Exception) { "" }
        return when {
            msg.contains("function", true) && msg.contains("not exist", true) || code == 404 ->
                "The cloud project is missing the M-Pesa setup. Run cloud/mpesa_webhook.sql in Supabase (see the README)."
            msg.isNotBlank() -> msg
            else -> "The cloud server replied $code."
        }
    }
}
