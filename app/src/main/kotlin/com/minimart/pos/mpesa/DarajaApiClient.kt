package com.minimart.pos.mpesa

import android.util.Base64
import android.util.Log
import com.minimart.pos.data.repository.SettingsRepository
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DarajaApiClient"
private const val TIMEOUT_MS = 15_000

sealed class StkPushResult {
    data class Initiated(val checkoutRequestId: String, val merchantRequestId: String) : StkPushResult()
    data class Error(val message: String) : StkPushResult()
}

sealed class StkStatus {
    object Pending : StkStatus()
    data class Success(val mpesaReceiptNumber: String?, val phoneNumber: String?) : StkStatus()
    data class Failed(val reason: String) : StkStatus()
    data class Error(val message: String) : StkStatus()
}

@Singleton
class DarajaApiClient @Inject constructor(
    private val settingsRepo: SettingsRepository
) {
    private data class CachedToken(val token: String, val expiresAtMs: Long)
    private val tokenRef = AtomicReference<CachedToken?>(null)

    // ── Phone number normalisation ─────────────────────────────────────────────
    // Kenyan numbers: 07XXXXXXXX, 01XXXXXXXX, 9-digit (7XXXXXXXX/1XXXXXXXX),
    // +254XXXXXXXXX, 254XXXXXXXXX — all collapse to 254XXXXXXXXX.
    fun normalizePhoneNumber(raw: String): String {
        val digits = raw.replace(Regex("[^0-9]"), "")
        return when {
            digits.startsWith("254") && digits.length == 12 -> digits
            digits.startsWith("0")  && digits.length == 10  -> "254${digits.substring(1)}"
            digits.startsWith("7")  && digits.length ==  9  -> "254$digits"
            digits.startsWith("1")  && digits.length ==  9  -> "254$digits"
            else -> digits // pass through; API will reject if bad
        }
    }

    // ── OAuth token (thread-safe, refresh 60s early) ───────────────────────────
    suspend fun getAccessToken(): String {
        val cfg = settingsRepo.getDarajaConfig()
        val now = System.currentTimeMillis()
        tokenRef.get()?.let { if (now < it.expiresAtMs - 60_000) return it.token }

        val base = if (cfg.sandbox) "https://sandbox.safaricom.co.ke" else "https://api.safaricom.co.ke"
        val credentials = Base64.encodeToString(
            "${cfg.consumerKey}:${cfg.consumerSecret}".toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        val conn = (URL("$base/oauth/v1/generate?grant_type=client_credentials").openConnection()
                as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Basic $credentials")
            connectTimeout = TIMEOUT_MS
            readTimeout    = TIMEOUT_MS
        }
        try {
            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val token   = json.getString("access_token")
            val expiresIn = json.optLong("expires_in", 3600L)
            tokenRef.set(CachedToken(token, now + expiresIn * 1000))
            return token
        } finally {
            conn.disconnect()
        }
    }

    // ── Password + timestamp ───────────────────────────────────────────────────
    private fun generatePasswordAndTimestamp(shortcode: String, passkey: String): Pair<String, String> {
        val ts = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())
        val raw = "$shortcode$passkey$ts"
        val password = Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return Pair(password, ts)
    }

    // ── STK Push ──────────────────────────────────────────────────────────────
    suspend fun initiateStkPush(
        phone: String,
        amountKes: Int,
        accountRef: String = "MiniMart",
        description: String = "Purchase"
    ): StkPushResult {
        return try {
            val cfg   = settingsRepo.getDarajaConfig()
            val token = getAccessToken()
            val base  = if (cfg.sandbox) "https://sandbox.safaricom.co.ke" else "https://api.safaricom.co.ke"
            val (password, timestamp) = generatePasswordAndTimestamp(cfg.shortcode, cfg.passkey)
            val normalizedPhone = normalizePhoneNumber(phone)

            val payload = JSONObject().apply {
                put("BusinessShortCode", cfg.shortcode)
                put("Password", password)
                put("Timestamp", timestamp)
                put("TransactionType", "CustomerPayBillOnline")
                put("Amount", amountKes)
                put("PartyA", normalizedPhone)
                put("PartyB", cfg.shortcode)
                put("PhoneNumber", normalizedPhone)
                put("CallBackURL", "https://example.com/mpesa/callback")
                put("AccountReference", accountRef.take(12))
                put("TransactionDesc", description.take(13))
            }

            val conn = (URL("$base/mpesa/stkpush/v1/processrequest").openConnection()
                    as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = TIMEOUT_MS
                readTimeout    = TIMEOUT_MS
            }
            try {
                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                val responseCode = conn.responseCode
                val body = if (responseCode in 200..299)
                    conn.inputStream.bufferedReader().readText()
                else
                    conn.errorStream?.bufferedReader()?.readText() ?: ""
                val json = JSONObject(body)
                if (responseCode in 200..299 && json.optString("ResponseCode") == "0") {
                    StkPushResult.Initiated(
                        checkoutRequestId  = json.getString("CheckoutRequestID"),
                        merchantRequestId  = json.getString("MerchantRequestID")
                    )
                } else {
                    val msg = json.optString("errorMessage").ifBlank {
                        json.optString("ResponseDescription").ifBlank { "STK push failed (HTTP $responseCode)" }
                    }
                    StkPushResult.Error(msg)
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "initiateStkPush error", e)
            StkPushResult.Error(e.message ?: "Network error")
        }
    }

    // ── Query STK status ──────────────────────────────────────────────────────
    suspend fun queryStkStatus(checkoutRequestId: String): StkStatus {
        return try {
            val cfg   = settingsRepo.getDarajaConfig()
            val token = getAccessToken()
            val base  = if (cfg.sandbox) "https://sandbox.safaricom.co.ke" else "https://api.safaricom.co.ke"
            val (password, timestamp) = generatePasswordAndTimestamp(cfg.shortcode, cfg.passkey)

            val payload = JSONObject().apply {
                put("BusinessShortCode", cfg.shortcode)
                put("Password", password)
                put("Timestamp", timestamp)
                put("CheckoutRequestID", checkoutRequestId)
            }

            val conn = (URL("$base/mpesa/stkpushquery/v1/query").openConnection()
                    as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = TIMEOUT_MS
                readTimeout    = TIMEOUT_MS
            }
            try {
                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                val responseCode = conn.responseCode
                val body = if (responseCode in 200..299)
                    conn.inputStream.bufferedReader().readText()
                else
                    conn.errorStream?.bufferedReader()?.readText() ?: ""
                val json = JSONObject(body)
                val resultCode = json.optString("ResultCode", "-1")
                val errorCode  = json.optString("errorCode", "")
                when {
                    // Safaricom error codes meaning "still in progress"
                    errorCode == "500.001.1001" || errorCode == "500.001.1032" -> StkStatus.Pending
                    resultCode == "0" -> StkStatus.Success(
                        mpesaReceiptNumber = json.optString("MpesaReceiptNumber").takeIf { it.isNotBlank() },
                        phoneNumber        = json.optString("PhoneNumber").takeIf { it.isNotBlank() }
                    )
                    resultCode == "1032" -> StkStatus.Failed("Customer cancelled or timed out")
                    resultCode == "1"    -> StkStatus.Failed("Insufficient balance")
                    resultCode.isNotBlank() && resultCode != "-1" ->
                        StkStatus.Failed(json.optString("ResultDesc").ifBlank { "Payment failed (code $resultCode)" })
                    else -> StkStatus.Pending
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "queryStkStatus error", e)
            StkStatus.Error(e.message ?: "Network error")
        }
    }
}
