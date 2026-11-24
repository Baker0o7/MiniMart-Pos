package com.minimart.pos.mpesa

import android.util.Base64
import android.util.Log
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.util.DarajaRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private data class CachedToken(val token: String, val expiresAtMs: Long, val cacheKey: String)
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
    suspend fun getAccessToken(): String = withContext(Dispatchers.IO) {
        val cfg = settingsRepo.getDarajaConfig()
        val now = System.currentTimeMillis()
        // The cached token is only valid for the environment + credentials it was issued for;
        // switching sandbox/live or changing the keys in Settings must not reuse it.
        val cacheKey = "${cfg.sandbox}|${cfg.consumerKey}|${cfg.consumerSecret}"
        tokenRef.get()?.let { if (it.cacheKey == cacheKey && now < it.expiresAtMs - 60_000) return@withContext it.token }

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
            val code = conn.responseCode
            when (DarajaRules.httpKind(code)) {
                DarajaRules.HttpKind.OK -> Unit
                DarajaRules.HttpKind.AUTH_EXPIRED, DarajaRules.HttpKind.REJECTED -> throw DarajaFailure(DarajaRules.authMessage())
                DarajaRules.HttpKind.RATE_LIMITED -> throw DarajaFailure(DarajaRules.rateLimitedMessage())
                DarajaRules.HttpKind.SERVER_BUSY -> throw DarajaFailure(DarajaRules.busyMessage())
            }
            val body = conn.inputStream.bufferedReader().readText()
            val json = parseObject(body) ?: throw DarajaFailure(DarajaRules.busyMessage())
            val token   = json.getString("access_token")
            val expiresIn = json.optLong("expires_in", 3600L)
            tokenRef.set(CachedToken(token, now + expiresIn * 1000, cacheKey))
            token
        } finally {
            conn.disconnect()
        }
    }

    /** The answer was not a JSON object (an HTML error page from a gateway, an empty reply): treated as "M-Pesa is busy". */
    private fun parseObject(body: String): JSONObject? = try { JSONObject(body) } catch (_: org.json.JSONException) { null }

    /** A failure with words already fit for the cashier. */
    private class DarajaFailure(message: String) : Exception(message)

    private data class Reply(val code: Int, val body: String)

    /** One POST. Never retries by itself: callers decide, because only some requests are safe to send twice. */
    private fun post(url: String, token: String, payload: JSONObject): Reply {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = TIMEOUT_MS
            readTimeout    = TIMEOUT_MS
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            // A reply is a few hundred bytes; anything bigger is not Daraja.
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { r ->
                val buf = CharArray(8192); val sb = StringBuilder(); var n = r.read(buf)
                while (n >= 0 && sb.length < 65_536) { sb.append(buf, 0, n); n = r.read(buf) }
                sb.toString()
            } ?: ""
            return Reply(code, body)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Sends [payload], with a fresh token. A rejected token (expired or revoked) is dropped and the request repeated once;
     * [retrySafe] says whether a network failure may be tried again (always for status checks, only before sending for a push).
     */
    private suspend fun postWithRecovery(path: String, payload: JSONObject, retrySafe: (Throwable) -> Boolean): Reply {
        var attempt = 0
        var tokenRefreshed = false
        while (true) {
            val cfg = settingsRepo.getDarajaConfig()
            val base = if (cfg.sandbox) "https://sandbox.safaricom.co.ke" else "https://api.safaricom.co.ke"
            try {
                val reply = post("$base$path", getAccessToken(), payload)
                when (DarajaRules.httpKind(reply.code)) {
                    DarajaRules.HttpKind.AUTH_EXPIRED -> {
                        if (tokenRefreshed) throw DarajaFailure(DarajaRules.authMessage())
                        tokenRefreshed = true; tokenRef.set(null); continue
                    }
                    DarajaRules.HttpKind.SERVER_BUSY -> {
                        if (retrySafe(DarajaBusy()) && ++attempt < DarajaRules.MAX_ATTEMPTS) {
                            kotlinx.coroutines.delay(DarajaRules.retryDelayMs(attempt, Math.random())); continue
                        }
                        return reply
                    }
                    else -> return reply
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: DarajaFailure) {
                throw e
            } catch (e: Exception) {
                if (retrySafe(e) && ++attempt < DarajaRules.MAX_ATTEMPTS) {
                    kotlinx.coroutines.delay(DarajaRules.retryDelayMs(attempt, Math.random())); continue
                }
                throw e
            }
        }
    }

    /** Stand-in "error" for a 5xx answer, so [postWithRecovery]'s retry rule can treat it like any other transient failure. */
    private class DarajaBusy : java.io.IOException("M-Pesa busy")

    // ── Password + timestamp ───────────────────────────────────────────────────
    private fun generatePasswordAndTimestamp(shortcode: String, passkey: String): Pair<String, String> {
        val ts = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("Africa/Nairobi") }.format(Date())
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
    ): StkPushResult = withContext(Dispatchers.IO) {
        try {
            val cfg   = settingsRepo.getDarajaConfig()
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
                put("CallBackURL", DarajaRules.callbackFor(cfg.callbackUrl))
                put("AccountReference", accountRef.take(12))
                put("TransactionDesc", description.take(13))
            }

            // Only a failure before anything was sent may be repeated: a second push could charge the customer twice.
            val reply = postWithRecovery("/mpesa/stkpush/v1/processrequest", payload) { DarajaRules.isSafeToRetryPush(it) }
            val json = parseObject(reply.body) ?: return@withContext StkPushResult.Error(DarajaRules.busyMessage())
            val kind = DarajaRules.httpKind(reply.code)
            when {
                kind == DarajaRules.HttpKind.OK && json.optString("ResponseCode") == "0" ->
                    StkPushResult.Initiated(
                        checkoutRequestId  = json.getString("CheckoutRequestID"),
                        merchantRequestId  = json.getString("MerchantRequestID")
                    )
                kind == DarajaRules.HttpKind.SERVER_BUSY -> StkPushResult.Error(DarajaRules.busyMessage())
                kind == DarajaRules.HttpKind.RATE_LIMITED -> StkPushResult.Error(DarajaRules.rateLimitedMessage())
                else -> StkPushResult.Error(
                    json.optString("errorMessage").ifBlank {
                        json.optString("ResponseDescription").ifBlank { "STK push failed (HTTP ${reply.code})" }
                    }
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e   // cashier pressed Cancel — don't turn it into an error result
        } catch (e: DarajaFailure) {
            StkPushResult.Error(e.message ?: DarajaRules.busyMessage())
        } catch (e: Exception) {
            Log.e(TAG, "initiateStkPush error", e)
            // If the answer was lost after sending, say the customer may already have the prompt, instead of inviting a second push.
            StkPushResult.Error(
                if (DarajaRules.pushOutcomeUnknown(e)) { com.minimart.pos.util.ErrorAdvice.explain(e, "The M-Pesa request"); DarajaRules.unknownOutcomeMessage() }
                else com.minimart.pos.util.ErrorAdvice.explain(e, "The M-Pesa request")
            )
        }
    }

    // ── Query STK status ──────────────────────────────────────────────────────
    suspend fun queryStkStatus(checkoutRequestId: String): StkStatus = withContext(Dispatchers.IO) {
        try {
            val cfg   = settingsRepo.getDarajaConfig()
            val (password, timestamp) = generatePasswordAndTimestamp(cfg.shortcode, cfg.passkey)

            val payload = JSONObject().apply {
                put("BusinessShortCode", cfg.shortcode)
                put("Password", password)
                put("Timestamp", timestamp)
                put("CheckoutRequestID", checkoutRequestId)
            }

            // A status check changes nothing, so it is always safe to repeat.
            val reply = postWithRecovery("/mpesa/stkpushquery/v1/query", payload) { true }
            val json = parseObject(reply.body)
            val kind = DarajaRules.httpKind(reply.code)
            if (json == null || kind == DarajaRules.HttpKind.SERVER_BUSY) return@withContext StkStatus.Error(DarajaRules.busyMessage())
            if (kind == DarajaRules.HttpKind.RATE_LIMITED) return@withContext StkStatus.Error(DarajaRules.rateLimitedMessage())
            val resultCode = json.optString("ResultCode", "-1")
            val errorCode  = json.optString("errorCode", "")
            when {
                // Safaricom error codes meaning "still in progress"
                DarajaRules.isStillProcessing(errorCode) -> StkStatus.Pending
                resultCode == "0" -> StkStatus.Success(
                    mpesaReceiptNumber = json.optString("MpesaReceiptNumber").takeIf { it.isNotBlank() },
                    phoneNumber        = json.optString("PhoneNumber").takeIf { it.isNotBlank() }
                )
                resultCode.isNotBlank() && resultCode != "-1" -> StkStatus.Failed(DarajaRules.resultMessage(resultCode, json.optString("ResultDesc")))
                else -> StkStatus.Pending
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: DarajaFailure) {
            StkStatus.Error(e.message ?: DarajaRules.busyMessage())
        } catch (e: Exception) {
            Log.e(TAG, "queryStkStatus error", e)
            StkStatus.Error(com.minimart.pos.util.ErrorAdvice.explain(e, "Checking the M-Pesa payment"))
        }
    }
}
