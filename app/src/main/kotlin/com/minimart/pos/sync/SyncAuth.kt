package com.minimart.pos.sync

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Signs and verifies LAN sync requests so the pairing code never travels over the network. */
object SyncAuth {
    const val MAX_SKEW_MS = 5 * 60_000L

    private val seen = ConcurrentHashMap<String, Long>()   // signature -> expiry, blocks replays

    fun sign(secret: String, method: String, path: String, body: String, timestamp: Long): String {
        val bodyHash = hex(MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8)))
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return hex(mac.doFinal("$timestamp\n$method\n$path\n$bodyHash".toByteArray(Charsets.UTF_8)))
    }

    fun verify(secret: String, method: String, path: String, body: String,
               timestamp: String?, signature: String?, now: Long = System.currentTimeMillis()): Boolean {
        val ts = timestamp?.toLongOrNull() ?: return false
        if (signature.isNullOrBlank() || kotlin.math.abs(now - ts) > MAX_SKEW_MS) return false
        val expected = sign(secret, method, path, body, ts)
        if (!MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), signature.lowercase().toByteArray(Charsets.UTF_8))) return false
        // Each valid signature works once (within the time window).
        seen.entries.removeIf { it.value < now }
        return seen.putIfAbsent(expected, ts + MAX_SKEW_MS) == null
    }

    /**
     * The main device signs what it sends back, bound to the till's own request timestamp, so a device
     * that merely answers on the main phone's IP address cannot hand a till forged products,
     * prices or customer balances.
     */
    fun signResponse(secret: String, path: String, requestTs: String, body: String): String {
        val bodyHash = hex(MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8)))
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return hex(mac.doFinal("resp\n$requestTs\n$path\n$bodyHash".toByteArray(Charsets.UTF_8)))
    }

    fun verifyResponse(secret: String, path: String, requestTs: String, body: String, signature: String?): Boolean {
        if (signature.isNullOrBlank()) return false
        val expected = signResponse(secret, path, requestTs, body)
        return MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), signature.lowercase().toByteArray(Charsets.UTF_8))
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
}
